package com.creativem.toblauncher

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.activity.compose.BackHandler
@OptIn(UnstableApi::class)
@Composable
fun FullscreenVideoPlayerWidget(
    onClose: () -> Unit
) {
    BackHandler {
        onClose()
    }
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current

    val videoPlayer = remember { SmartVideoPlayer.getInstance(context) }

    var showFolderModal by remember { mutableStateOf(false) }
    var showUIState by remember { mutableStateOf(true) }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    val currentVideo = videoPlayer.playlist.getOrNull(videoPlayer.currentTrackIndex)
    val interactionSource = remember { MutableInteractionSource() }
    val buttonScale = LocalButtonScale.current

    DisposableEffect(Unit) {
        videoPlayer.isFullscreenActive = true
        onDispose {
            videoPlayer.isFullscreenActive = false
        }
    }

    val sidebarWidth = 320.dp
    val endPadding = if (showUIState) sidebarWidth else 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                // Alterna visibilidad solo con el toque
                showUIState = !showUIState
            }
    ) {
        // ==========================================
        // 1. ÁREA DE VIDEO (REPRODUCTOR EXOPLAYER)
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = endPadding),
            contentAlignment = Alignment.Center
        ) {
            if (currentVideo != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false // Usamos nuestros propios botones Compose
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                            player = videoPlayer.getOrCreatePlayer()
                        }
                    },
                    update = { playerView ->
                        playerView.player = videoPlayer.getOrCreatePlayer()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay videos cargados", color = Color.Gray, fontSize = 14.sp)
                }
            }

            // BARRA INFERIOR DE CONTROLES
            AnimatedVisibility(
                visible = showUIState,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD14141E)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {

                        val totalMs = videoPlayer.totalDurationMs.coerceAtLeast(1L)

                        val displayPositionMs = if (isDraggingSlider) {
                            (sliderPosition * totalMs).toLong()
                        } else {
                            videoPlayer.currentPositionMs.coerceIn(0L, totalMs)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 0.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatMs(displayPositionMs),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatMs(totalMs),
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        val currentProgress = if (totalMs <= 1L) {
                            0f // Protege la barra para que NUNCA se llene sola si no hay duración
                        } else if (isDraggingSlider) {
                            sliderPosition
                        } else {
                            (displayPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                        }

                        // ==========================================
                        // SLIDER TÁCTIL FLUIDO E INSTANTÁNEO
                        // ==========================================
                        Slider(
                            value = currentProgress,
                            onValueChange = { newValue ->
                                isDraggingSlider = true
                                sliderPosition = newValue
                            },
                            onValueChangeFinished = {
                                val targetMs = (sliderPosition * totalMs).toLong()
                                videoPlayer.seekTo(targetMs)
                                isDraggingSlider = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = theme.accentOrange,
                                activeTrackColor = theme.accentOrange,
                                inactiveTrackColor = theme.cardBorder.copy(alpha = 0.5f)
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // BOTONERA DE CONTROL
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { videoPlayer.toggleShuffle() },
                                modifier = Modifier.size((40 * buttonScale).dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Aleatorio",
                                    tint = if (videoPlayer.isShuffleMode) theme.accentCyan else Color.DarkGray,
                                    modifier = Modifier.size((22 * buttonScale).dp)
                                )
                            }

                            IconButton(
                                onClick = { videoPlayer.playPreviousVideo() },
                                modifier = Modifier
                                    .size((46 * buttonScale).dp)
                                    .background(Color(0xFF22222E), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Anterior",
                                    tint = theme.accentOrange,
                                    modifier = Modifier.size((28 * buttonScale).dp)
                                )
                            }

                            IconButton(
                                onClick = { videoPlayer.togglePlayPause() },
                                modifier = Modifier
                                    .size((56 * buttonScale).dp)
                                    .background(theme.accentCyan, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (videoPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pausa",
                                    tint = Color.Black,
                                    modifier = Modifier.size((32 * buttonScale).dp)
                                )
                            }

                            IconButton(
                                onClick = { videoPlayer.playNextVideo() },
                                modifier = Modifier
                                    .size((46 * buttonScale).dp)
                                    .background(Color(0xFF22222E), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Siguiente",
                                    tint = theme.accentOrange,
                                    modifier = Modifier.size((28 * buttonScale).dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    videoPlayer.pausePlayback()
                                    onClose()
                                },
                                modifier = Modifier.size((40 * buttonScale).dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "Salir Pantalla Completa",
                                    tint = Color.White,
                                    modifier = Modifier.size((24 * buttonScale).dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. PANEL LATERAL DE VIDEOS
        // ==========================================
        AnimatedVisibility(
            visible = showUIState,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight()
                    .background(Color(0xFF101018).copy(alpha = 0.92f))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            videoPlayer.pausePlayback()
                            onClose()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Cerrar", tint = Color.White)
                        }

                        Text("LISTA DE VIDEOS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        IconButton(onClick = { showFolderModal = true }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "USB", tint = theme.accentOrange)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(videoPlayer.playlist) { index, video ->
                            val isSelected = index == videoPlayer.currentTrackIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) theme.accentCyan.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        videoPlayer.playVideoAtIndex(index, 0L)
                                        showUIState = true
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = if (isSelected) theme.accentCyan else Color.Gray,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = video.title,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // EXPLORADOR
        if (showFolderModal) {
            FolderPickerModal(
                onDismiss = {
                    showFolderModal = false
                    showUIState = true
                },
                onFolderSelected = { selectedFolder ->
                    videoPlayer.scanVideoFolderPath(selectedFolder)
                    showFolderModal = false
                }
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}