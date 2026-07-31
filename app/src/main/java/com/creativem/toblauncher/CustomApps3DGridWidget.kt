package com.creativem.toblauncher

import android.content.Context
import android.graphics.Bitmap
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

@Composable
fun CustomApps3DGridWidget(
    onRequestAppSelection: (slot: Int) -> Unit
) {
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current

    // Carga de paquetes guardados para los 4 accesos directos
    val prefs = remember { context.getSharedPreferences("custom_grid_apps_prefs", Context.MODE_PRIVATE) }

    val slot1 = remember(prefs.getString("grid_slot_1", null)) { prefs.getString("grid_slot_1", "") ?: "" }
    val slot2 = remember(prefs.getString("grid_slot_2", null)) { prefs.getString("grid_slot_2", "") ?: "" }
    val slot3 = remember(prefs.getString("grid_slot_3", null)) { prefs.getString("grid_slot_3", "") ?: "" }
    val slot4 = remember(prefs.getString("grid_slot_4", null)) { prefs.getString("grid_slot_4", "") ?: "" }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp) // Espaciado optimizado para pantallas de auto
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LargeApp3DButton(
                modifier = Modifier.weight(1f),
                packageName = slot1,
                themeAccent = theme.accentCyan,
                onClick = { launchOrAssign(context, slot1, 101, onRequestAppSelection) },
                onLongClick = { onRequestAppSelection(101) }
            )
            LargeApp3DButton(
                modifier = Modifier.weight(1f),
                packageName = slot2,
                themeAccent = theme.accentPurple,
                onClick = { launchOrAssign(context, slot2, 102, onRequestAppSelection) },
                onLongClick = { onRequestAppSelection(102) }
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LargeApp3DButton(
                modifier = Modifier.weight(1f),
                packageName = slot3,
                themeAccent = theme.accentOrange,
                onClick = { launchOrAssign(context, slot3, 103, onRequestAppSelection) },
                onLongClick = { onRequestAppSelection(103) }
            )
            LargeApp3DButton(
                modifier = Modifier.weight(1f),
                packageName = slot4,
                themeAccent = theme.accentCyan,
                onClick = { launchOrAssign(context, slot4, 104, onRequestAppSelection) },
                onLongClick = { onRequestAppSelection(104) }
            )
        }
    }
}

private fun launchOrAssign(
    context: Context,
    packageName: String,
    slotId: Int,
    onRequestAppSelection: (slot: Int) -> Unit
) {
    if (packageName.isNotEmpty()) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent)
        } else {
            onRequestAppSelection(slotId)
        }
    } else {
        onRequestAppSelection(slotId)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LargeApp3DButton(
    modifier: Modifier = Modifier,
    packageName: String,
    themeAccent: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val buttonShape = RoundedCornerShape(16.dp)

    // Carga del icono de la app asignada
    val appIcon = remember(packageName) {
        if (packageName.isEmpty()) null else {
            try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bmp = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(6.dp, buttonShape, spotColor = Color.Black)
            .clip(buttonShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF222A3A), Color(0xFF0F131C))))
            .border(
                1.dp, // Borde fino de relieve metálico
                Brush.linearGradient(listOf(Color.White.copy(0.2f), Color.Transparent, Color.Black)),
                buttonShape
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(4.dp), // Padding mínimo para expandir el icono al borde físico
        contentAlignment = Alignment.Center
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = "Icono App",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(buttonShape) // Mantiene la curvatura estética del botón en los extremos del icono
            )
        } else {
            // Icono de suma (+) si la ranura está vacía
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Asignar App",
                tint = themeAccent.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxSize(0.45f)
            )
        }
    }
}