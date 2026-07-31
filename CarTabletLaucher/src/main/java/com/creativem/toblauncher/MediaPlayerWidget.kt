package com.creativem.toblauncher

import android.os.Environment
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.viewinterop.AndroidView

// 1. ENUM PARA LOS 3 MODOS
enum class MediaMode {
    MUSIC, VIDEO, IPTV
}

// 2. WIDGET CON CONMUTACIÓN EXCLUSIVA DE REPRODUCTORES
@Composable
fun ModernMediaPlayerWidget(
    currentMode: MediaMode = MediaMode.MUSIC,
    onModeChange: (MediaMode) -> Unit = {},
    onExpandMusicFullscreen: () -> Unit = {},
    onExpandVideoFullscreen: () -> Unit = {},
    onExpandIptvFullscreen: () -> Unit = {}
) {
    val theme = LocalDashboardTheme.current
    val context = LocalContext.current

    // Instancias singleton de los reproductores
    val musicPlayer = remember { SmartMusicPlayer.getInstance(context) }
    val videoPlayer = remember { SmartVideoPlayer.getInstance(context) }
    val iptvPlayer = remember { SmartIptvPlayer.getInstance(context) }

    // =========================================================================
    // CONTROLADOR DE EXCLUSIVIDAD: PAUSA AUTOMÁTICAMENTE EL REPRODUCTOR ANTERIOR
    // =========================================================================
    LaunchedEffect(currentMode) {
        when (currentMode) {
            MediaMode.MUSIC -> {
                // Al entrar a Música: Pausar Video
                try {
                    if (videoPlayer.isPlaying) {
                        videoPlayer.mediaPlayer?.pause()
                        videoPlayer.isPlaying = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            MediaMode.VIDEO -> {
                // Al entrar a Video: Pausar Música
                try {
                    if (musicPlayer.isPlaying) {
                        musicPlayer.togglePlayPause()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            MediaMode.IPTV -> {
                try { musicPlayer.pausePlayback() } catch (e: Exception) { e.printStackTrace() }
                try { videoPlayer.pausePlayback() } catch (e: Exception) { e.printStackTrace() }
            }
        }

    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141414))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- 1. BARRA LATERAL IZQUIERDA (MENÚ DE NAVEGACIÓN) ---
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E1E))
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VerticalIconButton(
                icon = Icons.Default.MusicNote,
                isSelected = currentMode == MediaMode.MUSIC,
                activeColor = theme.accentCyan,
                onClick = { onModeChange(MediaMode.MUSIC) }
            )

            VerticalIconButton(
                icon = Icons.Default.PlayCircle,
                isSelected = currentMode == MediaMode.VIDEO,
                activeColor = theme.accentOrange,
                onClick = { onModeChange(MediaMode.VIDEO) }
            )

            VerticalIconButton(
                icon = Icons.Default.Tv,
                isSelected = currentMode == MediaMode.IPTV,
                activeColor = theme.accentPurple,
                onClick = { onModeChange(MediaMode.IPTV) }
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // --- 2. CONTENIDO PRINCIPAL (DERECHA) ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentMode,
                label = "modeTransition"
            ) { mode: MediaMode ->
                when (mode) {
                    MediaMode.MUSIC -> MusicPlayerView(
                        theme = theme,
                        onExpandFullscreen = onExpandMusicFullscreen
                    )

                    MediaMode.VIDEO -> VideoPlayerView(
                        theme = theme,
                        onExpandFullscreen = onExpandVideoFullscreen
                    )
                    MediaMode.IPTV -> IptvPlayerView(
                        theme = theme,
                        onExpandFullscreen = onExpandIptvFullscreen
                    )
                }
            }
        }
    }
}

// COMPONENTE DE ICONO VERTICAL
@Composable
private fun VerticalIconButton(
    icon: ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (isSelected) activeColor.copy(alpha = 0.25f) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) activeColor else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}

