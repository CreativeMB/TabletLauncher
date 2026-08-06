package com.creativem.toblauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.widget.VideoView
import androidx.collection.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.activity.compose.BackHandler

val iptvLogoCache = LruCache<String, Bitmap>(150)

// =========================================================================
// 💾 PERSISTENCIA COMPLETA DE FAVORITOS Y ELIMINADOS
// =========================================================================
fun saveIptvFavoriteChannels(context: Context, favoriteChannels: List<IptvChannel>) {
    val prefs = context.getSharedPreferences("iptv_player_prefs", Context.MODE_PRIVATE)
    val serialized = favoriteChannels.joinToString("###CHANNEL_DELIMITER###") { channel ->
        val name = channel.name.replace("~", "_")
        val url = channel.streamUrl.replace("~", "_")
        val logo = (channel.logoUrl ?: "").replace("~", "_")
        val group = (channel.groupTitle ?: "").replace("~", "_")
        "$name~$url~$logo~$group"
    }
    prefs.edit().putString("favorite_channels_v3_string", serialized).apply()
}

fun getSavedIptvFavoriteChannels(context: Context): List<IptvChannel> {
    val prefs = context.getSharedPreferences("iptv_player_prefs", Context.MODE_PRIVATE)
    val savedStr = prefs.getString("favorite_channels_v3_string", null) ?: return emptyList()
    if (savedStr.isEmpty()) return emptyList()

    return savedStr.split("###CHANNEL_DELIMITER###").mapNotNull { itemStr ->
        val parts = itemStr.split("~")
        if (parts.size >= 2 && parts[1].isNotEmpty()) {
            val name = parts[0]
            val url = parts[1]
            val logo = if (parts.size > 2 && parts[2].isNotEmpty()) parts[2] else null
            val group = if (parts.size > 3 && parts[3].isNotEmpty()) parts[3] else null
            IptvChannel(name = name, streamUrl = url, logoUrl = logo, groupTitle = group)
        } else null
    }
}

fun getSavedIptvDeleted(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("iptv_player_prefs", Context.MODE_PRIVATE)
    return prefs.getStringSet("deleted_channels_urls_v2", emptySet()) ?: emptySet()
}

fun saveIptvDeleted(context: Context, deleted: Set<String>) {
    val prefs = context.getSharedPreferences("iptv_player_prefs", Context.MODE_PRIVATE)
    prefs.edit().putStringSet("deleted_channels_urls_v2", deleted).apply()
}

