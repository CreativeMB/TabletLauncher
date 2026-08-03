package com.creativem.toblauncher

import android.content.Context
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory

// MODELO DE DATOS DE EMISORA ONLINE
data class RadioStation(
    val id: String,
    val name: String,
    val freqLabel: String,
    val city: String,
    val streamUrl: String,
    val genre: String
)

class SmartRadioManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: SmartRadioManager? = null

        fun getInstance(context: Context): SmartRadioManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmartRadioManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // =========================================================================
    // ✅ ESTADOS OBSERVABLES EN COMPOSE
    // =========================================================================
    private val isPlayingState = mutableStateOf(false)
    var isPlaying: Boolean
        get() = isPlayingState.value
        set(value) { isPlayingState.value = value }

    private val isLoadingState = mutableStateOf(false)
    var isLoading: Boolean
        get() = isLoadingState.value
        set(value) { isLoadingState.value = value }

    private val currentStationIndexState = mutableIntStateOf(0)
    var currentStationIndex: Int
        get() = currentStationIndexState.value
        set(value) { currentStationIndexState.value = value }

    private var player: ExoPlayer? = null

    // 📻 SESIÓN DE MEDIOS DEL SISTEMA (Para gestos, volante y notificación)
    private var mediaSession: MediaSession? = null

    // =========================================================================
    // 🇨🇴 LISTA NATIVA DE EMISORAS DE COLOMBIA
    // =========================================================================
    val stationList = listOf(
        RadioStation("1", "Caracol Radio", "100.9 FM", "Colombia", "https://playerservices.streamtheworld.com/api/livestream-redirect/CARACOL_RADIOAAC.aac", "Noticias"),
        RadioStation("2", "W Radio", "99.9 FM", "Colombia", "https://playerservices.streamtheworld.com/api/livestream-redirect/WRADIOAAC_SC", "Noticias / Opinión"),
        RadioStation("3", "Tropicana", "102.9 FM", "Bogotá", "https://playerservices.streamtheworld.com/api/livestream-redirect/TROPICANA_BOGAAC.aac", "Salsa / Urbana"),
        RadioStation("4", "La Mega", "90.9 FM", "Bogotá", "https://stream.rcn.com.co/lamega.mp3", "Pop / Reggaeton"),
        RadioStation("5", "Bésame", "97.4 FM", "Bogotá", "https://playerservices.streamtheworld.com/api/livestream-redirect/BESAME_BOGAAC.aac", "Romántica"),
        RadioStation("6", "RCN Radio", "93.9 FM", "Colombia", "https://stream.rcn.com.co/rcnradio.mp3", "Noticias"),
        RadioStation("7", "Olímpica Stereo", "105.9 FM", "Bogotá", "https://server2.ejeserver.com:8014/stream", "Variada / Cumbia"),
        RadioStation("8", "Radio Uno", "88.9 FM", "Bogotá", "https://stream.rcn.com.co/radiouno.mp3", "Popular")
    )

    init {
        currentStationIndex = getSavedStationIndex()
        setupMediaSession()
    }

    // =========================================================================
    // 🎛️ CONFIGURACIÓN DE MEDIA SESSION (GESTOS / BOTONES DEL SISTEMA)
    // =========================================================================
    private fun setupMediaSession() {
        try {
            mediaSession = MediaSession(context, "SmartRadioManager").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)

                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() { togglePlayPause() }
                    override fun onPause() { togglePlayPause() }
                    override fun onSkipToNext() { playNextStation() }
                    override fun onSkipToPrevious() { playPreviousStation() }
                    override fun onStop() { stopPlayback() }
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
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_STOP
                )
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)

            mediaSession?.setPlaybackState(stateBuilder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isConnectedToInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        return try {
            val network = cm.activeNetwork ?: return true
            val capabilities = cm.getNetworkCapabilities(network) ?: return true
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            true
        }
    }

    // =========================================================================
    // 🎧 INICIALIZACIÓN DE MEDIA3 EXOPLAYER PARA STREAMING EN DIRECTO
    // =========================================================================
    @OptIn(UnstableApi::class)
    private fun getOrCreatePlayer(): ExoPlayer {
        return player ?: run {

            // Búfer ligero de respuesta rápida (Ideal para Audio Streaming)
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 4000,
                    /* maxBufferMs = */ 15000,
                    /* bufferForPlaybackMs = */ 1000,
                    /* bufferForPlaybackAfterRebufferMs = */ 2000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            // Cliente HTTP estable para peticiones Shoutcast / Icecast / HLS
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(10000)

            val extractorsFactory = DefaultExtractorsFactory()
                .setConstantBitrateSeekingAlwaysEnabled(true)

            val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory, extractorsFactory)

            val renderersFactory = DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)

            ExoPlayer.Builder(context, renderersFactory, mediaSourceFactory)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus = */ true // Control automático de volumen con llamadas y GPS
                )
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setHandleAudioBecomingNoisy(true)
                .setLoadControl(loadControl)
                .build().also { newPlayer ->
                    player = newPlayer
                    newPlayer.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            this@SmartRadioManager.isPlaying = isPlaying
                            if (isPlaying) {
                                mediaSession?.isActive = true
                                updatePlaybackState(PlaybackState.STATE_PLAYING)
                            } else {
                                updatePlaybackState(PlaybackState.STATE_PAUSED)
                            }
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_BUFFERING -> {
                                    isLoading = true
                                    updatePlaybackState(PlaybackState.STATE_BUFFERING)
                                }
                                Player.STATE_READY -> {
                                    isLoading = false
                                    isPlaying = newPlayer.isPlaying
                                    if (isPlaying) {
                                        mediaSession?.isActive = true
                                        updatePlaybackState(PlaybackState.STATE_PLAYING)
                                    }
                                }
                                Player.STATE_ENDED, Player.STATE_IDLE -> {
                                    isLoading = false
                                    isPlaying = false
                                    mediaSession?.isActive = false
                                    updatePlaybackState(PlaybackState.STATE_STOPPED)
                                }
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            isLoading = false
                            isPlaying = false
                            mediaSession?.isActive = false
                            updatePlaybackState(PlaybackState.STATE_ERROR)
                            android.util.Log.e("SmartRadioManager", "Error de radio: ${error.message}")
                            Toast.makeText(context, "Error de conexión con la emisora", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
        }
    }

    // =========================================================================
    // 📻 REPRODUCCIÓN SIMPLE Y ESTABLE
    // =========================================================================
    fun playStationAtIndex(index: Int) {
        if (index !in stationList.indices) return

        currentStationIndex = index
        saveStationIndex(index)

        val station = stationList[index]

        if (!isConnectedToInternet()) {
            isPlaying = false
            isLoading = false
            mediaSession?.isActive = false
            updatePlaybackState(PlaybackState.STATE_NONE)
            Toast.makeText(context, "Sin conexión a Internet", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            isLoading = true
            val exoPlayer = getOrCreatePlayer()

            val mediaItem = MediaItem.fromUri(station.streamUrl)

            exoPlayer.stop()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()

            mediaSession?.isActive = true
            updatePlaybackState(PlaybackState.STATE_BUFFERING)

        } catch (e: Exception) {
            isLoading = false
            isPlaying = false
            mediaSession?.isActive = false
            updatePlaybackState(PlaybackState.STATE_ERROR)
            android.util.Log.e("SmartRadioManager", "Excepción al iniciar la emisora", e)
        }
    }

    fun togglePlayPause() {
        val exoPlayer = player ?: run {
            playStationAtIndex(currentStationIndex)
            return
        }

        if (isPlaying) {
            exoPlayer.pause()
            isPlaying = false
            updatePlaybackState(PlaybackState.STATE_PAUSED)
        } else {
            if (exoPlayer.playbackState == Player.STATE_READY) {
                exoPlayer.play()
                mediaSession?.isActive = true
                updatePlaybackState(PlaybackState.STATE_PLAYING)
            } else {
                playStationAtIndex(currentStationIndex)
            }
        }
    }

    fun playNextStation() {
        val nextIndex = (currentStationIndex + 1) % stationList.size
        playStationAtIndex(nextIndex)
    }

    fun playPreviousStation() {
        val prevIndex = if (currentStationIndex - 1 < 0) stationList.size - 1 else currentStationIndex - 1
        playStationAtIndex(prevIndex)
    }

    fun stopPlayback() {
        try {
            player?.stop()
            player?.clearMediaItems()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isPlaying = false
        isLoading = false
        mediaSession?.isActive = false
        updatePlaybackState(PlaybackState.STATE_STOPPED)
    }

    fun releasePlayer() {
        try {
            player?.release()
            player = null

            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isPlaying = false
        isLoading = false
    }

    // PERSISTENCIA DE CONFIGURACIÓN
    private fun getSavedStationIndex(): Int {
        val prefs = context.getSharedPreferences("online_radio_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("station_index", 0).coerceIn(0, stationList.size - 1)
    }

    private fun saveStationIndex(index: Int) {
        val prefs = context.getSharedPreferences("online_radio_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("station_index", index).apply()
    }

    fun saveFavorites(favIds: List<String>) {
        val prefs = context.getSharedPreferences("online_radio_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("radio_favs", favIds.joinToString(",")).apply()
    }

    fun getSavedFavorites(): List<String> {
        val prefs = context.getSharedPreferences("online_radio_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("radio_favs", "1,3,7,12,14") ?: ""
        return if (saved.isEmpty()) emptyList() else saved.split(",")
    }
}