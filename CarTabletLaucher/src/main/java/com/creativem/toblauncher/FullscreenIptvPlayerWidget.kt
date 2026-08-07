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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalClipboardManager  // IMPORTADO
import androidx.compose.ui.text.AnnotatedString            // IMPORTADO
import androidx.compose.ui.text.style.TextDecoration       // IMPORTADO
import androidx.compose.ui.text.style.TextAlign            // IMPORTADO

val iptvLogoCache = LruCache<String, Bitmap>(150)

// =========================================================================
// 📺 REPRODUCTOR IPTV EN PANTALLA COMPLETA (VERSIÓN LISTA REMOTA)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
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

    // Control del diálogo de la URL remota ("cajuela")
    var showUrlModal by remember { mutableStateOf(false) }
    var showUIState by remember { mutableStateOf(true) }

    // 🔍 BUSCADOR DE CANALES
    var searchQuery by remember { mutableStateOf("") }

    val currentChannel = iptvPlayer.playlist.getOrNull(iptvPlayer.currentChannelIndex)

    DisposableEffect(Unit) {
        iptvPlayer.isFullscreenActive = true
        onDispose {
            iptvPlayer.isFullscreenActive = false
        }
    }

    // 📋 LISTA DE CANALES FILTRADA POR BÚSQUEDA Y FAVORITOS EN LA PARTE SUPERIOR
    val displayedChannels = remember(iptvPlayer.playlist.size, iptvPlayer.favoriteUrls.size, searchQuery) {
        val filtered = if (searchQuery.isBlank()) {
            iptvPlayer.playlist
        } else {
            iptvPlayer.playlist.filter { channel ->
                channel.name.contains(searchQuery, ignoreCase = true) ||
                        (channel.groupTitle?.contains(searchQuery, ignoreCase = true) == true)
            }
        }

        // Ordenamos poniendo los favoritos al principio para mantener un acceso rápido
        filtered.sortedByDescending { iptvPlayer.isFavorite(it) }
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

                        // Icono de enlace para cargar nueva URL remota ("cajuela")
                        IconButton(onClick = { showUrlModal = true }) {
                            Icon(Icons.Default.Link, contentDescription = "Configurar URL", tint = theme.accentCyan)
                        }
                    }

                    // 🔍 BUSCADOR DE CANALES IPTV
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
                                val isFav = iptvPlayer.isFavorite(channel)

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

                                    // 2. EL CORAZÓN DE FAVORITOS (Agrega o quita directamente de la lista para mantener el orden)
                                    IconButton(
                                        onClick = { iptvPlayer.toggleFavorite(channel) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Favorito",
                                            tint = if (isFav) Color.Red else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // 3. EL NOMBRE Y GRUPO DEL CANAL
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = channel.name,
                                            color = if (isSelected) theme.accentCyan else Color.White,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!channel.groupTitle.isNullOrEmpty()) {
                                            Text(
                                                text = channel.groupTitle ?: "",
                                                color = Color.Gray,
                                                fontSize = 9.sp,
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
            }
        }

        // =========================================================================
        // 🚨 DIÁLOGO / CAJUELA DE ENTRADA DE LA URL REMOTA
        // =========================================================================
        if (showUrlModal) {
            FullscreenRemoteUrlInputDialog(
                theme = theme,
                currentUrl = context.getSharedPreferences("smart_iptv_prefs", Context.MODE_PRIVATE)
                    .getString("selected_playlist_url", "") ?: "",
                onDismiss = {
                    showUrlModal = false
                    showUIState = true
                },
                onConfirmUrl = { url ->
                    if (url.isNotBlank()) {
                        iptvPlayer.parseAndLoadM3uUrl(url.trim())
                    }
                    showUrlModal = false
                    showUIState = true
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

// =========================================================================
// ⌨️ DIÁLOGO PARA INGRESAR LA URL EN PANTALLA COMPLETA (CON URL SUGERIDA)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenRemoteUrlInputDialog(
    theme: DashboardTheme,
    currentUrl: String,
    onDismiss: () -> Unit,
    onConfirmUrl: (String) -> Unit
) {
    var urlText by remember { mutableStateOf(currentUrl) }
    val clipboardManager = LocalClipboardManager.current // Acceso al Portapapeles

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
                // 💡 SECCIÓN DE URL SUGERIDA INTERACTIVA
                // =========================================================================
                val suggestedUrl = "https://iptv-org.github.io/iptv/languages/spa.m3u"

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E28))
                        .clickable {
                            // Al tocar la sugerencia se copia al portapapeles y se escribe sola
                            urlText = suggestedUrl
                            clipboardManager.setText(AnnotatedString(suggestedUrl))
                        }
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "💡 Lista sugerida gratis (Toca para cargar y copiar):",
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