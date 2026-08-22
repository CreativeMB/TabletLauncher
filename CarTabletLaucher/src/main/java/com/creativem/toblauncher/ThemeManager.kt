package com.creativem.toblauncher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.json.JSONArray
import org.json.JSONObject

// =========================================================================
// 🎨 MODELO DE TEMA CON JERARQUÍA AUTOMOTRIZ DE 3 COLORES
// =========================================================================
data class DashboardTheme(
    val id: Int,
    val name: String,
    val dashBackground: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val accentCyan: Color,     // 🔵 1er Color: Bordes, Botones e Iconos Activos
    val accentPurple: Color,   // 🟣 2do Color: Letras, Títulos y Etiquetas de Texto
    val accentOrange: Color,   // 🟠 3er Color: Números, Velocímetro, Reloj y Métricas
    val isCustom: Boolean = false
) {
    // 💡 Aliases semánticos para usar directamente en tus vistas:
    val primaryColor: Color get() = accentCyan
    val textColor: Color get() = accentPurple
    val numberColor: Color get() = accentOrange
}

// =========================================================================
// 🏎️ TIPOGRAFÍAS AUTOMOTRICES Y FUTURISTAS DE ALTO IMPACTO PARA TABLET
// =========================================================================
enum class DashboardFont(
    val id: Int,
    val displayName: String,
    val subtitle: String,
    val fontFamily: FontFamily,
    val fontWeight: FontWeight,
    val fontStyle: FontStyle = FontStyle.Normal,
    val letterSpacing: TextUnit = 0.sp
) {
    HYPERCAR_NEO_TECH(0, "⚡ HYPERCAR NEO-TECH", "Estilo Tesla / Porsche Eléctrico (Letras Espaciadas)", FontFamily.SansSerif, FontWeight.ExtraBold, FontStyle.Normal, 2.8.sp),
    TITAN_ULTRA_FAT(1, "🦍 TITÁN GORDA BLACK", "Máximo grosor (Weight 900) para lectura instantánea", FontFamily.SansSerif, FontWeight.Black, FontStyle.Normal, 0.sp),
    DOT_MATRIX_DIGITAL(2, "🧱 MATRIZ LED DIGITAL", "Estilo reloj y tablero digital de puntos", FontFamily.Monospace, FontWeight.ExtraBold, FontStyle.Normal, 1.2.sp),
    AMG_SPORT_ITALIC(3, "🏁 AMG RACING ITALIC", "Inclinada deportiva estilo BMW M / AMG", FontFamily.SansSerif, FontWeight.Black, FontStyle.Italic, 1.0.sp),
    TELEMETRY_DIGITAL_HUD(4, "📟 TELEMETRÍA F1 HUD", "Estilo F1 / Pantallas de datos y tacómetro", FontFamily.Monospace, FontWeight.ExtraBold, FontStyle.Normal, 0.5.sp),
    CYBERPUNK_WIDE_TECH(5, "🚀 CYBERPUNK 2077 WIDE", "Apertura ancha futurista para temas Neón", FontFamily.SansSerif, FontWeight.ExtraBold, FontStyle.Normal, 3.5.sp),
    RAPTOR_HEAVY_BLOCK(6, "🛡️ RAPTOR 4X4 BLOCK", "Letras macizas compactas estilo camioneta blindada", FontFamily.SansSerif, FontWeight.Black, FontStyle.Normal, (-0.6).sp),
    NEO_TOKYO_DRIFT(7, "🖋️ TOKYO DRIFT SYNTH", "Cursiva deportiva para temas nocturnos", FontFamily.Cursive, FontWeight.Bold, FontStyle.Italic, 1.5.sp),
    RACING_COMPACT_GT(8, "🏎️ GT3 CUP COMPACT", "Letras condensadas de autos de turismo y rally", FontFamily.SansSerif, FontWeight.Bold, FontStyle.Normal, (-0.3).sp),
    STEALTH_MILITARY_HUD(9, "🎯 STEALTH TÁCTICO HUD", "Monospace militar de cabina de avión caza", FontFamily.Monospace, FontWeight.Black, FontStyle.Normal, 0.2.sp),
    RETRO_SYNTH_80S(10, "🕹️ RETRO WAVE 80s", "Estilo OutRun clásico digital itálico", FontFamily.Monospace, FontWeight.Bold, FontStyle.Italic, 0.8.sp),
    EXECUTIVE_LUXURY(11, "🏛️ LUXURY EXECUTIVE", "Estilo clásico formal para tableros elegantes", FontFamily.Serif, FontWeight.Bold, FontStyle.Normal, 1.2.sp);

    companion object {
        fun fromId(id: Int): DashboardFont = values().find { it.id == id } ?: HYPERCAR_NEO_TECH
    }
}

// 🌐 PROVEEDORES GLOBALES (COMPOSITION LOCALS)
val LocalDashboardTheme = compositionLocalOf { ThemeManager.themes[0] }
val LocalIsBoldText = compositionLocalOf { true }
val LocalButtonScale = compositionLocalOf { 1.0f }
val LocalEqualizerStyle = compositionLocalOf { EqualizerStyle.CLASSIC_BARS }
val LocalDashboardFont = compositionLocalOf { DashboardFont.HYPERCAR_NEO_TECH }

