package com.creativem.toblauncher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
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

private const val TAG = "TOBLauncher_Debug"

@Composable
fun CustomApps3DGridWidget(
    onRequestAppSelection: (slot: Int) -> Unit
) {
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current
    val prefs = remember { context.getSharedPreferences("custom_grid_apps_prefs", Context.MODE_PRIVATE) }

    // Estado reactivo que forzará el refresco instantáneo al cambiar SharedPreferences
    var prefUpdateTrigger by remember { mutableStateOf(0) }

    // Listener en tiempo real para detectar la selección de apps de inmediato
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            prefUpdateTrigger++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Configuración de la cuadrícula (3 Filas x 3 Columnas = 9 ranuras)
    val totalRows = 3
    val totalCols = 3
    val themeAccents = remember(theme) {
        listOf(theme.accentCyan, theme.accentPurple, theme.accentOrange)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (row in 0 until totalRows) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (col in 0 until totalCols) {
                    val index = row * totalCols + col
                    val slotId = 101 + index // Slots de 101 a 109

                    val packageName = remember(prefUpdateTrigger, slotId) {
                        prefs.getString("grid_slot_$slotId", null)
                            ?: prefs.getString("grid_slot_${index + 1}", null)
                            ?: ""
                    }

                    val accentColor = themeAccents[index % themeAccents.size]

                    LargeApp3DButton(
                        modifier = Modifier.weight(1f),
                        packageName = packageName,
                        themeAccent = accentColor,
                        onClick = { launchOrAssign(context, packageName, slotId, onRequestAppSelection) },
                        onLongClick = {
                            Log.d(TAG, "👆 Clic largo en Slot $slotId. Solicitando selector de app...")
                            onRequestAppSelection(slotId)
                        }
                    )
                }
            }
        }
    }
}

private fun launchOrAssign(
    context: Context,
    packageName: String,
    slotId: Int,
    onRequestAppSelection: (slot: Int) -> Unit
) {
    Log.d(TAG, "👉 Tocaste la casilla Slot $slotId | Package: '$packageName'")

    if (packageName.isNotEmpty()) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            Log.d(TAG, "🚀 Abriendo app '$packageName' mediante AppLauncher (con soporte PiP)...")
            // 🟢 AQUÍ: Ahora sí usa AppLauncher para abrir con ventana flotante
            AppLauncher.launchKeepingVideo(context, packageName)
        } else {
            Log.w(TAG, "⚠️ La app '$packageName' no existe o fue desinstalada. Asignando nueva app...")
            onRequestAppSelection(slotId)
        }
    } else {
        Log.d(TAG, "➕ La casilla Slot $slotId está vacía. Abriendo selector...")
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
    val buttonShape = RoundedCornerShape(14.dp)

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
            .shadow(5.dp, buttonShape, spotColor = Color.Black)
            .clip(buttonShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF222A3A), Color(0xFF0F131C))))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(0.2f), Color.Transparent, Color.Black)),
                buttonShape
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = "Icono App",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(buttonShape)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Asignar App",
                tint = themeAccent.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxSize(0.4f)
            )
        }
    }
}