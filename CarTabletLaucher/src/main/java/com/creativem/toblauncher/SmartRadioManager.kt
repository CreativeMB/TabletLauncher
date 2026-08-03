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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val scope = CoroutineScope(Dispatchers.Main)

    private val _stationListState = mutableStateOf<List<RadioStation>>(emptyList())
    val stationList: List<RadioStation>
        get() = _stationListState.value

    private val isApiErrorState = mutableStateOf(false)
    var isApiError: Boolean
        get() = isApiErrorState.value
        set(value) { isApiErrorState.value = value }

    private val isFetchingApiState = mutableStateOf(false)
    var isFetchingApi: Boolean
        get() = isFetchingApiState.value
        set(value) { isFetchingApiState.value = value }

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

    private val selectedCountryState = mutableStateOf("Colombia")
    var selectedCountry: String
        get() = selectedCountryState.value
        set(value) {
            selectedCountryState.value = value
            saveSelectedCountry(value)
        }

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    init {
        currentStationIndex = getSavedStationIndex()
        selectedCountryState.value = getSavedCountry()
        setupMediaSession()
        fetchStationsByCountry(selectedCountryState.value)
    }

    // =========================================================================
    // 🌐 CARGA RÁPIDA DE API POR PAÍS
    // =========================================================================
    fun fetchStationsByCountry(
        country: String,
        onComplete: (() -> Unit)? = null
    ) {
        selectedCountry = country

        if (!isConnectedToInternet()) {
            isApiError = true
            onComplete?.invoke()
            return
        }

        scope.launch {

            try {

                isFetchingApi = true
                isApiError = false

                // 🚀 CACHE
                RadioCache.get(country)?.let { cachedStations ->

                    _stationListState.value = cachedStations

                    if (currentStationIndex >= cachedStations.size) {
                        currentStationIndex = 0
                    }

                    isFetchingApi = false
                    onComplete?.invoke()

                    return@launch
                }

                val apiResult = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getStationsByCountry(country)
                }

                if (apiResult.isNotEmpty()) {

                    val mapped = apiResult.map { it.toRadioStation() }

                    // 🚀 GUARDAR EN CACHE
                    RadioCache.put(country, mapped)

                    _stationListState.value = mapped

                    if (currentStationIndex >= mapped.size) {
                        currentStationIndex = 0
                    }

                } else {

                    _stationListState.value = emptyList()
                    isApiError = true

                }

            } catch (e: Exception) {

                android.util.Log.e(
                    "SmartRadioManager",
                    "Error API ($country): ${e.message}"
                )

                isApiError = true

            } finally {

                isFetchingApi = false
                onComplete?.invoke()

            }
        }
    }

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
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return try {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    // =========================================================================
    // 🎧 EXOPLAYER ULTRA-RÁPIDO (CERO ESPERA DE BUFFER)
    // =========================================================================
    @OptIn(UnstableApi::class)
    private fun getOrCreatePlayer(): ExoPlayer {
        return player ?: run {

            // ⚡ Búfer ultraligero: Inicia la transmisión tan pronto recibe 250ms de audio
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 1500,
                    /* maxBufferMs = */ 5000,
                    /* bufferForPlaybackMs = */ 250,
                    /* bufferForPlaybackAfterRebufferMs = */ 500
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            // ⚡ Timeouts agresivos de 4s para brincar rápidamente emisoras muertas
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(4000)
                .setReadTimeoutMs(4000)

            val extractorsFactory = DefaultExtractorsFactory().setConstantBitrateSeekingAlwaysEnabled(true)
            val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory, extractorsFactory)
            val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)

            ExoPlayer.Builder(context, renderersFactory, mediaSourceFactory)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
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
                            Toast.makeText(context, "Emisora fuera de línea", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
        }
    }

    fun playStationAtIndex(index: Int) {
        if (stationList.isEmpty() || index !in stationList.indices) return

        currentStationIndex = index
        saveStationIndex(index)

        val station = stationList[index]

        if (!isConnectedToInternet()) {
            isPlaying = false
            isLoading = false
            mediaSession?.isActive = false
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
        }
    }

    fun playStation(station: RadioStation) {
        val index = stationList.indexOfFirst { it.id == station.id }
        if (index != -1) {
            playStationAtIndex(index)
        }
    }

    fun togglePlayPause() {
        if (stationList.isEmpty()) return

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
        if (stationList.isEmpty()) return
        val nextIndex = (currentStationIndex + 1) % stationList.size
        playStationAtIndex(nextIndex)
    }

    fun playPreviousStation() {
        if (stationList.isEmpty()) return
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

    // =========================================================================
    // 💾 PERSISTENCIA EN SHARED PREFERENCES
    // =========================================================================
    private fun getSavedStationIndex(): Int {
        val prefs = context.getSharedPreferences("online_radio_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("station_index", 0)
    }

    private fun saveStationIndex(index: Int) {
        val prefs = context.getSharedPreferences("online_radio_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("station_index", index).apply()
    }

    fun getSavedCountry(): String {
        val prefs = context.getSharedPreferences("online_radio_prefs", Context.MODE_PRIVATE)
        return prefs.getString("selected_country", "Colombia") ?: "Colombia"
    }

    private fun saveSelectedCountry(country: String) {
        val prefs = context.getSharedPreferences("online_radio_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_country", country).apply()
    }

    fun saveFavorites(favIds: List<String>) {
        val prefs = context.getSharedPreferences("online_radio_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("radio_favs", favIds.joinToString(",")).apply()
    }

    fun getSavedFavorites(): List<String> {
        val prefs = context.getSharedPreferences("online_radio_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("radio_favs", "") ?: ""
        return if (saved.isEmpty()) emptyList() else saved.split(",")
    }
}