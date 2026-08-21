package com.creativem.toblauncher

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.VideoView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    val dashboardFont = LocalDashboardFont.current

    var tabOrder by remember { mutableStateOf(getSavedMediaTabOrder(context)) }
    var showReorderModal by remember { mutableStateOf(false) }

    val preferredFirstMode = remember(tabOrder) {
        tabOrder.firstOrNull() ?: MediaMode.MUSIC
    }

    var isInitialized by remember { mutableStateOf(false) }
    val sidebarScrollState = rememberScrollState()

    val musicPlayer = remember { SmartMusicPlayer.getInstance(context) }
    val videoPlayer = remember { SmartVideoPlayer.getInstance(context) }
    val radioPlayer = remember { SmartRadioManager.getInstance(context) }
    val iptvPlayer = remember { SmartIptvPlayer.getInstance(context) }

    // 1. BLOQUEO Y SILENCIADO INICIAL
    LaunchedEffect(preferredFirstMode) {
        if (preferredFirstMode != MediaMode.MUSIC) runCatching { musicPlayer.pausePlayback() }
        if (preferredFirstMode != MediaMode.VIDEO) runCatching { videoPlayer.pausePlayback() }
        if (preferredFirstMode != MediaMode.RADIO) runCatching { radioPlayer.stopPlayback() }
        if (preferredFirstMode != MediaMode.IPTV) runCatching { iptvPlayer.pausePlayback() }

        if (currentMode != preferredFirstMode) {
            onModeChange(preferredFirstMode)
        }

        isInitialized = true
    }

    // 2. GUARDAS DE SEGURIDAD
    LaunchedEffect(currentMode, musicPlayer.isPlaying) {
        if (currentMode != MediaMode.MUSIC && musicPlayer.isPlaying) {
            runCatching { musicPlayer.pausePlayback() }
        }
    }

    LaunchedEffect(currentMode, videoPlayer.isPlaying) {
        if (currentMode != MediaMode.VIDEO && videoPlayer.isPlaying) {
            runCatching { videoPlayer.pausePlayback() }
        }
    }

    LaunchedEffect(currentMode, radioPlayer.isPlaying) {
        if (currentMode != MediaMode.RADIO && radioPlayer.isPlaying) {
            runCatching { radioPlayer.stopPlayback() }
        }
    }

    LaunchedEffect(currentMode, iptvPlayer.isPlaying) {
        if (currentMode != MediaMode.IPTV && iptvPlayer.isPlaying) {
            runCatching { iptvPlayer.pausePlayback() }
        }
    }

    // 3. REANUDACIÓN EN MODO ACTIVO
    LaunchedEffect(currentMode, isInitialized) {
        if (!isInitialized) return@LaunchedEffect

        when (currentMode) {
            MediaMode.MUSIC -> {
                runCatching { videoPlayer.pausePlayback() }
                runCatching { radioPlayer.stopPlayback() }
                runCatching { iptvPlayer.pausePlayback() }

                if (musicPlayer.playlist.isNotEmpty() && !musicPlayer.isPlaying) {
                    val targetIndex = musicPlayer.currentTrackIndex.coerceIn(0, musicPlayer.playlist.size - 1)
                    musicPlayer.playTrackAtIndex(targetIndex, musicPlayer.currentPositionMs)
                }
            }
            MediaMode.RADIO -> {
                runCatching { musicPlayer.pausePlayback() }
                runCatching { videoPlayer.pausePlayback() }
                runCatching { iptvPlayer.pausePlayback() }

                if (radioPlayer.stationList.isNotEmpty() && !radioPlayer.isPlaying && !radioPlayer.isLoading) {
                    val targetIndex = radioPlayer.currentStationIndex.coerceIn(0, radioPlayer.stationList.size - 1)
                    radioPlayer.playStationAtIndex(targetIndex)
                }
            }
            MediaMode.IPTV -> {
                runCatching { musicPlayer.pausePlayback() }
                runCatching { videoPlayer.pausePlayback() }
                runCatching { radioPlayer.stopPlayback() }

                if (iptvPlayer.playlist.isNotEmpty() && !iptvPlayer.isPlaying) {
                    val targetIndex = iptvPlayer.currentChannelIndex.coerceIn(0, iptvPlayer.playlist.size - 1)
                    iptvPlayer.playChannelAtIndex(targetIndex)
                }
            }
            MediaMode.VIDEO -> {
                runCatching { musicPlayer.pausePlayback() }
                runCatching { radioPlayer.stopPlayback() }
                runCatching { iptvPlayer.pausePlayback() }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        lerp(theme.cardBackground, theme.primaryColor, 0.22f),
                        theme.dashBackground
                    ),
                    radius = 700f
                )
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // =========================================================================
        // 🎛️ BARRA LATERAL CON LOS 3 COLORES DEL TEMA
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(115.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            theme.primaryColor.copy(alpha = 0.25f),
                            theme.textColor.copy(alpha = 0.16f),
                            theme.numberColor.copy(alpha = 0.20f),
                            theme.textColor.copy(alpha = 0.16f),
                            theme.primaryColor.copy(alpha = 0.25f)
                        )
                    )
                )
                .verticalScroll(sidebarScrollState)
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            tabOrder.forEach { mode ->
                val icon = when (mode) {
                    MediaMode.MUSIC -> Icons.Default.MusicNote
                    MediaMode.VIDEO -> Icons.Default.PlayCircle
                    MediaMode.RADIO -> Icons.Default.Radio
                    MediaMode.IPTV  -> Icons.Default.Tv
                }

                SquareMediaTabButton(
                    icon = icon,
                    isSelected = currentMode == mode,
                    theme = theme,
                    onClick = { onModeChange(mode) },
                    onLongClick = { showReorderModal = true }
                )
            }
        }

        // =========================================================================
        // CONTENIDO PRINCIPAL DEL REPRODUCTOR
        // =========================================================================
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
                        dashboardFont = dashboardFont,
                        onExpandFullscreen = onExpandMusicFullscreen
                    )

                    MediaMode.VIDEO -> VideoPlayerView(
                        theme = theme,
                        dashboardFont = dashboardFont,
                        onExpandFullscreen = onExpandVideoFullscreen
                    )

                    MediaMode.RADIO -> RadioPlayerView(
                        theme = theme,
                        dashboardFont = dashboardFont,
                        onExpandFullscreen = onExpandRadioFullscreen
                    )

                    MediaMode.IPTV -> IptvPlayerView(
                        theme = theme,
                        dashboardFont = dashboardFont,
                        onExpandFullscreen = onExpandIptvFullscreen
                    )
                }
            }
        }
    }

    if (showReorderModal) {
        ReorderMediaTabsModal(
            currentOrder = tabOrder,
            theme = theme,
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
    theme: DashboardTheme,
    onDismiss: () -> Unit,
    onOrderSaved: (List<MediaMode>) -> Unit
) {
    var tabsList by remember { mutableStateOf(currentOrder.toMutableList()) }
    val modalScrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E24),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapVert, contentDescription = null, tint = theme.primaryColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Organizar Menú de Medios", color = theme.textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                Text("El ícono en el puesto #1 será el que arrancará al encender el auto:", color = Color.Gray, fontSize = 11.sp)

                tabsList.forEachIndexed { index, mode ->
                    val (title, icon) = when (mode) {
                        MediaMode.MUSIC -> "Música USB" to Icons.Default.MusicNote
                        MediaMode.VIDEO -> "Videos USB" to Icons.Default.PlayCircle
                        MediaMode.RADIO -> "Radio Online" to Icons.Default.Radio
                        MediaMode.IPTV -> "Televisión IPTV" to Icons.Default.Tv
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
                                color = if (index == 0) theme.numberColor else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(imageVector = icon, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = title, color = theme.textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor)
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
// 🔲 BOTÓN CUADRADO DEL MENÚ LATERAL
// =========================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SquareMediaTabButton(
    icon: ImageVector,
    isSelected: Boolean,
    theme: DashboardTheme,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val buttonScale = LocalButtonScale.current

    val containerBrush = if (isSelected) {
        Brush.radialGradient(
            colors = listOf(
                theme.primaryColor,
                theme.primaryColor.copy(alpha = 0.80f)
            )
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF2C2D3E),
                Color(0xFF1C1C28),
                theme.textColor.copy(alpha = 0.18f)
            )
        )
    }

    val borderModifier = if (isSelected) {
        Modifier.border(
            width = 2.dp,
            color = Color.White.copy(alpha = 0.90f),
            shape = RoundedCornerShape((14 * buttonScale).dp)
        )
    } else {
        Modifier.border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    theme.primaryColor.copy(alpha = 0.50f),
                    theme.textColor.copy(alpha = 0.35f),
                    theme.numberColor.copy(alpha = 0.45f)
                )
            ),
            shape = RoundedCornerShape((14 * buttonScale).dp)
        )
    }

    Box(
        modifier = Modifier
            .size((50 * buttonScale).dp)
            .clip(RoundedCornerShape((14 * buttonScale).dp))
            .background(containerBrush)
            .then(borderModifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) Color.Black else Color(0xFFF1F5F9),
            modifier = Modifier.size((26 * buttonScale).dp)
        )
    }
}

