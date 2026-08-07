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
    // Obtenemos exactamente la misma instancia global del reproductor
    val videoPlayer = remember { SmartVideoPlayer.getInstance(context) }

    var showFolderModal by remember { mutableStateOf(false) }
    var showUIState by remember { mutableStateOf(true) }

    // Obtenemos el video actual basado en el índice que ya venía sonando
    val currentVideo = videoPlayer.playlist.getOrNull(videoPlayer.currentTrackIndex)
    val interactionSource = remember { MutableInteractionSource() }

    // TEMPORIZADOR DE 5 SEGUNDOS PARA OCULTAR INTERFAZ
    LaunchedEffect(showUIState, videoPlayer.currentTrackIndex) {
        if (showUIState) {
            delay(5000L)
            showUIState = false
        }
    }

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
        Row(modifier = Modifier.fillMaxSize()) {

            // CONTENEDOR DEL VIDEO (PANTALLA COMPLETA 100% ESTIRADO)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
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
                                setVideoURI(currentVideo.uri)
                                setOnPreparedListener { mp ->
                                    videoPlayer.bindMediaPlayer(mp)
                                    mp.seekTo(0) // SIEMPRE DESDE EL INICIO
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
                }
            }

            // PANEL LATERAL ANIMADO (Lista de videos y botones)
            AnimatedVisibility(
                visible = showUIState,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .width(320.dp)
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
                            // BOTÓN CERRAR / VOLVER AL WIDGET
                            IconButton(onClick = {
                                // Guardamos la posición exacta antes de salir al widget principal
                                videoPlayer.savedPlaybackPosition = videoPlayer.mediaPlayer?.currentPosition?.toLong() ?: 0L
                                videoPlayer.mediaPlayer?.pause()
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
                                            // Al tocar un video de la lista aquí, sí inicia desde cero (0L)
                                            videoPlayer.playVideoAtIndex(index, 0L)
                                            showUIState = true
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Movie, contentDescription = null, tint = if (isSelected) theme.accentCyan else Color.Gray)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(video.title, color = if (isSelected) Color.White else Color.LightGray, fontSize = 12.sp, maxLines = 1)
                                }
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