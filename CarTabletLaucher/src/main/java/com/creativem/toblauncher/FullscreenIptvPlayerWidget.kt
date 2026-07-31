package com.creativem.toblauncher

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val iptvLogoCache = LruCache<String, Bitmap>(150)

@Composable
fun FullscreenIptvPlayerWidget(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current
    val iptvPlayer = remember { SmartIptvPlayer.getInstance(context) }

    var showFolderModal by remember { mutableStateOf(false) }
    var showUIState by remember { mutableStateOf(true) }

    val currentChannel = iptvPlayer.playlist.getOrNull(iptvPlayer.currentChannelIndex)
    val buttonScale = LocalButtonScale.current

    // ✅ NOTIFICA QUE LA PANTALLA COMPLETA ESTÁ ACTIVA Y DESACTIVA EL WIDGET DE FONDO
    DisposableEffect(Unit) {
        iptvPlayer.isFullscreenActive = true
        onDispose {
            iptvPlayer.isFullscreenActive = false
        }
    }

    val sidebarWidth = 320.dp
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
                            Text("CH -", color = theme.accentPurple, fontSize = (12 * buttonScale).sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = {
                                iptvPlayer.togglePlayPause()
                                showUIState = true
                            },
                            modifier = Modifier
                                .size((52 * buttonScale).dp)
                                .background(theme.accentPurple, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (iptvPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pausa",
                                tint = Color.White,
                                modifier = Modifier.size((30 * buttonScale).dp)
                            )
                        }

                        Button(
                            onClick = {
                                iptvPlayer.playNextChannel()
                                showUIState = true
                            },
                            modifier = Modifier.height((40 * buttonScale).dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accentPurple),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("CH +", color = Color.White, fontSize = (12 * buttonScale).sp, fontWeight = FontWeight.Bold)
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

                        Text("CANALES IPTV", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        IconButton(onClick = { showFolderModal = true }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "USB", tint = theme.accentOrange)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(iptvPlayer.playlist) { index, channel ->
                            val isSelected = index == iptvPlayer.currentChannelIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) theme.accentPurple.copy(alpha = 0.35f) else Color.Transparent)
                                    .clickable {
                                        iptvPlayer.playChannelAtIndex(index)
                                        showUIState = true
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ChannelLogoImage(
                                    logoUrl = channel.logoUrl,
                                    modifier = Modifier.size(36.dp),
                                    tint = if (isSelected) theme.accentPurple else Color.Gray
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = channel.name,
                                        color = if (isSelected) Color.White else Color.LightGray,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (!channel.groupTitle.isNull_orEmpty()) {
                                        channel.groupTitle?.let {
                                            Text(
                                                text = it,
                                                color = Color.Gray,
                                                fontSize = 9.sp
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

        if (showFolderModal) {
            FolderPickerModal(
                onDismiss = { showFolderModal = false },
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

private fun String?.isNull_orEmpty(): Boolean = this == null || this.trim().isEmpty()