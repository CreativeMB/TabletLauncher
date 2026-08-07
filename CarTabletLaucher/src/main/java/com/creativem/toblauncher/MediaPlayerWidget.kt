package com.creativem.toblauncher

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.VideoView
import androidx.annotation.OptIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.isActive


import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.window.Dialog





import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
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

    // Carga del orden de íconos guardado en la tablet
    var tabOrder by remember { mutableStateOf(getSavedMediaTabOrder(context)) }
    var showReorderModal by remember { mutableStateOf(false) }

    // Identificar la prioridad #1 absoluta del usuario
    val preferredFirstMode = remember(tabOrder) {
        tabOrder.firstOrNull() ?: MediaMode.MUSIC
    }

    var isInitialized by remember { mutableStateOf(false) }
    val sidebarScrollState = rememberScrollState()

    // Instancias de reproductores
    val musicPlayer = remember { SmartMusicPlayer.getInstance(context) }
    val videoPlayer = remember { SmartVideoPlayer.getInstance(context) }
    val radioPlayer = remember { SmartRadioManager.getInstance(context) }
    val iptvPlayer = remember { SmartIptvPlayer.getInstance(context) }

    // =========================================================================
    // ✅ 1. BLOQUEO Y SILENCIADO INICIAL (SIN PAUSAR EL MODO #1)
    // =========================================================================
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

    // =========================================================================
    // 🛡️ 2. GUARDAS DE SEGURIDAD: SILENCIAR CUALQUIER AUDIO INACTIVO
    // =========================================================================
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

    // =========================================================================
    // 🚀 3. BUCLE DE CONTROL Y REANUDACIÓN SILENCIOSA
    // =========================================================================
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
                // La reproducción de video la gestiona de forma segura VideoPlayerView
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
                .width(120.dp)
                .background(Color(0xFF1A1A1A))
                .verticalScroll(sidebarScrollState)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            tabOrder.forEach { mode ->
                val icon = when (mode) {
                    MediaMode.MUSIC -> Icons.Default.MusicNote
                    MediaMode.VIDEO -> Icons.Default.PlayCircle
                    MediaMode.RADIO -> Icons.Default.Radio
                    MediaMode.IPTV -> Icons.Default.Tv
                }

                SquareMediaTabButton(
                    icon = icon,
                    isSelected = currentMode == mode,
                    activeColor = theme.accentCyan,
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
                        onExpandFullscreen = onExpandRadioFullscreen
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
                                color = if (index == 0) theme.accentCyan else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(imageVector = icon, contentDescription = null, tint = theme.accentCyan, modifier = Modifier.size(22.dp))
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
// 🌎 ESTRUCTURA DE DATOS Y AYUDANTE PARA LA API DE RADIO
// =========================================================================
data class CountryItem(
    val displayName: String, // Nombre en español para mostrar en pantalla
    val flag: String,        // Emoji de la bandera
    val apiName: String      // Nombre exacto que requiere la API de Radio Browser
)

// Convierte nombres en español al nombre en inglés reconocido por la API
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
// 📻 VISTA DE RADIO ONLINE (CON AUTO-RECONEXIÓN Y RETRY AUTOMÁTICO DE API)
// =========================================================================
// =========================================================================
// 📻 VISTA DE RADIO ONLINE (CON ECUALIZADOR DINÁMICO)
// =========================================================================
@Composable
fun RadioPlayerView(
    theme: DashboardTheme,
    onExpandFullscreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val buttonScale = LocalButtonScale.current ?: 1.0f

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

    // 🎛️ RECUPERAR ESTILO DE ECUALIZADOR GUARDADO
    val currentEqStyle = LocalEqualizerStyle.current

    LaunchedEffect(Unit) {
        while (isActive) {
            val connected = radioManager.isConnectedToInternet()
            if (connected != isOnline) {
                isOnline = connected
            }
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
                        theme.accentCyan.copy(alpha = 0.18f),
                        Color(0xFF101014),
                        theme.accentCyan.copy(alpha = 0.06f)
                    )
                )
            )
            .border(1.dp, theme.accentCyan.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
    ) {
        // 📊 ECUALIZADOR CON ESTILO Y COLORES DINÁMICOS
        EqualizerVisualizer(
            isPlaying = radioManager.isPlaying,
            primaryColor = theme.accentCyan,
            secondaryColor = theme.accentPurple,
            tertiaryColor = theme.accentOrange,
            style = currentEqStyle, // 👈 AHORA APLICA EL ESTILO SELECCIONADO
            modifier = Modifier.fillMaxSize()
        )

        when {
            !isOnline -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = theme.accentCyan,
                            strokeWidth = 3.dp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Esperando Conexión Wi-Fi / Datos...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Cargará las emisoras de radio automáticamente al obtener internet.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            radioManager.isFetchingApi && radioManager.stationList.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = theme.accentCyan,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Obteniendo emisoras de $selectedCountry...",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            radioManager.isApiError || radioManager.stationList.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color(0xFFFFB74D),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Conectando al servidor de radio ($selectedCountry)...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Reintentando conectar automáticamente...",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { showCountryModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accentCyan),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cambiar País", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
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
                                        .background(
                                            if (radioManager.isPlaying) Color(0xFF00C853) else theme.accentCyan.copy(alpha = 0.2f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (radioManager.isPlaying) "EN VIVO" else "SEÑAL RADIO",
                                        color = if (radioManager.isPlaying) Color.Black else theme.accentCyan,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1E28))
                                        .border(1.dp, theme.accentCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable { showCountryModal = true }
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = "Pon tu país",
                                        tint = theme.accentCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = selectedCountry,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
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
                                                val newFavs = if (isFavorite) {
                                                    favoriteIds - currentStation.id
                                                } else {
                                                    (favoriteIds + currentStation.id).distinct()
                                                }
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
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E1E26))
                                        .clickable { onExpandFullscreen() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Pantalla Completa",
                                        tint = theme.accentCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentStation?.name ?: "Cargando Emisora...",
                                color = Color.White,
                                fontSize = 16.sp,
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
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = theme.accentCyan,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${currentStation?.freqLabel ?: ""} • ${currentStation?.city ?: selectedCountry}",
                                    color = theme.accentCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = currentStation?.genre ?: "Variada",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }

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
                                Box(
                                    modifier = Modifier
                                        .size((40 * buttonScale).dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22222E))
                                        .border(1.dp, Color(0xFF333345), CircleShape)
                                        .clickable { radioManager.playPreviousStation() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Anterior",
                                        tint = theme.accentCyan,
                                        modifier = Modifier.size((22 * buttonScale).dp)
                                    )
                                }

                                val playButtonScale by animateFloatAsState(
                                    targetValue = if (radioManager.isPlaying) 1.05f else 1.0f,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "playScale"
                                )

                                Box(
                                    modifier = Modifier
                                        .scale(playButtonScale)
                                        .size((50 * buttonScale).dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    theme.accentCyan,
                                                    theme.accentCyan.copy(alpha = 0.85f)
                                                )
                                            )
                                        )
                                        .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                        .clickable { radioManager.togglePlayPause() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (radioManager.isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size((26 * buttonScale).dp),
                                            color = Color.Black,
                                            strokeWidth = 2.5.dp
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

                                Box(
                                    modifier = Modifier
                                        .size((40 * buttonScale).dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22222E))
                                        .border(1.dp, Color(0xFF333345), CircleShape)
                                        .clickable { radioManager.playNextStation() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Siguiente",
                                        tint = theme.accentCyan,
                                        modifier = Modifier.size((22 * buttonScale).dp)
                                    )
                                }
                            }
                        }
                    }

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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "EMISORAS (${displayStations.size})",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = theme.accentCyan,
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(displayStations) { _, station ->
                                val isCurrentSelected = currentStation?.id == station.id
                                val isStationFav = favoriteIds.contains(station.id)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isCurrentSelected) theme.accentCyan.copy(alpha = 0.22f)
                                            else Color(0xFF1E1E28)
                                        )
                                        .border(
                                            width = if (isCurrentSelected) 1.dp else 0.dp,
                                            color = if (isCurrentSelected) theme.accentCyan else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            val originalIndex = radioManager.stationList.indexOfFirst { it.id == station.id }
                                            if (originalIndex != -1) {
                                                radioManager.playStationAtIndex(originalIndex)
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = station.name,
                                            color = if (isCurrentSelected) theme.accentCyan else Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = if (isCurrentSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = station.city.ifEmpty { selectedCountry },
                                            color = Color.Gray,
                                            fontSize = 8.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (isStationFav) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = "Favorita",
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier
                                                .size(12.dp)
                                                .padding(start = 2.dp)
                                        )
                                    }
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
                        if (radioManager.stationList.isNotEmpty()) {
                            radioManager.playStationAtIndex(0)
                        }
                    }
                }
            )
        }
    }
}

