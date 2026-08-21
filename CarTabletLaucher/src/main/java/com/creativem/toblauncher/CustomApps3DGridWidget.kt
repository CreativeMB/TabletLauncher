package com.creativem.toblauncher

import android.content.Context
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
import androidx.compose.ui.unit.min

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomApps3DGridWidget(
    onRequestAppSelection: (slot: Int) -> Unit
) {
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current

    val prefs = remember { context.getSharedPreferences("custom_grid_apps_prefs", Context.MODE_PRIVATE) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // 📱 6 slots en total (2 filas x 3 columnas)
    val slots = (101..106).toList()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        val totalWidth = maxWidth
        val totalHeight = maxHeight

        // 📐 Calculamos el tamaño exacto para que sean 100% idénticos y quepan perfecto
        val horizontalSpacing = 6.dp
        val verticalSpacing = 6.dp

        val itemWidth = (totalWidth - (horizontalSpacing * 2)) / 3
        val itemHeight = (totalHeight - verticalSpacing) / 2
        val squareSize = min(itemWidth, itemHeight) // Tamaño idéntico para todos

        Column(
            modifier = Modifier.wrapContentSize(),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            slots.chunked(3).forEach { rowSlots ->
                Row(
                    modifier = Modifier.wrapContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowSlots.forEach { slotId ->
                        val pkgName = prefs.getString("grid_slot_$slotId", null)
                        val appInfo = remember(pkgName, refreshTrigger) {
                            pkgName?.let { getAppInfo(context, it) }
                        }

                        Box(
                            modifier = Modifier
                                .size(squareSize) // 📏 TAMAÑO FORZADO IDÉNTICO PARA TODOS
                                .shadow(6.dp, RoundedCornerShape(14.dp), spotColor = theme.primaryColor.copy(alpha = 0.25f))
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            theme.cardBackground.copy(alpha = 0.90f),
                                            theme.dashBackground.copy(alpha = 0.95f)
                                        )
                                    )
                                )
                                .border(
                                    1.2.dp,
                                    Brush.verticalGradient(
                                        listOf(
                                            theme.primaryColor.copy(alpha = 0.6f),
                                            Color.Transparent
                                        )
                                    ),
                                    RoundedCornerShape(14.dp)
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
                                Image(
                                    bitmap = appInfo.icon.asImageBitmap(),
                                    contentDescription = appInfo.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Agregar App",
                                    tint = theme.primaryColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(squareSize * 0.4f)
                                )
                            }
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
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        SimpleAppInfo(name, packageName, bitmap)
    } catch (e: Exception) {
        null
    }
}