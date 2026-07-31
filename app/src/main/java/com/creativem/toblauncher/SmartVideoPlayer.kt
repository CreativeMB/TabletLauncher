package com.creativem.toblauncher

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import java.io.File

data class VideoTrack(val uri: Uri, val title: String)

class SmartVideoPlayer private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: SmartVideoPlayer? = null

        fun getInstance(context: Context): SmartVideoPlayer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmartVideoPlayer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    var isShuffleMode by mutableStateOf(false)
        private set

    fun toggleShuffle() {
        isShuffleMode = !isShuffleMode
    }
    private val prefs = context.getSharedPreferences("smart_video_prefs", Context.MODE_PRIVATE)
    var mediaPlayer: MediaPlayer? = null
        private set

    var playlist = mutableStateListOf<VideoTrack>()
        private set

    var currentTrackIndex by mutableStateOf(-1)
        private set
    // Dentro de la clase SmartVideoPlayer:
    var savedPlaybackPosition: Long = 0L
    var isPlaying by mutableStateOf(false)
        private set

    var currentPositionMs by mutableLongStateOf(0L)
        private set

    var totalDurationMs by mutableLongStateOf(1L)
        private set

    var selectedFolderName by mutableStateOf(prefs.getString("folder_name", "Memoria USB") ?: "Memoria USB")
        private set

    var isScanning by mutableStateOf(false)
        private set

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    init {
        autoStartVideoOnBoot()
    }

    private fun autoStartVideoOnBoot() {
        if (playlist.isNotEmpty()) return

        val savedFolderPath = prefs.getString("selected_folder_path", null)
        if (savedFolderPath != null && File(savedFolderPath).exists()) {
            scanVideoFolderPath(File(savedFolderPath))
        } else {
            scanMediaStoreVideoFallback()
        }
    }

    // --- ESCANEAR CARPETA Y SUBCARPETAS DE VIDEO (UNIVERSAL PROFUNDO) ---
    fun scanVideoFolderPath(folderFile: File) {
        isScanning = true
        selectedFolderName = folderFile.name.ifEmpty { "Memoria USB" }
        prefs.edit().putString("folder_name", selectedFolderName).apply()
        prefs.edit().putString("selected_folder_path", folderFile.absolutePath).apply()

        scope.launch(Dispatchers.IO) {
            val tracks = mutableListOf<VideoTrack>()

            // 1. Escaneo por recorrido directo de archivos Java
            try {
                if (folderFile.exists()) {
                    folderFile.walkTopDown()
                        .filter { it.isFile && isVideoFile(it.name) }
                        .forEach { file ->
                            tracks.add(VideoTrack(Uri.fromFile(file), file.nameWithoutExtension))
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Si Java devolvió 0 videos (por Scoped Storage de Android en USB), filtra vía MediaStore
            if (tracks.isEmpty()) {
                scanVideoViaMediaStoreUniversal(folderFile.absolutePath, folderFile.name, tracks)
            }

            // 3. Respaldo total: Carga todos los videos detectados en la memoria externa/USB
            if (tracks.isEmpty()) {
                scanAllVideosUniversal(tracks)
            }

            withContext(Dispatchers.Main) {
                playlist.clear()
                playlist.addAll(tracks)
                isScanning = false

                if (playlist.isNotEmpty()) {
                    val lastVideoUri = prefs.getString("last_video_uri", null)
                    val lastPos = prefs.getLong("last_position_ms", 0L)
                    val savedIndex = playlist.indexOfFirst { it.uri.toString() == lastVideoUri }
                    val indexToPlay = if (savedIndex != -1) savedIndex else 0

                    prepareVideoAtIndex(indexToPlay, lastPos)
                }
            }
        }
    }

    // ESCÁNER UNIVERSAL EN MEMORIA (EVITA BLOQUEOS SQL DE ANDROID 10/11/12)
    private fun scanVideoViaMediaStoreUniversal(folderPath: String, folderName: String, tracksList: MutableList<VideoTrack>) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA
        )

        val urisToQuery = listOf(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.INTERNAL_CONTENT_URI
        )

        for (contentUri in urisToQuery) {
            try {
                context.contentResolver.query(
                    contentUri,
                    projection,
                    null, // Sin filtro SQL restringido
                    null,
                    "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                    val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                    val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                    while (cursor.moveToNext()) {
                        val id = if (idCol != -1) cursor.getLong(idCol) else -1L
                        val title = if (titleCol != -1) cursor.getString(titleCol) else null
                        val name = if (nameCol != -1) cursor.getString(nameCol) else null
                        val filePath = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""

                        val videoName = title ?: name ?: "Video"

                        // Filtra si la ruta de la subcarpeta coincide o contiene el nombre de la carpeta
                        val matchesFolder = filePath.isEmpty() ||
                                filePath.contains(folderPath, ignoreCase = true) ||
                                filePath.contains(folderName, ignoreCase = true)

                        if (matchesFolder) {
                            val videoUri = if (id != -1L) Uri.withAppendedPath(contentUri, id.toString()) else if (filePath.isNotEmpty()) Uri.fromFile(File(filePath)) else null
                            if (videoUri != null) {
                                tracksList.add(VideoTrack(videoUri, videoName.substringBeforeLast(".")))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun scanMediaStoreVideoFallback() {
        scope.launch(Dispatchers.IO) {
            val tracks = mutableListOf<VideoTrack>()
            scanAllVideosUniversal(tracks)

            withContext(Dispatchers.Main) {
                if (tracks.isNotEmpty()) {
                    playlist.clear()
                    playlist.addAll(tracks)
                    selectedFolderName = "Memoria USB"

                    val lastVideoUri = prefs.getString("last_video_uri", null)
                    val lastPos = prefs.getLong("last_position_ms", 0L)
                    val savedIndex = playlist.indexOfFirst { it.uri.toString() == lastVideoUri }
                    val indexToPlay = if (savedIndex != -1) savedIndex else 0

                    prepareVideoAtIndex(indexToPlay, lastPos)
                }
            }
        }
    }

    private fun scanAllVideosUniversal(tracksList: MutableList<VideoTrack>) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME
        )

        val urisToQuery = listOf(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.INTERNAL_CONTENT_URI
        )

        for (contentUri in urisToQuery) {
            try {
                context.contentResolver.query(
                    contentUri,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                    val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                    val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)

                    while (cursor.moveToNext()) {
                        val id = if (idCol != -1) cursor.getLong(idCol) else -1L
                        val title = if (titleCol != -1) cursor.getString(titleCol) else null
                        val name = if (nameCol != -1) cursor.getString(nameCol) else null

                        val videoName = title ?: name ?: "Video"
                        if (id != -1L) {
                            val videoUri = Uri.withAppendedPath(contentUri, id.toString())
                            tracksList.add(VideoTrack(videoUri, videoName.substringBeforeLast(".")))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isVideoFile(name: String?): Boolean {
        val ext = name?.substringAfterLast(".", "")?.lowercase() ?: ""
        return ext in listOf("mp4", "mkv", "avi", "webm", "3gp", "mov", "m4v", "ts", "mpg", "flv")
    }

    // --- CONTROLES DE REPRODUCCIÓN ---
    fun togglePlayPause(videoUrl: String) {
        try {
            if (mediaPlayer == null) {
                // Si la instancia se destruyó por completo, la creamos de nuevo
                prepareAndPlay(videoUrl)
                return
            }

            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            } else {
                // Si no está reproduciéndose, intentamos iniciar.
                // Si el reproductor está en estado Idle/Stopped, esto fallará o no hará nada,
                // por lo que evaluamos si necesitamos llamar a prepare() nuevamente.
                mediaPlayer?.start()
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            // Si el estado no es válido, reintentamos preparar el video
            prepareAndPlay(videoUrl)
        }
    }
    fun prepareAndPlay(videoUrl: String) {
        try {
            mediaPlayer?.reset() // Limpia cualquier estado anterior inválido
            mediaPlayer?.setDataSource(videoUrl) // Vuelve a asignar la fuente
            mediaPlayer?.prepareAsync() // Prepara el video de forma asíncrona

            mediaPlayer?.setOnPreparedListener { mp ->
                // Una vez que está listo, ahora sí puede reproducir sin problemas
                mp.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun playNextVideo() {
        if (playlist.isEmpty()) return
        val nextIndex = if (isShuffleMode && playlist.size > 1) {
            var randomIndex = playlist.indices.random()
            while (randomIndex == currentTrackIndex) {
                randomIndex = playlist.indices.random()
            }
            randomIndex
        } else {
            (currentTrackIndex + 1) % playlist.size
        }
        playVideoAtIndex(nextIndex, 0L) // Fuerza siempre a iniciar desde el inicio (0)
    }

    fun playPreviousVideo() {
        if (playlist.isEmpty()) return
        val prevIndex = if (isShuffleMode && playlist.size > 1) {
            playlist.indices.random()
        } else {
            if (currentTrackIndex - 1 < 0) playlist.size - 1 else currentTrackIndex - 1
        }
        playVideoAtIndex(prevIndex, 0L) // Fuerza siempre a iniciar desde el inicio (0)
    }

    fun prepareVideoAtIndex(index: Int, startPosMs: Long = 0L) {
        if (index !in playlist.indices) return
        currentTrackIndex = index
        currentPositionMs = startPosMs
    }

    fun playVideoAtIndex(index: Int, position: Long = 0L) {
        if (playlist.isEmpty()) return
        currentTrackIndex = index.coerceIn(0, playlist.size - 1)
        val video = playlist[currentTrackIndex]

        try {
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(video.uri.toString())
            mediaPlayer?.prepareAsync()
            mediaPlayer?.setOnPreparedListener { mp ->
                bindMediaPlayer(mp)
                mp.seekTo(0) // Siempre empieza desde el principio (0)
                mp.start()
                isPlaying = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun bindMediaPlayer(player: MediaPlayer) {
        this.mediaPlayer = player
        player.setOnCompletionListener {
            playNextVideo()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && isPlaying) {
                mediaPlayer?.let { player ->
                    try {
                        // Intentamos consultar el estado del reproductor
                        if (player.isPlaying) {
                            currentPositionMs = player.currentPosition.toLong()
                            totalDurationMs = player.duration.toLong().coerceAtLeast(1L)
                            saveCurrentState()
                        }
                    } catch (e: IllegalStateException) {
                        // El reproductor fue liberado, reiniciado o está en un estado inválido.
                        // Apagamos el estado de reproducción para salir del ciclo (while).
                        isPlaying = false
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                delay(1000L)
            }
        }
    }

    fun saveCurrentState() {
        if (currentTrackIndex in playlist.indices) {
            val trackUri = prefs.getString("last_video_uri", null) ?: playlist[currentTrackIndex].uri.toString()
            prefs.edit()
                .putString("last_video_uri", trackUri)
                .putLong("last_position_ms", currentPositionMs)
                .apply()
        }
    }
}