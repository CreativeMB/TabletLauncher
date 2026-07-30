package com.creativem.toblauncher

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class DashboardTheme(
    val id: Int,
    val name: String,
    val dashBackground: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val accentCyan: Color,     // 1er Color (Primario)
    val accentPurple: Color,   // 2do Color (Secundario/Gradiente)
    val accentOrange: Color    // 3er Color (Destaque/Alta velocidad)
)

val LocalDashboardTheme = compositionLocalOf { ThemeManager.themes[0] }
val LocalIsBoldText = compositionLocalOf { true } // INYECCIÓN LOCAL DE NEGRITA

object ThemeManager {
    val themes = listOf(
        DashboardTheme(0, "Cian Eléctrico", Color(0xFF070D14), Color(0xFF0F1926), Color(0xFF00838F), Color(0xFF00F2FE), Color(0xFF4FACFE), Color(0xFFFF5252)),
        DashboardTheme(1, "Rojo Pasión", Color(0xFF0A0405), Color(0xFF1C0B0E), Color(0xFF88111A), Color(0xFFFF1744), Color(0xFFFF6100), Color(0xFFFFD600)),
        DashboardTheme(2, "Verde Esmeralda", Color(0xFF040B08), Color(0xFF0B1C14), Color(0xFF0D6136), Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFFFFAB00)),
        DashboardTheme(3, "Azul Zafiro", Color(0xFF050A14), Color(0xFF0C1628), Color(0xFF1446A0), Color(0xFF2979FF), Color(0xFF00E5FF), Color(0xFFFF6D00)),
        DashboardTheme(4, "Cyberpunk", Color(0xFF0A0414), Color(0xFF190C28), Color(0xFF6A1B9A), Color(0xFFF50057), Color(0xFF7C4DFF), Color(0xFF00E5FF)),
        DashboardTheme(5, "Naranja Fuego", Color(0xFF0D0703), Color(0xFF211208), Color(0xFFA84200), Color(0xFFFF6D00), Color(0xFFFFD600), Color(0xFFFF1744)),
        DashboardTheme(6, "Platino Titanio", Color(0xFF0D1015), Color(0xFF1E242E), Color(0xFF607D8B), Color(0xFFFFFFFF), Color(0xFF80D8FF), Color(0xFFFFD600)),
        DashboardTheme(7, "Noche OLED Oro", Color(0xFF000000), Color(0xFF0A0A0A), Color(0xFF806A00), Color(0xFFFFD700), Color(0xFF00E5FF), Color(0xFFFF3D00))
    )

    fun getSavedTheme(context: Context): DashboardTheme {
        val prefs = context.getSharedPreferences("dashboard_theme_prefs", Context.MODE_PRIVATE)
        val savedId = prefs.getInt("theme_id", 0)
        return themes.find { it.id == savedId } ?: themes[0]
    }

    fun saveTheme(context: Context, themeId: Int) {
        val prefs = context.getSharedPreferences("dashboard_theme_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("theme_id", themeId).apply()
    }

    fun getSavedTextScale(context: Context): Float {
        val prefs = context.getSharedPreferences("dashboard_theme_prefs", Context.MODE_PRIVATE)
        return prefs.getFloat("text_scale_factor", 1.35f)
    }

    fun saveTextScale(context: Context, scale: Float) {
        val prefs = context.getSharedPreferences("dashboard_theme_prefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("text_scale_factor", scale).apply()
    }

    // PERSISTENCIA DE TEXTO EN NEGRITA (BOLD)
    fun getSavedIsBold(context: Context): Boolean {
        val prefs = context.getSharedPreferences("dashboard_theme_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("text_is_bold", true) // Por defecto activado en negrita
    }

    fun saveIsBold(context: Context, isBold: Boolean) {
        val prefs = context.getSharedPreferences("dashboard_theme_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("text_is_bold", isBold).apply()
    }
}

// ==========================================
// MODAL DE PERSONALIZACIÓN COMPLETO
// ==========================================
@Composable
fun ThemeSelectorModal(
    currentTheme: DashboardTheme,
    currentTextScale: Float,
    currentIsBold: Boolean,
    onDismiss: () -> Unit,
    onThemeSelected: (DashboardTheme) -> Unit,
    onTextScaleChanged: (Float) -> Unit,
    onIsBoldChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var sliderValue by remember { mutableFloatStateOf(currentTextScale) }
    var isBoldState by remember { mutableStateOf(currentIsBold) }

    val previewWeight = if (isBoldState) FontWeight.ExtraBold else FontWeight.Normal

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(currentTheme.dashBackground)
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ENCABEZADO
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = currentTheme.accentCyan,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "PERSONALIZACIÓN DEL TABLERO",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(currentTheme.cardBackground, RoundedCornerShape(12.dp))
                            .border(1.dp, currentTheme.cardBorder, RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // COLUMNA IZQUIERDA: SLIDER DE TAMAÑO Y INTERRUPTOR DE NEGRITA
                    Surface(
                        color = currentTheme.cardBackground,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(1.dp, currentTheme.cardBorder, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FormatSize, contentDescription = null, tint = currentTheme.accentCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Tamaño y Grosor de Letra", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Personaliza legibilidad para alta resolución.", color = Color.Gray, fontSize = 11.sp)
                            }

                            // VISTA PREVIA CON TAMAÑO Y BOLD EN TIEMPO REAL
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(85.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(currentTheme.dashBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${(sliderValue * 100).toInt()}% - 120 KM/H",
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = previewWeight
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Navegación GPS y Sistema",
                                        color = currentTheme.accentCyan,
                                        fontSize = 12.sp,
                                        fontWeight = previewWeight
                                    )
                                }
                            }

                            // INTERRUPTOR DE TEXTO EN NEGRITA (BOLD)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.FormatSize, // ICONO INTEGRADO (SIN DEPENDENCIAS EXTRA)
                                        contentDescription = null,
                                        tint = currentTheme.accentCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Texto en Negrita (Bold)",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Switch(
                                    checked = isBoldState,
                                    onCheckedChange = {
                                        isBoldState = it
                                        onIsBoldChanged(it)
                                        ThemeManager.saveIsBold(context, it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = currentTheme.accentCyan,
                                        checkedTrackColor = currentTheme.cardBorder
                                    )
                                )
                            }

                            // SLIDER DESLIZABLE
                            Column {
                                Text("Escala Actual: ${(sliderValue * 100).toInt()}%", color = currentTheme.accentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = sliderValue,
                                    onValueChange = {
                                        sliderValue = it
                                        onTextScaleChanged(it)
                                        ThemeManager.saveTextScale(context, it)
                                    },
                                    valueRange = 1.0f..2.8f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = currentTheme.accentCyan,
                                        activeTrackColor = currentTheme.accentCyan,
                                        inactiveTrackColor = currentTheme.cardBorder
                                    )
                                )
                            }
                        }
                    }

                    // COLUMNA DERECHA: PALETAS MUNDO DE COLORES (MOSTRANDO LOS 3 COLORES)
                    Surface(
                        color = currentTheme.cardBackground,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .border(1.dp, currentTheme.cardBorder, RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Estilo de Colores del Auto", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(ThemeManager.themes) { theme ->
                                    val isSelected = theme.id == currentTheme.id

                                    Surface(
                                        color = theme.dashBackground,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(60.dp)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) theme.accentCyan else theme.cardBorder,
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .clickable {
                                                ThemeManager.saveTheme(context, theme.id)
                                                onThemeSelected(theme)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(theme.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                                            // AQUÍ ESTÁN CORREGIDOS LOS 3 CÍRCULOS DE COLORES POR PALETA
                                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(theme.accentCyan))
                                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(theme.accentPurple))
                                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(theme.accentOrange))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}