data class CountryItem(
    val displayName: String,
    val flag: String,
    val apiName: String
)

fun getApiCountryName(displayName: String): String {
    return when (displayName) {
        "México" -> "Mexico"
        "España" -> "Spain"
        "Perú" -> "Peru"
        "Estados Unidos" -> "The United States Of America"
        "República Dominicana" -> "Dominican Republic"
        else -> displayName
    }
}

// =========================================================================
// 📻 VISTA DE RADIO ONLINE CON 3 COLORES
// =========================================================================
@Composable
fun RadioPlayerView(
    theme: DashboardTheme,
    dashboardFont: DashboardFont,
    onExpandFullscreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val buttonScale = LocalButtonScale.current
    val radioManager = remember { SmartRadioManager.getInstance(context) }

    var isOnline by remember { mutableStateOf(radioManager.isConnectedToInternet()) }
    var selectedCountry by remember { mutableStateOf(radioManager.getSavedCountry()) }
    var showCountryModal by remember { mutableStateOf(false) }

    val currentStation = radioManager.stationList.getOrNull(radioManager.currentStationIndex)
    var favoriteIds by remember { mutableStateOf(radioManager.getSavedFavorites()) }
    val isFavorite = currentStation != null && favoriteIds.contains(currentStation.id)

    val displayStations = remember(radioManager.stationList, favoriteIds) {
        radioManager.stationList.sortedByDescending { favoriteIds.contains(it.id) }
    }

    val currentEqStyle = LocalEqualizerStyle.current

    LaunchedEffect(Unit) {
        while (isActive) {
            val connected = radioManager.isConnectedToInternet()
            if (connected != isOnline) isOnline = connected
            delay(2000L)
        }
    }

    LaunchedEffect(isOnline, selectedCountry, radioManager.stationList.size, radioManager.isApiError) {
        if (isOnline && (radioManager.stationList.isEmpty() || radioManager.isApiError)) {
            while (isActive && isOnline && radioManager.stationList.isEmpty()) {
                if (!radioManager.isFetchingApi) {
                    val apiCountry = getApiCountryName(selectedCountry)
                    radioManager.fetchStationsByCountry(apiCountry) {
                        if (radioManager.stationList.isNotEmpty() && !radioManager.isPlaying) {
                            val savedIndex = if (radioManager.currentStationIndex in radioManager.stationList.indices) {
                                radioManager.currentStationIndex
                            } else 0
                            radioManager.playStationAtIndex(savedIndex)
                        }
                    }
                }
                delay(4000L)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        theme.primaryColor.copy(alpha = 0.20f),
                        theme.dashBackground,
                        theme.primaryColor.copy(alpha = 0.08f)
                    )
                )
            )
            .border(1.dp, theme.primaryColor.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
    ) {
        EqualizerVisualizer(
            isPlaying = radioManager.isPlaying,
            primaryColor = theme.primaryColor,
            secondaryColor = theme.textColor,
            tertiaryColor = theme.numberColor,
            style = currentEqStyle,
            modifier = Modifier.fillMaxSize()
        )

        when {
            !isOnline -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = theme.primaryColor,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Esperando Conexión Wi-Fi / Datos...",
                            color = theme.textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cargará las emisoras automáticamente al obtener internet.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            else -> {
                Row(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
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
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (radioManager.isPlaying) theme.primaryColor else theme.primaryColor.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (radioManager.isPlaying) "EN VIVO" else "SEÑAL RADIO",
                                        color = if (radioManager.isPlaying) Color.Black else theme.primaryColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1E28))
                                        .border(1.dp, theme.primaryColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable { showCountryModal = true }
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Public, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = selectedCountry, color = theme.textColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E1E26))
                                        .clickable {
                                            if (currentStation != null) {
                                                val newFavs = if (isFavorite) favoriteIds - currentStation.id else (favoriteIds + currentStation.id).distinct()
                                                favoriteIds = newFavs
                                                radioManager.saveFavorites(newFavs)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorito",
                                        tint = if (isFavorite) Color(0xFFFF5252) else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF1E1E26)).clickable { onExpandFullscreen() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // 🟣 Título de la emisora (Color de Texto) y 🟠 Frecuencia (Color de Números)
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentStation?.name ?: "Cargando Emisora...",
                                color = theme.textColor,
                                fontSize = 16.sp,
                                fontFamily = dashboardFont.fontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = currentStation?.freqLabel ?: "",
                                    color = theme.numberColor, // 🟠 Frecuencia en Color de Números
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = " • ${currentStation?.city ?: selectedCountry}",
                                    color = theme.textColor.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Botonera de Control (Color Primario)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF16161E).copy(alpha = 0.85f))
                                .border(1.dp, Color(0xFF262636), RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { radioManager.playPreviousStation() },
                                    modifier = Modifier.size((40 * buttonScale).dp).background(Color(0xFF22222E), CircleShape)
                                ) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size((22 * buttonScale).dp))
                                }

                                IconButton(
                                    onClick = { radioManager.togglePlayPause() },
                                    modifier = Modifier.size((50 * buttonScale).dp).background(theme.primaryColor, CircleShape)
                                ) {
                                    if (radioManager.isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size((26 * buttonScale).dp), color = Color.Black, strokeWidth = 2.5.dp)
                                    } else {
                                        Icon(if (radioManager.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size((28 * buttonScale).dp))
                                    }
                                }

                                IconButton(
                                    onClick = { radioManager.playNextStation() },
                                    modifier = Modifier.size((40 * buttonScale).dp).background(Color(0xFF22222E), CircleShape)
                                ) {
                                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size((22 * buttonScale).dp))
                                }
                            }
                        }
                    }

                    // Lista de Emisoras Lateral
                    Column(
                        modifier = Modifier
                            .widthIn(min = 125.dp, max = 230.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF14141C).copy(alpha = 0.9f))
                            .border(1.dp, Color(0xFF22222E), RoundedCornerShape(12.dp))
                            .padding(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("EMISORAS", color = theme.textColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            Text("(${displayStations.size})", color = theme.numberColor, fontSize = 9.sp, fontWeight = FontWeight.Bold) // 🟠 Cantidad
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(displayStations) { _, station ->
                                val isCurrentSelected = currentStation?.id == station.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCurrentSelected) theme.primaryColor.copy(alpha = 0.22f) else Color(0xFF1E1E28))
                                        .border(if (isCurrentSelected) 1.dp else 0.dp, if (isCurrentSelected) theme.primaryColor else Color.Transparent, RoundedCornerShape(8.dp))
                                        .clickable {
                                            val idx = radioManager.stationList.indexOfFirst { it.id == station.id }
                                            if (idx != -1) radioManager.playStationAtIndex(idx)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = station.name,
                                        color = if (isCurrentSelected) theme.primaryColor else theme.textColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCountryModal) {
            CountryPickerModal(
                currentCountry = selectedCountry,
                theme = theme,
                onDismiss = { showCountryModal = false },
                onCountrySelected = { displayName, apiName ->
                    selectedCountry = displayName
                    radioManager.selectedCountry = displayName
                    showCountryModal = false
                    radioManager.fetchStationsByCountry(apiName) {
                        if (radioManager.stationList.isNotEmpty()) radioManager.playStationAtIndex(0)
                    }
                }
            )
        }
    }
}