// =========================================================================
// 🚀 GESTOR PARA SELECCIÓN DE LAUNCHER PREDETERMINADO
// =========================================================================
object LauncherManager {
    fun isDefaultLauncher(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                }
            }
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            val currentPackage = resolveInfo?.activityInfo?.packageName
            currentPackage != null && currentPackage == context.packageName
        } catch (e: Exception) {
            false
        }
    }

    fun forceAndroidChooser(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                        return
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }

        try {
            val homeSettingsIntent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeSettingsIntent)
            return
        } catch (e: Exception) { e.printStackTrace() }

        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(homeIntent, "Selecciona Car Tablet Launcher").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            val appIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appIntent)
        }
    }
}

// =========================================================================
// 🎛️ GESTOR PRINCIPAL DE TEMAS, COLORES Y PERSISTENCIA
// =========================================================================
object ThemeManager {
    private const val PREFS_NAME = "dashboard_theme_prefs"
    private const val CUSTOM_THEMES_KEY = "custom_saved_themes_json"

    // Colores ordenados: 1° Borde/Iconos | 2° Letras | 3° Números/Datos
    // 🎨 ORDEN ESTRICTO: 1° Borde/Iconos | 2° Letras/Texto | 3° Números/Métricas
    val themes = listOf(
        // =========================================================================
        // 🏎️ SECCIÓN 1: SUPERCARS & RACING (COLORES FUERTES Y DEPORTIVOS)
        // =========================================================================

        // 0. OLED CLÁSICO HYPER
        DashboardTheme(0, "OLED Clásico (Predeterminado)", Color(0xFF000000), Color(0xFF0B0D14), Color(0xFF1B2234), Color(0xFF00E5FF), Color(0xFFE040FB), Color(0xFFFF9100)),

        // 1. FERRARI CORSA RED: Rojo Corsa + Blanco Glaciar + Amarillo Modena
        DashboardTheme(1, "Ferrari Corsa Sport", Color(0xFF080102), Color(0xFF140407), Color(0xFF7A0C14), Color(0xFFFF1744), Color(0xFFFFFFFF), Color(0xFFFFD600)),

        // 2. PORSCHE GT3 ACID GREEN: Verde Ácido + Lima Neón + Naranja Carrera
        DashboardTheme(2, "Porsche GT3 Acid Green", Color(0xFF020904), Color(0xFF07170B), Color(0xFF135A25), Color(0xFF00FF66), Color(0xFFB2FF59), Color(0xFFFF3D00)),

        // 3. LAMBORGHINI GIALLO ORION: Amarillo Hipervelocidad + Blanco Puro + Cian Eléctrico
        DashboardTheme(3, "Lamborghini Giallo", Color(0xFF080701), Color(0xFF141203), Color(0xFF5E500A), Color(0xFFFFEA00), Color(0xFFFFFFFF), Color(0xFF00E5FF)),

        // 4. MCLAREN PAPAYA ORANGE: Naranja Papaya F1 + Blanco Diamante + Turquesa Aerodinámico
        DashboardTheme(4, "McLaren Papaya F1", Color(0xFF090401), Color(0xFF170903), Color(0xFF8A3300), Color(0xFFFF6D00), Color(0xFFFFF3E0), Color(0xFF00F2FE)),

        // 5. BMW M PERFORMANCE CARBON: Azul BMW M + Celeste Glaciar + Rojo M Power
        DashboardTheme(5, "BMW M Power", Color(0xFF000000), Color(0xFF070B14), Color(0xFF162544), Color(0xFF2979FF), Color(0xFF90CAF9), Color(0xFFFF1744)),

        // 6. ASTON MARTIN F1 RACING: Verde Competición + Lima Flúor + Plata Platino
        DashboardTheme(6, "Aston Martin Racing", Color(0xFF010805), Color(0xFF05140D), Color(0xFF004D25), Color(0xFF00E676), Color(0xFFCCFF00), Color(0xFFE0E0E0)),

        // 7. MERCEDES-AMG SOLARSUN: Oro Ámbar AMG + Blanco Ártico + Naranja Fuego
        DashboardTheme(7, "Mercedes-AMG Solar", Color(0xFF080501), Color(0xFF140D03), Color(0xFF6E430A), Color(0xFFFFAB00), Color(0xFFFFFFFF), Color(0xFFFF3D00)),

        // 8. BUGATTI CHIRON ATLANTIC: Azul Francés Hyper + Celeste Polar + Carmín
        DashboardTheme(8, "Bugatti Chiron Blue", Color(0xFF010612), Color(0xFF050E24), Color(0xFF0D2C6B), Color(0xFF0080FF), Color(0xFF80D8FF), Color(0xFFFF2A4B)),

        // 9. TOKYO DRIFT SYNTHWAVE: Púrpura Nocturno + Magenta Láser + Amarillo Neón
        DashboardTheme(9, "Tokyo Drift JDM", Color(0xFF08010C), Color(0xFF14031E), Color(0xFF660E8A), Color(0xFFFF007F), Color(0xFFE040FB), Color(0xFFFFEA00)),

        // 10. CYBERPUNK NIGHT CITY: Cian 2077 + Rosa Neón + Oro Láser
        DashboardTheme(10, "Cyberpunk 2077", Color(0xFF020810), Color(0xFF051224), Color(0xFF00607A), Color(0xFF00F5FF), Color(0xFFFF006E), Color(0xFFFFD700)),


        // =========================================================================
        // 🌿 SECCIÓN 2: AMBIENT LOUNGE & SUAVES (CONDUCCIÓN RELAJADA Y NOCTURNA)
        // =========================================================================

        // 11. AUDI MOONLIGHT MINIMAL: Blanco Lunar Suave + Celeste Glaciar + Ámbar Sutil
        DashboardTheme(11, "Audi Moonlight Soft", Color(0xFF060709), Color(0xFF0F1116), Color(0xFF282E3D), Color(0xFFE2E8F0), Color(0xFF90CAF9), Color(0xFFFFB74D)),

        // 12. MERCEDES S-CLASS AMBIENT BLUE: Azul Índigo Suave + Celeste Niebla + Champán Cálido
        DashboardTheme(12, "Mercedes Ambient Blue", Color(0xFF020710), Color(0xFF071020), Color(0xFF1C3150), Color(0xFF64B5F6), Color(0xFFBBDEFB), Color(0xFFFFD180)),

        // 13. MENTA NÓRDICA & EUCALIPTO: Verde Salvia Suave + Menta Pastel + Durazno Cálido
        DashboardTheme(13, "Menta Salvia Nórdica", Color(0xFF020906), Color(0xFF07150F), Color(0xFF1E4234), Color(0xFF48CAE4), Color(0xFF80CBC4), Color(0xFFFFAB91)),

        // 14. LAVANDA NOCTURNA RELAX: Lavanda Sereno + Malva Claro + Oro Suave
        DashboardTheme(14, "Lavanda Nocturna", Color(0xFF06040C), Color(0xFF100B1C), Color(0xFF382A54), Color(0xFFB388FF), Color(0xFFE1BEE7), Color(0xFFFFE082)),

        // 15. TURQUESA BRISA MARINA: Aguamarina Suave + Turquesa Cristal + Coral Pálido
        DashboardTheme(15, "Turquesa Brisa Marina", Color(0xFF01080C), Color(0xFF04141E), Color(0xFF1B4054), Color(0xFF00E5FF), Color(0xFF80DEEA), Color(0xFFFF8A80)),

        // 16. SUNSET CREPÚSCULO: Índigo Nocturno + Rosa Atardecer + Melocotón
        DashboardTheme(16, "Sunset Atardecer", Color(0xFF08030A), Color(0xFF140819), Color(0xFF4A1E46), Color(0xFFFF80AB), Color(0xFFF8BBD0), Color(0xFFFFB74D)),

        // 17. MATCHA JAPONÉS: Verde Matcha + Crema Té + Naranja Miel
        DashboardTheme(17, "Matcha Lounge Zen", Color(0xFF040803), Color(0xFF0C160B), Color(0xFF2D4428), Color(0xFFAED581), Color(0xFFDCEDC8), Color(0xFFFFB74D)),

        // 18. OCÉANO PROFUNDO ABISAL: Azul Océano + Celeste Cristal + Arena Dorada
        DashboardTheme(18, "Océano Profundo", Color(0xFF01060E), Color(0xFF041021), Color(0xFF123458), Color(0xFF00B0FF), Color(0xFFB3E5FC), Color(0xFFFFCC80)),

        // 19. ROSA PASTEL & FROST: Rosa Algodón + Blanco Nieve + Azul Hielo
        DashboardTheme(19, "Rosa Pastel & Frost", Color(0xFF090306), Color(0xFF160910), Color(0xFF4B2239), Color(0xFFF48FB1), Color(0xFFFCE4EC), Color(0xFF80D8FF)),

        // 20. NIEBLA ÁRTICA MINIMAL: Blanco Nieve + Gris Grafito + Ámbar Sutil
        DashboardTheme(20, "Niebla Ártica Minimal", Color(0xFF040608), Color(0xFF0A0E13), Color(0xFF222B36), Color(0xFFCFD8DC), Color(0xFFECEFF1), Color(0xFFFFAB40)),


        // =========================================================================
        // 💎 SECCIÓN 3: PLATINO, METÁLICOS & ULTRA LUXURY (PREMIUM Y EXCLUSIVO)
        // =========================================================================

        // 21. PLATINO PURO & CROMO: Platino Cepillado + Blanco Diamante + Azul Acero
        DashboardTheme(21, "Platino Puro & Cromo", Color(0xFF030508), Color(0xFF090D13), Color(0xFF283444), Color(0xFFE2E8F0), Color(0xFFF8FAFC), Color(0xFF38BDF8)),

        // 22. TITANIO FORJADO (GUNMETAL): Gris Titanio + Plata Líquida + Naranja Cobre
        DashboardTheme(22, "Titanio Forjado GT", Color(0xFF040507), Color(0xFF0C0E12), Color(0xFF282D37), Color(0xFF94A3B8), Color(0xFFE2E8F0), Color(0xFFFF7A00)),

        // 23. ORO ROSA ROLLS-ROYCE: Oro Rosa Champán + Marfil Suave + Bronce Noble
        DashboardTheme(23, "Oro Rosa Rolls-Royce", Color(0xFF080304), Color(0xFF14080B), Color(0xFF532832), Color(0xFFF472B6), Color(0xFFFDE8E9), Color(0xFFF59E0B)),

        // 24. MAYBACH CHAMPAGNE GOLD: Oro Champán + Platino Puro + Rubí Emperador
        DashboardTheme(24, "Maybach Champán Gold", Color(0xFF000000), Color(0xFF0F0D05), Color(0xFF483C08), Color(0xFFFFD700), Color(0xFFFFF9C4), Color(0xFFFF2A4B)),

        // 25. BRONCE FORJADO HYPERCAR: Bronce Metálico + Champán + Naranja Lava
        DashboardTheme(25, "Bronce Forjado Hypercar", Color(0xFF060301), Color(0xFF120904), Color(0xFF4E2A12), Color(0xFFFB923C), Color(0xFFFED7AA), Color(0xFFFF5722)),

        // 26. PLATA LÍQUIDA MERCURIO: Plata Mercurio Brillante + Blanco Láser + Rojo Rubí
        DashboardTheme(26, "Plata Líquida SLR", Color(0xFF000000), Color(0xFF090A0D), Color(0xFF262B36), Color(0xFFCBD5E1), Color(0xFFFFFFFF), Color(0xFFFF1744)),

        // 27. COBRE AHUMADO LUXURY: Cobre Pulido + Marfil Cálido + Fuego Ámbar
        DashboardTheme(27, "Cobre Ahumado Luxury", Color(0xFF080402), Color(0xFF140B06), Color(0xFF56301A), Color(0xFFF97316), Color(0xFFFFEDD5), Color(0xFFFFB300)),

        // 28. CUARZO AZUL TITANIUM: Cuarzo Glaciar + Titanio Claro + Azul Zafiro
        DashboardTheme(28, "Cuarzo Azul Titanium", Color(0xFF01060B), Color(0xFF061019), Color(0xFF183B58), Color(0xFF38BDF8), Color(0xFFE0F2FE), Color(0xFF0284C7)),

        // 29. OBSIDIANA MONOCROMO PURO: Blanco Láser Ultra HD + Gris Acero + Blanco Nieve (OLED Puro)
        DashboardTheme(29, "Obsidiana Monocromo OLED", Color(0xFF000000), Color(0xFF050505), Color(0xFF222222), Color(0xFFFFFFFF), Color(0xFFE5E5E5), Color(0xFF9E9E9E))
    )

