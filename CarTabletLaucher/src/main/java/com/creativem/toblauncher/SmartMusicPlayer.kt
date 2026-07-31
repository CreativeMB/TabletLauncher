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
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import java.io.File

enum class RepeatMode { ALL, ONE }

data class AudioTrack(val uri: Uri, val title: String)

class SmartMusicPlayer private constructor(private val context: Context) {

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
    private var audioFocusRequest: AudioFocusRequest? = null

    // Sesión de medios del sistema
    private var mediaSession: MediaSession? = null

    // --- ALGORITMO INTELIGENTE DE SHUFFLE PARA MILES DE PISTAS ---
    private val shuffledDeck = mutableListOf<Int>()  // Mazo de cartas barajadas sin repetir
    private val historyStack = mutableListOf<Int>()   // Historial para botón "Anterior"

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

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    init {
        setupMediaSession()
        autoStartPlaybackOnBoot()
    }

    fun toggleAutoPlay() {
        isAutoPlayEnabled = !isAutoPlayEnabled
        prefs.edit().putBoolean("auto_play_enabled", isAutoPlayEnabled).apply()
    }

    private fun autoStartPlaybackOnBoot() {
        if (playlist.isNotEmpty()) return

        val savedFolderPath = prefs.getString("selected_folder_path", null)
        val savedFolderUri = prefs.getString("selected_folder_uri", null)

        if (savedFolderPath != null && File(savedFolderPath).exists()) {
            scanFolderPathAndAutoPlay(File(savedFolderPath))
        } else if (savedFolderUri != null) {
            scanFolderAndAutoPlay(Uri.parse(savedFolderUri))
        } else {
            scanMediaStoreFallbackAndPlay()
        }
    }

    // --- RECONSTRUCCIÓN INTELIGENTE DEL MAZO DE SHUFFLE ---
    private fun rebuildShuffledDeck() {
        shuffledDeck.clear()
        if (playlist.isEmpty()) return

        val indices = playlist.indices.toMutableList()
        // Evitamos que la canción actual vuelva a salir de primera en el mazo nuevo
        if (currentTrackIndex in indices) {
            indices.remove(currentTrackIndex)
        }
        indices.shuffle()
        shuffledDeck.addAll(indices)
    }

    // --- CONFIGURACIÓN DE LA SESIÓN DE MEDIOS ---
    private fun setupMediaSession() {
        try {
            mediaSession = MediaSession(context, "SmartMusicPlayer").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)

                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        togglePlayPause()
                    }

                    override fun onPause() {
                        pausePlayback()
                    }

                    override fun onSkipToNext() {
                        playNextTrack(userTriggered = true)
                    }