// =========================================================================
// 🔄 FUSIÓN INTELIGENTE DE M3U Y FAVORITOS AL CARGAR USB
// =========================================================================
fun loadM3uAndPreserveFavorites(context: Context, iptvPlayer: SmartIptvPlayer, m3uFile: File) {
    // 1. Obtenemos los favoritos (estos SÍ queremos que se mantengan)
    val savedFavorites = getSavedIptvFavoriteChannels(context)

    // 2. ¡IMPORTANTE! Al cargar una lista nueva, limpiamos la lista de eliminados.
    // Esto hace que los canales que borraste de la lista anterior vuelvan a aparecer
    // si vienen en esta nueva lista.
    saveIptvDeleted(context, emptySet())
    val deletedUrls = emptySet<String>() // Usamos un set vacío para la carga actual

    // 3. Cargamos el nuevo archivo M3U
    iptvPlayer.parseAndLoadM3uFile(m3uFile)

    // 4. Obtenemos los canales del nuevo archivo
    val m3uChannels = iptvPlayer.playlist.toList()

    // 5. Combinamos: Canales del archivo + Tus Favoritos
    // Usamos distinctBy para que si un favorito ya está en la lista, no se duplique
    val combined = (m3uChannels + savedFavorites).distinctBy { it.streamUrl }

    // 6. Actualizamos el reproductor
    try {
        val list = iptvPlayer.playlist
        if (list is MutableList<*>) {
            @Suppress("UNCHECKED_CAST")
            val mutableList = list as MutableList<IptvChannel>
            mutableList.clear()
            mutableList.addAll(combined)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// =========================================================================
// 📺 REPRODUCTOR IPTV EN PANTALLA COMPLETA
// =========================================================================
@Composable
fun FullscreenIptvPlayerWidget(
    onClose: () -> Unit
) {
    BackHandler {
        onClose()
    }
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current
    val buttonScale = LocalButtonScale.current ?: 1.0f
    val textScale = LocalDensity.current.fontScale

    val iptvPlayer = remember { SmartIptvPlayer.getInstance(context) }

    var showFolderModal by remember { mutableStateOf(false) }
    var showUIState by remember { mutableStateOf(true) }

    // 🔍 BUSCADOR DE CANALES
    var searchQuery by remember { mutableStateOf("") }

    // Carga de Favoritos y Eliminados
    var favoriteChannels by remember { mutableStateOf(getSavedIptvFavoriteChannels(context)) }
    var favoriteUrls by remember(favoriteChannels) { mutableStateOf(favoriteChannels.map { it.streamUrl }.toSet()) }
    var deletedUrls by remember { mutableStateOf(getSavedIptvDeleted(context)) }

    // Diálogos de Confirmación
    var channelToDelete by remember { mutableStateOf<IptvChannel?>(null) }
    var channelToToggleFav by remember { mutableStateOf<IptvChannel?>(null) }

    val currentChannel = iptvPlayer.playlist.getOrNull(iptvPlayer.currentChannelIndex)

    DisposableEffect(Unit) {
        iptvPlayer.isFullscreenActive = true
        onDispose {
            iptvPlayer.isFullscreenActive = false
        }
    }

    // ✅ LIMPIA CANALES ELIMINADOS DE LA MEMORIA
    LaunchedEffect(favoriteChannels, deletedUrls, iptvPlayer.playlist.size) {
        val combined = (favoriteChannels + iptvPlayer.playlist)
            .distinctBy { it.streamUrl }
            .filter { !deletedUrls.contains(it.streamUrl) }

        try {
            val list = iptvPlayer.playlist
            if (list is MutableList<*>) {
                @Suppress("UNCHECKED_CAST")
                val mutableList = list as MutableList<IptvChannel>
                if (mutableList.size != combined.size || mutableList.any { deletedUrls.contains(it.streamUrl) }) {
                    mutableList.clear()
                    mutableList.addAll(combined)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 📋 LISTA DE CANALES FILTRADA POR BÚSQUEDA Y FAVORITOS PRIMERO
    val displayedChannels = remember(favoriteChannels, iptvPlayer.playlist.size, deletedUrls, searchQuery) {
        val allChannels = (favoriteChannels + iptvPlayer.playlist).distinctBy { it.streamUrl }
        val nonDeleted = allChannels.filter { !deletedUrls.contains(it.streamUrl) }

        val filtered = if (searchQuery.isBlank()) {
            nonDeleted
        } else {
            nonDeleted.filter { channel ->
                channel.name.contains(searchQuery, ignoreCase = true) ||
                        (channel.groupTitle?.contains(searchQuery, ignoreCase = true) == true)
            }
        }

        filtered.sortedByDescending { favoriteUrls.contains(it.streamUrl) }
    }

    val sidebarWidth = 340.dp
    val endPadding = if (showUIState) sidebarWidth else 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = endPadding),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (currentChannel != null) {
                AndroidView(
                    factory = { ctx ->
                        object : VideoView(ctx) {
                            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                                val width = MeasureSpec.getSize(widthMeasureSpec)
                                val height = MeasureSpec.getSize(heightMeasureSpec)
                                setMeasuredDimension(width, height)
                            }
                        }.apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setVideoPath(currentChannel.streamUrl)
                            tag = currentChannel.streamUrl

                            setOnTouchListener { _, event ->
                                if (event.action == android.view.MotionEvent.ACTION_UP) {
                                    showUIState = !showUIState
                                }
                                true
                            }

                            setOnPreparedListener { mp ->
                                iptvPlayer.bindMediaPlayer(mp)
                                mp.start()
                                iptvPlayer.isPlaying = true
                            }
                        }
                    },
                    update = { view ->
                        val currentUrl = view.tag as? String
                        val newUrl = currentChannel.streamUrl
                        if (currentUrl != newUrl) {
                            view.tag = newUrl
                            view.stopPlayback()
                            view.setVideoPath(newUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showUIState = !showUIState },
                    contentAlignment = Alignment.Center
                ) {
                    Text("No hay listas IPTV cargadas", color = Color.Gray, fontSize = 14.sp)
                }
            }

            // CONTROLES INFERIORES
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                iptvPlayer.playPreviousChannel()
                                showUIState = true
                            },
                            modifier = Modifier.height((40 * buttonScale).dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22222E)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("CH -", color = theme.accentCyan, fontSize = (12 * buttonScale).sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = {
                                iptvPlayer.togglePlayPause()
                                showUIState = true
                            },
                            modifier = Modifier
                                .size((52 * buttonScale).dp)
                                .background(theme.accentCyan, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (iptvPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pausa",
                                tint = Color.Black,
                                modifier = Modifier.size((30 * buttonScale).dp)
                            )
                        }

                        Button(
                            onClick = {
                                iptvPlayer.playNextChannel()
                                showUIState = true
                            },
                            modifier = Modifier.height((40 * buttonScale).dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accentCyan),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("CH +", color = Color.Black, fontSize = (12 * buttonScale).sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = {
                                iptvPlayer.pausePlayback()
                                onClose()
                            },
                            modifier = Modifier.size((40 * buttonScale).dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Salir",
                                tint = Color.White,
                                modifier = Modifier.size((24 * buttonScale).dp)
                            )
                        }
                    }
                }
            }
        }

        // BARRA LATERAL DE CANALES CON BUSCADOR EN LA CABECERA
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
                    .background(Color(0xFF101018).copy(alpha = 0.95f))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // CABECERA SUPERIOR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            iptvPlayer.pausePlayback()
                            onClose()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Cerrar", tint = Color.White)
                        }

                        Text("CANALES IPTV (${displayedChannels.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        IconButton(onClick = { showFolderModal = true }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "USB", tint = theme.accentCyan)
                        }
                    }

                    // 🔍 BUSCADOR DE CANALES IPTV EN LA CABECERA
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "🔍 Buscar...",
                                fontSize = (8 * textScale).sp,
                                color = Color.Gray
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = (52 * buttonScale).dp)
                            .padding(bottom = 8.dp),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = (8 * textScale).sp,
                            fontWeight = FontWeight.Medium
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.accentCyan,
                            unfocusedBorderColor = Color(0xFF282836),
                            focusedContainerColor = Color(0xFF1E1E2A),
                            unfocusedContainerColor = Color(0xFF181822)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {



                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size((10 * buttonScale).dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpiar",
                                        tint = Color.Gray
                                    )
                                }
                            }
                        }
                    )

                    // LISTA DE CANALES
                    if (displayedChannels.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Sin resultados para \"$searchQuery\"" else "No hay canales",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(displayedChannels) { _, channel ->
                                val isSelected = currentChannel?.streamUrl == channel.streamUrl
                                val isFav = favoriteUrls.contains(channel.streamUrl)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) theme.accentCyan.copy(alpha = 0.25f) else Color(0xFF1A1A24))
                                        .clickable {
                                            val realIndex = iptvPlayer.playlist.indexOfFirst { it.streamUrl == channel.streamUrl }
                                            if (realIndex != -1) {
                                                iptvPlayer.playChannelAtIndex(realIndex)
                                            }
                                            showUIState = true
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. EL LOGO
                                    ChannelLogoImage(
                                        logoUrl = channel.logoUrl,
                                        modifier = Modifier.size(32.dp),
                                        tint = if (isSelected) theme.accentCyan else Color.Gray
                                    )

                                    // 2. EL CORAZÓN (AL PRINCIPIO, JUNTO AL LOGO)
                                    IconButton(
                                        onClick = { channelToToggleFav = channel },
                                        modifier = Modifier.size(30.dp) // Un poco más de espacio para tocar fácil
                                    ) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Favorito",
                                            tint = if (isFav) Color.Red else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp)) // Espacio pequeño entre corazón y nombre

                                    // 3. EL NOMBRE Y GRUPO (ESTO OCUPA TODO EL ESPACIO CENTRAL)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = channel.name,
                                            color = if (isSelected) theme.accentCyan else Color.White,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                        // AQUÍ ESTÁ LA CORRECCIÓN: isNull_or_empty (con e minúscula)
                                        if (!channel.groupTitle.isNull_or_empty()) {
                                            channel.groupTitle?.let {
                                                Text(
                                                    text = it,
                                                    color = Color.Gray,
                                                    fontSize = 9.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    // 4. LA PAPELERA (AL FINAL DE TODO)
                                    IconButton(
                                        onClick = { channelToDelete = channel },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar",
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 🚨 MODAL: ELIMINAR CANAL DEFINITIVO
        // =========================================================================
        channelToDelete?.let { channel ->
            AlertDialog(
                onDismissRequest = { channelToDelete = null },
                containerColor = Color(0xFF1E1E24),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text("Eliminar Canal", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "¿Estás seguro de que deseas eliminar '${channel.name}'?\n\nEl canal se borrará de la lista y no se volverá a reproducir.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val targetUrl = channel.streamUrl

                            val newDeleted = deletedUrls + targetUrl
                            deletedUrls = newDeleted
                            saveIptvDeleted(context, newDeleted)

                            val updatedFavs = favoriteChannels.filter { it.streamUrl != targetUrl }
                            if (updatedFavs.size != favoriteChannels.size) {
                                favoriteChannels = updatedFavs
                                saveIptvFavoriteChannels(context, updatedFavs)
                            }

                            try {
                                val list = iptvPlayer.playlist
                                if (list is MutableList<*>) {
                                    @Suppress("UNCHECKED_CAST")
                                    (list as MutableList<IptvChannel>).removeAll { it.streamUrl == targetUrl }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            if (currentChannel?.streamUrl == targetUrl) {
                                iptvPlayer.playNextChannel()
                            }

                            channelToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Text("Sí, Eliminar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { channelToDelete = null }) {
                        Text("Cancelar", color = Color.White, fontSize = 12.sp)
                    }
                }
            )
        }

        // =========================================================================
        // ❤️ MODAL: FAVORITOS
        // =========================================================================
        channelToToggleFav?.let { channel ->
            val isCurrentlyFav = favoriteUrls.contains(channel.streamUrl)

            AlertDialog(
                onDismissRequest = { channelToToggleFav = null },
                containerColor = Color(0xFF1E1E24),
                icon = {
                    Icon(
                        imageVector = if (isCurrentlyFav) Icons.Default.FavoriteBorder else Icons.Default.Favorite,
                        contentDescription = null,
                        tint = if (isCurrentlyFav) Color.Gray else Color.Red,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = if (isCurrentlyFav) "Quitar de Favoritos" else "Agregar a Favoritos",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = if (isCurrentlyFav) {
                            "¿Deseas quitar '${channel.name}' de tu lista de favoritos?"
                        } else {
                            "¿Deseas agregar '${channel.name}' a tus favoritos?\n\nPermanecerá guardado incluso cuando cargues una lista nueva desde tu USB."
                        },
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val newFavChannels = if (isCurrentlyFav) {
                                favoriteChannels.filter { it.streamUrl != channel.streamUrl }
                            } else {
                                (favoriteChannels + channel).distinctBy { it.streamUrl }
                            }

                            favoriteChannels = newFavChannels
                            saveIptvFavoriteChannels(context, newFavChannels)

                            channelToToggleFav = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accentCyan)
                    ) {
                        Text(
                            text = if (isCurrentlyFav) "Quitar" else "Agregar",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { channelToToggleFav = null }) {
                        Text("Cancelar", color = Color.White, fontSize = 12.sp)
                    }
                }
            )
        }

        // MODAL EXPLORADOR DE CARPETAS USB
        if (showFolderModal) {
            FolderPickerModal(
                onDismiss = { showFolderModal = false },
                onFolderSelected = { selectedFolder ->
                    val m3uFile = selectedFolder.listFiles()?.firstOrNull {
                        it.extension.lowercase() in listOf("m3u", "m3u8")
                    }
                    if (m3uFile != null) {
                        loadM3uAndPreserveFavorites(context, iptvPlayer, m3uFile)
                        favoriteChannels = getSavedIptvFavoriteChannels(context)
                    }
                    showFolderModal = false
                }
            )
        }
    }
}

// =========================================================================
// 🖼️ LOGOS DE CANALES
// =========================================================================
@Composable
fun ChannelLogoImage(
    logoUrl: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Gray
) {
    var logoBitmap by remember(logoUrl) { mutableStateOf(logoUrl?.let { iptvLogoCache.get(it) }) }

    LaunchedEffect(logoUrl) {
        if (!logoUrl.isNullOrEmpty() && logoBitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    val url = URL(logoUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000
                    connection.doInput = true
                    connection.connect()
                    val input = connection.inputStream
                    val decoded = BitmapFactory.decodeStream(input)
                    if (decoded != null) {
                        iptvLogoCache.put(logoUrl, decoded)
                        logoBitmap = decoded
                    }
                } catch (e: Exception) {
                    logoBitmap = null
                }
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (logoBitmap != null) {
            Image(
                bitmap = logoBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Icon(
                imageVector = Icons.Default.LiveTv,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.fillMaxSize(0.7f)
            )
        }
    }
}

fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()