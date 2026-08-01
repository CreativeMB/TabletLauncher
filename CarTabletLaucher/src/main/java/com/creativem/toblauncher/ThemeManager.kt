package com.creativem.toblauncher

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
val LocalIsBoldText = compositionLocalOf { true }
val LocalButtonScale = compositionLocalOf { 1.0f }

object ThemeManager {
    val themes = listOf(
        // =========================================================================
        // 🏁 1. CLÁSICOS Y OLED PURO (ALTO CONTRASTE)
        // =========================================================================
        DashboardTheme(0, "Negro OLED (Predeterminado)", Color(0xFF000000), Color(0xFF0C0E14), Color(0xFF1E2230), Color(0xFF00E5FF), Color(0xFF9D00FF), Color(0xFFFF9100)),
        DashboardTheme(1, "Cian Eléctrico", Color(0xFF070D14), Color(0xFF0F1926), Color(0xFF00838F), Color(0xFF00F2FE), Color(0xFF4FACFE), Color(0xFFFF5252)),
        DashboardTheme(2, "Rojo Pasión Sport", Color(0xFF0A0405), Color(0xFF1C0B0E), Color(0xFF88111A), Color(0xFFFF1744), Color(0xFFFF6100), Color(0xFFFFD600)),
        DashboardTheme(3, "Verde Esmeralda", Color(0xFF040B08), Color(0xFF0B1C14), Color(0xFF0D6136), Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFFFFAB00)),
        DashboardTheme(4, "Azul Zafiro", Color(0xFF050A14), Color(0xFF0C1628), Color(0xFF1446A0), Color(0xFF2979FF), Color(0xFF00E5FF), Color(0xFFFF6D00)),

        // =========================================================================
        // 🎆 2. NEÓN VIVOS Y CYBERPUNK (GRADIENTES FUERTES)
        // =========================================================================
        DashboardTheme(5, "Cyberpunk Synthwave", Color(0xFF0A0414), Color(0xFF190C28), Color(0xFF6A1B9A), Color(0xFFF50057), Color(0xFF7C4DFF), Color(0xFF00E5FF)),
        DashboardTheme(6, "Tokyo Drift Pink", Color(0xFF0D030A), Color(0xFF1F0818), Color(0xFF880E4F), Color(0xFFFF007F), Color(0xFF00F5FF), Color(0xFFCCFF00)),
        DashboardTheme(7, "Naranja Fuego Volcánico", Color(0xFF0D0703), Color(0xFF211208), Color(0xFFA84200), Color(0xFFFF6D00), Color(0xFFFFD600), Color(0xFFFF1744)),
        DashboardTheme(8, "Verde Neón Tóxico", Color(0xFF030D05), Color(0xFF0A240F), Color(0xFF1B5E20), Color(0xFF00FF66), Color(0xFFB2FF59), Color(0xFFFF3D00)),
        DashboardTheme(9, "Hyper Violeta", Color(0xFF080312), Color(0xFF160A2D), Color(0xFF4A148C), Color(0xFFD500F9), Color(0xFF29B6F6), Color(0xFFFFEE58)),

        // =========================================================================
        // 🌸 3. TONOS SUAVES Y PASTELES (ELEGANTES Y RELAJANTES)
        // =========================================================================
        DashboardTheme(10, "Menta Suave Pastel", Color(0xFF0A1210), Color(0xFF142420), Color(0xFF2E5A4C), Color(0xFF80CBC4), Color(0xFFA5D6A7), Color(0xFFFFAB91)),
        DashboardTheme(11, "Lavanda Nocturna", Color(0xFF0F0C1B), Color(0xFF1C1830), Color(0xFF453A68), Color(0xFFCE93D8), Color(0xFF9FA8DA), Color(0xFFFFCC80)),
        DashboardTheme(12, "Turquesa Brisa Marina", Color(0xFF06141B), Color(0xFF11252D), Color(0xFF254B5A), Color(0xFF80DEEA), Color(0xFF80CBC4), Color(0xFFFF8A80)),
        DashboardTheme(13, "Rosa Pastel & Cyan", Color(0xFF140A10), Color(0xFF261420), Color(0xFF5E2B4E), Color(0xFFF48FB1), Color(0xFF80DEEA), Color(0xFFFFE082)),
        DashboardTheme(14, "Crema & Albaricoque", Color(0xFF140E0A), Color(0xFF261D16), Color(0xFF594130), Color(0xFFFFCC80), Color(0xFFBCAAA4), Color(0xFF80CBC4)),

