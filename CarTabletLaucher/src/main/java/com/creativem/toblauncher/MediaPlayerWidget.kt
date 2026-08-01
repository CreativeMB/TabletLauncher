package com.creativem.toblauncher

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.VideoView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.itemsIndexed

// =========================================================================
// 1. ENUM PARA LOS 4 MODOS
// =========================================================================
enum class MediaMode {
    MUSIC, VIDEO, RADIO, IPTV
}

// =========================================================================
// 2. WIDGET CON CONMUTACIÓN EXCLUSIVA Y AUTO-ARRANQUE EN LA POSICIÓN #1
// =========================================================================
@Composable
fun ModernMediaPlayerWidget(
    currentMode: MediaMode = MediaMode.MUSIC,
    onModeChange: (MediaMode) -> Unit = {},
    onExpandMusicFullscreen: () -> Unit = {},
    onExpandVideoFullscreen: () -> Unit = {},
    onExpandIptvFullscreen: () -> Unit = {},
    onExpandRadioFullscreen: () -> Unit = {}
) {
    val theme = LocalDashboardTheme.current
    val context = LocalContext.current

    // Carga inicial del orden de íconos guardado en la tablet
    var tabOrder by remember { mutableStateOf(getSavedMediaTabOrder(context)) }
    var showReorderModal by remember { mutableStateOf(false) }

    // Estado de Scroll para la barra lateral
    val sidebarScrollState = rememberScrollState()

    // Instancias singleton de reproductores
    val musicPlayer = remember { SmartMusicPlayer.getInstance(context) }
    val videoPlayer = remember { SmartVideoPlayer.getInstance(context) }
    val radioPlayer = remember { SmartRadioManager.getInstance(context) }
    val iptvPlayer = remember { SmartIptvPlayer.getInstance(context) }

    // =========================================================================
    // ✅ AUTO-DISPARAR EL MODO DE LA POSICIÓN #1 AL ABRIR EL LAUNCHER
    // =========================================================================
    LaunchedEffect(Unit) {
        if (tabOrder.isNotEmpty()) {
            val firstPreferredMode = tabOrder.first()
            if (currentMode != firstPreferredMode) {
                onModeChange(firstPreferredMode)
            }
        }
    }

    // =========================================================================
    // CONTROLADOR DE EXCLUSIVIDAD TOTAL (GARANTIZA UN SOLO AUDIO ACTIVO)
    // =========================================================================
    LaunchedEffect(currentMode) {
        when (currentMode) {
            MediaMode.MUSIC -> {
                try { radioPlayer.stopPlayback() } catch (e: Exception) { e.printStackTrace() }
                try { videoPlayer.pausePlayback() } catch (e: Exception) { e.printStackTrace() }
                try { iptvPlayer.pausePlayback() } catch (e: Exception) { e.printStackTrace() }
            }
            MediaMode.VIDEO -> {
                try { radioPlayer.stopPlayback() } catch (e: Exception) { e.printStackTrace() }
                try { musicPlayer.pausePlayback() } catch (e: Exception) { e.printStackTrace() }
                try { iptvPlayer.pausePlayback() } catch (e: Exception) { e.printStackTrace() }
            }
            MediaMode.RADIO -> {
                try { musicPlayer.pausePlayback() } catch (e: Exception) { e.printStackTrace() }
                try { videoPlayer.pausePlayback() } catch (e: Exception) { e.printStackTrace() }
                try { iptvPlayer.pausePlayback() } catch (e: Exception) { e.printStackTrace() }
            }
            MediaMode.IPTV -> {
                try { radioPlayer.stopPlayback() } catch (e: Exception) { e.printStackTrace() }
                try { musicPlayer.pausePlayback() } catch (e: Exception) { e.printStackTrace() }
                try { videoPlayer.pausePlayback() } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141414)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // BARRA LATERAL DINÁMICA CON SCROLL
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(62.dp)
                .background(Color(0xFF1A1A1A))
                .verticalScroll(sidebarScrollState)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            tabOrder.forEach { mode ->
                val (icon, color) = when (mode) {
                    MediaMode.MUSIC -> Icons.Default.MusicNote to theme.accentCyan
                    MediaMode.VIDEO -> Icons.Default.PlayCircle to theme.accentOrange
                    MediaMode.RADIO -> Icons.Default.Radio to Color(0xFF00E676)
                    MediaMode.IPTV -> Icons.Default.Tv to theme.accentPurple
                }

                SquareMediaTabButton(
                    icon = icon,
                    isSelected = currentMode == mode,
                    activeColor = color,
                    onClick = { onModeChange(mode) },
                    onLongClick = { showReorderModal = true }
                )
            }
        }

        // CONTENIDO PRINCIPAL REPRODUCTOR
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(8.dp),
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

                    MediaMode.RADIO -> RadioPlayerView(
                        theme = theme,
                    )

                    MediaMode.IPTV -> IptvPlayerView(
                        theme = theme,
                        onExpandFullscreen = onExpandIptvFullscreen
                    )
                }
            }
        }
    }

    // Modal de reorganización
    if (showReorderModal) {
        ReorderMediaTabsModal(
            currentOrder = tabOrder,
            onDismiss = { showReorderModal = false },
            onOrderSaved = { newOrder ->
                tabOrder = newOrder
                saveMediaTabOrder(context, newOrder)

                if (newOrder.isNotEmpty()) {
                    onModeChange(newOrder.first())
                }
            }
        )
    }
}

