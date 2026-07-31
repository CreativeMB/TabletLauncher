package com.creativem.toblauncher

import android.widget.VideoView
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
import kotlinx.coroutines.delay

@Composable
fun FullscreenVideoPlayerWidget(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current

    // Instancia global única y compartida del reproductor de video
    val videoPlayer = remember { SmartVideoPlayer.getInstance(context) }

    var showFolderModal by remember { mutableStateOf(false) }
    var showUIState by remember { mutableStateOf(true) }

    val currentVideo = videoPlayer.playlist.getOrNull(videoPlayer.currentTrackIndex)
    val interactionSource = remember { MutableInteractionSource() }

    val buttonScale = LocalButtonScale.current

    // TEMPORIZADOR DE 5 SEGUNDOS (Oculta automáticamente la interfaz al reproducir)
    LaunchedEffect(showUIState, videoPlayer.isPlaying) {
        if (showUIState && videoPlayer.isPlaying) {
            delay(5000L)
            showUIState = false
        }
    }

    // El ancho físico que ocupará la lista lateral derecha
    val sidebarWidth = 320.dp

    // Cálculo dinámico del margen derecho para que la barra inferior y el video respeten la lista
    val endPadding = if (showUIState) sidebarWidth else 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                showUIState = !showUIState
            }
    ) {
        // ==========================================
        // 1. ÁREA DE VIDEO Y CONTROLES (IZQUIERDA / DESPLAZAMIENTO DINÁMICO)
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = endPadding), // Empuja todo a la izquierda respetando la lista
            contentAlignment = Alignment.BottomCenter
        ) {
            // Renderizador nativo del video
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
                                mp.seekTo(0)
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
                            view.seekTo(0)
                            view.start()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay videos cargados", color = Color.Gray, fontSize = 14.sp)
                }
            }

            // Barra inferior de controles (Sincronizada con el ancho libre de video)
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
                        // Tiempos (Transcurrido vs Total)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatMs(videoPlayer.currentPositionMs),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatMs(videoPlayer.totalDurationMs),
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        val progressPercent: Float = if (videoPlayer.totalDurationMs > 0L) {
                            (videoPlayer.currentPositionMs.toFloat() / videoPlayer.totalDurationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        // Barra de progreso corriendo
                        LinearProgressIndicator(
                            progress = progressPercent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = theme.accentOrange,
                            trackColor = theme.cardBorder
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Botonera de Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Modo Shuffle
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

                            // Anterior
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

                            // Play / Pausa (Gigante central)
                            IconButton(
                                onClick = { videoPlayer.togglePlayPause(currentVideo?.uri?.toString() ?: "") },
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

                            // Siguiente
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

                            // BOTÓN SALIR DE PANTALLA COMPLETA EN CONTROLADORES INFERIORES
                            IconButton(
                                onClick = {
                                    // Consulta segura de la posición
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
        // 2. PANEL LATERAL DE VIDEOS (DERECHA - SIN FILAS / SIN COMPLICACIONES)
        // ==========================================
        AnimatedVisibility(
            visible = showUIState,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd) // Alineación perfecta de Compose nativo
        ) {
            Box(
                modifier = Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight()
                    .background(Color(0xFF101018).copy(alpha = 0.9f))
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
                        // BOTÓN CERRAR SUPERIOR EN LA LISTA LATERAL
                        IconButton(onClick = {
                            // Consulta segura de la posición
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

                            onClose()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Cerrar", tint = Color.White)
                        }

                        Text("LISTA DE VIDEOS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        IconButton(onClick = { showFolderModal = true }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "USB", tint = theme.accentOrange)
                        }
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = if (isSelected) theme.accentCyan else Color.Gray
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = video.title,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    fontSize = 12.sp,
                                    maxLines = 1
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