                    override fun onSkipToPrevious() {
                        playPreviousTrack()
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

    // --- ESCANEAR CARPETA DIRECTA DESDE EL EXPLORADOR INTERNO ---
    fun scanFolderPath(folderFile: File) {
        isScanning = true
        selectedFolderName = folderFile.name.ifEmpty { "Memoria USB" }
        prefs.edit().putString("folder_name", selectedFolderName).apply()
        prefs.edit().putString("selected_folder_path", folderFile.absolutePath).apply()

        scope.launch(Dispatchers.IO) {
            val tracks = mutableListOf<AudioTrack>()

            try {
                if (folderFile.exists()) {
                    folderFile.walkTopDown()
                        .filter { it.isFile && isAudioFile(null, it.name) }
                        .forEach { file ->
                            tracks.add(AudioTrack(Uri.fromFile(file), file.nameWithoutExtension))
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (tracks.isEmpty()) {
                scanAudioViaMediaStoreUniversal(folderFile.absolutePath, folderFile.name, tracks)
            }

            if (tracks.isEmpty()) {
                scanMediaStoreFallbackInternal(tracks)
            }

            withContext(Dispatchers.Main) {
                playlist.clear()
                playlist.addAll(tracks)
                historyStack.clear()
                rebuildShuffledDeck()
                isScanning = false

                if (playlist.isNotEmpty()) {
                    playTrackAtIndex(0, startPosMs = 0L)
                }
            }
        }
    }

    private fun scanFolderPathAndAutoPlay(folderFile: File) {
        isScanning = true
        scope.launch(Dispatchers.IO) {
            val tracks = mutableListOf<AudioTrack>()
            try {
                if (folderFile.exists()) {
                    folderFile.walkTopDown()
                        .filter { it.isFile && isAudioFile(null, it.name) }
                        .forEach { file ->
                            tracks.add(AudioTrack(Uri.fromFile(file), file.nameWithoutExtension))
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (tracks.isEmpty()) {
                scanAudioViaMediaStoreUniversal(folderFile.absolutePath, folderFile.name, tracks)
            }

            withContext(Dispatchers.Main) {
                playlist.clear()
                playlist.addAll(tracks)
                historyStack.clear()
                rebuildShuffledDeck()
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

    private fun scanAudioViaMediaStoreUniversal(folderPath: String, folderName: String, tracksList: MutableList<AudioTrack>) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA
        )

        val urisToQuery = listOf(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.INTERNAL_CONTENT_URI
        )

        for (contentUri in urisToQuery) {
            try {
                context.contentResolver.query(
                    contentUri,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                    while (cursor.moveToNext()) {
                        val id = if (idCol != -1) cursor.getLong(idCol) else -1L
                        val title = if (titleCol != -1) cursor.getString(titleCol) else null
                        val name = if (nameCol != -1) cursor.getString(nameCol) else null
                        val filePath = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""

                        val audioName = title ?: name ?: "Pista"

                        val matchesFolder = filePath.isEmpty() ||
                                filePath.contains(folderPath, ignoreCase = true) ||
                                filePath.contains(folderName, ignoreCase = true)

                        if (matchesFolder) {
                            val audioUri = if (id != -1L) {
                                Uri.withAppendedPath(contentUri, id.toString())
                            } else if (filePath.isNotEmpty()) {
                                Uri.fromFile(File(filePath))
                            } else null

                            if (audioUri != null) {
                                tracksList.add(AudioTrack(audioUri, audioName.substringBeforeLast(".")))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun scanFolder(selectedUri: Uri) {
        isScanning = true
        scope.launch(Dispatchers.IO) {
            val tracks = mutableListOf<AudioTrack>()

            try {
                selectedFolderName = getFolderName(context, selectedUri)
                prefs.edit().putString("folder_name", selectedFolderName).apply()
                prefs.edit().putString("selected_folder_uri", selectedUri.toString()).apply()

                val rootDocId = DocumentsContract.getTreeDocumentId(selectedUri)
                scanDirectoryNative(context, selectedUri, rootDocId, tracks)
            } catch (e: Exception) {
                e.printStackTrace()
                scanMediaStoreFallbackInternal(tracks)
            }

            withContext(Dispatchers.Main) {
                playlist.clear()
                playlist.addAll(tracks)
                historyStack.clear()
                rebuildShuffledDeck()
                isScanning = false

                if (playlist.isNotEmpty()) {
                    val selectedIndex = playlist.indexOfFirst { it.uri == selectedUri }
                    val indexToPlay = if (selectedIndex != -1) selectedIndex else 0
                    playTrackAtIndex(indexToPlay, startPosMs = 0L)
                }
            }
        }
    }

    fun forceAutoScanUsb() {
        isScanning = true
        selectedFolderName = "Memoria USB"
        prefs.edit().putString("folder_name", selectedFolderName).apply()

        scope.launch(Dispatchers.IO) {
            val tracks = mutableListOf<AudioTrack>()
            scanMediaStoreFallbackInternal(tracks)

            withContext(Dispatchers.Main) {
                playlist.clear()
                playlist.addAll(tracks)
                historyStack.clear()
                rebuildShuffledDeck()
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
                scanMediaStoreFallbackInternal(tracks)
            }

            withContext(Dispatchers.Main) {
                playlist.clear()
                playlist.addAll(tracks)
                historyStack.clear()
                rebuildShuffledDeck()
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
            scanMediaStoreFallbackInternal(tracks)

            withContext(Dispatchers.Main) {
                if (tracks.isNotEmpty()) {
                    playlist.clear()
                    playlist.addAll(tracks)
                    selectedFolderName = "Memoria USB"
                    historyStack.clear()
                    rebuildShuffledDeck()

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

    private fun scanMediaStoreFallbackInternal(tracksList: MutableList<AudioTrack>) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val urisToQuery = listOf(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.INTERNAL_CONTENT_URI
        )

        for (contentUri in urisToQuery) {
            try {
                context.contentResolver.query(
                    contentUri,
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
                        val audioUri = Uri.withAppendedPath(contentUri, id.toString())
                        tracksList.add(AudioTrack(audioUri, title.substringBeforeLast(".")))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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

    // --- CONTROLES DE REPRODUCCIÓN Y MANEJO DE FOCO ---

    fun pausePlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            }
        }
        isPlaying = false
        updatePlaybackState(PlaybackState.STATE_PAUSED)
        mediaSession?.isActive = false
        abandonAudioFocus()
        saveCurrentState()
    }

    fun togglePlayPause() {
        if (mediaPlayer == null && playlist.isNotEmpty()) {
            playTrackAtIndex(if (currentTrackIndex >= 0) currentTrackIndex else 0)
            return
        }

        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                pausePlayback()
            } else {
                requestAudioFocus()
                mediaSession?.isActive = true
                player.start()
                isPlaying = true
                updatePlaybackState(PlaybackState.STATE_PLAYING)
                startProgressTracker()
            }
        }
    }

    // --- NAVEGACIÓN INTELIGENTE DE CANCIONES (SIN REPETICIONES) ---
    fun playNextTrack(userTriggered: Boolean = false) {
        if (playlist.isEmpty()) return

        // Repetir una sola canción
        if (repeatMode == RepeatMode.ONE && !userTriggered) {
            playTrackAtIndex(currentTrackIndex, 0L)
            return
        }

        // Guardamos en el historial de canciones escuchadas
        if (currentTrackIndex in playlist.indices) {
            historyStack.add(currentTrackIndex)
            // Limitamos el historial a 100 elementos para evitar fugas de memoria
            if (historyStack.size > 100) historyStack.removeAt(0)
        }

        if (isShuffle) {
            // Si el mazo se agotó (se escucharon TODAS las canciones de la carpeta) -> Rebarajamos
            if (shuffledDeck.isEmpty()) {
                rebuildShuffledDeck()
            }

            if (shuffledDeck.isNotEmpty()) {
                val nextIndex = shuffledDeck.removeAt(0)
                playTrackAtIndex(nextIndex, 0L)
            } else {
                playTrackAtIndex(0, 0L)
            }
        } else {
            // MODO NORMAL DE CORRIDO (Secuencial: 0, 1, 2... N-1, 0)
            val nextIndex = (currentTrackIndex + 1) % playlist.size
            playTrackAtIndex(nextIndex, 0L)
        }
    }

    fun playPreviousTrack() {
        if (playlist.isEmpty()) return

        if (isShuffle && historyStack.isNotEmpty()) {
            // Regresa a la canción exacta que acabas de escuchar
            val prevIndex = historyStack.removeAt(historyStack.size - 1)
            playTrackAtIndex(prevIndex, 0L)
        } else {
            // MODO NORMAL DE CORRIDO EN REVERSA
            val prevIndex = if (currentTrackIndex - 1 < 0) playlist.size - 1 else currentTrackIndex - 1
            playTrackAtIndex(prevIndex, 0L)
        }
    }

    fun toggleShuffle() {
        isShuffle = !isShuffle
        prefs.edit().putBoolean("is_shuffle", isShuffle).apply()
        historyStack.clear()
        if (isShuffle) {
            rebuildShuffledDeck()
        }
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
            mediaSession?.isActive = false
            updatePlaybackState(PlaybackState.STATE_PAUSED)
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
            mediaSession?.isActive = true
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
            updatePlaybackState(PlaybackState.STATE_PLAYING)
            startProgressTracker()
            saveCurrentState()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val builder = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
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

        abandonAudioFocus()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
    }
}