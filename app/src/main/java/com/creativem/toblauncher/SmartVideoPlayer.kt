package com.creativem.toblauncher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
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

    private val prefs = context.getSharedPreferences("smart_video_prefs", Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    // --- ALGORITMO INTELIGENTE DE SHUFFLE PARA MILES DE VIDEOS ---
    private val shuffledDeck = mutableListOf<Int>()  // Mazo de videos barajados sin repetir
    private val historyStack = mutableListOf<Int>()   // Historial para el botón "Anterior"

    // Modo Shuffle persistente
    var isShuffleMode by mutableStateOf(prefs.getBoolean("is_shuffle_mode", false))
        private set

    fun toggleShuffle() {
        isShuffleMode = !isShuffleMode
        prefs.edit().putBoolean("is_shuffle_mode", isShuffleMode).apply()
        historyStack.clear()
        if (isShuffleMode) {
            rebuildShuffledDeck()
        }
    }

    var mediaPlayer: MediaPlayer? = null
        private set

    var playlist = mutableStateListOf<VideoTrack>()
        private set

    var currentTrackIndex by mutableStateOf(-1)
        private set

    var savedPlaybackPosition: Long = 0L

    // Sesión de medios nativa para el reproductor de video
    private var mediaSession: MediaSession? = null

    private val _isPlaying = mutableStateOf(false)

    // Setter sincronizado con Compose, el foco de audio y la MediaSession
    var isPlaying: Boolean
        get() = _isPlaying.value
        set(value) {
            _isPlaying.value = value
            if (value) {
                requestAudioFocus()
                mediaSession?.isActive = true
                updatePlaybackState(PlaybackState.STATE_PLAYING)
                startProgressTracker()
            } else {
                updatePlaybackState(PlaybackState.STATE_PAUSED)
                mediaSession?.isActive = false
                abandonAudioFocus()
                progressJob?.cancel()
            }
        }

    var currentPositionMs by mutableLongStateOf(0L)
        private set

    var totalDurationMs by mutableLongStateOf(1L)
        private set

    var selectedFolderName by mutableStateOf(prefs.getString("folder_name", "Memoria USB") ?: "Memoria USB")
        private set

    var isScanning by mutableStateOf(false)
        private set
    var isFullscreenActive by mutableStateOf(false)
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    init {
        setupMediaSession()
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

    // --- RECONSTRUCCIÓN INTELIGENTE DEL MAZO DE VIDEOS ---
    private fun rebuildShuffledDeck() {
        shuffledDeck.clear()
        if (playlist.isEmpty()) return

        val indices = playlist.indices.toMutableList()
        if (currentTrackIndex in indices) {
            indices.remove(currentTrackIndex)
        }
        indices.shuffle()
        shuffledDeck.addAll(indices)
    }

    // --- CONFIGURACIÓN DE LA SESIÓN DE MEDIOS PARA VIDEOS ---
    private fun setupMediaSession() {
        try {
            mediaSession = MediaSession(context, "SmartVideoPlayer").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)

                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        val currentVideo = playlist.getOrNull(currentTrackIndex)
                        togglePlayPause(currentVideo?.uri?.toString() ?: "")
                    }

                    override fun onPause() {
                        pausePlayback()
                    }

                    override fun onSkipToNext() {
                        playNextVideo()
                    }

                    override fun onSkipToPrevious() {
                        playPreviousVideo()
                    }
                })
                isActive = false
            }
            updatePlaybackState(PlaybackState.STATE_NONE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updatePlaybackState(state: Int) {
        try {
            val stateBuilder = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(state, currentPositionMs, 1.0f)

            mediaSession?.setPlaybackState(stateBuilder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Escaneo de carpetas de video
    fun scanVideoFolderPath(folderFile: File) {
        isScanning = true
        selectedFolderName = folderFile.name.ifEmpty { "Memoria USB" }
        prefs.edit().putString("folder_name", selectedFolderName).apply()
        prefs.edit().putString("selected_folder_path", folderFile.absolutePath).apply()

        scope.launch(Dispatchers.IO) {
            val tracks = mutableListOf<VideoTrack>()

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

            if (tracks.isEmpty()) {
                scanVideoViaMediaStoreUniversal(folderFile.absolutePath, folderFile.name, tracks)
            }

            if (tracks.isEmpty()) {
                scanAllVideosUniversal(tracks)
            }

            withContext(Dispatchers.Main) {
                playlist.clear()
                playlist.addAll(tracks)
                historyStack.clear()
                rebuildShuffledDeck()
                isScanning = false

                if (playlist.isNotEmpty()) {
                    val lastVideoUri = prefs.getString("last_video_uri", null)
                    val savedIndex = playlist.indexOfFirst { it.uri.toString() == lastVideoUri }
                    val indexToPlay = if (savedIndex != -1) savedIndex else 0

                    prepareVideoAtIndex(indexToPlay, 0L)
                }
            }
        }
    }

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
                    null,
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
                    historyStack.clear()
                    rebuildShuffledDeck()

                    val lastVideoUri = prefs.getString("last_video_uri", null)
                    val savedIndex = playlist.indexOfFirst { it.uri.toString() == lastVideoUri }
                    val indexToPlay = if (savedIndex != -1) savedIndex else 0

                    prepareVideoAtIndex(indexToPlay, 0L)
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

    fun pausePlayback() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isPlaying.value = false
        updatePlaybackState(PlaybackState.STATE_PAUSED)
        mediaSession?.isActive = false
        abandonAudioFocus()
        progressJob?.cancel()
        saveCurrentState()
    }

    fun togglePlayPause(videoUrl: String) {
        try {
            if (mediaPlayer == null) {
                prepareAndPlay(videoUrl)
                return
            }

            if (mediaPlayer?.isPlaying == true) {
                pausePlayback()
            } else {
                requestAudioFocus()
                mediaSession?.isActive = true
                mediaPlayer?.start()
                _isPlaying.value = true
                updatePlaybackState(PlaybackState.STATE_PLAYING)
                startProgressTracker()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            prepareAndPlay(videoUrl)
        }
    }

    fun prepareAndPlay(videoUrl: String) {
        try {
            requestAudioFocus()
            mediaSession?.isActive = true
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(videoUrl)
            mediaPlayer?.prepareAsync()

            mediaPlayer?.setOnPreparedListener { mp ->
                bindMediaPlayer(mp)
                mp.start()
                _isPlaying.value = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- NAVEGACIÓN INTELIGENTE DE VIDEOS (SIN REPETICIONES) ---
    fun playNextVideo() {
        if (playlist.isEmpty()) return

        // Guardamos el video actual en la pila del historial
        if (currentTrackIndex in playlist.indices) {
            historyStack.add(currentTrackIndex)
            if (historyStack.size > 100) historyStack.removeAt(0)
        }

        val nextIndex = if (isShuffleMode) {
            // Si el mazo se agotó (se vieron TODOS los videos de la carpeta) -> Rebarajamos
            if (shuffledDeck.isEmpty()) {
                rebuildShuffledDeck()
            }

            if (shuffledDeck.isNotEmpty()) {
                shuffledDeck.removeAt(0)
            } else {
                0
            }
        } else {
            // MODO NORMAL DE CORRIDO (Secuencial: 0, 1, 2... N-1, 0)
            (currentTrackIndex + 1) % playlist.size
        }

        playVideoAtIndex(nextIndex, 0L)
    }

    fun playPreviousVideo() {
        if (playlist.isEmpty()) return

        val prevIndex = if (isShuffleMode && historyStack.isNotEmpty()) {
            // Regresa al video exacto que acabas de ver
            historyStack.removeAt(historyStack.size - 1)
        } else {
            // MODO NORMAL DE CORRIDO EN REVERSA
            if (currentTrackIndex - 1 < 0) playlist.size - 1 else currentTrackIndex - 1
        }

        playVideoAtIndex(prevIndex, 0L)
    }

    fun prepareVideoAtIndex(index: Int, startPosMs: Long = 0L) {
        if (index !in playlist.indices) return
        currentTrackIndex = index
        currentPositionMs = startPosMs
        mediaSession?.isActive = false
        updatePlaybackState(PlaybackState.STATE_PAUSED)
    }

    fun playVideoAtIndex(index: Int, position: Long = 0L) {
        if (playlist.isEmpty()) return
        currentTrackIndex = index.coerceIn(0, playlist.size - 1)
        val video = playlist[currentTrackIndex]

        try {
            requestAudioFocus()
            mediaSession?.isActive = true
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(video.uri.toString())
            mediaPlayer?.prepareAsync()
            mediaPlayer?.setOnPreparedListener { mp ->
                bindMediaPlayer(mp)
                mp.seekTo(position.toInt())
                mp.start()
                _isPlaying.value = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun bindMediaPlayer(player: MediaPlayer) {
        this.mediaPlayer = player
        this._isPlaying.value = player.isPlaying
        if (player.isPlaying) {
            requestAudioFocus()
            mediaSession?.isActive = true
            updatePlaybackState(PlaybackState.STATE_PLAYING)
        } else {
            mediaSession?.isActive = false
            updatePlaybackState(PlaybackState.STATE_PAUSED)
        }
        this.totalDurationMs = player.duration.toLong().coerceAtLeast(1L)
        startProgressTracker()

        player.setOnCompletionListener {
            playNextVideo()
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val builder = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                audioFocusRequest = builder.build()
                audioManager.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            currentPositionMs = player.currentPosition.toLong()
                            totalDurationMs = player.duration.toLong().coerceAtLeast(1L)
                            saveCurrentState()
                        }
                    } catch (e: IllegalStateException) {
                        // Reproductor inestable
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
            val trackUri = playlist[currentTrackIndex].uri.toString()
            prefs.edit()
                .putString("last_video_uri", trackUri)
                .putLong("last_position_ms", currentPositionMs)
                .apply()
        }
    }

    fun release() {
        progressJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null

        abandonAudioFocus()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
    }
}