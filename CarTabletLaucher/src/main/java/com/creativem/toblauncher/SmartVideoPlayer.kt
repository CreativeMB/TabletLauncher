package com.creativem.toblauncher

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
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
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import kotlinx.coroutines.*
import java.io.File

data class VideoTrack(val uri: Uri, val title: String, val durationMs: Long = 0L)

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

    var exoPlayer: ExoPlayer? = null
        private set

    private val shuffledDeck = mutableListOf<Int>()
    private val historyStack = mutableListOf<Int>()

    var isShuffleMode by mutableStateOf(prefs.getBoolean("is_shuffle_mode", false))
        private set

    fun toggleShuffle() {
        isShuffleMode = !isShuffleMode
        prefs.edit().putBoolean("is_shuffle_mode", isShuffleMode).apply()
        historyStack.clear()
        if (isShuffleMode) {
            rebuildShuffledDeck()
        }
        resetControlsTimer()
    }

    val playlist = mutableStateListOf<VideoTrack>()

    var currentTrackIndex by mutableIntStateOf(-1)
        private set

    var savedPlaybackPosition: Long = 0L

    private var mediaSession: MediaSession? = null
    private val _isPlaying = mutableStateOf(false)

    var isPlaying: Boolean
        get() = _isPlaying.value
        set(value) {
            _isPlaying.value = value
            if (value) {
                mediaSession?.isActive = true
                updatePlaybackState(PlaybackState.STATE_PLAYING)
                startProgressTracker()
                resetControlsTimer()
            } else {
                updatePlaybackState(PlaybackState.STATE_PAUSED)
                mediaSession?.isActive = false
                progressJob?.cancel()
                controlsTimerJob?.cancel()
                showControls = true
            }
        }

    // =========================================================================
    // ⏱️ GESTIÓN DE OCULTAR CONTROLES A LOS 5 SEGUNDOS
    // =========================================================================
    var showControls by mutableStateOf(true)
    private var controlsTimerJob: Job? = null

    fun resetControlsTimer() {
        showControls = true
        controlsTimerJob?.cancel()
        if (isPlaying) {
            controlsTimerJob = scope.launch {
                delay(5000L)
                showControls = false
            }
        }
    }

    fun toggleControls() {
        if (showControls) {
            showControls = false
            controlsTimerJob?.cancel()
        } else {
            resetControlsTimer()
        }
    }

    var currentPositionMs by mutableLongStateOf(0L)
    var totalDurationMs by mutableLongStateOf(1L)

    var selectedFolderName by mutableStateOf(prefs.getString("folder_name", "Memoria USB") ?: "Memoria USB")
        private set

    var isScanning by mutableStateOf(false)
        private set

    var isFullscreenActive by mutableStateOf(false)
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    init {
        setupMediaSession()
        getOrCreatePlayer()
        autoStartVideoOnBoot()
    }

    // =========================================================================
    // 🎧 INICIALIZACIÓN MEDIA3 EXOPLAYER ULTRA-COMPATIBLE (1080P / 4K / USB)
    // =========================================================================
    @OptIn(UnstableApi::class)
    fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: run {
            val dataSourceFactory = DefaultDataSource.Factory(context)
            val extractorsFactory = DefaultExtractorsFactory()
                .setConstantBitrateSeekingAlwaysEnabled(true)

            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

            val renderersFactory = DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(20000, 60000, 2500, 5000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            ExoPlayer.Builder(context, renderersFactory, mediaSourceFactory)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus = */ false
                )
                .setLoadControl(loadControl)
                .build().also { newPlayer ->
                    exoPlayer = newPlayer
                    newPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)

                    newPlayer.addListener(object : Player.Listener {

                        override fun onEvents(player: Player, events: Player.Events) {
                            updateDuration(player)
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            _isPlaying.value = playing
                            if (playing) {
                                mediaSession?.isActive = true
                                updatePlaybackState(PlaybackState.STATE_PLAYING)
                                startProgressTracker()
                                resetControlsTimer()
                            } else {
                                updatePlaybackState(PlaybackState.STATE_PAUSED)
                                mediaSession?.isActive = false
                                progressJob?.cancel()
                                controlsTimerJob?.cancel()
                                showControls = true
                            }
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            when (state) {
                                Player.STATE_READY -> {
                                    updateDuration(newPlayer)
                                    _isPlaying.value = newPlayer.isPlaying
                                }
                                Player.STATE_ENDED -> {
                                    _isPlaying.value = false
                                    showControls = true
                                    playNextVideo()
                                }
                                Player.STATE_IDLE -> {
                                    _isPlaying.value = false
                                    showControls = true
                                }
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            _isPlaying.value = false
                            showControls = true
                            android.util.Log.e("SmartVideoPlayer", "Error de Video ExoPlayer: ${error.message}")
                            if (playlist.size > 1) {
                                playNextVideo()
                            }
                        }
                    })
                }
        }
    }

    private fun updateDuration(player: Player) {
        val duration = player.duration
        if (duration != C.TIME_UNSET && duration > 0L) {
            totalDurationMs = duration
        } else if (totalDurationMs <= 1L) {
            fetchFallbackDuration()
        }
    }

    private fun fetchFallbackDuration() {
        if (currentTrackIndex !in playlist.indices) return
        val video = playlist[currentTrackIndex]

        if (video.durationMs > 0L) {
            totalDurationMs = video.durationMs
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                if (video.uri.scheme == "file") {
                    val file = File(video.uri.path ?: "")
                    if (file.exists()) {
                        retriever.setDataSource(file.absolutePath)
                    }
                } else {
                    context.contentResolver.openFileDescriptor(video.uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                    } ?: retriever.setDataSource(context, video.uri)
                }

                val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                retriever.release()

                val durationMs = timeStr?.toLongOrNull() ?: 0L
                if (durationMs > 0L) {
                    withContext(Dispatchers.Main) {
                        totalDurationMs = durationMs
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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

    private fun setupMediaSession() {
        try {
            mediaSession = MediaSession(context, "SmartVideoPlayer").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)

                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() { togglePlayPause() }
                    override fun onPause() { pausePlayback() }
                    override fun onSkipToNext() { playNextVideo() }
                    override fun onSkipToPrevious() { playPreviousVideo() }
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
                    val lastPos = prefs.getLong("last_position_ms", 0L)
                    val savedIndex = playlist.indexOfFirst { it.uri.toString() == lastVideoUri }
                    val indexToPlay = if (savedIndex != -1) savedIndex else 0

                    playVideoAtIndex(indexToPlay, lastPos)
                }
            }
        }
    }

    private fun scanVideoViaMediaStoreUniversal(folderPath: String, folderName: String, tracksList: MutableList<VideoTrack>) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION
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
                    val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val id = if (idCol != -1) cursor.getLong(idCol) else -1L
                        val title = if (titleCol != -1) cursor.getString(titleCol) else null
                        val name = if (nameCol != -1) cursor.getString(nameCol) else null
                        val filePath = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                        val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L

                        val videoName = title ?: name ?: "Video"

                        val matchesFolder = filePath.isEmpty() ||
                                filePath.contains(folderPath, ignoreCase = true) ||
                                filePath.contains(folderName, ignoreCase = true)

                        if (matchesFolder) {
                            val videoUri = if (id != -1L) Uri.withAppendedPath(contentUri, id.toString()) else if (filePath.isNotEmpty()) Uri.fromFile(File(filePath)) else null
                            if (videoUri != null) {
                                tracksList.add(VideoTrack(videoUri, videoName.substringBeforeLast("."), duration))
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
                    val lastPos = prefs.getLong("last_position_ms", 0L)
                    val savedIndex = playlist.indexOfFirst { it.uri.toString() == lastVideoUri }
                    val indexToPlay = if (savedIndex != -1) savedIndex else 0

                    playVideoAtIndex(indexToPlay, lastPos)
                }
            }
        }
    }

    private fun scanAllVideosUniversal(tracksList: MutableList<VideoTrack>) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
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
                    val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val id = if (idCol != -1) cursor.getLong(idCol) else -1L
                        val title = if (titleCol != -1) cursor.getString(titleCol) else null
                        val name = if (nameCol != -1) cursor.getString(nameCol) else null
                        val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L

                        val videoName = title ?: name ?: "Video"
                        if (id != -1L) {
                            val videoUri = Uri.withAppendedPath(contentUri, id.toString())
                            tracksList.add(VideoTrack(videoUri, videoName.substringBeforeLast("."), duration))
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
        return ext in listOf("mp4", "mkv", "avi", "webm", "3gp", "mov", "m4v", "ts", "mpg", "flv", "vob", "wmv", "m2ts")
    }

    fun pausePlayback() {
        try {
            exoPlayer?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isPlaying.value = false
        updatePlaybackState(PlaybackState.STATE_PAUSED)
        mediaSession?.isActive = false
        progressJob?.cancel()
        controlsTimerJob?.cancel()
        showControls = true
        saveCurrentState()
    }

    fun togglePlayPause(videoUrl: String = "") {
        if (playlist.isEmpty()) return

        val player = getOrCreatePlayer()

        if (player.isPlaying) {
            pausePlayback()
        } else {
            if (currentTrackIndex !in playlist.indices) {
                currentTrackIndex = 0
            }

            val video = playlist[currentTrackIndex]
            val mediaItem = MediaItem.fromUri(video.uri)

            if (player.mediaItemCount == 0 || player.currentMediaItem?.localConfiguration?.uri != video.uri) {
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
            _isPlaying.value = true
            mediaSession?.isActive = true
            updatePlaybackState(PlaybackState.STATE_PLAYING)
            startProgressTracker()
            resetControlsTimer()
        }
    }

    fun seekTo(positionMs: Long) {
        currentPositionMs = positionMs
        exoPlayer?.seekTo(positionMs)
        resetControlsTimer()
    }

    fun playNextVideo() {
        if (playlist.isEmpty()) return

        if (currentTrackIndex in playlist.indices) {
            historyStack.add(currentTrackIndex)
            if (historyStack.size > 100) historyStack.removeAt(0)
        }

        val nextIndex = if (isShuffleMode) {
            if (shuffledDeck.isEmpty()) {
                rebuildShuffledDeck()
            }

            if (shuffledDeck.isNotEmpty()) {
                shuffledDeck.removeAt(0)
            } else {
                0
            }
        } else {
            (currentTrackIndex + 1) % playlist.size
        }

        playVideoAtIndex(nextIndex, 0L)
    }

    fun playPreviousVideo() {
        if (playlist.isEmpty()) return

        val prevIndex = if (isShuffleMode && historyStack.isNotEmpty()) {
            historyStack.removeAt(historyStack.size - 1)
        } else {
            if (currentTrackIndex - 1 < 0) playlist.size - 1 else currentTrackIndex - 1
        }

        playVideoAtIndex(prevIndex, 0L)
    }

    fun prepareVideoAtIndex(index: Int, startPosMs: Long = 0L) {
        if (index !in playlist.indices) return
        currentTrackIndex = index
        currentPositionMs = startPosMs
        val video = playlist[index]
        totalDurationMs = if (video.durationMs > 0L) video.durationMs else 1L

        try {
            val player = getOrCreatePlayer()
            val mediaItem = MediaItem.fromUri(video.uri)

            player.stop()
            player.setMediaItem(mediaItem)
            player.prepare()
            if (startPosMs > 0L) {
                player.seekTo(startPosMs)
            }
            player.playWhenReady = false
            _isPlaying.value = false
            mediaSession?.isActive = false
            updatePlaybackState(PlaybackState.STATE_PAUSED)
            showControls = true
            fetchFallbackDuration()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playVideoAtIndex(index: Int, position: Long = 0L) {
        if (playlist.isEmpty()) return
        currentTrackIndex = index.coerceIn(0, playlist.size - 1)
        val video = playlist[currentTrackIndex]
        totalDurationMs = if (video.durationMs > 0L) video.durationMs else 1L

        try {
            val player = getOrCreatePlayer()
            val mediaItem = MediaItem.fromUri(video.uri)

            player.stop()
            player.setMediaItem(mediaItem)
            player.prepare()
            if (position > 0L) {
                player.seekTo(position)
            }
            player.playWhenReady = true
            player.play()

            _isPlaying.value = true
            mediaSession?.isActive = true
            updatePlaybackState(PlaybackState.STATE_PLAYING)
            startProgressTracker()
            resetControlsTimer()
            saveCurrentState()
            fetchFallbackDuration()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    // 💥 AGREGAR ESTA FUNCIÓN EN SmartVideoPlayer.kt
    fun forcePlay() {
        val player = getOrCreatePlayer()
        if (!player.isPlaying) {
            player.playWhenReady = true
            player.play()
            _isPlaying.value = true
            mediaSession?.isActive = true
            updatePlaybackState(PlaybackState.STATE_PLAYING)
            startProgressTracker()
            resetControlsTimer()
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
                            updateDuration(player)
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
        controlsTimerJob?.cancel()
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