// =========================================================================
// 🌎 MODAL PARA SELECCIONAR PAÍS
// =========================================================================
@Composable
fun CountryPickerModal(
    currentCountry: String,
    theme: DashboardTheme,
    onDismiss: () -> Unit,
    onCountrySelected: (displayName: String, apiName: String) -> Unit
) {
    val countries = listOf(
        CountryItem("Colombia", "🇨🇴", "Colombia"),
        CountryItem("México", "🇲🇽", "Mexico"),
        CountryItem("España", "🇪🇸", "Spain"),
        CountryItem("Argentina", "🇦🇷", "Argentina"),
        CountryItem("Chile", "🇨🇱", "Chile"),
        CountryItem("Perú", "🇵🇪", "Peru"),
        CountryItem("Ecuador", "🇪🇨", "Ecuador"),
        CountryItem("Venezuela", "🇻🇪", "Venezuela"),
        CountryItem("Estados Unidos", "🇺🇸", "The United States Of America"),
        CountryItem("Uruguay", "🇺🇾", "Uruguay"),
        CountryItem("Bolivia", "🇧🇴", "Bolivia"),
        CountryItem("República Dominicana", "🇩🇴", "Dominican Republic")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C24),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Public, contentDescription = null, tint = theme.primaryColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Selecciona Tu País", color = theme.textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                countries.forEach { item ->
                    val isSelected = item.displayName.equals(currentCountry, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) theme.primaryColor.copy(alpha = 0.2f) else Color(0xFF262632))
                            .border(1.dp, if (isSelected) theme.primaryColor else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { onCountrySelected(item.displayName, item.apiName) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = item.flag, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = item.displayName, color = if (isSelected) theme.primaryColor else theme.textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = Color.White) }
        }
    )
}