// =========================================================================
// 🌎 MODAL PARA SELECCIONAR EL PAÍS DE EMISORAS
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
                Icon(Icons.Default.Public, contentDescription = null, tint = theme.accentCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pon Tu País", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Elige tu ubicación para cargar emisoras locales HD:", color = Color.Gray, fontSize = 11.sp)

                countries.forEach { item ->
                    val isSelected = item.displayName.equals(currentCountry, ignoreCase = true)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) theme.accentCyan.copy(alpha = 0.2f) else Color(0xFF262632))
                            .border(1.dp, if (isSelected) theme.accentCyan else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { onCountrySelected(item.displayName, item.apiName) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = item.flag, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = item.displayName,
                                color = if (isSelected) theme.accentCyan else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = theme.accentCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.White)
            }
        }
    )
}

// =========================================================================
// 🎵 VISTA DE MÚSICA CON ECUALIZADOR DINÁMICO
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

    // 🎛️ RECUPERAR ESTILO DE ECUALIZADOR GUARDADO
    val currentEqStyle = LocalEqualizerStyle.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        theme.accentCyan.copy(alpha = 0.25f),
                        Color(0xFF0F0F14),
                        theme.accentCyan.copy(alpha = 0.10f)
                    )
                )
            )
            .clickable { onExpandFullscreen() }
            .padding(10.dp)
    ) {
        // 📊 ECUALIZADOR CON ESTILO Y COLORES DINÁMICOS DEL TEMA
        EqualizerVisualizer(
            isPlaying = musicPlayer.isPlaying,
            primaryColor = theme.accentCyan,
            secondaryColor = theme.accentPurple,
            tertiaryColor = theme.accentOrange,
            style = currentEqStyle, // 👈 AHORA APLICA EL ESTILO SELECCIONADO
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
                        color = theme.accentCyan,
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
                        tint = theme.accentCyan,
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
                    color = theme.accentCyan,
                    trackColor = theme.cardBorder
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val buttonScale = LocalButtonScale.current ?: 1.0f

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
                        tint = theme.accentCyan,
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
                        tint = theme.accentCyan,
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
                        tint = theme.accentCyan,
                        modifier = Modifier.size((22 * buttonScale).dp)
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
// 🎬 VISTA DE VIDEO (AUTO-ARRANQUE ÚNICO SIN PAUSA DOBLE)
// =========================================================================
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    theme: DashboardTheme,
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
    // ✅ AUTO-ARRANQUE GARANTIZADO ÚNICO (EVITA QUE SE PAUSE TRAS 1 SEGUNDO)
    var hasAutoPlayedForCurrentVideo by remember(currentVideo) { mutableStateOf(false) }

    // ✅ 1. Auto-arranque al cargar el video por primera vez
    LaunchedEffect(currentVideo) {
        if (currentVideo != null && !hasAutoPlayedForCurrentVideo) {
            videoPlayer.forcePlay()
            hasAutoPlayedForCurrentVideo = true
        }
    }

    // ✅ 2. Reanudar cuando SALGAS de Pantalla Completa (USANDO forcePlay, NUNCA togglePlayPause)
    LaunchedEffect(videoPlayer.isFullscreenActive) {
        // Solo actúa si NO está en pantalla completa y el video estaba pausado
        if (!videoPlayer.isFullscreenActive && videoPlayer.playlist.isNotEmpty() && !videoPlayer.isPlaying) {
            videoPlayer.forcePlay() // 👈 AQUÍ ESTABA EL ERROR: Decía togglePlayPause()
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
                        exoPlayer?.playWhenReady = true // 👈 ESTO FORZA EL PLAY INMEDIATO EN EXOPLAYER
                        player = exoPlayer
                    }
                },
                update = { playerView ->
                    val player = if (!videoPlayer.isFullscreenActive) videoPlayer.getOrCreatePlayer() else null
                    if (rebindTrigger >= 0 && player != null) {
                        playerView.player = null // Desconecta la superficie vieja
                        playerView.player = player // Reconecta la superficie activa
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando videos de la USB...", color = Color.Gray, fontSize = 12.sp)
            }
        }

        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = theme.accentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentVideo?.title ?: "Reproductor de Video",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            videoPlayer.resetControlsTimer()
                            showFolderModal = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Elegir Carpeta USB",
                            tint = theme.accentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                val totalMs = videoPlayer.totalDurationMs.coerceAtLeast(1L)
                val displayPositionMs = if (isDraggingSlider) {
                    (sliderPosition * totalMs).toLong()
                } else {
                    videoPlayer.currentPositionMs.coerceIn(0L, totalMs)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMs(displayPositionMs),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatMs(totalMs),
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val currentProgress = if (totalMs <= 1L) {
                    0f
                } else if (isDraggingSlider) {
                    sliderPosition
                } else {
                    (displayPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                }

                Slider(
                    value = currentProgress,
                    onValueChange = { newValue ->
                        isDraggingSlider = true
                        sliderPosition = newValue
                        videoPlayer.resetControlsTimer()
                    },
                    onValueChangeFinished = {
                        val targetMs = (sliderPosition * totalMs).toLong()
                        videoPlayer.seekTo(targetMs)
                        isDraggingSlider = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = theme.accentCyan,
                        activeTrackColor = theme.accentCyan,
                        inactiveTrackColor = theme.cardBorder.copy(alpha = 0.5f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { videoPlayer.toggleShuffle() },
                        modifier = Modifier.size((34 * buttonScale).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Aleatorio",
                            tint = if (videoPlayer.isShuffleMode) theme.accentCyan else Color.DarkGray,
                            modifier = Modifier.size((18 * buttonScale).dp)
                        )
                    }

                    IconButton(
                        onClick = { videoPlayer.playPreviousVideo() },
                        modifier = Modifier
                            .size((38 * buttonScale).dp)
                            .background(Color(0xFF22222E), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Anterior",
                            tint = theme.accentCyan,
                            modifier = Modifier.size((22 * buttonScale).dp)
                        )
                    }

                    IconButton(
                        onClick = { videoPlayer.togglePlayPause() },
                        modifier = Modifier
                            .size((46 * buttonScale).dp)
                            .background(theme.accentCyan, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (videoPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pausa",
                            tint = Color.Black,
                            modifier = Modifier.size((26 * buttonScale).dp)
                        )
                    }

                    IconButton(
                        onClick = { videoPlayer.playNextVideo() },
                        modifier = Modifier
                            .size((38 * buttonScale).dp)
                            .background(Color(0xFF22222E), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Siguiente",
                            tint = theme.accentCyan,
                            modifier = Modifier.size((22 * buttonScale).dp)
                        )
                    }

                    IconButton(
                        onClick = { onExpandFullscreen() },
                        modifier = Modifier.size((34 * buttonScale).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Expandir",
                            tint = Color.White,
                            modifier = Modifier.size((20 * buttonScale).dp)
                        )
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
// 📺 VISTA DE IPTV (CON RECONEXIÓN AUTOMÁTICA EN TIEMPO REAL)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IptvPlayerView(
    theme: DashboardTheme,
    onExpandFullscreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val iptvPlayer = remember { SmartIptvPlayer.getInstance(context) }

    // Cambiado de modal de carpetas a diálogo de URL remota
    var showUrlModal by remember { mutableStateOf(false) }

    val currentChannel = iptvPlayer.playlist.getOrNull(iptvPlayer.currentChannelIndex)
    val buttonScale = LocalButtonScale.current

    var showUI by remember { mutableStateOf(true) }
    val interactionSource = remember { MutableInteractionSource() }

    // 📡 ESTADO DE INTERNET EN TIEMPO REAL
    var isOnline by remember { mutableStateOf(iptvPlayer.isConnectedToInternet()) }

    // 🔄 MONITOR DE CONEXIÓN EN TIEMPO REAL
    LaunchedEffect(Unit) {
        while (isActive) {
            val connected = iptvPlayer.isConnectedToInternet()
            if (connected != isOnline) {
                isOnline = connected
                if (connected && iptvPlayer.playlist.isNotEmpty() && !iptvPlayer.isPlaying) {
                    val targetIndex = if (iptvPlayer.currentChannelIndex in iptvPlayer.playlist.indices) {
                        iptvPlayer.currentChannelIndex
                    } else 0
                    iptvPlayer.playChannelAtIndex(targetIndex)
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

    LaunchedEffect(iptvPlayer.playlist.isNotEmpty(), iptvPlayer.isFullscreenActive, isOnline) {
        if (isOnline && !iptvPlayer.isFullscreenActive && iptvPlayer.playlist.isNotEmpty()) {
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
                    .background(Color(0xFF0F0F14))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = theme.accentCyan,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Esperando Conexión Wi-Fi / Datos...",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Conectará automáticamente en cuanto la tablet obtenga internet",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
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

                                try {
                                    val videoWidth = mp.videoWidth.toFloat()
                                    val videoHeight = mp.videoHeight.toFloat()
                                    if (videoWidth > 0 && videoHeight > 0) {
                                        val surfaceViewField = VideoView::class.java.getDeclaredField("mSurfaceView")
                                        surfaceViewField.isAccessible = true
                                        val surfaceView = surfaceViewField.get(this) as? android.view.SurfaceView
                                        surfaceView?.let { sv ->
                                            val lp = sv.layoutParams
                                            lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                            lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                            sv.layoutParams = lp
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

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
            // Estado vacío cuando no hay lista cargada
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = theme.accentCyan,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Carga una lista remota (.m3u) ingresando la URL", color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showUrlModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentCyan),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Ingresar Enlace", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
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
                            tint = theme.accentCyan
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
                                color = theme.accentCyan,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Botón de enlace remoto (reemplaza el de carpeta local)
                    IconButton(
                        onClick = {
                            showUrlModal = true
                            showUI = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "Configurar URL", tint = theme.accentCyan, modifier = Modifier.size(22.dp))
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
                        Text("CH -", color = Color.White, fontSize = (11 * buttonScale).sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = {
                            iptvPlayer.togglePlayPause()
                            showUI = true
                        },
                        modifier = Modifier
                            .size((42 * buttonScale).dp)
                            .background(theme.accentCyan, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (iptvPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pausa",
                            tint = Color.Black,
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

                    // Botón de Favorito ⭐ (para conservar el orden/acceso rápido)
                    if (currentChannel != null) {
                        val isFav = iptvPlayer.isFavorite(currentChannel)
                        IconButton(
                            onClick = {
                                iptvPlayer.toggleFavorite(currentChannel)
                            },
                            modifier = Modifier.size((34 * buttonScale).dp)
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favoritos",
                                tint = if (isFav) Color(0xFFFFD700) else theme.accentCyan,
                                modifier = Modifier.size((22 * buttonScale).dp)
                            )
                        }
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
                            tint = theme.accentCyan,
                            modifier = Modifier.size((22 * buttonScale).dp)
                        )
                    }
                }
            }
        }

        // Diálogo / Cajuela de Entrada de la URL Remota
        if (showUrlModal) {
            RemoteUrlInputDialog(
                theme = theme,
                currentUrl = context.getSharedPreferences("smart_iptv_prefs", Context.MODE_PRIVATE)
                    .getString("selected_playlist_url", "") ?: "",
                onDismiss = {
                    showUrlModal = false
                    showUI = true
                },
                onConfirmUrl = { url ->
                    if (url.isNotBlank()) {
                        iptvPlayer.parseAndLoadM3uUrl(url.trim())
                    }
                    showUrlModal = false
                    showUI = true
                }
            )
        }
    }
}

// Composable del cuadro de diálogo personalizado para pegar la URL
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Configurar Lista IPTV Remota",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // =========================================================================
                // 💡 SECCIÓN DE URL SUGERIDA (Interactiva)
                // =========================================================================
                val suggestedUrl = "https://iptv-org.github.io/iptv/languages/spa.m3u"

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E28))
                        .clickable {
                            // Al tocarla se escribe sola y se copia al portapapeles
                            urlText = suggestedUrl
                            clipboardManager.setText(AnnotatedString(suggestedUrl))
                        }
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "💡 Enlace sugerido (Toca para copiar y usar):",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = suggestedUrl,
                        color = theme.accentCyan,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        textDecoration = TextDecoration.Underline,
                        maxLines = 2
                    )
                }
                // =========================================================================

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Pegar enlace M3U", color = Color.Gray, fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = theme.accentCyan,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = theme.accentCyan,
                        unfocusedLabelColor = Color.Gray
                    ),
                    textStyle = TextStyle(fontSize = 12.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color.Gray, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirmUrl(urlText) },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accentCyan),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Guardar", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

//===========================================
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