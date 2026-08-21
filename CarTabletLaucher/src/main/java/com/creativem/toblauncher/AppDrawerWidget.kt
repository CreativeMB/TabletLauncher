package com.creativem.toblauncher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
// FUNCIÓN DE GUARDADO DINÁMICO PARA SLOTS
// =======================================================================
fun saveSelectedAppForSlot(context: Context, slotId: Int, packageName: String) {
    if (slotId in 1..2) {
        val prefs = context.getSharedPreferences("speedometer_apps_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("slot_$slotId", packageName).apply()
    } else if (slotId >= 101) {
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
    BackHandler { onClose() }

    val context = LocalContext.current
    val theme = LocalDashboardTheme.current
    val isBold = LocalIsBoldText.current
    val dashboardFont = LocalDashboardFont.current

    val buttonScale = LocalButtonScale.current
    val textScale = remember { ThemeManager.getSavedTextScale(context) }

    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<AndroidInstalledApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 🌌 FONDO DINÁMICO DEL TEMA DE ADENTRO HACIA AFUERA
    val dynamicDrawerBackground = remember(theme) {
        Brush.radialGradient(
            colors = listOf(
                lerp(theme.cardBackground, theme.primaryColor, 0.30f), // Centro iluminado
                lerp(theme.dashBackground, theme.textColor, 0.12f),    // Halo medio
                lerp(theme.dashBackground, theme.numberColor, 0.06f),  // Destello exterior
                theme.dashBackground                                  // Fondo general
            ),
            radius = 900f
        )
    }

    // LECTOR DE APLICACIONES INSTALADAS
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
            .background(dynamicDrawerBackground)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // =========================================================================
            // BARRA SUPERIOR DE NAVEGACIÓN Y BÚSQUEDA
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 🔵 Botón Volver (Color Primario)
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.cardBackground)
                            .border(1.2.dp, theme.primaryColor.copy(alpha = 0.6f), CircleShape)
                            .size((34 * buttonScale).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = theme.primaryColor,
                            modifier = Modifier.size((18 * buttonScale).dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // 🟣 Título (Color de Texto) + 🟠 Contador Numérico
                    Column {
                        Text(
                            text = title,
                            fontSize = (13 * textScale * 0.85f).sp,
                            fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Bold,
                            fontFamily = dashboardFont.fontFamily,
                            color = theme.textColor,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${filteredApps.size} APPS INSTALADAS",
                            fontSize = (8.5f * textScale * 0.8f).sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.numberColor, // 🟠 Números en color de números
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // 🔍 BARRA DE BÚSQUEDA MODERNA (100% COMPATIBLE Y CENTRADA)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = theme.cardBackground,
                    border = BorderStroke(
                        1.2.dp,
                        if (searchQuery.isNotEmpty()) theme.primaryColor else theme.cardBorder.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .width((250 * buttonScale).dp)
                        .height((40 * buttonScale).dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = theme.primaryColor, // 🔵 Icono Primario
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Buscar aplicación...",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    fontFamily = dashboardFont.fontFamily,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 10.5.sp,
                                    color = Color.White,
                                    fontFamily = dashboardFont.fontFamily,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = Brush.verticalGradient(listOf(theme.primaryColor, theme.primaryColor)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpiar",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // CUADRÍCULA DE APLICACIONES
            // =========================================================================
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = theme.primaryColor, strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Cargando aplicaciones de la tablet...",
                            color = Color.LightGray,
                            fontSize = (11 * textScale * 0.8f).sp,
                            fontFamily = dashboardFont.fontFamily,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = (120 * buttonScale).dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredApps) { app ->
                        ModernAppTileCard(
                            app = app,
                            theme = theme,
                            dashboardFont = dashboardFont,
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
// 🔲 TARJETA DE APLICACIÓN CON FONDO Y TEXTO UNIFICADO
// =========================================================================
@Composable
private fun ModernAppTileCard(
    app: AndroidInstalledApp,
    theme: DashboardTheme,
    dashboardFont: DashboardFont,
    isBold: Boolean,
    buttonScale: Float,
    textScale: Float,
    onClick: () -> Unit
) {
    val iconSize = (70 * buttonScale).dp
    val iconCorner = (16 * buttonScale).dp
    val fontSize = (9.5f * textScale * 0.85f).sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(18.dp), spotColor = theme.primaryColor.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(18.dp))
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
                        theme.primaryColor.copy(alpha = 0.40f),
                        Color.Transparent
                    )
                ),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Ícono cuadrado de la app
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

        // 🟣 Nombre de la app (Color de Texto / Letras)
        Text(
            text = app.appName,
            color = theme.textColor, // 🟣 Letras en Color de Texto
            fontSize = fontSize,
            fontFamily = dashboardFont.fontFamily,
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