    fun getAllThemes(context: Context): List<DashboardTheme> = themes + getCustomThemes(context)

    fun getCustomThemes(context: Context): List<DashboardTheme> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(CUSTOM_THEMES_KEY, null) ?: return emptyList()
        val list = mutableListOf<DashboardTheme>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    DashboardTheme(
                        id = obj.getInt("id"),
                        name = obj.getString("name"),
                        dashBackground = Color(obj.getInt("dashBackground")),
                        cardBackground = Color(obj.getInt("cardBackground")),
                        cardBorder = Color(obj.getInt("cardBorder")),
                        accentCyan = Color(obj.getInt("accentCyan")),
                        accentPurple = Color(obj.getInt("accentPurple")),
                        accentOrange = Color(obj.getInt("accentOrange")),
                        isCustom = true
                    )
                )
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    fun saveCustomTheme(context: Context, newTheme: DashboardTheme) {
        val current = getCustomThemes(context).toMutableList()
        current.removeAll { it.id == newTheme.id }
        current.add(newTheme)
        persistCustomThemes(context, current)
    }

    fun deleteCustomTheme(context: Context, themeId: Int) {
        val current = getCustomThemes(context).filter { it.id != themeId }
        persistCustomThemes(context, current)
    }

    private fun persistCustomThemes(context: Context, list: List<DashboardTheme>) {
        val array = JSONArray()
        for (t in list) {
            val obj = JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("dashBackground", t.dashBackground.toArgb())
                put("cardBackground", t.cardBackground.toArgb())
                put("cardBorder", t.cardBorder.toArgb())
                put("accentCyan", t.accentCyan.toArgb())
                put("accentPurple", t.accentPurple.toArgb())
                put("accentOrange", t.accentOrange.toArgb())
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(CUSTOM_THEMES_KEY, array.toString()).apply()
    }

    fun getSavedTheme(context: Context): DashboardTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getInt("theme_id", 0)
        return getAllThemes(context).find { it.id == savedId } ?: themes[0]
    }

    fun saveTheme(context: Context, themeId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt("theme_id", themeId).apply()
    }

    fun getSavedFont(context: Context): DashboardFont {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fontId = prefs.getInt("dashboard_font_id", DashboardFont.HYPERCAR_NEO_TECH.id)
        return DashboardFont.values().find { it.id == fontId } ?: DashboardFont.HYPERCAR_NEO_TECH
    }

    fun saveFont(context: Context, font: DashboardFont) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt("dashboard_font_id", font.id).apply()
    }

    fun getSavedTextScale(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat("text_scale_factor", 1.35f)

    fun saveTextScale(context: Context, scale: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat("text_scale_factor", scale).apply()
    }

    fun getSavedIsBold(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("text_is_bold", true)

    fun saveIsBold(context: Context, isBold: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("text_is_bold", isBold).apply()
    }

    fun getSavedButtonScale(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat("button_scale_factor", 1.0f)

    fun saveButtonScale(context: Context, scale: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat("button_scale_factor", scale).apply()
    }

    fun getSavedEqualizerStyle(context: Context): EqualizerStyle {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val styleName = prefs.getString("equalizer_style", EqualizerStyle.CLASSIC_BARS.name)
        return try { EqualizerStyle.valueOf(styleName ?: EqualizerStyle.CLASSIC_BARS.name) } catch (e: Exception) { EqualizerStyle.CLASSIC_BARS }
    }

    fun saveEqualizerStyle(context: Context, style: EqualizerStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("equalizer_style", style.name).apply()
    }

    fun getSavedAutoRotateEqEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("auto_rotate_eq_enabled", false)

    fun saveAutoRotateEqEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("auto_rotate_eq_enabled", enabled).apply()
    }

    fun getSavedAutoRotateEqInterval(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("auto_rotate_eq_interval", 30)

    fun saveAutoRotateEqInterval(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt("auto_rotate_eq_interval", seconds).apply()
    }
}

// =========================================================================
// 🎨 SELECTOR DE COLOR 2D
// =========================================================================
@Composable
fun InteractiveHsvColorPicker(
    initialColor: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(
            AndroidColor.rgb(
                (initialColor.red * 255).toInt(),
                (initialColor.green * 255).toInt(),
                (initialColor.blue * 255).toInt()
            ),
            hsv
        )
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }

    fun updateColor() {
        val colorInt = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
        onColorChanged(Color(colorInt))
    }

    Row(
        modifier = modifier.fillMaxWidth().height(180.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF333344), RoundedCornerShape(8.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            hue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                            saturation = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                            updateColor()
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                            saturation = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                            updateColor()
                        }
                    }
            ) {
                val w = size.width
                val h = size.height

                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFF0000), Color(0xFFFF8800), Color(0xFFFFFF00),
                            Color(0xFF00FF00), Color(0xFF00FFFF), Color(0xFF0000FF),
                            Color(0xFFFF00FF), Color(0xFFFF0000)
                        )
                    )
                )

                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.White)
                    )
                )

                val posX = (hue / 360f) * w
                val posY = (1f - saturation) * h

                drawCircle(color = Color.Black, radius = 8.dp.toPx(), center = Offset(posX, posY), style = Stroke(width = 3.dp.toPx()))
                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = Offset(posX, posY), style = Stroke(width = 2.dp.toPx()))
            }
        }

        val currentColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))
        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(currentColor)
                .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
        )

        Box(
            modifier = Modifier
                .width(22.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF333344), RoundedCornerShape(12.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            brightness = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                            updateColor()
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            brightness = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                            updateColor()
                        }
                    }
            ) {
                val h = size.height
                val w = size.width

                drawRect(brush = Brush.verticalGradient(listOf(Color.White, Color.Black)))

                val handleY = (1f - brightness) * h
                drawCircle(color = Color.Black, radius = 8.dp.toPx(), center = Offset(w / 2f, handleY))
                drawCircle(color = Color.White, radius = 6.5.dp.toPx(), center = Offset(w / 2f, handleY))
            }
        }
    }
}