// =========================================================================
// 3. MODAL REORGANIZADOR
// =========================================================================
@Composable
fun ReorderMediaTabsModal(
    currentOrder: List<MediaMode>,
    onDismiss: () -> Unit,
    onOrderSaved: (List<MediaMode>) -> Unit
) {
    var tabsList by remember { mutableStateOf(currentOrder.toMutableList()) }
    val theme = LocalDashboardTheme.current
    val modalScrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E24),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapVert, contentDescription = null, tint = theme.accentCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Organizar Menú de Medios", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(modalScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("El ícono en el puesto #1 será el que arrancarás al encender el auto:", color = Color.Gray, fontSize = 11.sp)

                tabsList.forEachIndexed { index, mode ->
                    val (title, icon, color) = when (mode) {
                        MediaMode.MUSIC -> Triple("Música USB", Icons.Default.MusicNote, theme.accentCyan)
                        MediaMode.VIDEO -> Triple("Videos USB", Icons.Default.PlayCircle, theme.accentOrange)
                        MediaMode.RADIO -> Triple("Radio Online", Icons.Default.Radio, Color(0xFF00E676))
                        MediaMode.IPTV -> Triple("Televisión IPTV", Icons.Default.Tv, theme.accentPurple)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF282832))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${index + 1}.",
                                color = if (index == 0) theme.accentCyan else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Row {
                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    val mutable = tabsList.toMutableList()
                                    val temp = mutable[index]
                                    mutable[index] = mutable[index - 1]
                                    mutable[index - 1] = temp
                                    tabsList = mutable
                                }
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Subir", tint = if (index > 0) Color.White else Color.DarkGray)
                            }

                            IconButton(
                                enabled = index < tabsList.size - 1,
                                onClick = {
                                    val mutable = tabsList.toMutableList()
                                    val temp = mutable[index]
                                    mutable[index] = mutable[index + 1]
                                    mutable[index + 1] = temp
                                    tabsList = mutable
                                }
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Bajar", tint = if (index < tabsList.size - 1) Color.White else Color.DarkGray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onOrderSaved(tabsList)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accentCyan)
            ) {
                Text("Guardar Cambios", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.White)
            }
        }
    )
}

// =========================================================================
// 🔲 BOTÓN CUADRADO QUE CRECE CON EL TEMA DE LA APLICACIÓN
// =========================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SquareMediaTabButton(
    icon: ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val buttonScale = LocalButtonScale.current

    Box(
        modifier = Modifier
            .size((48 * buttonScale).dp)
            .clip(RoundedCornerShape((12 * buttonScale).dp))
            .background(
                if (isSelected) activeColor.copy(alpha = 0.2f)
                else Color(0xFF252525)
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) activeColor else Color.Transparent,
                shape = RoundedCornerShape((12 * buttonScale).dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) activeColor else Color.Gray,
            modifier = Modifier.size((26 * buttonScale).dp)
        )
    }
}

