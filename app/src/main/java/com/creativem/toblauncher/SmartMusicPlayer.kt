package com.creativem.toblauncher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.runtime.*
import kotlinx.coroutines.*

enum class RepeatMode { ALL, ONE }

data class AudioTrack(val uri: Uri, val title: String)

class SmartMusicPlayer private constructor(private val context: Context) {

    // --- PATRÓN SINGLETON: CONSERVA LA MÚSICA VIVA EN MEMORIA NAVEGUES DONDE NAVEGUES ---
    companion object {
        @Volatile
        private var INSTANCE: SmartMusicPlayer? = null

        fun getInstance(context: Context): SmartMusicPlayer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmartMusicPlayer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences("smart_music_prefs", Context.MODE_PRIVATE)
    private var mediaPlayer: MediaPlayer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // --- ESTADOS OBSERVABLES EN COMPOSE ---

    var isAutoPlayEnabled by mutableStateOf(prefs.getBoolean("auto_play_enabled", true))
        private set

    var playlist = mutableStateListOf<AudioTrack>()
        private set

    var currentTrackIndex by mutableStateOf(-1)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var currentPositionMs by mutableLongStateOf(0L)
        private set

    var totalDurationMs by mutableLongStateOf(1L)
        private set

    var isShuffle by mutableStateOf(prefs.getBoolean("is_shuffle", true))
        private set

    var repeatMode by mutableStateOf(
        if (prefs.getBoolean("repeat_one", false)) RepeatMode.ONE else RepeatMode.ALL
    )
        private set

    var selectedFolderName by mutableStateOf(prefs.getString("folder_name", "Memoria USB") ?: "Memoria USB")
        private set

    var isScanning by mutableStateOf(false)
        private set

    private val playedIndicesHistory = mutableSetOf<Int>()
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    init {
        // SOLAMENTE ESCANEA UNA VEZ CUANDO ENCIENDE LA APP POR PRIMERA VEZ
        autoStartPlaybackOnBoot()
    }

    // --- CONFIGURACIÓN DE AUTOREPRODUCCIÓN ---
    fun toggleAutoPlay() {
        isAutoPlayEnabled = !isAutoPlayEnabled
        prefs.edit().putBoolean("auto_play_enabled", isAutoPlayEnabled).apply()
    }

    private fun autoStartPlaybackOnBoot() {
        if (playlist.isNotEmpty()) return // Si ya está cargada en memoria, NO vuelve a escanear

        val savedFolderUri = prefs.getString("selected_folder_uri", null)
        if (savedFolderUri != null) {
            scanFolderAndAutoPlay(Uri.parse(savedFolderUri))
        } else {
            scanMediaStoreFallbackAndPlay()
        }
    }

    // --- ESCANEAR CARPETA SELECCIONADA MANUALMENTE ---
    fun scanFolder(folderUri: Uri) {
        isScanning = true
        scope.launch(Dispatchers.IO) {
            val tracks = mutableListOf<AudioTrack>()

            selectedFolderName = getFolderName(context, folderUri)
            prefs.edit().putString("folder_name", selectedFolderName).apply()
            prefs.edit().putString("selected_folder_uri", folderUri.toString()).apply()

            val rootDocId = DocumentsContract.getTreeDocumentId(folderUri)
            scanDirectoryNative(context, folderUri, rootDocId, tracks)

            withContext(Dispatchers.Main) {
                playlist.clear()
                playlist.addAll(tracks)
                playedIndicesHistory.clear()
                isScanning = false

                if (playlist.isNotEmpty()) {
                    playTrackAtIndex(0, startPosMs = 0L)
                }
            }
        }
    }

    private fun scanFolderAndAutoPlay(folderUri: Uri) {
        isScanning = true
        scope.launch(Dispatchers.IO) {
            val tracks = mutableListOf<AudioTrack>()
            try {
                val rootDocId = DocumentsContract.getTreeDocumentId(folderUri)
                scanDirectoryNative(context, folderUri, rootDocId, tracks)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                playlist.clear()
                playlist.addAll(tracks)
                isScanning = false

                if (playlist.isNotEmpty()) {
                    val lastTrackUri = prefs.getString("last_track_uri", null)
                    val lastPos = prefs.getLong("last_position_ms", 0L)

                    val savedIndex = playlist.indexOfFirst { it.uri.toString() == lastTrackUri }
                    val indexToPlay = if (savedIndex != -1) savedIndex else 0

                    if (isAutoPlayEnabled) {
                        playTrackAtIndex(indexToPlay, startPosMs = lastPos)
                    } else {
                        prepareTrackAtIndex(indexToPlay, startPosMs = lastPos)
                    }
                } else {
                    scanMediaStoreFallbackAndPlay()
                }
            }
        }
    }

    private fun scanMediaStoreFallbackAndPlay() {
        scope.launch(Dispatchers.IO) {
            val tracks = mutableListOf<AudioTrack>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DISPLAY_NAME
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

            try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol) ?: cursor.getString(nameCol) ?: "Pista"
                        val contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                        tracks.add(AudioTrack(contentUri, title.substringBeforeLast(".")))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                if (tracks.isNotEmpty()) {
                    playlist.clear()
                    playlist.addAll(tracks)
                    selectedFolderName = "Memoria USB"

                    val lastTrackUri = prefs.getString("last_track_uri", null)
                    val lastPos = prefs.getLong("last_position_ms", 0L)

                    val savedIndex = playlist.indexOfFirst { it.uri.toString() == lastTrackUri }
                    val indexToPlay = if (savedIndex != -1) savedIndex else 0

                    if (isAutoPlayEnabled) {
                        playTrackAtIndex(indexToPlay, startPosMs = lastPos)
                    } else {
                        prepareTrackAtIndex(indexToPlay, startPosMs = lastPos)
                    }
                }
            }
        }
    }

    private fun scanDirectoryNative(
        context: Context,
        treeUri: Uri,
        dirDocId: String,
        tracksList: MutableList<AudioTrack>
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol) ?: "Pista"
                    val mime = cursor.getString(mimeCol) ?: ""

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        scanDirectoryNative(context, treeUri, docId, tracksList)
                    } else if (isAudioFile(mime, name)) {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        tracksList.add(AudioTrack(fileUri, name.substringBeforeLast(".")))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFolderName(context: Context, treeUri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "Carpeta USB"
        } catch (e: Exception) {
            "Carpeta USB"
        }
    }

    private fun isAudioFile(mimeType: String?, name: String?): Boolean {
        if (mimeType?.startsWith("audio/") == true) return true
        val ext = name?.substringAfterLast(".", "")?.lowercase() ?: ""
        return ext in listOf("mp3", "wav", "flac", "m4a", "aac", "ogg")
    }

    // --- CONTROLES DE REPRODUCCIÓN ---
    fun togglePlayPause() {
        if (mediaPlayer == null && playlist.isNotEmpty()) {
            playTrackAtIndex(if (currentTrackIndex >= 0) currentTrackIndex else 0)
            return
        }

        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
                saveCurrentState()
            } else {
                requestAudioFocus()
                player.start()
                isPlaying = true
                startProgressTracker()
            }
        }
    }

    fun playNextTrack(userTriggered: Boolean = false) {
        if (playlist.isEmpty()) return

        if (repeatMode == RepeatMode.ONE && !userTriggered) {
            playTrackAtIndex(currentTrackIndex, 0L)
            return
        }

        if (isShuffle) {
            if (playedIndicesHistory.size >= playlist.size) {
                playedIndicesHistory.clear()
            }
            playedIndicesHistory.add(currentTrackIndex)

            val unplayedIndices = playlist.indices.filter { it !in playedIndicesHistory }
            val nextIndex = if (unplayedIndices.isNotEmpty()) {
                unplayedIndices.random()
            } else {
                playlist.indices.random()
            }

            playTrackAtIndex(nextIndex, 0L)
        } else {
            val nextIndex = (currentTrackIndex + 1) % playlist.size
            playTrackAtIndex(nextIndex, 0L)
        }
    }

    fun playPreviousTrack() {
        if (playlist.isEmpty()) return
        val prevIndex = if (currentTrackIndex - 1 < 0) playlist.size - 1 else currentTrackIndex - 1
        playTrackAtIndex(prevIndex, 0L)
    }

    fun toggleShuffle() {
        isShuffle = !isShuffle
        prefs.edit().putBoolean("is_shuffle", isShuffle).apply()
        playedIndicesHistory.clear()
    }

    fun toggleRepeatMode() {
        repeatMode = if (repeatMode == RepeatMode.ALL) RepeatMode.ONE else RepeatMode.ALL
        prefs.edit().putBoolean("repeat_one", repeatMode == RepeatMode.ONE).apply()
    }

    private fun prepareTrackAtIndex(index: Int, startPosMs: Long) {
        if (index !in playlist.indices) return
        currentTrackIndex = index
        val track = playlist[index]

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, track.uri)
                prepare()
                seekTo(startPosMs.toInt())
                totalDurationMs = duration.toLong().coerceAtLeast(1L)
                currentPositionMs = startPosMs
                setOnCompletionListener { playNextTrack(userTriggered = false) }
            }
            isPlaying = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

 fun playTrackAtIndex(index: Int, startPosMs: Long = 0L) {
        if (index !in playlist.indices) return
        currentTrackIndex = index
        val track = playlist[index]

        try {
            requestAudioFocus()
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, track.uri)
                prepare()
                seekTo(startPosMs.toInt())
                totalDurationMs = duration.toLong().coerceAtLeast(1L)
                currentPositionMs = startPosMs
                start()
                setOnCompletionListener { playNextTrack(userTriggered = false) }
            }
            isPlaying = true
            startProgressTracker()
            saveCurrentState()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && isPlaying) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        currentPositionMs = player.currentPosition.toLong()
                        saveCurrentState()
                    }
                }
                delay(1000L)
            }
        }
    }

    private fun saveCurrentState() {
        if (currentTrackIndex in playlist.indices) {
            val trackUri = playlist[currentTrackIndex].uri.toString()
            prefs.edit()
                .putString("last_track_uri", trackUri)
                .putLong("last_position_ms", currentPositionMs)
                .apply()
        }
    }

    fun release() {
        progressJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}