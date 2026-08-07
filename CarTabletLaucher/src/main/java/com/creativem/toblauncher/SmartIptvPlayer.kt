package com.creativem.toblauncher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

data class IptvChannel(
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null
)

class SmartIptvPlayer private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: SmartIptvPlayer? = null

        fun getInstance(context: Context): SmartIptvPlayer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmartIptvPlayer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences("smart_iptv_prefs", Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    var mediaPlayer: MediaPlayer? = null
        private set

    var playlist = mutableStateListOf<IptvChannel>()
        private set

    var currentChannelIndex by mutableStateOf(-1)
        private set

    private var consecutiveFailures = 0 // <-- PROTECCIÓN CONTRA BUCLES INFINITOS
    private var mediaSession: MediaSession? = null
    private val _isPlaying = mutableStateOf(false)

    var isPlaying: Boolean
        get() = _isPlaying.value
        set(value) {
            _isPlaying.value = value
            if (value) {
                requestAudioFocus()
                mediaSession?.isActive = true
                updatePlaybackState(PlaybackState.STATE_PLAYING)
            } else {
                updatePlaybackState(PlaybackState.STATE_PAUSED)
                mediaSession?.isActive = false
                abandonAudioFocus()
            }
        }

    var selectedFileName by mutableStateOf(prefs.getString("playlist_name", "Lista IPTV USB") ?: "Lista IPTV USB")
        private set

    var isFullscreenActive by mutableStateOf(false)
    var isScanning by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    init {
        setupMediaSession()
        autoLoadPlaylistOnBoot()
    }

    fun isConnectedToInternet(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun autoLoadPlaylistOnBoot() {
        val savedPath = prefs.getString("selected_playlist_path", null)
        val savedUri = prefs.getString("selected_playlist_uri", null)

        if (savedPath != null && File(savedPath).exists()) {
            parseAndLoadM3uFile(File(savedPath))
        } else if (savedUri != null) {
            parseAndLoadM3uUri(Uri.parse(savedUri))
        }
    }

    private fun setupMediaSession() {
        try {
            mediaSession = MediaSession(context, "SmartIptvPlayer").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        if (currentChannelIndex >= 0) playChannelAtIndex(currentChannelIndex)
                    }

                    override fun onPause() {
                        pausePlayback()
                    }

                    override fun onSkipToNext() {
                        playNextChannel()
                    }

                    override fun onSkipToPrevious() {
                        playPreviousChannel()
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
                .setState(state, 0L, 1.0f)

            mediaSession?.setPlaybackState(stateBuilder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun parseAndLoadM3uFile(m3uFile: File) {
        pausePlayback()
        isScanning = true
        selectedFileName = m3uFile.nameWithoutExtension.ifEmpty { "Lista IPTV" }

        prefs.edit()
            .putString("playlist_name", selectedFileName)
            .putString("selected_playlist_path", m3uFile.absolutePath)
            .remove("selected_playlist_uri")
            .apply()

        scope.launch(Dispatchers.IO) {
            val channels = mutableListOf<IptvChannel>()
            try {
                val reader = try {
                    m3uFile.bufferedReader(StandardCharsets.UTF_8)
                } catch (e: Exception) {
                    m3uFile.bufferedReader(StandardCharsets.ISO_8859_1)
                }
                channels.addAll(parseM3uStream(reader))
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                playlist.clear()
                playlist.addAll(channels)
                isScanning = false

                if (playlist.isNotEmpty()) {
                    val lastUrl = prefs.getString("last_channel_url", null)
                    val lastIndex = prefs.getInt("last_channel_index", 0)

                    val indexToPlay = playlist.indexOfFirst { it.streamUrl == lastUrl }.let {
                        if (it != -1) it else lastIndex.coerceIn(0, playlist.size - 1)
                    }
                    playChannelAtIndex(indexToPlay)
                }
            }
        }
    }

    fun parseAndLoadM3uUri(uri: Uri) {
        pausePlayback()
        isScanning = true
        selectedFileName = "Lista IPTV USB"

        prefs.edit()
            .putString("playlist_name", selectedFileName)
            .putString("selected_playlist_uri", uri.toString())
            .remove("selected_playlist_path")
            .apply()

        scope.launch(Dispatchers.IO) {
            val channels = mutableListOf<IptvChannel>()
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    channels.addAll(parseM3uStream(reader))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                playlist.clear()
                playlist.addAll(channels)
                isScanning = false

                if (playlist.isNotEmpty()) {
                    val lastUrl = prefs.getString("last_channel_url", null)
                    val lastIndex = prefs.getInt("last_channel_index", 0)

                    val indexToPlay = playlist.indexOfFirst { it.streamUrl == lastUrl }.let {
                        if (it != -1) it else lastIndex.coerceIn(0, playlist.size - 1)
                    }
                    playChannelAtIndex(indexToPlay)
                }
            }
        }
    }

    private fun parseM3uStream(reader: BufferedReader): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        var currentName = ""
        var currentLogo: String? = null
        var currentGroup: String? = null

        reader.useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEach

                if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                    val logoMatch = Regex("""tvg-logo="([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)
                    currentLogo = logoMatch?.groupValues?.get(1)

                    val groupMatch = Regex("""group-title="([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)
                    currentGroup = groupMatch?.groupValues?.get(1)

                    val tvgNameMatch = Regex("""tvg-name="([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)
                    val tvgName = tvgNameMatch?.groupValues?.get(1)

                    val nameAfterComma = trimmed.substringAfterLast(",", "").trim()

                    currentName = when {
                        nameAfterComma.isNotEmpty() -> nameAfterComma
                        !tvgName.isNullOrEmpty() -> tvgName
                        else -> ""
                    }
                } else if (trimmed.startsWith("#EXTGRP:", ignoreCase = true)) {
                    currentGroup = trimmed.substring(8).trim()
                } else if (!trimmed.startsWith("#")) {
                    if (trimmed.contains("://") || trimmed.startsWith("rtmp", ignoreCase = true) || trimmed.startsWith("udp", ignoreCase = true)) {
                        val finalName = if (currentName.isNotEmpty()) currentName else "Canal ${channels.size + 1}"
                        channels.add(
                            IptvChannel(
                                name = finalName,
                                streamUrl = trimmed,
                                logoUrl = currentLogo,
                                groupTitle = currentGroup
                            )
                        )
                    }
                    currentName = ""
                    currentLogo = null
                    currentGroup = null
                }
            }
        }
        return channels
    }

    // NAVEGACIÓN Y CAMBIO DE CANAL SEGURO
    fun playChannelAtIndex(index: Int) {
        if (playlist.isEmpty()) return

        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        currentChannelIndex = index.coerceIn(0, playlist.size - 1)
        val channel = playlist[currentChannelIndex]

        prefs.edit()
            .putInt("last_channel_index", currentChannelIndex)
            .putString("last_channel_url", channel.streamUrl)
            .apply()
    }

    fun playNextChannel() {
        if (playlist.isEmpty()) return

        // Protección contra bucles infinitos si muchos canales seguidos fallan
        consecutiveFailures++
        if (consecutiveFailures >= playlist.size) {
            consecutiveFailures = 0
            pausePlayback()
            return
        }

        val next = (currentChannelIndex + 1) % playlist.size
        playChannelAtIndex(next)
    }

    fun playPreviousChannel() {
        if (playlist.isEmpty()) return
        consecutiveFailures = 0 // Reseteamos al cambiar manualmente
        val prev = if (currentChannelIndex - 1 < 0) playlist.size - 1 else currentChannelIndex - 1
        playChannelAtIndex(prev)
    }

    fun togglePlayPause() {
        if (playlist.isEmpty()) return
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    pausePlayback()
                } else {
                    requestAudioFocus()
                    mediaSession?.isActive = true
                    player.start()
                    _isPlaying.value = true
                    updatePlaybackState(PlaybackState.STATE_PLAYING)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (currentChannelIndex >= 0) playChannelAtIndex(currentChannelIndex)
            }
        } ?: run {
            if (currentChannelIndex >= 0) playChannelAtIndex(currentChannelIndex)
        }
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
    }

    fun bindMediaPlayer(player: MediaPlayer) {
        this.mediaPlayer = player
        this._isPlaying.value = player.isPlaying

        // Canal cargado con éxito, reseteamos el contador de fallos
        consecutiveFailures = 0

        if (player.isPlaying) {
            requestAudioFocus()
            mediaSession?.isActive = true
            updatePlaybackState(PlaybackState.STATE_PLAYING)
        } else {
            mediaSession?.isActive = false
            updatePlaybackState(PlaybackState.STATE_PAUSED)
        }

        // Manejo de errores si el stream se cae o es inválido en tiempo de ejecución
        player.setOnErrorListener { _, what, extra ->
            // Salta al siguiente canal automáticamente de forma segura si el stream falla
            playNextChannel()
            true
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

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null

        abandonAudioFocus()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
    }
}