        // =========================================================================
        // 🏆 4. LUJO, METÁLICOS Y EDICIONES VIP
        // =========================================================================
        DashboardTheme(15, "Oro Imperial Luxury", Color(0xFF000000), Color(0xFF0F0D05), Color(0xFF5E4E0A), Color(0xFFFFD700), Color(0xFFFFF59D), Color(0xFFFF3D00)),
        DashboardTheme(16, "Platino Titanio", Color(0xFF0D1015), Color(0xFF1E242E), Color(0xFF607D8B), Color(0xFFFFFFFF), Color(0xFF80D8FF), Color(0xFFFFD600)),
        DashboardTheme(17, "Cobre Ejecutivo", Color(0xFF0E0B08), Color(0xFF211812), Color(0xFF5C3A21), Color(0xFFFF8A65), Color(0xFFD7CCC8), Color(0xFFFFD54F)),
        DashboardTheme(18, "Calamar Carbón & Plata", Color(0xFF08090A), Color(0xFF14171A), Color(0xFF363B42), Color(0xFFE0E0E0), Color(0xFF90A4AE), Color(0xFFFF5252)),
        DashboardTheme(19, "Noche Zafiro & Oro", Color(0xFF04060F), Color(0xFF0D1326), Color(0xFF1D2D59), Color(0xFF448AFF), Color(0xFFFFD700), Color(0xFFFF5252)),

        // =========================================================================
        // 🏎️ 5. DEPORTIVOS Y RACING HERITAGE
        // =========================================================================
        DashboardTheme(20, "Gulf Racing Classic", Color(0xFF081018), Color(0xFF112030), Color(0xFF214468), Color(0xFF81D4FA), Color(0xFFFF8A65), Color(0xFFFFFFFF)),
        DashboardTheme(21, "Scuderia Monza", Color(0xFF0A0202), Color(0xFF1F0808), Color(0xFF6B0F0F), Color(0xFFFF1744), Color(0xFFFFEA00), Color(0xFFFFFFFF)),
        DashboardTheme(22, "Carbono M Performance", Color(0xFF000000), Color(0xFF0F1218), Color(0xFF1F2838), Color(0xFF2979FF), Color(0xFFFF1744), Color(0xFF00E5FF)),
        DashboardTheme(23, "Amarillo Speed GT", Color(0xFF0A0A02), Color(0xFF1A1A05), Color(0xFF52520B), Color(0xFFFFEA00), Color(0xFF00E5FF), Color(0xFFFF3D00)),
        DashboardTheme(24, "Verde Británico Racing", Color(0xFF020A05), Color(0xFF081C0F), Color(0xFF114223), Color(0xFF00C853), Color(0xFFFFD700), Color(0xFF00E5FF)),

