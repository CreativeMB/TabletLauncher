package com.creativem.toblauncher

import android.content.Context
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import kotlinx.coroutines.*
import java.io.File

enum class RepeatMode { ALL, ONE }

data class AudioTrack(val uri: Uri, val title: String, val durationMs: Long = 0L)

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

    var exoPlayer: ExoPlayer? = null
        private set

    private var mediaSession: MediaSession? = null

    private val shuffledDeck = mutableListOf<Int>()
    private val historyStack = mutableListOf<Int>()

    var isAutoPlayEnabled by mutableStateOf(prefs.getBoolean("auto_play_enabled", true))
        private set

    var playlist = mutableStateListOf<AudioTrack>()
        private set

    var currentTrackIndex by mutableIntStateOf(-1)
        private set

    // 🚀 DISPARADOR PARA REINICIAR LOS GESTOS DE COMPOSE
    var gestureResetTrigger by mutableIntStateOf(0)
        private set

    private val _isPlaying = mutableStateOf(false)

    // 2. En el setter de isPlaying, gestiona el isActive de la sesión
    var isPlaying: Boolean
        get() = _isPlaying.value
        set(value) {
            _isPlaying.value = value
            if (value) {
                // 🚀 QUITA O COMENTA ESTA LÍNEA PARA QUE NO BLOQUEE AL DARLE PLAY
                // mediaSession?.isActive = true
                updatePlaybackState(PlaybackState.STATE_PLAYING)
                startProgressTracker()
            } else {
                mediaSession?.isActive = false
                updatePlaybackState(PlaybackState.STATE_PAUSED)
                progressJob?.cancel()
            }
        }
    fun deactivateMediaSession() {
        try {
            // Esto le dice al sistema que suelte los gestos de Compose
            mediaSession?.isActive = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
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
        getOrCreatePlayer()
        autoStartPlaybackOnBoot()
    }

    @OptIn(UnstableApi::class)
    fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: run {
            val dataSourceFactory = DefaultDataSource.Factory(context)
            val extractorsFactory = DefaultExtractorsFactory()
                .setConstantBitrateSeekingAlwaysEnabled(true)

            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

            val renderersFactory = DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(5000, 30000, 1000, 2000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            ExoPlayer.Builder(context, renderersFactory, mediaSourceFactory)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                .setLoadControl(loadControl)
                .build().also { newPlayer ->
                    exoPlayer = newPlayer
                    newPlayer.addListener(object : Player.Listener {

                        override fun onEvents(player: Player, events: Player.Events) {
                            val duration = player.duration
                            if (duration != C.TIME_UNSET && duration > 0L) {
                                totalDurationMs = duration
                            }
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            this@SmartMusicPlayer.isPlaying = playing
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            when (state) {
                                Player.STATE_READY -> {
                                    val duration = newPlayer.duration
                                    if (duration != C.TIME_UNSET && duration > 0L) {
                                        totalDurationMs = duration
                                    }
                                    this@SmartMusicPlayer.isPlaying = newPlayer.isPlaying
                                }
                                Player.STATE_ENDED -> {
                                    this@SmartMusicPlayer.isPlaying = false
                                    if (repeatMode == RepeatMode.ONE) {
                                        playTrackAtIndex(currentTrackIndex, 0L)
                                    } else {
                                        playNextTrack(userTriggered = false)
                                    }
                                }
                                Player.STATE_IDLE -> {
                                    this@SmartMusicPlayer.isPlaying = false
                                }
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            this@SmartMusicPlayer.isPlaying = false
                            android.util.Log.e("SmartMusicPlayer", "Error de Audio Media3: ${error.message}")
                            if (playlist.size > 1) {
                                playNextTrack(userTriggered = false)
                            }
                        }
                    })
                }
        }
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

    // --- DENTRO DE SmartMusicPlayer.kt ---

    // 1. En setupMediaSession, cambia isActive a false
    private fun setupMediaSession() {
        try {
            mediaSession = MediaSession(context, "SmartMusicPlayer").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() { togglePlayPause() }
                    override fun onPause() { pausePlayback() }
                    override fun onSkipToNext() { playNextTrack(userTriggered = true) }
                    override fun onSkipToPrevious() { playPreviousTrack() }
                    override fun onSeekTo(pos: Long) { seekTo(pos) }
                    override fun onStop() { pausePlayback() }
                })
                isActive = false // <--- CAMBIAR DE true A false
            }
            updatePlaybackState(PlaybackState.STATE_NONE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updatePlaybackState(state: Int) {
        try {
            val speed = if (state == PlaybackState.STATE_PLAYING) 1.0f else 0.0f
            val stateBuilder = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_SEEK_TO or
                            PlaybackState.ACTION_STOP
                )
                .setState(state, currentPositionMs, speed)

            mediaSession?.isActive = true
            mediaSession?.setPlaybackState(stateBuilder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION
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
                    val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val id = if (idCol != -1) cursor.getLong(idCol) else -1L
                        val title = if (titleCol != -1) cursor.getString(titleCol) else null
                        val name = if (nameCol != -1) cursor.getString(nameCol) else null
                        val filePath = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                        val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L

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
                                tracksList.add(AudioTrack(audioUri, audioName.substringBeforeLast("."), duration))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION
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
                    val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol) ?: cursor.getString(nameCol) ?: "Pista"
                        val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                        val audioUri = Uri.withAppendedPath(contentUri, id.toString())
                        tracksList.add(AudioTrack(audioUri, title.substringBeforeLast("."), duration))
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

    private fun isAudioFile(mimeType: String?, name: String?): Boolean {
        if (mimeType?.startsWith("audio/") == true) return true
        val ext = name?.substringAfterLast(".", "")?.lowercase() ?: ""
        return ext in listOf("mp3", "wav", "flac", "m4a", "aac", "ogg", "opus", "amr", "wma", "alac", "ape")
    }

    fun pausePlayback() {
        try {
            exoPlayer?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isPlaying = false
        progressJob?.cancel()
        saveCurrentState()
    }

    fun togglePlayPause() {
        if (playlist.isEmpty()) return

        val player = getOrCreatePlayer()

        if (player.isPlaying) {
            pausePlayback()
        } else {
            if (currentTrackIndex !in playlist.indices) {
                currentTrackIndex = 0
            }

            val track = playlist[currentTrackIndex]
            val mediaItem = MediaItem.fromUri(track.uri)

            if (player.mediaItemCount == 0 || player.currentMediaItem?.localConfiguration?.uri != track.uri) {
                player.setMediaItem(mediaItem)
                player.prepare()
                if (currentPositionMs > 0L) {
                    player.seekTo(currentPositionMs)
                }
            } else if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }

            player.playWhenReady = true
            player.play()
            isPlaying = true
        }
    }

    fun seekTo(positionMs: Long) {
        currentPositionMs = positionMs
        exoPlayer?.seekTo(positionMs)
        updatePlaybackState(if (exoPlayer?.isPlaying == true) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED)
    }

    fun playNextTrack(userTriggered: Boolean = false) {
        if (playlist.isEmpty()) return

        if (repeatMode == RepeatMode.ONE && !userTriggered) {
            playTrackAtIndex(currentTrackIndex, 0L)
            return
        }

        if (currentTrackIndex in playlist.indices) {
            historyStack.add(currentTrackIndex)
            if (historyStack.size > 100) historyStack.removeAt(0)
        }

        if (isShuffle) {
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
            val nextIndex = (currentTrackIndex + 1) % playlist.size
            playTrackAtIndex(nextIndex, 0L)
        }
    }

    fun playPreviousTrack() {
        if (playlist.isEmpty()) return

        if (isShuffle && historyStack.isNotEmpty()) {
            val prevIndex = historyStack.removeAt(historyStack.size - 1)
            playTrackAtIndex(prevIndex, 0L)
        } else {
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
        totalDurationMs = if (track.durationMs > 0L) track.durationMs else 1L
        currentPositionMs = startPosMs

        try {
            val player = getOrCreatePlayer()
            val mediaItem = MediaItem.fromUri(track.uri)

            player.stop()
            player.setMediaItem(mediaItem)
            player.prepare()
            if (startPosMs > 0L) {
                player.seekTo(startPosMs)
            }
            player.playWhenReady = false
            isPlaying = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playTrackAtIndex(index: Int, startPosMs: Long = 0L) {
        if (index !in playlist.indices) return
        currentTrackIndex = index
        val track = playlist[index]
        totalDurationMs = if (track.durationMs > 0L) track.durationMs else 1L

        try {
            val player = getOrCreatePlayer()
            val mediaItem = MediaItem.fromUri(track.uri)

            player.stop()
            player.setMediaItem(mediaItem)
            player.prepare()
            if (startPosMs > 0L) {
                player.seekTo(startPosMs)
            }
            player.playWhenReady = true
            player.play()

            isPlaying = true
            saveCurrentState()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
                            val duration = player.duration
                            if (duration != C.TIME_UNSET && duration > 0L) {
                                totalDurationMs = duration
                            }
                            saveCurrentState()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                delay(300L)
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

    fun notifyChange() {
        // 🚀 AUMENTA EL CONTADOR PARA FORZAR LA RECOMPOSICIÓN DE GESTOS EN COMPOSE
        gestureResetTrigger++
    }

    fun release() {
        progressJob?.cancel()
        try {
            exoPlayer?.release()
            exoPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
    }
}