// =========================================================================
// 🎛️ MODAL PRINCIPAL DE AJUSTES
// =========================================================================
@Composable
fun ThemeSelectorModal(
    currentTheme: DashboardTheme,
    currentTextScale: Float,
    currentIsBold: Boolean,
    currentButtonScale: Float = 1.0f,
    currentEqualizerStyle: EqualizerStyle = EqualizerStyle.CLASSIC_BARS,
    currentFont: DashboardFont = DashboardFont.HYPERCAR_NEO_TECH,
    onDismiss: () -> Unit,
    onThemeSelected: (DashboardTheme) -> Unit,
    onTextScaleChanged: (Float) -> Unit,
    onIsBoldChanged: (Boolean) -> Unit,
    onButtonScaleChanged: (Float) -> Unit = {},
    onEqualizerStyleChanged: (EqualizerStyle) -> Unit = {},
    onFontChanged: (DashboardFont) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var localTheme by remember { mutableStateOf(currentTheme) }
    var localTextScale by remember { mutableFloatStateOf(currentTextScale) }
    var localButtonScale by remember { mutableFloatStateOf(currentButtonScale) }
    var localIsBold by remember { mutableStateOf(currentIsBold) }
    var localFont by remember { mutableStateOf(currentFont) }
    var localEqStyle by remember { mutableStateOf(currentEqualizerStyle) }

    var isAutoRotateEq by remember { mutableStateOf(ThemeManager.getSavedAutoRotateEqEnabled(context)) }
    var autoRotateEqInterval by remember { mutableIntStateOf(ThemeManager.getSavedAutoRotateEqInterval(context)) }

    var allThemesList by remember { mutableStateOf(ThemeManager.getAllThemes(context)) }
    var showLauncherListModal by remember { mutableStateOf(false) }
    var showCreateThemeDialog by remember { mutableStateOf(false) }

    var isDefaultLauncher by remember { mutableStateOf(LauncherManager.isDefaultLauncher(context)) }

    fun applyAllChangesAndClose() {
        ThemeManager.saveTheme(context, localTheme.id)
        ThemeManager.saveTextScale(context, localTextScale)
        ThemeManager.saveButtonScale(context, localButtonScale)
        ThemeManager.saveIsBold(context, localIsBold)
        ThemeManager.saveFont(context, localFont)
        ThemeManager.saveEqualizerStyle(context, localEqStyle)
        ThemeManager.saveAutoRotateEqEnabled(context, isAutoRotateEq)
        ThemeManager.saveAutoRotateEqInterval(context, autoRotateEqInterval)

        onThemeSelected(localTheme)
        onTextScaleChanged(localTextScale)
        onButtonScaleChanged(localButtonScale)
        onIsBoldChanged(localIsBold)
        onFontChanged(localFont)
        onEqualizerStyleChanged(localEqStyle)

        onDismiss()
    }

    BackHandler { applyAllChangesAndClose() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultLauncher = LauncherManager.isDefaultLauncher(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { BrightnessManager.init(context) }

    var isAutoBrightness by remember { mutableStateOf(BrightnessManager.isAutoBrightnessEnabled) }
    var dayBrightness by remember { mutableFloatStateOf(BrightnessManager.dayBrightnessValue) }
    var nightBrightness by remember { mutableFloatStateOf(BrightnessManager.nightBrightnessValue) }

    val leftScrollState = rememberScrollState()
    val rightScrollState = rememberScrollState()

    Dialog(
        onDismissRequest = { applyAllChangesAndClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Interactive3DBackground(
            theme = localTheme,
            modifier = Modifier.padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ENCABEZADO
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = localTheme.accentCyan, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ESTUDIO DE PERSONALIZACIÓN DEL TABLERO",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = localFont.fontFamily,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }

                    Button(
                        onClick = { applyAllChangesAndClose() },
                        colors = ButtonDefaults.buttonColors(containerColor = localTheme.accentCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("LISTO / APLICAR", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 👈 COLUMNA IZQUIERDA
                    Surface(
                        color = localTheme.cardBackground,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1.0f).fillMaxHeight().border(1.dp, localTheme.cardBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(leftScrollState).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // VISTA PREVIA DINÁMICA CON JERARQUÍA VISUAL REAL
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(76.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(localTheme.dashBackground)
                                    .border(1.5.dp, localTheme.accentCyan, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "120",
                                            color = localTheme.numberColor,
                                            fontSize = 20.sp,
                                            fontFamily = localFont.fontFamily,
                                            fontWeight = FontWeight.Black
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "KM/H",
                                            color = localTheme.textColor,
                                            fontSize = 12.sp,
                                            fontFamily = localFont.fontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Letras en ${localFont.displayName}",
                                        color = localTheme.textColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // TIPOGRAFÍAS AUTOMOTRICES
                            Text("🔤 Selecciona Tipo de Letra:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                DashboardFont.values().forEach { font ->
                                    val isSelected = font == localFont
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) localTheme.accentCyan.copy(alpha = 0.25f) else Color(0x14FFFFFF))
                                            .border(if (isSelected) 1.5.dp else 0.dp, if (isSelected) localTheme.accentCyan else Color.Transparent, RoundedCornerShape(8.dp))
                                            .clickable { localFont = font }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = font.displayName,
                                                fontFamily = font.fontFamily,
                                                fontWeight = font.fontWeight,
                                                fontStyle = font.fontStyle,
                                                letterSpacing = font.letterSpacing,
                                                fontSize = 11.sp,
                                                color = if (isSelected) localTheme.accentCyan else Color.White
                                            )
                                            Text(font.subtitle, fontSize = 8.sp, color = if (isSelected) Color.LightGray else Color.Gray)
                                        }
                                    }
                                }
                            }

                            // ESCALA DE LETRAS
                            Column {
                                Text("Tamaño de Letras: ${(localTextScale * 100).toInt()}%", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = localTextScale,
                                    onValueChange = { localTextScale = it },
                                    valueRange = 1.0f..2.8f,
                                    colors = SliderDefaults.colors(thumbColor = localTheme.accentCyan, activeTrackColor = localTheme.accentCyan)
                                )
                            }

                            // BOTONES MULTIMEDIA
                            Column {
                                Text("Tamaño Botones Multimedia: ${(localButtonScale * 100).toInt()}%", color = localTheme.accentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = localButtonScale,
                                    onValueChange = { localButtonScale = it },
                                    valueRange = 0.8f..2.5f,
                                    colors = SliderDefaults.colors(thumbColor = localTheme.accentCyan, activeTrackColor = localTheme.accentCyan)
                                )
                            }

                            HorizontalDivider(color = localTheme.cardBorder.copy(alpha = 0.5f))

                            // BRILLO
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Brillo Automático Día/Noche", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Switch(
                                    checked = isAutoBrightness,
                                    onCheckedChange = {
                                        isAutoBrightness = it
                                        BrightnessManager.setAutoEnabled(context, it)
                                        BrightnessManager.applyBrightnessToActivity(context)
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = localTheme.accentCyan)
                                )
                            }

                            Column {
                                Text("☀️ Brillo Día: ${(dayBrightness * 100).toInt()}%", color = localTheme.accentOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = dayBrightness,
                                    onValueChange = {
                                        dayBrightness = it
                                        BrightnessManager.setDayBrightness(context, dayBrightness)
                                        if (!BrightnessManager.isNightTime() && isAutoBrightness) {
                                            BrightnessManager.applyHardwareBrightness(context, dayBrightness)
                                        }
                                    },
                                    valueRange = 0.1f..1.0f,
                                    colors = SliderDefaults.colors(thumbColor = localTheme.accentOrange, activeTrackColor = localTheme.accentOrange)
                                )
                            }

                            Column {
                                Text("🌙 Brillo Noche: ${(nightBrightness * 100).toInt()}%", color = localTheme.accentPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = nightBrightness,
                                    onValueChange = {
                                        nightBrightness = it
                                        BrightnessManager.setNightBrightness(context, nightBrightness)
                                    },
                                    valueRange = 0.0f..1.0f,
                                    colors = SliderDefaults.colors(thumbColor = localTheme.accentPurple, activeTrackColor = localTheme.accentPurple)
                                )
                            }
                        }
                    }

                    // 👉 COLUMNA DERECHA
                    Surface(
                        color = localTheme.cardBackground,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1.1f).fillMaxHeight().border(1.dp, localTheme.cardBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rightScrollState).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // GESTOR DE LAUNCHER
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161622)),
                                border = BorderStroke(1.dp, if (isDefaultLauncher) Color(0xFF00E676) else localTheme.accentCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Home,
                                                contentDescription = null,
                                                tint = if (isDefaultLauncher) Color(0xFF00E676) else localTheme.accentCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Launcher Predeterminado", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Surface(
                                            color = if (isDefaultLauncher) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFF5252).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (isDefaultLauncher) "ACTIVO" else "NO ACTIVO",
                                                color = if (isDefaultLauncher) Color(0xFF00E676) else Color(0xFFFF5252),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            if (!isDefaultLauncher) {
                                                LauncherManager.forceAndroidChooser(context)
                                            } else {
                                                showLauncherListModal = true
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isDefaultLauncher) Color(0xFF262638) else Color(0xFF00E676),
                                            contentColor = if (isDefaultLauncher) Color.White else Color.Black
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(
                                            if (isDefaultLauncher) Icons.Default.Check else Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isDefaultLauncher) "CONFIGURACIÓN LAUNCHER" else "⭐ ACTIVAR COMO PREDETERMINADO",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { showCreateThemeDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = localTheme.accentCyan, contentColor = Color.Black),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🎨 MEZCLAR COLOR Y CREAR TEMA", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                            }

                            Text("🎨 Colección de Temas (${allThemesList.size})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                            // LISTA DE TEMAS
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                allThemesList.chunked(2).forEach { rowThemes ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        rowThemes.forEach { theme ->
                                            val isSelected = theme.id == localTheme.id
                                            Surface(
                                                color = theme.dashBackground,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(56.dp)
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) localTheme.accentCyan else theme.cardBorder,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable { localTheme = theme }
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(theme.name, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                        if (theme.isCustom) Text("Personalizado", color = localTheme.accentCyan, fontSize = 7.sp)
                                                    }

                                                    // 3 Puntos ordenados: [Bordes/Acción] [Letras] [Números]
                                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(theme.primaryColor))
                                                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(theme.textColor))
                                                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(theme.numberColor))
                                                    }

                                                    if (theme.isCustom) {
                                                        IconButton(
                                                            onClick = {
                                                                ThemeManager.deleteCustomTheme(context, theme.id)
                                                                allThemesList = ThemeManager.getAllThemes(context)
                                                            },
                                                            modifier = Modifier.size(20.dp)
                                                        ) {
                                                            Icon(Icons.Default.Close, contentDescription = "Borrar", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = localTheme.cardBorder.copy(alpha = 0.5f))

                            // ESTILOS DE ECUALIZADOR
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = localTheme.accentCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Estilo de Ecualizador (${EqualizerStyle.values().size})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                EqualizerStyle.values().toList().chunked(2).forEach { rowStyles ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        rowStyles.forEach { style ->
                                            val isSelected = style == localEqStyle
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSelected) localTheme.accentCyan.copy(alpha = 0.25f) else Color(0xFF1E1E28))
                                                    .border(if (isSelected) 1.5.dp else 0.dp, if (isSelected) localTheme.accentCyan else Color.Transparent, RoundedCornerShape(6.dp))
                                                    .clickable { localEqStyle = style }
                                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(style.displayName, color = if (isSelected) localTheme.accentCyan else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }

                            // ECUALIZADOR AUTO-ROTACIÓN
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161622)),
                                border = BorderStroke(1.dp, if (isAutoRotateEq) localTheme.accentCyan else localTheme.cardBorder),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Shuffle,
                                                contentDescription = null,
                                                tint = if (isAutoRotateEq) localTheme.accentCyan else Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Ecualizador Aleatorio (Auto)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Switch(
                                            checked = isAutoRotateEq,
                                            onCheckedChange = { isAutoRotateEq = it },
                                            colors = SwitchDefaults.colors(checkedThumbColor = localTheme.accentCyan)
                                        )
                                    }

                                    if (isAutoRotateEq) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Intervalo de cambio de ecualizador:", color = Color.LightGray, fontSize = 9.sp)
                                        Spacer(modifier = Modifier.height(4.dp))

                                        val intervals = listOf(
                                            10 to "10s",
                                            30 to "30s",
                                            60 to "1 Min",
                                            300 to "5 Min",
                                            600 to "10 Min"
                                        )

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            intervals.forEach { (sec, label) ->
                                                val isSelected = autoRotateEqInterval == sec
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isSelected) localTheme.accentCyan else Color(0xFF262638))
                                                        .clickable { autoRotateEqInterval = sec }
                                                        .padding(vertical = 5.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = label,
                                                        color = if (isSelected) Color.Black else Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.ExtraBold
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
            }
        }
    }

    if (showCreateThemeDialog) {
        CustomColorMixerDialog(
            currentTheme = localTheme,
            onDismiss = { showCreateThemeDialog = false },
            onSave = { newCustomTheme ->
                ThemeManager.saveCustomTheme(context, newCustomTheme)
                allThemesList = ThemeManager.getAllThemes(context)
                localTheme = newCustomTheme
                showCreateThemeDialog = false
            }
        )
    }

    if (showLauncherListModal) {
        LauncherPickerModal(theme = localTheme, onDismiss = { showLauncherListModal = false })
    }
}