// =========================================================================
// 📻 VISTA DE RADIO ONLINE EN VIVO (CON CONTROL EXCLUSIVO DE AUDIO)
// =========================================================================
@Composable
fun RadioPlayerView(
    theme: DashboardTheme
) {
    val context = LocalContext.current
    val buttonScale = LocalButtonScale.current

    val radioManager = remember { SmartRadioManager.getInstance(context) }

    // Verificación flexible de conexión
    var isOnline by remember { mutableStateOf(radioManager.isConnectedToInternet()) }

    val currentStation = radioManager.stationList.getOrNull(radioManager.currentStationIndex)
    var favoriteIds by remember { mutableStateOf(radioManager.getSavedFavorites()) }

    val isFavorite = currentStation != null && favoriteIds.contains(currentStation.id)

    // ✅ REPRODUCCIÓN INICIAL LIMPIA AL ABRIR LA RADIO
    // (Sin DisposableEffect para evitar que la animación de Compose mate la señal)
    LaunchedEffect(Unit) {
        isOnline = radioManager.isConnectedToInternet()
        if (isOnline && !radioManager.isPlaying && !radioManager.isLoading) {
            radioManager.playStationAtIndex(radioManager.currentStationIndex)
        }
    }

    // PANTALLA DE ADVERTENCIA SOLO SI REALMENTE NO HAY INTERNET
    if (!isOnline) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0F14))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Se requiere Internet para Radio Online",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        isOnline = true
                        radioManager.playStationAtIndex(radioManager.currentStationIndex)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentCyan)
                ) {
                    Text("Reintentar Conexión", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0B1E16),
                        Color(0xFF141414),
                        theme.accentCyan.copy(alpha = 0.15f)
                    )
                )
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- SECCIÓN IZQUIERDA: INFORMACIÓN DE EMISORA Y CONTROLES ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // CABECERA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = "Radio",
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Radio Online HD",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // BOTÓN CORAZÓN (FAVORITOS)
                IconButton(
                    onClick = {
                        if (currentStation != null) {
                            val newFavs = if (isFavorite) {
                                favoriteIds - currentStation.id
                            } else {
                                (favoriteIds + currentStation.id).distinct()
                            }
                            favoriteIds = newFavs
                            radioManager.saveFavorites(newFavs)
                        }
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (isFavorite) Color.Red else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // DATOS DE LA EMISORA ACTUAL
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentStation?.name ?: "Selecciona Emisora",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${currentStation?.freqLabel} • ${currentStation?.city}",
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = currentStation?.genre ?: "",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }

            // BOTONES DE CONTROL DE STREAMING
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Emisora Anterior
                IconButton(
                    onClick = { radioManager.playPreviousStation() },
                    modifier = Modifier
                        .size((38 * buttonScale).dp)
                        .background(Color(0xFF22222E), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = Color.White,
                        modifier = Modifier.size((24 * buttonScale).dp)
                    )
                }

                // Play / Pausa (Con indicador de Carga)
                IconButton(
                    onClick = { radioManager.togglePlayPause() },
                    modifier = Modifier
                        .size((48 * buttonScale).dp)
                        .background(Color(0xFF00E676), CircleShape)
                ) {
                    if (radioManager.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size((24 * buttonScale).dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (radioManager.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pausa",
                            tint = Color.Black,
                            modifier = Modifier.size((28 * buttonScale).dp)
                        )
                    }
                }

                // Emisora Siguiente
                IconButton(
                    onClick = { radioManager.playNextStation() },
                    modifier = Modifier
                        .size((38 * buttonScale).dp)
                        .background(Color(0xFF22222E), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Siguiente",
                        tint = Color.White,
                        modifier = Modifier.size((24 * buttonScale).dp)
                    )
                }
            }
        }

        // --- SECCIÓN DERECHA: LISTA DE EMISORAS DISPONIBLES ---
        Column(
            modifier = Modifier
                .widthIn(min = 120.dp, max = 160.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF18181F))
                .padding(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "EMISORAS",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(12.dp)
                )
            }

            // LISTA CON SCROLL DE TODAS LAS EMISORAS
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(radioManager.stationList) { index, station ->
                    val isCurrentSelected = index == radioManager.currentStationIndex

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isCurrentSelected) Color(0xFF00E676).copy(alpha = 0.25f)
                                else Color(0xFF252530)
                            )
                            .border(
                                width = if (isCurrentSelected) 1.dp else 0.dp,
                                color = if (isCurrentSelected) Color(0xFF00E676) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                radioManager.playStationAtIndex(index)
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = station.name,
                                color = if (isCurrentSelected) Color(0xFF00E676) else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = station.freqLabel,
                                color = Color.Gray,
                                fontSize = 8.sp,
                                maxLines = 1
                            )
                        }

                        if (favoriteIds.contains(station.id)) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 🎵 VISTA DE MÚSICA
