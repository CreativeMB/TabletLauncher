package com.creativem.toblauncher

import android.content.Context
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

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

    // =========================================================================
    // ✅ ESTADOS OBSERVABLES
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

    // =========================================================================
    // 🇨🇴 LISTA NATIVA DE EMISORAS DE COLOMBIA
    // =========================================================================
    val stationList = listOf(
        // PRISA RADIO
        RadioStation("1", "Caracol Radio", "100.9 FM", "Colombia", "https://playerservices.streamtheworld.com/api/livestream-redirect/CARACOL_RADIOAAC.aac", "Noticias"),
        RadioStation("2", "W Radio", "99.9 FM", "Colombia", "https://playerservices.streamtheworld.com/api/livestream-redirect/WRADIOAAC_SC", "Noticias / Opinión")

    )

    init {
        currentStationIndex = getSavedStationIndex()
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
    // 🎧 INICIALIZACIÓN DE EXOPLAYER PARA STREAMING PROGRESIVO DE RADIO
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

            // Cliente HTTP estable para peticiones Shoutcast / Icecast
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(10000)

            ExoPlayer.Builder(context)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setHandleAudioBecomingNoisy(true)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
                .build().also { newPlayer ->
                    player = newPlayer
                    newPlayer.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            this@SmartRadioManager.isPlaying = isPlaying
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_BUFFERING -> {
                                    isLoading = true
                                }
                                Player.STATE_READY -> {
                                    isLoading = false
                                    isPlaying = newPlayer.isPlaying
                                }
                                Player.STATE_ENDED, Player.STATE_IDLE -> {
                                    isLoading = false
                                    isPlaying = false
                                }
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            isLoading = false
                            isPlaying = false
                            android.util.Log.e("SmartRadioManager", "Error en reproducción: ${error.message}")
                            Toast.makeText(context, "Error de conexión con la emisora", Toast.LENGTH_SHORT).show()
                            // 🛑 NO REINTENTAMOS AQUÍ PARA EVITAR EL BUCLE REPETITIVO DE DESCONEXIÓN
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
            Toast.makeText(context, "Sin conexión a Internet", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            isLoading = true
            val exoPlayer = getOrCreatePlayer()

            // Cargar la URL de forma progresiva limpia (sin LiveConfiguration que dañe la transmisión)
            val mediaItem = MediaItem.fromUri(station.streamUrl)

            exoPlayer.stop()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()

        } catch (e: Exception) {
            isLoading = false
            isPlaying = false
            android.util.Log.e("SmartRadioManager", "Excepción al iniciar", e)
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
        } else {
            if (exoPlayer.playbackState == Player.STATE_READY) {
                exoPlayer.play()
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
    }

    fun releasePlayer() {
        try {
            player?.release()
            player = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isPlaying = false
        isLoading = false
    }

    // PERSISTENCIA
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

    companion object {
        @Volatile
        private var instance: SmartRadioManager? = null

        fun getInstance(context: Context): SmartRadioManager {
            return instance ?: synchronized(this) {
                instance ?: SmartRadioManager(context.applicationContext).also { instance = it }
            }
        }
    }
}