        // =========================================================================
        // 🌌 6. AMBIENTALES, DEVANECIDOS Y NATURALEZA
        // =========================================================================
        DashboardTheme(25, "Aurora Boreal", Color(0xFF030D12), Color(0xFF091E26), Color(0xFF144552), Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFFD500F9)),
        DashboardTheme(26, "Puesta de Sol Acapulco", Color(0xFF0F050C), Color(0xFF240D1D), Color(0xFF5E1A48), Color(0xFFFF4081), Color(0xFFFF6D00), Color(0xFFFFD600)),
        DashboardTheme(27, "Océano Profundo", Color(0xFF020B14), Color(0xFF06182B), Color(0xFF0E3860), Color(0xFF00B8D4), Color(0xFF004D40), Color(0xFFFFAB00)),
        DashboardTheme(28, "Cielo Estrellado", Color(0xFF050510), Color(0xFF0E0E24), Color(0xFF232354), Color(0xFF7C4DFF), Color(0xFF536DFE), Color(0xFFFFD54F)),
        DashboardTheme(29, "Coral & Turquesa", Color(0xFF041014), Color(0xFF0B222A), Color(0xFF1A4D5C), Color(0xFF1DE9B6), Color(0xFFFF5252), Color(0xFFFFD600))
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

    fun getSavedIsBold(context: Context): Boolean {
        val prefs = context.getSharedPreferences("dashboard_theme_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("text_is_bold", true)
    }

    fun saveIsBold(context: Context, isBold: Boolean) {
        val prefs = context.getSharedPreferences("dashboard_theme_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("text_is_bold", isBold).apply()
    }

    fun getSavedButtonScale(context: Context): Float {
        val prefs = context.getSharedPreferences("dashboard_theme_prefs", Context.MODE_PRIVATE)
        return prefs.getFloat("button_scale_factor", 1.0f)
    }

    fun saveButtonScale(context: Context, scale: Float) {
        val prefs = context.getSharedPreferences("dashboard_theme_prefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("button_scale_factor", scale).apply()
    }
}

// ==========================================
// MODAL DE PERSONALIZACIÓN Y BRILLO DÍA/NOCHE
// ==========================================
@Composable
fun ThemeSelectorModal(
    currentTheme: DashboardTheme,
    currentTextScale: Float,
    currentIsBold: Boolean,
    currentButtonScale: Float = 1.0f,
    onDismiss: () -> Unit,
    onThemeSelected: (DashboardTheme) -> Unit,
    onTextScaleChanged: (Float) -> Unit,
    onIsBoldChanged: (Boolean) -> Unit,
    onButtonScaleChanged: (Float) -> Unit = {}
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        BrightnessManager.init(context)
    }

    var sliderValue by remember { mutableFloatStateOf(currentTextScale) }
    var buttonScaleValue by remember { mutableFloatStateOf(currentButtonScale) }
    var isBoldState by remember { mutableStateOf(currentIsBold) }

    // ESTADOS LOCALES PARA CONTROL DE BRILLO
    var isAutoBrightness by remember { mutableStateOf(BrightnessManager.isAutoBrightnessEnabled) }
    var dayBrightness by remember { mutableFloatStateOf(BrightnessManager.dayBrightnessValue) }
    var nightBrightness by remember { mutableFloatStateOf(BrightnessManager.nightBrightnessValue) }
    var hasSettingsPermission by remember { mutableStateOf(BrightnessManager.hasWriteSettingsPermission(context)) }

    val previewWeight = if (isBoldState) FontWeight.ExtraBold else FontWeight.Normal
    val leftScrollState = rememberScrollState()

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
                            text = "PERSONALIZACIÓN Y ESTILO DEL TABLERO",
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
                    // COLUMNA IZQUIERDA: SLIDERS, BRILLO Y NEGRITA
                    Surface(
                        color = currentTheme.cardBackground,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1.0f)
                            .fillMaxHeight()
                            .border(1.dp, currentTheme.cardBorder, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(leftScrollState)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FormatSize, contentDescription = null, tint = currentTheme.accentCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Ajustes Visuales y Brillo GPS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Ajusta letras, botones y brillo para el día y noche.", color = Color.Gray, fontSize = 11.sp)
                            }

                            // ⚠️ TARJETA DE PERMISO SI LA TABLET AÚN NO LO HA CONCEDIDO
                            if (!hasSettingsPermission) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1200)),
                                    border = BorderStroke(1.dp, Color(0xFFFF9100)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("⚠️ Permiso de Brillo Tablet", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("Toca para activar el control de pantalla en tu estéreo.", color = Color.LightGray, fontSize = 9.sp)
                                        }
                                        Button(
                                            onClick = {
                                                BrightnessManager.requestWriteSettingsPermission(context)
                                                hasSettingsPermission = BrightnessManager.hasWriteSettingsPermission(context)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Activar", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }

                            // VISTA PREVIA
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(currentTheme.dashBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${(sliderValue * 100).toInt()}% - 120 KM/H",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = previewWeight
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Botones: ${(buttonScaleValue * 100).toInt()}% | Modo: ${if (BrightnessManager.isNightTime()) "🌙 Noche" else "☀️ Día"}",
                                        color = currentTheme.accentCyan,
                                        fontSize = 11.sp,
                                        fontWeight = previewWeight
                                    )
                                }
                            }

                            // 1. SWITCH BRILLO AUTOMÁTICO DÍA / NOCHE
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Brillo Automático Día/Noche", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Switch(
                                    checked = isAutoBrightness,
                                    onCheckedChange = {
                                        isAutoBrightness = it
                                        BrightnessManager.setAutoEnabled(context, it)
                                        BrightnessManager.applyBrightnessToActivity(context)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = currentTheme.accentCyan,
                                        checkedTrackColor = currentTheme.cardBorder
                                    )
                                )
                            }

                            // 2. SLIDER BRILLO DE DÍA (6 AM - 18 PM)
                            Column {
                                Text("☀️ Brillo de Día: ${(dayBrightness * 100).toInt()}%", color = currentTheme.accentOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = dayBrightness,
                                    onValueChange = {
                                        dayBrightness = it
                                        BrightnessManager.setDayBrightness(context, it)
                                        if (!BrightnessManager.isNightTime() && isAutoBrightness) {
                                            BrightnessManager.applyHardwareBrightness(context, it)
                                        }
                                    },
                                    valueRange = 0.1f..1.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = currentTheme.accentOrange,
                                        activeTrackColor = currentTheme.accentOrange,
                                        inactiveTrackColor = currentTheme.cardBorder
                                    )
                                )
                            }

                            // 3. SLIDER BRILLO DE NOCHE (18 PM - 6 AM)
                            Column {
                                val overlayPercent = (BrightnessManager.nightOverlayAlpha * 100).toInt()
                                Text(
                                    text = "🌙 Brillo de Noche: ${(nightBrightness * 100).toInt()}% ${if (overlayPercent > 0) "(Filtro +$overlayPercent%)" else ""}",
                                    color = currentTheme.accentPurple,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = nightBrightness,
                                    onValueChange = {
                                        nightBrightness = it
                                        BrightnessManager.setNightBrightness(context, it)
                                    },
                                    valueRange = 0.0f..1.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = currentTheme.accentPurple,
                                        activeTrackColor = currentTheme.accentPurple,
                                        inactiveTrackColor = currentTheme.cardBorder
                                    )
                                )
                            }

                            // 4. INTERRUPTOR DE TEXTO EN NEGRITA
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Texto en Negrita", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

                            // 5. SLIDER ESCALA DE TEXTO
                            Column {
                                Text("Escala Letras: ${(sliderValue * 100).toInt()}%", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

                            // 6. SLIDER ESCALA DE BOTONES DEL REPRODUCTOR
                            Column {
                                Text("Tamaño Botones Reproductor: ${(buttonScaleValue * 100).toInt()}%", color = currentTheme.accentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = buttonScaleValue,
                                    onValueChange = {
                                        buttonScaleValue = it
                                        onButtonScaleChanged(it)
                                        ThemeManager.saveButtonScale(context, it)
                                    },
                                    valueRange = 0.8f..2.5f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = currentTheme.accentCyan,
                                        activeTrackColor = currentTheme.accentCyan,
                                        inactiveTrackColor = currentTheme.cardBorder
                                    )
                                )
                            }
                        }
                    }

                    // COLUMNA DERECHA: SELECCIÓN DE TEMAS EN GRILLA
                    Surface(
                        color = currentTheme.cardBackground,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .border(1.dp, currentTheme.cardBorder, RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Colección de Estilos (${ThemeManager.themes.size} Temas)",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                                            .height(62.dp)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
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
                                            Text(
                                                text = theme.name,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 2
                                            )

                                            Spacer(modifier = Modifier.width(4.dp))

                                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Box(modifier = Modifier.size(11.dp).clip(CircleShape).background(theme.accentCyan))
                                                Box(modifier = Modifier.size(11.dp).clip(CircleShape).background(theme.accentPurple))
                                                Box(modifier = Modifier.size(11.dp).clip(CircleShape).background(theme.accentOrange))
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