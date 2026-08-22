package com.creativem.toblauncher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomApps3DGridWidget(
    onRequestAppSelection: (slot: Int) -> Unit
) {
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current

    val prefs = remember {
        context.getSharedPreferences("custom_grid_apps_prefs", Context.MODE_PRIVATE)
    }

    var updateTrigger by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key.startsWith("grid_slot_")) {
                updateTrigger = System.currentTimeMillis()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val slots = remember { (101..109).toList() }
    val cellSpacing = 3.dp // Espaciado mínimo ultra-limpio

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp), // Se aprovecha hasta la última esquina
        verticalArrangement = Arrangement.spacedBy(cellSpacing)
    ) {
        slots.chunked(3).forEach { rowSlots ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // Llena toda la altura disponible
                horizontalArrangement = Arrangement.spacedBy(cellSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowSlots.forEach { slotId ->
                    val pkgName = remember(slotId, updateTrigger) {
                        prefs.getString("grid_slot_$slotId", null)
                    }

                    val appInfo = remember(pkgName, updateTrigger) {
                        pkgName?.let { getAppInfo(context, it) }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f) // Llena todo el ancho disponible
                            .fillMaxHeight()
                            .shadow(
                                elevation = 3.dp,
                                shape = RoundedCornerShape(10.dp),
                                spotColor = theme.primaryColor.copy(alpha = 0.35f)
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        theme.cardBackground.copy(alpha = 0.85f),
                                        theme.dashBackground.copy(alpha = 0.95f)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        theme.primaryColor.copy(alpha = 0.55f),
                                        Color.Transparent
                                    )
                                ),
                                RoundedCornerShape(10.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    if (appInfo != null) {
                                        AppLauncher.launchKeepingVideo(context, appInfo.packageName)
                                    } else {
                                        onRequestAppSelection(slotId)
                                    }
                                },
                                onLongClick = {
                                    onRequestAppSelection(slotId)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (appInfo != null) {
                            // 🚀 ICONO DE BORDE A BORDE (SIN MÁRGENES NEGROS)
                            Image(
                                bitmap = appInfo.icon.asImageBitmap(),
                                contentDescription = appInfo.name,
                                contentScale = ContentScale.FillBounds, // Llena el 100% de la celda
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Agregar App",
                                tint = theme.primaryColor.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxSize(0.45f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SimpleAppInfo(val name: String, val packageName: String, val icon: Bitmap)

private fun getAppInfo(context: Context, packageName: String): SimpleAppInfo? {
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        val name = pm.getApplicationLabel(info).toString()
        val drawable = pm.getApplicationIcon(info)

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        SimpleAppInfo(name, packageName, bitmap)
    } catch (e: Exception) {
        null
    }
}