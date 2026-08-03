package com.creativem.toblauncher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AndroidInstalledApp(
    val appName: String,
    val packageName: String,
    val iconBitmap: Bitmap
)

// =======================================================================
// FUNCIÓN DE GUARDADO DINÁMICO PARA CUALQUIER NÚMERO DE SLOT (101 A 120+)
// =======================================================================
fun saveSelectedAppForSlot(context: Context, slotId: Int, packageName: String) {
    if (slotId in 1..2) {
        // Guardado para los accesos del Velocímetro
        val prefs = context.getSharedPreferences("speedometer_apps_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("slot_$slotId", packageName).apply()
    } else if (slotId >= 101) {
        // Guardado dinámico para la cuadrícula
        val prefs = context.getSharedPreferences("custom_grid_apps_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("grid_slot_$slotId", packageName).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenAppDrawerWidget(
    title: String = "APLICACIONES DE LA TABLET",
    selectedSlotId: Int? = null,
    onClose: () -> Unit,
    onAppSelected: ((packageName: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current
    val isBold = LocalIsBoldText.current

    // 📏 ESCALAS SEPARADAS: ÍCONOS Y TEXTOS INDEPENDIENTES
    val buttonScale = LocalButtonScale.current ?: 1.0f
    val textScale = remember { ThemeManager.getSavedTextScale(context) }

    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<AndroidInstalledApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // LECTOR DE APLICACIONES INSTALADAS EN LA TABLET
    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(launcherIntent, 0).mapNotNull { resolveInfo ->
                try {
                    val name = resolveInfo.loadLabel(pm).toString()
                    val pkg = resolveInfo.activityInfo.packageName
                    val drawable = resolveInfo.loadIcon(pm)
                    val bitmap = drawableToBitmap(drawable)
                    AndroidInstalledApp(name, pkg, bitmap)
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.appName.lowercase() }
        }
        isLoading = false
    }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter { it.appName.contains(searchQuery, ignoreCase = true) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.dashBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // BARRA SUPERIOR MODERNA DE NAVEGACIÓN Y BÚSQUEDA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.cardBackground)
                            .size((28 * buttonScale).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = theme.accentCyan,
                            modifier = Modifier.size((18 * buttonScale).dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = title,
                        fontSize = (14 * textScale * 0.8f).sp,
                        fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }

                // BARRA DE BÚSQUEDA TÁCTIL
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Buscar aplicación...",
                            color = Color.Gray,
                            fontSize = (8 * textScale * 0.8f).sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = theme.accentCyan,
                            modifier = Modifier.size((20 * buttonScale).dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accentCyan,
                        unfocusedBorderColor = theme.cardBorder.copy(alpha = 0.5f),
                        focusedContainerColor = theme.cardBackground,
                        unfocusedContainerColor = theme.cardBackground,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .width((280 * buttonScale).dp)
                        .height((50 * buttonScale).dp)
                )
            }

            // CUADRÍCULA PANTALLA COMPLETA
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = theme.accentCyan, strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Cargando aplicaciones de la tablet...",
                            color = Color.Gray,
                            fontSize = (12 * textScale * 0.8f).sp,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = (125 * buttonScale).dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredApps) { app ->
                        ModernAppTileCard(
                            app = app,
                            theme = theme,
                            isBold = isBold,
                            buttonScale = buttonScale,
                            textScale = textScale,
                            onClick = {
                                if (selectedSlotId != null) {
                                    saveSelectedAppForSlot(context, selectedSlotId, app.packageName)
                                }
                                onAppSelected?.invoke(app.packageName)
                                onClose()
                            }
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 🔲 TARJETA DE APLICACIÓN CON ÍCONOS CUADRADOS Y TEXTO INDEPENDIENTE
// =========================================================================
@Composable
private fun ModernAppTileCard(
    app: AndroidInstalledApp,
    theme: DashboardTheme,
    isBold: Boolean,
    buttonScale: Float,
    textScale: Float,
    onClick: () -> Unit
) {
    // 📐 1. EL ÍCONO ES CUADRADO Y GIGANTE (ESCALA CON BOTONES)
    val iconSize = (74 * buttonScale).dp
    val iconCorner = (16 * buttonScale).dp

    // 🔤 2. EL TEXTO ESCALA DE FORMA INDEPENDIENTE (CON LA ESCALA DE FUENTES DEL TEMA)
    val fontSize = (4.5f * textScale).sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.04f)) // Fondo suave translúcido
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ÍCONO CUADRADO Y GRANDE PARA PANTALLAS DE AUTO
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(iconCorner)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = app.iconBitmap.asImageBitmap(),
                contentDescription = app.appName,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // NOMBRE DE LA APLICACIÓN (ESCALADO INDEPENDIENTE POR TEMA)
        Text(
            text = app.appName,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth.coerceAtLeast(1),
        drawable.intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}