// =========================================================================
// 🎵 VISTA DE MÚSICA CON 3 COLORES
// =========================================================================
@Composable
fun MusicPlayerView(
    theme: DashboardTheme,
    dashboardFont: DashboardFont,
    onExpandFullscreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val musicPlayer = remember { SmartMusicPlayer.getInstance(context) }
    var showFolderModal by remember { mutableStateOf(false) }

    val currentTrack = musicPlayer.playlist.getOrNull(musicPlayer.currentTrackIndex)
    val currentEqStyle = LocalEqualizerStyle.current
    val buttonScale = LocalButtonScale.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        theme.primaryColor.copy(alpha = 0.20f),
                        theme.dashBackground,
                        theme.primaryColor.copy(alpha = 0.08f)
                    )
                )
            )
            .clickable { onExpandFullscreen() }
            .padding(10.dp)
    ) {
        EqualizerVisualizer(
            isPlaying = musicPlayer.isPlaying,
            primaryColor = theme.primaryColor,
            secondaryColor = theme.textColor,
            tertiaryColor = theme.numberColor,
            style = currentEqStyle,
            modifier = Modifier.fillMaxSize()
        )

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
                    // 🟣 Título de la canción en COLOR DE TEXTO
                    Text(
                        text = currentTrack?.title ?: if (musicPlayer.isScanning) "Analizando USB..." else "Sin Música",
                        color = theme.textColor,
                        fontSize = 15.sp,
                        fontFamily = dashboardFont.fontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // 🟣 Carpeta + 🟠 Cantidad de pistas en COLOR DE NÚMEROS
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (musicPlayer.playlist.isNotEmpty()) "📂 ${musicPlayer.selectedFolderName} • " else "📂 Conecta tu USB",
                            color = theme.textColor.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (musicPlayer.playlist.isNotEmpty()) {
                            Text(
                                text = "${musicPlayer.playlist.size} pistas",
                                color = theme.numberColor, // 🟠 Número de pistas
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { showFolderModal = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Abrir USB",
                        tint = theme.primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Barra de progreso y tiempos
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                val elapsedTimeFormatted = formatMs(musicPlayer.currentPositionMs)
                val totalTimeFormatted = formatMs(musicPlayer.totalDurationMs)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 🟠 Tiempos en COLOR DE NÚMEROS
                    Text(
                        text = elapsedTimeFormatted,
                        color = theme.numberColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = totalTimeFormatted,
                        color = theme.numberColor.copy(alpha = 0.7f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val progressPercent: Float = if (musicPlayer.totalDurationMs > 0L) {
                    (musicPlayer.currentPositionMs.toFloat() / musicPlayer.totalDurationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                LinearProgressIndicator(
                    progress = progressPercent,
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = theme.primaryColor, // 🔵 Barra en Color Primario
                    trackColor = theme.cardBorder.copy(alpha = 0.4f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botonera Multimedia
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { musicPlayer.toggleShuffle() },
                    modifier = Modifier.size((38 * buttonScale).dp)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, tint = if (musicPlayer.isShuffle) theme.primaryColor else Color.DarkGray, modifier = Modifier.size((20 * buttonScale).dp))
                }

                IconButton(
                    onClick = { musicPlayer.playPreviousTrack() },
                    modifier = Modifier.size((44 * buttonScale).dp).background(Color(0xFF22222E), CircleShape)
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size((28 * buttonScale).dp))
                }

                IconButton(
                    onClick = { musicPlayer.togglePlayPause() },
                    modifier = Modifier.size((54 * buttonScale).dp).background(theme.primaryColor, CircleShape)
                ) {
                    Icon(if (musicPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size((32 * buttonScale).dp))
                }

                IconButton(
                    onClick = { musicPlayer.playNextTrack(userTriggered = true) },
                    modifier = Modifier.size((44 * buttonScale).dp).background(Color(0xFF22222E), CircleShape)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size((28 * buttonScale).dp))
                }

                IconButton(
                    onClick = { onExpandFullscreen() },
                    modifier = Modifier.size((38 * buttonScale).dp)
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size((22 * buttonScale).dp))
                }
            }
        }

        if (showFolderModal) {
            FolderPickerModal(
                onDismiss = { showFolderModal = false },
                onFolderSelected = { selectedFolder -> musicPlayer.scanFolderPath(selectedFolder) }
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
// 🎬 VISTA DE VIDEO CON 3 COLORES
// =========================================================================
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    theme: DashboardTheme,
    dashboardFont: DashboardFont,
    onExpandFullscreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val videoPlayer = remember { SmartVideoPlayer.getInstance(context) }
    var showFolderModal by remember { mutableStateOf(false) }

    val currentVideo = videoPlayer.playlist.getOrNull(videoPlayer.currentTrackIndex)
    val showUI = videoPlayer.showControls
    val interactionSource = remember { MutableInteractionSource() }
    val buttonScale = LocalButtonScale.current

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    val rebindTrigger = videoPlayer.rebindTrigger
    var hasAutoPlayedForCurrentVideo by remember(currentVideo) { mutableStateOf(false) }

    LaunchedEffect(currentVideo) {
        if (currentVideo != null && !hasAutoPlayedForCurrentVideo) {
            videoPlayer.forcePlay()
            hasAutoPlayedForCurrentVideo = true
        }
    }

    LaunchedEffect(videoPlayer.isFullscreenActive) {
        if (!videoPlayer.isFullscreenActive && videoPlayer.playlist.isNotEmpty() && !videoPlayer.isPlaying) {
            videoPlayer.forcePlay()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable(interactionSource = interactionSource, indication = null) {
                videoPlayer.toggleControls()
            }
    ) {
        if (currentVideo != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val exoPlayer = if (!videoPlayer.isFullscreenActive) videoPlayer.getOrCreatePlayer() else null
                        exoPlayer?.playWhenReady = true
                        player = exoPlayer
                    }
                },
                update = { playerView ->
                    val player = if (!videoPlayer.isFullscreenActive) videoPlayer.getOrCreatePlayer() else null
                    if (rebindTrigger >= 0 && player != null) {
                        playerView.player = null
                        playerView.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentVideo?.title ?: "Reproductor de Video",
                        color = theme.textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        videoPlayer.resetControlsTimer()
                        showFolderModal = true
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(18.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                val totalMs = videoPlayer.totalDurationMs.coerceAtLeast(1L)
                val displayPositionMs = if (isDraggingSlider) (sliderPosition * totalMs).toLong() else videoPlayer.currentPositionMs.coerceIn(0L, totalMs)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = formatMs(displayPositionMs), color = theme.numberColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(text = formatMs(totalMs), color = theme.numberColor.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }

                val currentProgress = if (totalMs <= 1L) 0f else if (isDraggingSlider) sliderPosition else (displayPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

                Slider(
                    value = currentProgress,
                    onValueChange = {
                        isDraggingSlider = true
                        sliderPosition = it
                        videoPlayer.resetControlsTimer()
                    },
                    onValueChangeFinished = {
                        videoPlayer.seekTo((sliderPosition * totalMs).toLong())
                        isDraggingSlider = false
                    },
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                    colors = SliderDefaults.colors(thumbColor = theme.primaryColor, activeTrackColor = theme.primaryColor)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { videoPlayer.toggleShuffle() }, modifier = Modifier.size((34 * buttonScale).dp)) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = if (videoPlayer.isShuffleMode) theme.primaryColor else Color.DarkGray, modifier = Modifier.size((18 * buttonScale).dp))
                    }
                    IconButton(onClick = { videoPlayer.playPreviousVideo() }, modifier = Modifier.size((38 * buttonScale).dp).background(Color(0xFF22222E), CircleShape)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size((22 * buttonScale).dp))
                    }
                    IconButton(onClick = { videoPlayer.togglePlayPause() }, modifier = Modifier.size((46 * buttonScale).dp).background(theme.primaryColor, CircleShape)) {
                        Icon(if (videoPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size((26 * buttonScale).dp))
                    }
                    IconButton(onClick = { videoPlayer.playNextVideo() }, modifier = Modifier.size((38 * buttonScale).dp).background(Color(0xFF22222E), CircleShape)) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size((22 * buttonScale).dp))
                    }
                    IconButton(onClick = { onExpandFullscreen() }, modifier = Modifier.size((34 * buttonScale).dp)) {
                        Icon(Icons.Default.Fullscreen, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size((20 * buttonScale).dp))
                    }
                }
            }
        }

        if (showFolderModal) {
            FolderPickerModal(
                onDismiss = {
                    showFolderModal = false
                    videoPlayer.resetControlsTimer()
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
// 📺 VISTA DE IPTV CON 3 COLORES
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IptvPlayerView(
    theme: DashboardTheme,
    dashboardFont: DashboardFont,
    onExpandFullscreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val iptvPlayer = remember { SmartIptvPlayer.getInstance(context) }
    var showUrlModal by remember { mutableStateOf(false) }

    val currentChannel = iptvPlayer.playlist.getOrNull(iptvPlayer.currentChannelIndex)
    val buttonScale = LocalButtonScale.current
    var showUI by remember { mutableStateOf(true) }
    val interactionSource = remember { MutableInteractionSource() }
    var isOnline by remember { mutableStateOf(iptvPlayer.isConnectedToInternet()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val connected = iptvPlayer.isConnectedToInternet()
            if (connected != isOnline) {
                isOnline = connected
                if (connected && iptvPlayer.playlist.isNotEmpty() && !iptvPlayer.isPlaying) {
                    val idx = if (iptvPlayer.currentChannelIndex in iptvPlayer.playlist.indices) iptvPlayer.currentChannelIndex else 0
                    iptvPlayer.playChannelAtIndex(idx)
                }
            }
            delay(2000L)
        }
    }

    LaunchedEffect(showUI, iptvPlayer.isPlaying) {
        if (showUI && iptvPlayer.isPlaying) {
            delay(5000L)
            showUI = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.Black).clickable(interactionSource = interactionSource, indication = null) { showUI = !showUI }
    ) {
        if (currentChannel != null) {
            AndroidView(
                factory = { ctx ->
                    object : VideoView(ctx) {
                        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), MeasureSpec.getSize(h))
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
        }

        AnimatedVisibility(visible = showUI, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent, Color(0xCC000000)))).padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Red).padding(horizontal = 5.dp, vertical = 2.dp)) {
                            Text("EN VIVO", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = currentChannel?.name ?: "Sin Canal",
                                color = theme.textColor, // 🟣 Canal en Color de Texto
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Row {
                                Text(text = "📺 ${iptvPlayer.selectedFileName} • ", color = theme.textColor.copy(alpha = 0.8f), fontSize = 9.sp)
                                Text(text = "${iptvPlayer.playlist.size} canales", color = theme.numberColor, fontSize = 9.sp, fontWeight = FontWeight.Bold) // 🟠 Cantidad de Canales
                            }
                        }
                    }

                    IconButton(onClick = { showUrlModal = true; showUI = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(22.dp))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { iptvPlayer.playPreviousChannel(); showUI = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E28)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("CH -", color = theme.textColor, fontSize = (11 * buttonScale).sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = { iptvPlayer.togglePlayPause(); showUI = true },
                        modifier = Modifier.size((42 * buttonScale).dp).background(theme.primaryColor, CircleShape)
                    ) {
                        Icon(if (iptvPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size((24 * buttonScale).dp))
                    }

                    Button(
                        onClick = { iptvPlayer.playNextChannel(); showUI = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E28)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("CH +", color = theme.textColor, fontSize = (11 * buttonScale).sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = { onExpandFullscreen() },
                        modifier = Modifier.size((34 * buttonScale).dp)
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size((22 * buttonScale).dp))
                    }
                }
            }
        }

        if (showUrlModal) {
            RemoteUrlInputDialog(
                theme = theme,
                currentUrl = context.getSharedPreferences("smart_iptv_prefs", Context.MODE_PRIVATE).getString("selected_playlist_url", "") ?: "",
                onDismiss = { showUrlModal = false; showUI = true },
                onConfirmUrl = { url ->
                    if (url.isNotBlank()) iptvPlayer.parseAndLoadM3uUrl(url.trim())
                    showUrlModal = false
                    showUI = true
                }
            )
        }
    }
}

// =========================================================================
// 🔗 MODAL URL REMOTA
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteUrlInputDialog(
    theme: DashboardTheme,
    currentUrl: String,
    onDismiss: () -> Unit,
    onConfirmUrl: (String) -> Unit
) {
    var urlText by remember { mutableStateOf(currentUrl) }
    val clipboardManager = LocalClipboardManager.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16161F)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Configurar Lista IPTV", color = theme.textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Pegar enlace M3U", color = Color.Gray, fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = theme.primaryColor,
                        focusedLabelColor = theme.primaryColor
                    ),
                    textStyle = TextStyle(fontSize = 12.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.Gray, fontSize = 12.sp) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirmUrl(urlText) }, colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor), shape = RoundedCornerShape(6.dp)) {
                        Text("Guardar", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 💾 PERSISTENCIA
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