// =========================================================================
@Composable
fun MusicPlayerView(
    theme: DashboardTheme,
    onExpandFullscreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val musicPlayer = remember { SmartMusicPlayer.getInstance(context) }
    var showFolderModal by remember { mutableStateOf(false) }

    val currentTrack = musicPlayer.playlist.getOrNull(musicPlayer.currentTrackIndex)

    LaunchedEffect(musicPlayer.playlist.isNotEmpty()) {
        if (!musicPlayer.isPlaying && musicPlayer.playlist.isNotEmpty()) {
            val indexToPlay = if (musicPlayer.currentTrackIndex in musicPlayer.playlist.indices) {
                musicPlayer.currentTrackIndex
            } else {
                0
            }
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

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// =========================================================================
// 🎬 VISTA DE VIDEO
// =========================================================================
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

    LaunchedEffect(videoPlayer.playlist.isNotEmpty(), videoPlayer.isFullscreenActive) {
        if (!videoPlayer.isFullscreenActive && videoPlayer.playlist.isNotEmpty()) {
            val targetIndex = if (videoPlayer.currentTrackIndex in videoPlayer.playlist.indices) {
                videoPlayer.currentTrackIndex
            } else 0

            if (!videoPlayer.isPlaying) {
                videoPlayer.playVideoAtIndex(targetIndex, 0L)
            }
        }
    }

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

// =========================================================================
// 📺 VISTA DE IPTV
// =========================================================================
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

    LaunchedEffect(iptvPlayer.playlist.isNotEmpty(), iptvPlayer.isFullscreenActive) {
        if (!iptvPlayer.isFullscreenActive && iptvPlayer.playlist.isNotEmpty()) {
            val targetIndex = if (iptvPlayer.currentChannelIndex in iptvPlayer.playlist.indices) {
                iptvPlayer.currentChannelIndex
            } else 0

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

// =========================================================================
// 💾 FUNCIONES DE PERSISTENCIA GENERAL
// =========================================================================
fun saveMediaTabOrder(context: Context, newOrder: List<MediaMode>) {
    val prefs = context.getSharedPreferences("media_widget_prefs", Context.MODE_PRIVATE)
    val serialized = newOrder.joinToString(",") { it.name }
    prefs.edit().putString("tab_order_v1", serialized).apply()
}

fun getSavedMediaTabOrder(context: Context): List<MediaMode> {
    val prefs = context.getSharedPreferences("media_widget_prefs", Context.MODE_PRIVATE)
    val savedStr = prefs.getString("tab_order_v1", null)
    if (savedStr.isNullOrEmpty()) {
        return listOf(MediaMode.MUSIC, MediaMode.VIDEO, MediaMode.RADIO, MediaMode.IPTV)
    }
    return try {
        savedStr.split(",").map { MediaMode.valueOf(it) }
    } catch (e: Exception) {
        listOf(MediaMode.MUSIC, MediaMode.VIDEO, MediaMode.RADIO, MediaMode.IPTV)
    }
}