// =========================================================================
// 🎨 MODAL MEZCLADOR 2D CON CATEGORÍAS CLARAS
// =========================================================================
@Composable
fun CustomColorMixerDialog(
    currentTheme: DashboardTheme,
    onDismiss: () -> Unit,
    onSave: (DashboardTheme) -> Unit
) {
    var themeName by remember { mutableStateOf("Mi Estilo Personalizado") }
    var selectedColorIndex by remember { mutableIntStateOf(1) }

    var colorPrimario by remember { mutableStateOf(Color(0xFF00E5FF)) }   // Bordes y Botones
    var colorLetras by remember { mutableStateOf(Color(0xFFD500F9)) }     // Letras y Títulos
    var colorNumeros by remember { mutableStateOf(Color(0xFFFF9100)) }    // Números y Métricas

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141E),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = colorPrimario)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Asignar Colores del Tablero", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = themeName,
                    onValueChange = { themeName = it },
                    label = { Text("Nombre del Tema") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = colorPrimario
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 3 Pestañas con su función clara
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        Triple(1, "1️⃣ Bordes/Iconos", colorPrimario),
                        Triple(2, "2️⃣ Letras/Texto", colorLetras),
                        Triple(3, "3️⃣ Números/Datos", colorNumeros)
                    ).forEach { (idx, label, colorVal) ->
                        val isSelected = selectedColorIndex == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colorVal.copy(alpha = 0.25f) else Color(0xFF222232))
                                .border(if (isSelected) 2.dp else 0.dp, colorVal, RoundedCornerShape(8.dp))
                                .clickable { selectedColorIndex = idx }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(colorVal))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(label, color = if (isSelected) colorVal else Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }

                InteractiveHsvColorPicker(
                    initialColor = when (selectedColorIndex) {
                        1 -> colorPrimario
                        2 -> colorLetras
                        else -> colorNumeros
                    },
                    onColorChanged = { newColor ->
                        when (selectedColorIndex) {
                            1 -> colorPrimario = newColor
                            2 -> colorLetras = newColor
                            3 -> colorNumeros = newColor
                        }
                    }
                )

                // Previsualización directa
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF07090E))
                        .border(1.5.dp, colorPrimario, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("MÚSICA:", color = colorLetras, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("03:45", color = colorNumeros, fontSize = 13.sp, fontWeight = FontWeight.Black)
                        Text("•", color = colorPrimario, fontSize = 12.sp)
                        Text("VELOCIDAD:", color = colorLetras, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("80 KM/H", color = colorNumeros, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newId = 100 + (System.currentTimeMillis() % 10000).toInt()
                    val created = DashboardTheme(
                        id = newId,
                        name = themeName.ifBlank { "Personalizado #$newId" },
                        dashBackground = Color(0xFF05070C),
                        cardBackground = Color(0xFF0E121B),
                        cardBorder = colorPrimario.copy(alpha = 0.40f),
                        accentCyan = colorPrimario,
                        accentPurple = colorLetras,
                        accentOrange = colorNumeros,
                        isCustom = true
                    )
                    onSave(created)
                },
                colors = ButtonDefaults.buttonColors(containerColor = colorPrimario, contentColor = Color.Black)
            ) {
                Text("Guardar Tema", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.Gray) }
        }
    )
}

