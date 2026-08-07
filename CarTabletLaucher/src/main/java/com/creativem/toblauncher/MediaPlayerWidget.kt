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

// 2. WIDGET CON BARRA VERTICAL DE ICONOS A LA DERECHA
@Composable
fun ModernMediaPlayerWidget(
    currentMode: MediaMode = MediaMode.MUSIC,
    onModeChange: (MediaMode) -> Unit = {},
    onExpandMusicFullscreen: () -> Unit = {},
    onExpandVideoFullscreen: () -> Unit = {}
) {
    val theme = LocalDashboardTheme.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141414))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- 1. CONTENIDO PRINCIPAL (IZQUIERDA) ---
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
                    onExpandFullscreen = onExpandVideoFullscreen // <--- CONECTADO AQUÍ
                )
                    MediaMode.IPTV -> IptvPlayerView(theme)
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // --- 2. BARRA LATERAL DERECHA (SOLO ICONOS) ---
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
            // Corregido: Ahora solo abre la pantalla completa de música al tocar el fondo
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

                // BOTÓN DE CARPETA (ABRE EL EXPLORADOR CON EL BOTÓN "SELECCIONAR ESTA CARPETA")
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

            // --- 2. CONTADORES DE TIEMPO Y BARRA DE PROGRESO ---
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

            val buttonScale = LocalButtonScale.current // ESCALA DINÁMICA (0.8x a 1.8x)

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

                // Play / Pausa (GIGANTE CENTRAL)
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

                // BOTÓN CAMBIADO: Reemplazado "Repetir" por "Expandir Pantalla"
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
        // --- VENTANA EMERGENTE: EXPLORADOR CON BOTÓN "SELECCIONAR ESTA CARPETA" ---
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

    // ESTADOS PARA OCULTAR INTERFAZ
    var showUI by remember { mutableStateOf(true) }
    val interactionSource = remember { MutableInteractionSource() }

    val buttonScale = LocalButtonScale.current

    // TEMPORIZADOR ESTRICTO DE 5 SEGUNDOS (Oculta controles automáticamente si está reproduciendo)
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
        // 1. RENDERIZADOR NATIVO DE VIDEO
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
                        setVideoURI(currentVideo.uri)
                        setOnPreparedListener { mp ->
                            videoPlayer.bindMediaPlayer(mp)
                            mp.seekTo(0) // Fuerza siempre inicio desde cero
                            mp.start()
                        }
                        setOnCompletionListener {
                            videoPlayer.playNextVideo()
                        }
                    }
                },
                update = { view ->
                    val currentPlayingUri = view.tag as? String
                    val newUri = currentVideo.uri.toString()

                    if (currentPlayingUri != newUri) {
                        view.tag = newUri
                        view.setVideoURI(currentVideo.uri)
                        view.start()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay videos cargados", color = Color.Gray, fontSize = 12.sp)
            }
        }

        // 2. CAPA SUPERPUESTA DE CONTROLES (ANIMADA)
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
                // CABECERA
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

                // BARRA INFERIOR DE CONTROLES E INDICADORES DE TIEMPO
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Contadores de reproducción (Transcurrido vs Duración Total)
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
                        // 1. BOTÓN ALEATORIO (SHUFFLE PERSISTENTE)
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

                        // 2. ANTERIOR
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

                        // 3. PLAY / PAUSA
                        IconButton(
                            onClick = {
                                videoPlayer.togglePlayPause(currentVideo?.uri?.toString() ?: "")
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

                        // 4. SIGUIENTE
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

                        // BOTÓN PANTALLA COMPLETA (VideoPlayerView.kt)
                        IconButton(
                            onClick = {
                                showUI = false

                                // Consulta segura de la posición para evitar cierres (crashes)
                                val safePosition = try {
                                    videoPlayer.mediaPlayer?.currentPosition?.toLong() ?: 0L
                                } catch (e: Exception) {
                                    0L
                                }
                                videoPlayer.savedPlaybackPosition = safePosition

                                // Pausa segura
                                try {
                                    videoPlayer.mediaPlayer?.pause()
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

        // EXPLORADOR DE CARPETAS
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

@Composable
private fun IptvPlayerView(theme: DashboardTheme) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0D0D0D))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(75.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1E1E1E), Color(0xFF000000)))),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Red)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text("EN VIVO", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Icon(Icons.Default.LiveTv, contentDescription = null, tint = theme.accentPurple, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Canal 05 HD", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Noticias & Deportes", color = Color.Gray, fontSize = 10.sp)

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.height(26.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("CH -", color = theme.accentPurple, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { },
                    modifier = Modifier.height(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentPurple),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("CH +", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}