package com.creativem.toblauncher

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FullscreenMusicPlayerWidget(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current
    val musicPlayer = remember { SmartMusicPlayer.getInstance(context) }
    val currentTrack = musicPlayer.playlist.getOrNull(musicPlayer.currentTrackIndex)

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            musicPlayer.scanFolder(uri)
        }
    }

    // PANTALLA COMPLETA TOTAL
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B10))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- 1. BARRA SUPERIOR (HEADER) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xFF1E1E28), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "REPRODUCTOR MULTIMEDIA",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Botón Seleccionar USB / Carpeta
                Button(
                    onClick = { folderPickerLauncher.launch(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.cardBackground),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = theme.accentOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "📂 ${musicPlayer.selectedFolderName}", color = Color.White, fontSize = 12.sp)
                }
            }

            // --- 2. CONTENIDO PRINCIPAL (PANEL IZQUIERDO Y LISTA DERECHA) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // PANEL IZQUIERDO: CARÁTULA Y DETALLES DE CANCIÓN ACTUAL
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(theme.accentPurple.copy(alpha = 0.4f), Color(0xFF14141E))
                            )
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(theme.accentPurple, theme.accentCyan)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(70.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = currentTrack?.title ?: "Sin Canción",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Audio de Conducción • ${musicPlayer.playlist.size} Canciones",
                            color = theme.accentCyan,
                            fontSize = 12.sp
                        )
                    }
                }

                // PANEL DERECHO: LISTA DE REPRODUCCIÓN COMPLETA (LAZYCOLUMN)
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF14141E))
                        .padding(8.dp)
                ) {
                    if (musicPlayer.playlist.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (musicPlayer.isScanning) "⚡ Analizando archivos USB..." else "No hay canciones cargadas",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(musicPlayer.playlist) { index, track ->
                                val isSelected = index == musicPlayer.currentTrackIndex

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) theme.accentCyan.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable { musicPlayer.playTrackAtIndex(index) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "${index + 1}.",
                                            color = if (isSelected) theme.accentCyan else Color.Gray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(30.dp)
                                        )

                                        Text(
                                            text = track.title,
                                            color = if (isSelected) Color.White else Color.LightGray,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = "Reproduciendo",
                                            tint = theme.accentCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- 3. BARRA INFERIOR DE REPRODUCCIÓN Y BOTONES GIGANTES ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Tiempos y Barra de Progreso
                    val elapsedTimeFormatted = formatMs(musicPlayer.currentPositionMs)
                    val totalTimeFormatted = formatMs(musicPlayer.totalDurationMs)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = elapsedTimeFormatted, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = totalTimeFormatted, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    val progressPercent: Float = if (musicPlayer.totalDurationMs > 0L) {
                        (musicPlayer.currentPositionMs.toFloat() / musicPlayer.totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

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

                    // Controles de Conducción Gigantes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { musicPlayer.toggleShuffle() }, modifier = Modifier.size(42.dp)) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Aleatorio",
                                tint = if (musicPlayer.isShuffle) theme.accentCyan else Color.DarkGray,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = { musicPlayer.playPreviousTrack() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF22222E), CircleShape)
                        ) {
                            Icon(Icons.Default.SkipPrevious, null, tint = theme.accentOrange, modifier = Modifier.size(30.dp))
                        }

                        // PLAY / PAUSA PRINCIPAL GIGANTE
                        IconButton(
                            onClick = { musicPlayer.togglePlayPause() },
                            modifier = Modifier
                                .size(58.dp)
                                .background(theme.accentCyan, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (musicPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pausa",
                                tint = Color.Black,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        IconButton(
                            onClick = { musicPlayer.playNextTrack(userTriggered = true) },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF22222E), CircleShape)
                        ) {
                            Icon(Icons.Default.SkipNext, null, tint = theme.accentOrange, modifier = Modifier.size(30.dp))
                        }

                        IconButton(onClick = { musicPlayer.toggleRepeatMode() }, modifier = Modifier.size(42.dp)) {
                            Icon(
                                imageVector = if (musicPlayer.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                contentDescription = "Repetir",
                                tint = if (musicPlayer.repeatMode == RepeatMode.ONE) theme.accentOrange else Color.DarkGray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
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