// =========================================================================
// 📱 MODAL ASIGNACIÓN DIRECTA LAUNCHER
// =========================================================================
@Composable
fun LauncherPickerModal(
    theme: DashboardTheme,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E28),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Home, contentDescription = null, tint = theme.accentCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Asignar Launcher Predeterminado", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Selecciona Car Tablet Launcher como predeterminado para disfrutar del contenido y la mejor experiencia en tu vehículo.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = {
                        LauncherManager.forceAndroidChooser(context)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("⭐ ESTABLECER COMO PREDETERMINADO", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White) }
        }
    )
}

// =========================================================================
// 🌌 FONDO 3D INTERACTIVO CON AMBIENT NEÓN
// =========================================================================
@Composable
fun Interactive3DBackground(
    theme: DashboardTheme,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize().background(theme.dashBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(theme.accentCyan.copy(alpha = 0.35f), theme.accentCyan.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = width * 0.55f
                )
            )

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(theme.accentOrange.copy(alpha = 0.30f), theme.accentOrange.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(width, height),
                    radius = width * 0.55f
                )
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(theme.accentPurple.copy(alpha = 0.25f), theme.accentPurple.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(width * 0.50f, height * 0.50f),
                    radius = width * 0.45f
                ),
                center = Offset(width * 0.50f, height * 0.50f),
                radius = width * 0.45f
            )

            val stripeSpacing = 36f
            val stripeColor = theme.accentCyan.copy(alpha = 0.05f)

            var x = -height
            while (x < width + height) {
                drawLine(
                    color = stripeColor,
                    start = Offset(x, 0f),
                    end = Offset(x + height, height),
                    strokeWidth = 2f
                )
                x += stripeSpacing
            }

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, theme.dashBackground.copy(alpha = 0.85f)),
                    center = Offset(width / 2f, height / 2f),
                    radius = width * 0.65f
                )
            )
        }
        content()
    }
}