// --- VISTA DE MÚSICA CON EXPLORADOR INTERNO NATIVO ---
@Composable
fun MusicPlayerView(
    theme: DashboardTheme,
    onExpandFullscreen: () -> Unit = {},
    onExpandVideoFullscreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val musicPlayer = remember { SmartMusicPlayer.getInstance(context) }
    var showFolderModal by remember { mutableStateOf(false) }

    val currentTrack = musicPlayer.playlist.getOrNull(musicPlayer.currentTrackIndex)

    // =========================================================================
    // AUTO-DISPARO INMEDIATO: REPRODUCE DE UNA AL ENTRAR AL MODO MÚSICA
    // =========================================================================
    LaunchedEffect(musicPlayer.playlist.isNotEmpty()) {
        if (!musicPlayer.isPlaying && musicPlayer.playlist.isNotEmpty()) {
            val indexToPlay = if (musicPlayer.currentTrackIndex in musicPlayer.playlist.indices) {
                musicPlayer.currentTrackIndex
            } else {
                0
            }
            // Reanuda desde el último milisegundo guardado o inicia la pista
            musicPlayer.playTrackAtIndex(indexToPlay, musicPlayer.currentPositionMs)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        theme.accentPurple.copy(alpha = 0.35f),
                        Color(0xFF0F0F14),
                        theme.accentCyan.copy(alpha = 0.20f)
                    )
                )
            )
            .clickable { onExpandFullscreen() }
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // CABECERA CON BOTÓN DE EXPLORADOR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack?.title ?: if (musicPlayer.isScanning) "Analizando USB..." else "Sin Música",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (musicPlayer.isScanning) {
                            "⚡ Actualizando biblioteca..."
                        } else if (musicPlayer.playlist.isNotEmpty()) {
                            "📂 ${musicPlayer.selectedFolderName} • ${musicPlayer.playlist.size} pistas"
                        } else {
                            "📂 Toca aquí o conecta tu USB"
                        },
                        color = if (musicPlayer.isScanning) theme.accentOrange else theme.accentCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = { showFolderModal = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Abrir USB",
                        tint = theme.accentOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // --- CONTADORES DE TIEMPO Y BARRA DE PROGRESO ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                val elapsedTimeFormatted = formatMs(musicPlayer.currentPositionMs)
                val totalTimeFormatted = formatMs(musicPlayer.totalDurationMs)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = elapsedTimeFormatted,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = totalTimeFormatted,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val progressPercent: Float = if (musicPlayer.totalDurationMs > 0L) {
                    (musicPlayer.currentPositionMs.toFloat() / musicPlayer.totalDurationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                LinearProgressIndicator(
                    progress = progressPercent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape),
                    color = theme.accentOrange,
                    trackColor = theme.cardBorder
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val buttonScale = LocalButtonScale.current

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(
                    onClick = { musicPlayer.toggleShuffle() },
                    modifier = Modifier.size((38 * buttonScale).dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Aleatorio",
                        tint = if (musicPlayer.isShuffle) theme.accentCyan else Color.DarkGray,
                        modifier = Modifier.size((20 * buttonScale).dp)
                    )
                }

                // Anterior
                IconButton(
                    onClick = { musicPlayer.playPreviousTrack() },
                    modifier = Modifier
                        .size((44 * buttonScale).dp)
                        .background(Color(0xFF22222E), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = theme.accentOrange,
                        modifier = Modifier.size((28 * buttonScale).dp)
                    )
                }

                // Play / Pausa
                IconButton(
                    onClick = { musicPlayer.togglePlayPause() },
                    modifier = Modifier
                        .size((54 * buttonScale).dp)
                        .background(theme.accentCyan, CircleShape)
                ) {
                    Icon(
                        imageVector = if (musicPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pausa",
                        tint = Color.Black,
                        modifier = Modifier.size((32 * buttonScale).dp)
                    )
                }

                // Siguiente
                IconButton(
                    onClick = { musicPlayer.playNextTrack(userTriggered = true) },
                    modifier = Modifier
                        .size((44 * buttonScale).dp)
                        .background(Color(0xFF22222E), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Siguiente",
                        tint = theme.accentOrange,
                        modifier = Modifier.size((28 * buttonScale).dp)
                    )
                }

                // Expandir Pantalla
                IconButton(
                    onClick = { onExpandFullscreen() },
                    modifier = Modifier.size((38 * buttonScale).dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Pantalla Completa",
                        tint = theme.accentOrange,
                        modifier = Modifier.size((22 * buttonScale).dp)
                    )
                }

                // AutoStart
                IconButton(
                    onClick = { musicPlayer.toggleAutoPlay() },
                    modifier = Modifier.size((38 * buttonScale).dp)
                ) {
                    Icon(
                        imageVector = if (musicPlayer.isAutoPlayEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "AutoStart",
                        tint = if (musicPlayer.isAutoPlayEnabled) theme.accentOrange else Color.DarkGray,
                        modifier = Modifier.size((20 * buttonScale).dp)
                    )
                }
            }
        }

        if (showFolderModal) {
            FolderPickerModal(
                onDismiss = { showFolderModal = false },
                onFolderSelected = { selectedFolder ->
                    musicPlayer.scanFolderPath(selectedFolder)
                }
            )
        }
    }
}
// FUNCIÓN AUXILIAR FORMATO TIEMPO
private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun VideoPlayerView(
    theme: DashboardTheme,
    onExpandFullscreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val videoPlayer = remember { SmartVideoPlayer.getInstance(context) }
    var showFolderModal by remember { mutableStateOf(false) }

    val currentVideo = videoPlayer.playlist.getOrNull(videoPlayer.currentTrackIndex)

    var showUI by remember { mutableStateOf(true) }
    val interactionSource = remember { MutableInteractionSource() }
    val buttonScale = LocalButtonScale.current

    // =========================================================================
    // RECONEXIÓN AUTOMÁTICA AL REGRESAR DE PANTALLA COMPLETA
    // =========================================================================
    LaunchedEffect(videoPlayer.playlist.isNotEmpty(), videoPlayer.isFullscreenActive) {
        if (!videoPlayer.isFullscreenActive && videoPlayer.playlist.isNotEmpty()) {
            val targetIndex = if (videoPlayer.currentTrackIndex in videoPlayer.playlist.indices) {
                videoPlayer.currentTrackIndex
            } else 0

            // Si no está reproduciendo al regresar de pantalla completa, reconecta desde el principio
            if (!videoPlayer.isPlaying) {
                videoPlayer.playVideoAtIndex(targetIndex, 0L)
            }
        }
    }

    // TEMPORIZADOR DE 5 SEGUNDOS PARA OCULTAR INTERFAZ
    LaunchedEffect(showUI, videoPlayer.isPlaying) {
        if (showUI && videoPlayer.isPlaying) {
            kotlinx.coroutines.delay(5000L)
            showUI = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                showUI = !showUI
            }
    ) {
        if (currentVideo != null) {
            AndroidView(
                factory = { ctx ->
                    object : VideoView(ctx) {
                        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                            val width = MeasureSpec.getSize(widthMeasureSpec)
                            val height = MeasureSpec.getSize(heightMeasureSpec)
                            setMeasuredDimension(width, height)
                        }
                    }.apply {
                        if (!videoPlayer.isFullscreenActive) {
                            setVideoURI(currentVideo.uri)
                            tag = currentVideo.uri.toString()
                            setOnPreparedListener { mp ->
                                videoPlayer.bindMediaPlayer(mp)
                                mp.seekTo(0)
                                mp.start()
                                videoPlayer.isPlaying = true
                            }
                            setOnCompletionListener {
                                videoPlayer.playNextVideo()
                            }
                        }
                    }
                },
                update = { view ->
                    val currentPlayingUri = view.tag as? String
                    val newUri = currentVideo.uri.toString()

                    // ✅ RECONEXIÓN DINÁMICA: Solo actualiza el VideoView si la pantalla completa NO está activa
                    if (!videoPlayer.isFullscreenActive) {
                        if (currentPlayingUri != newUri || !view.isPlaying) {
                            view.tag = newUri
                            view.setVideoURI(currentVideo.uri)
                            view.setOnPreparedListener { mp ->
                                videoPlayer.bindMediaPlayer(mp)
                                mp.seekTo(0)
                                mp.start()
                                videoPlayer.isPlaying = true
                            }
                        }
                    } else {
                        // Limpia el estado mientras la pantalla completa esté abierta para evitar congelamientos
                        view.tag = null
                        view.stopPlayback()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay videos cargados", color = Color.Gray, fontSize = 12.sp)
            }
        }

        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent, Color(0xCC000000))))
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentVideo?.title ?: "Reproductor de Video",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "📂 ${videoPlayer.selectedFolderName} (${videoPlayer.playlist.size} videos)",
                            color = theme.accentCyan,
                            fontSize = 9.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            showFolderModal = true
                            showUI = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "USB", tint = theme.accentOrange, modifier = Modifier.size(20.dp))
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatMs(videoPlayer.currentPositionMs),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatMs(videoPlayer.totalDurationMs),
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    val progressPercent: Float = if (videoPlayer.totalDurationMs > 0L) {
                        (videoPlayer.currentPositionMs.toFloat() / videoPlayer.totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    LinearProgressIndicator(
                        progress = progressPercent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = theme.accentOrange,
                        trackColor = Color.DarkGray
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                videoPlayer.toggleShuffle()
                                showUI = true
                            },
                            modifier = Modifier.size((36 * buttonScale).dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Aleatorio",
                                tint = if (videoPlayer.isShuffleMode) theme.accentCyan else Color.Gray,
                                modifier = Modifier.size((20 * buttonScale).dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                videoPlayer.playPreviousVideo()
                                showUI = true
                            },
                            modifier = Modifier
                                .size((40 * buttonScale).dp)
                                .background(Color(0xFF22222E), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Anterior",
                                tint = theme.accentOrange,
                                modifier = Modifier.size((24 * buttonScale).dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val videoUrl = currentVideo?.uri?.toString() ?: ""
                                if (videoPlayer.isPlaying) {
                                    videoPlayer.pausePlayback()
                                } else {
                                    videoPlayer.togglePlayPause(videoUrl)
                                }
                                showUI = true
                            },
                            modifier = Modifier
                                .size((46 * buttonScale).dp)
                                .background(theme.accentCyan, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (videoPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pausa",
                                tint = Color.Black,
                                modifier = Modifier.size((28 * buttonScale).dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                videoPlayer.playNextVideo()
                                showUI = true
                            },
                            modifier = Modifier
                                .size((40 * buttonScale).dp)
                                .background(Color(0xFF22222E), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Siguiente",
                                tint = theme.accentOrange,
                                modifier = Modifier.size((24 * buttonScale).dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                showUI = false
                                try {
                                    videoPlayer.pausePlayback()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                onExpandFullscreen()
                            },
                            modifier = Modifier.size((36 * buttonScale).dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Expandir",
                                tint = theme.accentOrange,
                                modifier = Modifier.size((22 * buttonScale).dp)
                            )
                        }
                    }
                }
            }
        }

        if (showFolderModal) {
            FolderPickerModal(
                onDismiss = {
                    showFolderModal = false
                    showUI = true
                },
                onFolderSelected = { selectedFolder ->
                    videoPlayer.scanVideoFolderPath(selectedFolder)
                    showFolderModal = false
                }
            )
        }
    }
}

// --- VISTA IPTV CON CONTROLES TÁCTILES DESAPARECIBLES Y SEÑAL COMPLETA AJUSTADA ---
@Composable
fun IptvPlayerView(
    theme: DashboardTheme,
    onExpandFullscreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val iptvPlayer = remember { SmartIptvPlayer.getInstance(context) }
    var showFolderModal by remember { mutableStateOf(false) }

    val currentChannel = iptvPlayer.playlist.getOrNull(iptvPlayer.currentChannelIndex)
    val buttonScale = LocalButtonScale.current

    var showUI by remember { mutableStateOf(true) }
    val interactionSource = remember { MutableInteractionSource() }

    val isOnline = remember(iptvPlayer.currentChannelIndex) { iptvPlayer.isConnectedToInternet() }

    // =========================================================================
    // AUTO-CARGA Y RECONEXIÓN AUTOMÁTICA AL REGRESAR DE PANTALLA COMPLETA
    // =========================================================================
    LaunchedEffect(iptvPlayer.playlist.isNotEmpty(), iptvPlayer.isFullscreenActive) {
        if (!iptvPlayer.isFullscreenActive && iptvPlayer.playlist.isNotEmpty()) {
            val targetIndex = if (iptvPlayer.currentChannelIndex in iptvPlayer.playlist.indices) {
                iptvPlayer.currentChannelIndex
            } else 0

            // Si el reproductor no está sonando al regresar de pantalla completa, reconecta
            if (!iptvPlayer.isPlaying) {
                iptvPlayer.playChannelAtIndex(targetIndex)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                showUI = !showUI
            }
    ) {
        if (!isOnline) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F14)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color.Red, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(6.dp))
                Text("Se requiere Internet para ver IPTV", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Conecta la tablet a Wi-Fi o datos móviles", color = Color.Gray, fontSize = 10.sp)
            }
        } else if (currentChannel != null) {
            AndroidView(
                factory = { ctx ->
                    object : VideoView(ctx) {
                        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                            val width = MeasureSpec.getSize(widthMeasureSpec)
                            val height = MeasureSpec.getSize(heightMeasureSpec)
                            setMeasuredDimension(width, height)
                        }
                    }.apply {
                        if (!iptvPlayer.isFullscreenActive) {
                            setVideoPath(currentChannel.streamUrl)
                            tag = currentChannel.streamUrl
                            setOnPreparedListener { mp ->
                                iptvPlayer.bindMediaPlayer(mp)
                                mp.start()
                                iptvPlayer.isPlaying = true
                            }
                        }
                    }
                },
                update = { view ->
                    val currentPlayingUri = view.tag as? String
                    val newUrl = currentChannel.streamUrl

                    // ✅ RECONEXIÓN DINÁMICA: Solo procesa señal si la pantalla completa NO está activa
                    if (!iptvPlayer.isFullscreenActive) {
                        if (currentPlayingUri != newUrl || !view.isPlaying) {
                            view.tag = newUrl
                            view.setVideoPath(newUrl)
                            view.setOnPreparedListener { mp ->
                                iptvPlayer.bindMediaPlayer(mp)
                                mp.start()
                                iptvPlayer.isPlaying = true
                            }
                        }
                    } else {
                        // Limpia el estado en el fondo mientras esté abierta la pantalla completa
                        view.tag = null
                        view.stopPlayback()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = theme.accentPurple, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(6.dp))
                Text("Carga una lista .m3u desde tu USB", color = Color.Gray, fontSize = 11.sp)
            }
        }

        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent, Color(0xCC000000))))
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Red)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text("EN VIVO", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        ChannelLogoImage(
                            logoUrl = currentChannel?.logoUrl,
                            modifier = Modifier.size(24.dp),
                            tint = theme.accentPurple
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Column {
                            Text(
                                text = currentChannel?.name ?: if (iptvPlayer.isScanning) "Cargando M3U..." else "Sin Lista IPTV",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "📺 ${iptvPlayer.selectedFileName} (${iptvPlayer.playlist.size} canales)",
                                color = theme.accentPurple,
                                fontSize = 9.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            showFolderModal = true
                            showUI = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "USB", tint = theme.accentOrange, modifier = Modifier.size(20.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            iptvPlayer.playPreviousChannel()
                            showUI = true
                        },
                        modifier = Modifier.height((34 * buttonScale).dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E28)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("CH -", color = theme.accentPurple, fontSize = (11 * buttonScale).sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = {
                            iptvPlayer.togglePlayPause()
                            showUI = true
                        },
                        modifier = Modifier
                            .size((42 * buttonScale).dp)
                            .background(theme.accentPurple, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (iptvPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pausa",
                            tint = Color.White,
                            modifier = Modifier.size((24 * buttonScale).dp)
                        )
                    }

                    Button(
                        onClick = {
                            iptvPlayer.playNextChannel()
                            showUI = true
                        },
                        modifier = Modifier.height((34 * buttonScale).dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E28)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("CH +", color = Color.White, fontSize = (11 * buttonScale).sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = {
                            try {
                                iptvPlayer.pausePlayback()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            onExpandFullscreen()
                        },
                        modifier = Modifier.size((34 * buttonScale).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Pantalla Completa",
                            tint = theme.accentOrange,
                            modifier = Modifier.size((22 * buttonScale).dp)
                        )
                    }
                }
            }
        }

        if (showFolderModal) {
            FolderPickerModal(
                onDismiss = {
                    showFolderModal = false
                    showUI = true
                },
                onFolderSelected = { selectedFolder ->
                    val m3uFile = selectedFolder.listFiles()?.firstOrNull {
                        it.extension.lowercase() in listOf("m3u", "m3u8")
                    }
                    if (m3uFile != null) {
                        iptvPlayer.parseAndLoadM3uFile(m3uFile)
                    }
                    showFolderModal = false
                }
            )
        }
    }
}