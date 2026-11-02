package com.creativem.toblauncher

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.jvm.java

// =========================================================================
// 🚀 GESTOR DE DESCARGA E INSTALACIÓN DE ACTUALIZACIONES
// =========================================================================
object AppUpdater {
    const val APK_URL = "https://github.com/CreativeMB/center/releases/download/apk/CarTabletLaucher-debug.apk"

    suspend fun downloadAndInstall(
        context: Context,
        onProgress: (Float) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    withContext(Dispatchers.Main) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        onError("Concede el permiso para instalar apps desconocidas y vuelve a pulsar el botón.")
                    }
                    return@withContext
                }
            }

            var currentUrl = APK_URL
            var connection: HttpURLConnection? = null
            var redirectCount = 0

            while (redirectCount < 6) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "CarTabletLauncher-Updater/1.0")
                }
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == 307 || responseCode == 308) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    currentUrl = location
                    redirectCount++
                } else if (responseCode == HttpURLConnection.HTTP_OK) {
                    break
                } else {
                    throw Exception("El servidor respondió con código HTTP $responseCode")
                }
            }

            val finalConn = connection ?: throw Exception("No se pudo establecer conexión.")
            val totalSize = finalConn.contentLength
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val outputFile = File(downloadDir, "CarTabletLauncher-update.apk")
            if (outputFile.exists()) outputFile.delete()

            val input = finalConn.inputStream
            val output = FileOutputStream(outputFile)
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (totalSize > 0) {
                    onProgress((totalBytesRead.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f))
                }
            }

            output.flush()
            output.close()
            input.close()
            finalConn.disconnect()

            withContext(Dispatchers.Main) {
                installApk(context, outputFile)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onError("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                } catch (e: Exception) {
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                }
            } else {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }
    }
}

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
    val primaryColor: Color get() = accentCyan
    val textColor: Color get() = accentPurple
    val numberColor: Color get() = accentOrange
}

// =========================================================================
// 🏎️ TIPOGRAFÍAS AUTOMOTRICES
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
    HYPERCAR_NEO_TECH(0, "⚡ HYPERCAR NEO-TECH", "Estilo Tesla / Porsche Eléctrico", FontFamily.SansSerif, FontWeight.ExtraBold, FontStyle.Normal, 2.5.sp),
    TITAN_ULTRA_FAT(1, "🦍 TITÁN GORDA BLACK", "Máximo grosor (900) para lectura rápida", FontFamily.SansSerif, FontWeight.Black, FontStyle.Normal, 0.sp),
    DOT_MATRIX_DIGITAL(2, "🧱 MATRIZ LED DIGITAL", "Estilo tablero de instrumentos digital", FontFamily.Monospace, FontWeight.ExtraBold, FontStyle.Normal, 1.2.sp),
    AMG_SPORT_ITALIC(3, "🏁 AMG RACING ITALIC", "Inclinada deportiva estilo BMW M / AMG", FontFamily.SansSerif, FontWeight.Black, FontStyle.Italic, 1.0.sp),
    TELEMETRY_DIGITAL_HUD(4, "📟 TELEMETRÍA F1 HUD", "Estilo telemetría y pantallas de datos F1", FontFamily.Monospace, FontWeight.ExtraBold, FontStyle.Normal, 0.5.sp),
    CYBERPUNK_WIDE_TECH(5, "🚀 CYBERPUNK 2077 WIDE", "Apertura futurista extendida", FontFamily.SansSerif, FontWeight.ExtraBold, FontStyle.Normal, 3.0.sp),
    RAPTOR_HEAVY_BLOCK(6, "🛡️ RAPTOR 4X4 BLOCK", "Letras macizas estilo pickup táctica", FontFamily.SansSerif, FontWeight.Black, FontStyle.Normal, (-0.5).sp),
    NEO_TOKYO_DRIFT(7, "🖋️ TOKYO DRIFT SYNTH", "Deportiva cursiva nocturna", FontFamily.Cursive, FontWeight.Bold, FontStyle.Italic, 1.2.sp),
    RACING_COMPACT_GT(8, "🏎️ GT3 CUP COMPACT", "Condensada para carreras y turismo", FontFamily.SansSerif, FontWeight.Bold, FontStyle.Normal, (-0.2).sp),
    STEALTH_MILITARY_HUD(9, "🎯 STEALTH TÁCTICO HUD", "Monospace cabina de avión de combate", FontFamily.Monospace, FontWeight.Black, FontStyle.Normal, 0.2.sp),
    RETRO_SYNTH_80S(10, "🕹️ RETRO WAVE 80s", "Estilo Synthwave arcade 80s", FontFamily.Monospace, FontWeight.Bold, FontStyle.Italic, 0.8.sp),
    EXECUTIVE_LUXURY(11, "🏛️ LUXURY EXECUTIVE", "Serif formal refinado de gran lujo", FontFamily.Serif, FontWeight.Bold, FontStyle.Normal, 1.0.sp);

    companion object {
        fun fromId(id: Int): DashboardFont = values().find { it.id == id } ?: HYPERCAR_NEO_TECH
    }
}

val LocalDashboardTheme = compositionLocalOf { ThemeManager.themes[0] }
val LocalIsBoldText = compositionLocalOf { true }
val LocalButtonScale = compositionLocalOf { 1.0f }
val LocalEqualizerStyle = compositionLocalOf { EqualizerStyle.CLASSIC_BARS }
val LocalDashboardFont = compositionLocalOf { DashboardFont.HYPERCAR_NEO_TECH }

// =========================================================================
// 🚀 GESTOR DE LAUNCHER RESISTENTE
// =========================================================================
object LauncherManager {
    private const val TAG = "LAUNCHER_DEBUG"

    private fun Context.findActivity(): Activity? {
        var currentContext = this
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) return currentContext
            currentContext = currentContext.baseContext
        }
        return null
    }

    fun isDefaultLauncher(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                }
            }
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            val currentPackage = resolveInfo?.activityInfo?.packageName
            currentPackage != null && currentPackage == context.packageName
        } catch (e: Exception) {
            false
        }
    }

    fun forceAndroidChooser(context: Context) {
        val activity = context.findActivity()

        try {
            val homeSettingsIntent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeSettingsIntent)
            Log.i(TAG, "ACTION_HOME_SETTINGS iniciado")
            return
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_HOME_SETTINGS no disponible: ${e.message}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val defaultAppsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(defaultAppsIntent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Default apps settings fallo: ${e.message}")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    if (activity != null) {
                        activity.startActivity(intent)
                    } else {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "RoleManager fallo: ${e.message}")
            }
        }

        try {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appDetailsIntent)
        } catch (e: Exception) {
            val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
        }
    }
}

// =========================================================================
// 🎛️ GESTOR PRINCIPAL DE TEMAS (40 TEMAS COMPLETOS)
// =========================================================================
object ThemeManager {
    private const val PREFS_NAME = "dashboard_theme_prefs"
    private const val CUSTOM_THEMES_KEY = "custom_saved_themes_json"

    val themes = listOf(
        DashboardTheme(0, "OLED Clásico", Color(0xFF000000), Color(0xFF0B0D14), Color(0xFF1B2234), Color(0xFF00E5FF), Color(0xFFE040FB), Color(0xFFFF9100)),
        DashboardTheme(1, "Ferrari Corsa Sport", Color(0xFF080102), Color(0xFF140407), Color(0xFF7A0C14), Color(0xFFFF1744), Color(0xFFFFFFFF), Color(0xFFFFD600)),
        DashboardTheme(2, "Porsche GT3 Acid Green", Color(0xFF020904), Color(0xFF07170B), Color(0xFF135A25), Color(0xFF00FF66), Color(0xFFB2FF59), Color(0xFFFF3D00)),
        DashboardTheme(3, "Lamborghini Giallo", Color(0xFF080701), Color(0xFF141203), Color(0xFF5E500A), Color(0xFFFFEA00), Color(0xFFFFFFFF), Color(0xFF00E5FF)),
        DashboardTheme(4, "McLaren Papaya F1", Color(0xFF090401), Color(0xFF170903), Color(0xFF8A3300), Color(0xFFFF6D00), Color(0xFFFFF3E0), Color(0xFF00F2FE)),
        DashboardTheme(5, "BMW M Power", Color(0xFF000000), Color(0xFF070B14), Color(0xFF162544), Color(0xFF2979FF), Color(0xFF90CAF9), Color(0xFFFF1744)),
        DashboardTheme(6, "Aston Martin Racing", Color(0xFF010805), Color(0xFF05140D), Color(0xFF004D25), Color(0xFF00E676), Color(0xFFCCFF00), Color(0xFFE0E0E0)),
        DashboardTheme(7, "Mercedes-AMG Solar", Color(0xFF080501), Color(0xFF140D03), Color(0xFF6E430A), Color(0xFFFFAB00), Color(0xFFFFFFFF), Color(0xFFFF3D00)),
        DashboardTheme(8, "Bugatti Chiron Blue", Color(0xFF010612), Color(0xFF050E24), Color(0xFF0D2C6B), Color(0xFF0080FF), Color(0xFF80D8FF), Color(0xFFFF2A4B)),
        DashboardTheme(9, "Tokyo Drift JDM", Color(0xFF08010C), Color(0xFF14031E), Color(0xFF660E8A), Color(0xFFFF007F), Color(0xFFE040FB), Color(0xFFFFEA00)),
        DashboardTheme(10, "Cyberpunk 2077", Color(0xFF020810), Color(0xFF051224), Color(0xFF00607A), Color(0xFF00F5FF), Color(0xFFFF006E), Color(0xFFFFD700)),
        DashboardTheme(11, "Audi Moonlight Soft", Color(0xFF060709), Color(0xFF0F1116), Color(0xFF282E3D), Color(0xFFE2E8F0), Color(0xFF90CAF9), Color(0xFFFFB74D)),
        DashboardTheme(12, "Mercedes Ambient Blue", Color(0xFF020710), Color(0xFF071020), Color(0xFF1C3150), Color(0xFF64B5F6), Color(0xFFBBDEFB), Color(0xFFFFD180)),
        DashboardTheme(13, "Menta Salvia Nórdica", Color(0xFF020906), Color(0xFF07150F), Color(0xFF1E4234), Color(0xFF48CAE4), Color(0xFF80CBC4), Color(0xFFFFAB91)),
        DashboardTheme(14, "Lavanda Nocturna", Color(0xFF06040C), Color(0xFF100B1C), Color(0xFF382A54), Color(0xFFB388FF), Color(0xFFE1BEE7), Color(0xFFFFE082)),
        DashboardTheme(15, "Turquesa Brisa Marina", Color(0xFF01080C), Color(0xFF04141E), Color(0xFF1B4054), Color(0xFF00E5FF), Color(0xFF80DEEA), Color(0xFFFF8A80)),
        DashboardTheme(16, "Sunset Atardecer", Color(0xFF08030A), Color(0xFF140819), Color(0xFF4A1E46), Color(0xFFFF80AB), Color(0xFFF8BBD0), Color(0xFFFFB74D)),
        DashboardTheme(17, "Matcha Lounge Zen", Color(0xFF040803), Color(0xFF0C160B), Color(0xFF2D4428), Color(0xFFAED581), Color(0xFFDCEDC8), Color(0xFFFFB74D)),
        DashboardTheme(18, "Océano Profundo", Color(0xFF01060E), Color(0xFF041021), Color(0xFF123458), Color(0xFF00B0FF), Color(0xFFB3E5FC), Color(0xFFFFCC80)),
        DashboardTheme(19, "Rosa Pastel & Frost", Color(0xFF090306), Color(0xFF160910), Color(0xFF4B2239), Color(0xFFF48FB1), Color(0xFFFCE4EC), Color(0xFF80D8FF)),
        DashboardTheme(20, "Niebla Ártica Minimal", Color(0xFF040608), Color(0xFF0A0E13), Color(0xFF222B36), Color(0xFFCFD8DC), Color(0xFFECEFF1), Color(0xFFFFAB40)),
        DashboardTheme(21, "Platino Puro & Cromo", Color(0xFF030508), Color(0xFF090D13), Color(0xFF283444), Color(0xFFE2E8F0), Color(0xFFF8FAFC), Color(0xFF38BDF8)),
        DashboardTheme(22, "Titanio Forjado GT", Color(0xFF040507), Color(0xFF0C0E12), Color(0xFF282D37), Color(0xFF94A3B8), Color(0xFFE2E8F0), Color(0xFFFF7A00)),
        DashboardTheme(23, "Oro Rosa Rolls-Royce", Color(0xFF080304), Color(0xFF14080B), Color(0xFF532832), Color(0xFFF472B6), Color(0xFFFDE8E9), Color(0xFFF59E0B)),
        DashboardTheme(24, "Maybach Champán Gold", Color(0xFF000000), Color(0xFF0F0D05), Color(0xFF483C08), Color(0xFFFFD700), Color(0xFFFFF9C4), Color(0xFFFF2A4B)),
        DashboardTheme(25, "Bronce Forjado Hypercar", Color(0xFF060301), Color(0xFF120904), Color(0xFF4E2A12), Color(0xFFFB923C), Color(0xFFFED7AA), Color(0xFFFF5722)),
        DashboardTheme(26, "Plata Líquida SLR", Color(0xFF000000), Color(0xFF090A0D), Color(0xFF262B36), Color(0xFFCBD5E1), Color(0xFFFFFFFF), Color(0xFFFF1744)),
        DashboardTheme(27, "Cobre Ahumado Luxury", Color(0xFF080402), Color(0xFF140B06), Color(0xFF56301A), Color(0xFFF97316), Color(0xFFFFEDD5), Color(0xFFFFB300)),
        DashboardTheme(28, "Cuarzo Azul Titanium", Color(0xFF01060B), Color(0xFF061019), Color(0xFF183B58), Color(0xFF38BDF8), Color(0xFFE0F2FE), Color(0xFF0284C7)),
        DashboardTheme(29, "Obsidiana Monocromo OLED", Color(0xFF000000), Color(0xFF050505), Color(0xFF222222), Color(0xFFFFFFFF), Color(0xFFE5E5E5), Color(0xFF9E9E9E)),
        DashboardTheme(30, "Cuero Cognac & Nogal", Color(0xFF070402), Color(0xFF130B06), Color(0xFF532E16), Color(0xFFD97724), Color(0xFFFDE8D0), Color(0xFFFFB300)),
        DashboardTheme(31, "Caoba & Cuero Habana", Color(0xFF080202), Color(0xFF160806), Color(0xFF5E2016), Color(0xFFC85A32), Color(0xFFFEE8DF), Color(0xFFFFA000)),
        DashboardTheme(32, "Saddle Brown & Roble", Color(0xFF060403), Color(0xFF120C08), Color(0xFF4A301E), Color(0xFFB45309), Color(0xFFF5E6D3), Color(0xFFFFC107)),
        DashboardTheme(33, "Bentley Beige & Nogal", Color(0xFF050302), Color(0xFF110B07), Color(0xFF4E3622), Color(0xFFD4A373), Color(0xFFFAEDCD), Color(0xFFFFD54F)),
        DashboardTheme(34, "Cuero Burdeos & Ébano", Color(0xFF080204), Color(0xFF17060A), Color(0xFF5E1724), Color(0xFFBE123C), Color(0xFFFCE7F3), Color(0xFFFF8A80)),
        DashboardTheme(35, "Terracota Ferrari GT", Color(0xFF060505), Color(0xFF140D0B), Color(0xFF54291F), Color(0xFFEA580C), Color(0xFFEDE9FE), Color(0xFFFF9E80)),
        DashboardTheme(36, "Moca Espresso & Zebrano", Color(0xFF060302), Color(0xFF120A07), Color(0xFF452417), Color(0xFFA16207), Color(0xFFFEF3C7), Color(0xFFFFB74D)),
        DashboardTheme(37, "Alcántara & Costura Oro", Color(0xFF040404), Color(0xFF0E0E10), Color(0xFF333338), Color(0xFFEAB308), Color(0xFFE2E8F0), Color(0xFFFFD600)),
        DashboardTheme(38, "Yachting & Madera Teca", Color(0xFF020509), Color(0xFF070F1A), Color(0xFF422818), Color(0xFFD97706), Color(0xFFE0F2FE), Color(0xFF38BDF8)),
        DashboardTheme(39, "Jaguar Vintage Tobacco", Color(0xFF060402), Color(0xFF130D08), Color(0xFF4B321C), Color(0xFF9A3412), Color(0xFFFFFBEB), Color(0xFFFFAB00))
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

    fun getSavedFloatingSpeedometerEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("floating_speedometer_enabled", true)

    fun saveFloatingSpeedometerEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("floating_speedometer_enabled", enabled).apply()
    }

    fun getSavedFloatingMediaEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("floating_media_enabled", true)

    fun saveFloatingMediaEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("floating_media_enabled", enabled).apply()
    }
}

// =========================================================================
// 🎛️ ACORDEÓN EXPANDIBLE DE ALTO IMPACTO PARA VEHÍCULO
// =========================================================================
@Composable
fun CarAccordionCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    accentColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val rotationAngle by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "accordion_arrow")

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10131C).copy(alpha = 0.95f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, if (expanded) accentColor else Color(0xFF232838)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.15f))
                            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (expanded) accentColor.copy(alpha = 0.2f) else Color(0x22FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = if (expanded) accentColor else Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(rotationAngle)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF22283A), thickness = 1.dp)
                    content()
                }
            }
        }
    }
}

// =========================================================================
// 🎨 SELECTOR DE COLOR 2D EXPANDIDO
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
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .border(1.5.dp, Color(0xFF323A52), RoundedCornerShape(14.dp))
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

                drawCircle(color = Color.Black, radius = 10.dp.toPx(), center = Offset(posX, posY), style = Stroke(width = 3.dp.toPx()))
                drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(posX, posY), style = Stroke(width = 2.dp.toPx()))
            }
        }

        val currentColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))
        Box(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(currentColor)
                .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
        )

        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .border(1.5.dp, Color(0xFF323A52), RoundedCornerShape(14.dp))
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
                drawCircle(color = Color.Black, radius = 9.dp.toPx(), center = Offset(w / 2f, handleY))
                drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(w / 2f, handleY))
            }
        }
    }
}

// =========================================================================
// 🎛️ MODAL PRINCIPAL: ESTUDIO DE PERSONALIZACIÓN AUTOMOTRIZ (FULLSCREEN)
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
    val coroutineScope = rememberCoroutineScope()

    var localTheme by remember { mutableStateOf(currentTheme) }
    var localTextScale by remember { mutableFloatStateOf(currentTextScale) }
    var localButtonScale by remember { mutableFloatStateOf(currentButtonScale) }
    var localIsBold by remember { mutableStateOf(currentIsBold) }
    var localFont by remember { mutableStateOf(currentFont) }
    var localEqStyle by remember { mutableStateOf(currentEqualizerStyle) }

    var isAutoRotateEq by remember { mutableStateOf(ThemeManager.getSavedAutoRotateEqEnabled(context)) }
    var autoRotateEqInterval by remember { mutableIntStateOf(ThemeManager.getSavedAutoRotateEqInterval(context)) }

    var isFloatingSpeedometerEnabled by remember { mutableStateOf(ThemeManager.getSavedFloatingSpeedometerEnabled(context)) }
    var isFloatingMediaEnabled by remember { mutableStateOf(ThemeManager.getSavedFloatingMediaEnabled(context)) }

    var isDownloadingApk by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    var allThemesList by remember { mutableStateOf(ThemeManager.getAllThemes(context)) }
    var showLauncherListModal by remember { mutableStateOf(false) }
    var showCreateThemeDialog by remember { mutableStateOf(false) }
    var isDefaultLauncher by remember { mutableStateOf(LauncherManager.isDefaultLauncher(context)) }

    // Control de acordeones abiertos
    var accordionThemesExpanded by remember { mutableStateOf(false) }
    var accordionFontsExpanded by remember { mutableStateOf(false) }
    var accordionFloatingExpanded by remember { mutableStateOf(false) }
    var accordionBrightnessExpanded by remember { mutableStateOf(false) }
    var accordionEqExpanded by remember { mutableStateOf(false) }

    fun applyAllChangesAndClose() {
        ThemeManager.saveTheme(context, localTheme.id)
        ThemeManager.saveTextScale(context, localTextScale)
        ThemeManager.saveButtonScale(context, localButtonScale)
        ThemeManager.saveIsBold(context, localIsBold)
        ThemeManager.saveFont(context, localFont)
        ThemeManager.saveEqualizerStyle(context, localEqStyle)
        ThemeManager.saveAutoRotateEqEnabled(context, isAutoRotateEq)
        ThemeManager.saveAutoRotateEqInterval(context, autoRotateEqInterval)
        ThemeManager.saveFloatingSpeedometerEnabled(context, isFloatingSpeedometerEnabled)
        ThemeManager.saveFloatingMediaEnabled(context, isFloatingMediaEnabled)

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
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Interactive3DBackground(
            theme = localTheme,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                // =========================================================================
                // 🔝 BARRA SUPERIOR HUD PROFESIONAL (ESTILO TABLET COCKPIT)
                // =========================================================================
                Surface(
                    color = Color(0xFF090B12).copy(alpha = 0.90f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.2.dp, localTheme.accentCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(localTheme.accentCyan.copy(alpha = 0.2f))
                                    .border(1.5.dp, localTheme.accentCyan, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = localTheme.accentCyan, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "ESTUDIO DE PERSONALIZACIÓN Y SISTEMA",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = localFont.fontFamily,
                                    letterSpacing = 1.2.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Tema Activo: ${localTheme.name.uppercase()} • Fuente: ${localFont.displayName}",
                                    fontSize = 10.sp,
                                    color = localTheme.accentCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { applyAllChangesAndClose() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = localTheme.accentCyan,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "APLICAR CAMBIOS",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
                }

                // =========================================================================
                // 📊 CUERPO PRINCIPAL: 2 COLUMNAS SIN ESPACIOS PERDIDOS
                // =========================================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 👈 COLUMNA IZQUIERDA: SIMULADOR DE INSTRUMENTOS HUD GIGANTE + SISTEMA
                    Surface(
                        color = localTheme.cardBackground.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1.05f)
                            .fillMaxHeight()
                            .border(1.2.dp, localTheme.cardBorder, RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(leftScrollState)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // 🏁 SIMULADOR TELEMETRÍA GIGANTE EN TIEMPO REAL
                            Surface(
                                color = localTheme.dashBackground,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(2.dp, localTheme.accentCyan),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                listOf(localTheme.accentCyan.copy(alpha = 0.25f), Color.Transparent),
                                                center = Offset(size.width * 0.5f, size.height * 0.5f),
                                                radius = size.width * 0.45f
                                            )
                                        )
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(localTheme.accentCyan)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "TELEMETRÍA EN VIVO",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = localTheme.accentCyan,
                                                    letterSpacing = 1.sp
                                                )
                                            }

                                            Surface(
                                                color = localTheme.accentOrange.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, localTheme.accentOrange.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = "GEAR [ D ] • SPORT",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = localTheme.accentOrange,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        // VELOCÍMETRO CENTRAL GIGANTE
                                        Row(
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "120",
                                                color = localTheme.numberColor,
                                                fontSize = 68.sp,
                                                fontFamily = localFont.fontFamily,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = (-2).sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                                Text(
                                                    text = "KM/H",
                                                    color = localTheme.textColor,
                                                    fontSize = 18.sp,
                                                    fontFamily = localFont.fontFamily,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Text(
                                                    text = "VELOCIDAD",
                                                    color = Color.Gray,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // TARJETA DE REPRODUCCIÓN MULTIMEDIA SIMULADA
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, localTheme.accentCyan.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = localTheme.accentCyan, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Car Audio • High Fidelity Sound",
                                                        color = localTheme.textColor,
                                                        fontSize = 10.sp,
                                                        fontFamily = localFont.fontFamily,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Text(
                                                    text = "03:45",
                                                    color = localTheme.numberColor,
                                                    fontSize = 11.sp,
                                                    fontFamily = localFont.fontFamily,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 🚀 TARJETA: LAUNCHER PREDETERMINADO RESISTENTE
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
                                border = BorderStroke(1.5.dp, if (isDefaultLauncher) Color(0xFF00E676) else localTheme.accentCyan),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isDefaultLauncher) Color(0xFF00E676).copy(alpha = 0.15f) else localTheme.accentCyan.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Home,
                                                    contentDescription = null,
                                                    tint = if (isDefaultLauncher) Color(0xFF00E676) else localTheme.accentCyan,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text("Launcher del Sistema", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                Text("Establece como interfaz principal", color = Color.Gray, fontSize = 9.sp)
                                            }
                                        }

                                        Surface(
                                            color = if (isDefaultLauncher) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFF5252).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, if (isDefaultLauncher) Color(0xFF00E676) else Color(0xFFFF5252))
                                        ) {
                                            Text(
                                                text = if (isDefaultLauncher) "PREDETERMINADO" else "NO ACTIVO",
                                                color = if (isDefaultLauncher) Color(0xFF00E676) else Color(0xFFFF5252),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                                            containerColor = if (isDefaultLauncher) Color(0xFF262A3B) else Color(0xFF00E676),
                                            contentColor = if (isDefaultLauncher) Color.White else Color.Black
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                    ) {
                                        Icon(
                                            if (isDefaultLauncher) Icons.Default.CheckCircle else Icons.Default.Stars,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isDefaultLauncher) "CONFIGURAR GESTOR LAUNCHER" else "ACTIVAR COMO LAUNCHER DEL CARRO",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }

                            // 🔄 ACTUALIZACIÓN ONLINE DESDE GITHUB (BOTÓN PERMANENTE)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
                                border = BorderStroke(1.5.dp, localTheme.accentCyan.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(localTheme.accentCyan.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudSync,
                                                contentDescription = null,
                                                tint = localTheme.accentCyan,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Actualización de Car Tablet Launcher",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Descarga de APK directo desde GitHub Releases",
                                                color = Color.Gray,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            if (!isDownloadingApk) {
                                                isDownloadingApk = true
                                                downloadProgress = 0f
                                                coroutineScope.launch {
                                                    AppUpdater.downloadAndInstall(
                                                        context = context,
                                                        onProgress = { progress -> downloadProgress = progress },
                                                        onError = { errorMsg ->
                                                            isDownloadingApk = false
                                                            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                                        }
                                                    )
                                                    isDownloadingApk = false
                                                }
                                            }
                                        },
                                        enabled = !isDownloadingApk,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = localTheme.accentCyan,
                                            contentColor = Color.Black,
                                            disabledContainerColor = localTheme.accentCyan.copy(alpha = 0.4f),
                                            disabledContentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isDownloadingApk) Icons.Default.Downloading else Icons.Default.DownloadForOffline,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isDownloadingApk) "DESCARGANDO... ${(downloadProgress * 100).toInt()}%" else "DESCARGAR E INSTALAR ÚLTIMA VERSIÓN",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }

                                    if (isDownloadingApk) {
                                        LinearProgressIndicator(
                                            progress = { downloadProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = localTheme.accentCyan,
                                            trackColor = Color(0xFF262A3B)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 👉 COLUMNA DERECHA: ACORDEONES ORGANIZADOS Y ERGONÓMICOS
                    Surface(
                        color = localTheme.cardBackground.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .border(1.2.dp, localTheme.cardBorder, RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rightScrollState)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // -------------------------------------------------------------
                            // 📂 ACORDEÓN 1: TEMAS AUTOMOTRICES (40 TEMAS + PERSONALIZADO)
                            // -------------------------------------------------------------
                            CarAccordionCard(
                                title = "Colección de Temas (${allThemesList.size})",
                                subtitle = "Estilo activo: ${localTheme.name}",
                                icon = Icons.Default.ColorLens,
                                accentColor = localTheme.accentCyan,
                                expanded = accordionThemesExpanded,
                                onToggle = { accordionThemesExpanded = !accordionThemesExpanded }
                            ) {
                                Button(
                                    onClick = { showCreateThemeDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = localTheme.accentCyan, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                ) {
                                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("MEZCLAR COLORES Y CREAR MI TEMA", fontWeight = FontWeight.Black, fontSize = 11.sp)
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    allThemesList.chunked(2).forEach { rowThemes ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowThemes.forEach { theme ->
                                                val isSelected = theme.id == localTheme.id
                                                Surface(
                                                    color = theme.dashBackground,
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(64.dp)
                                                        .border(
                                                            width = if (isSelected) 2.5.dp else 1.dp,
                                                            color = if (isSelected) localTheme.accentCyan else Color(0xFF282D3D),
                                                            shape = RoundedCornerShape(12.dp)
                                                        )
                                                        .clickable { localTheme = theme }
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(horizontal = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                theme.name,
                                                                color = Color.White,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            if (theme.isCustom) {
                                                                Text("Custom Car Style", color = localTheme.accentCyan, fontSize = 8.sp)
                                                            }
                                                        }

                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(theme.primaryColor))
                                                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(theme.textColor))
                                                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(theme.numberColor))

                                                            if (theme.isCustom) {
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                IconButton(
                                                                    onClick = {
                                                                        ThemeManager.deleteCustomTheme(context, theme.id)
                                                                        allThemesList = ThemeManager.getAllThemes(context)
                                                                    },
                                                                    modifier = Modifier.size(24.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
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

                            // -------------------------------------------------------------
                            // 📂 ACORDEÓN 2: TIPOGRAFÍAS AUTOMOTRICES HUD & ESCALAS
                            // -------------------------------------------------------------
                            CarAccordionCard(
                                title = "Tipografías HUD y Tamaños",
                                subtitle = "Fuente: ${localFont.displayName} (${(localTextScale * 100).toInt()}%)",
                                icon = Icons.Default.TextFields,
                                accentColor = localTheme.accentPurple,
                                expanded = accordionFontsExpanded,
                                onToggle = { accordionFontsExpanded = !accordionFontsExpanded }
                            ) {
                                Text("Tipografía de Lectura Rápida:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    DashboardFont.values().forEach { font ->
                                        val isSelected = font == localFont
                                        Surface(
                                            color = if (isSelected) localTheme.accentPurple.copy(alpha = 0.25f) else Color(0xFF161925),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) localTheme.accentPurple else Color.Transparent
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { localFont = font }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = font.displayName,
                                                        fontFamily = font.fontFamily,
                                                        fontWeight = font.fontWeight,
                                                        fontStyle = font.fontStyle,
                                                        letterSpacing = font.letterSpacing,
                                                        fontSize = 12.sp,
                                                        color = if (isSelected) localTheme.accentPurple else Color.White
                                                    )
                                                    Text(font.subtitle, fontSize = 9.sp, color = Color.Gray)
                                                }
                                                if (isSelected) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = localTheme.accentPurple, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // SLIDER DE TEXTO
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Tamaño Letras del Tablero:", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("${(localTextScale * 100).toInt()}%", color = localTheme.accentPurple, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                    }
                                    Slider(
                                        value = localTextScale,
                                        onValueChange = { localTextScale = it },
                                        valueRange = 1.0f..2.8f,
                                        colors = SliderDefaults.colors(thumbColor = localTheme.accentPurple, activeTrackColor = localTheme.accentPurple)
                                    )
                                }

                                // SLIDER BOTONES MULTIMEDIA
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Tamaño de Botones Multimedia:", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("${(localButtonScale * 100).toInt()}%", color = localTheme.accentCyan, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                    }
                                    Slider(
                                        value = localButtonScale,
                                        onValueChange = { localButtonScale = it },
                                        valueRange = 0.8f..2.5f,
                                        colors = SliderDefaults.colors(thumbColor = localTheme.accentCyan, activeTrackColor = localTheme.accentCyan)
                                    )
                                }
                            }

                            // -------------------------------------------------------------
                            // 📂 ACORDEÓN 3: GADGETS FLOTANTES AL SALIR (MAPS / WAZE)
                            // -------------------------------------------------------------
                            CarAccordionCard(
                                title = "Gadgets Flotantes en Segundo Plano",
                                subtitle = "Velocímetro y Controles de Música",
                                icon = Icons.Default.Layers,
                                accentColor = localTheme.accentOrange,
                                expanded = accordionFloatingExpanded,
                                onToggle = { accordionFloatingExpanded = !accordionFloatingExpanded }
                            ) {
                                Text(
                                    text = "Ventanas flotantes automáticas sobre Waze, Google Maps u otras apps de navegación:",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )

                                // VELOCÍMETRO FLOTANTE
                                Surface(
                                    color = Color(0xFF161925),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Speed, contentDescription = null, tint = localTheme.accentOrange, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text("Velocímetro Flotante", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("Muestra velocidad digital sobre el mapa", color = Color.Gray, fontSize = 9.sp)
                                            }
                                        }
                                        Switch(
                                            checked = isFloatingSpeedometerEnabled,
                                            onCheckedChange = { isEnabled ->
                                                isFloatingSpeedometerEnabled = isEnabled
                                                ThemeManager.saveFloatingSpeedometerEnabled(context, isEnabled)
                                                if (!isEnabled) {
                                                    FloatingSpeedometerService.stop(context)
                                                }
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = localTheme.accentOrange)
                                        )
                                    }
                                }

                                // REPRODUCTOR FLOTANTE
                                Surface(
                                    color = Color(0xFF161925),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = localTheme.accentCyan, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text("Música / Video Flotante", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("Controles rápidos de cambio de pista", color = Color.Gray, fontSize = 9.sp)
                                            }
                                        }
                                        Switch(
                                            checked = isFloatingMediaEnabled,
                                            onCheckedChange = { isEnabled ->
                                                isFloatingMediaEnabled = isEnabled
                                                ThemeManager.saveFloatingMediaEnabled(context, isEnabled)
                                                if (!isEnabled) {
                                                    FloatingMediaService.stop(context)
                                                }
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = localTheme.accentCyan)
                                        )
                                    }
                                }
                            }

                            // -------------------------------------------------------------
                            // 📂 ACORDEÓN 4: BRILLO DÍA / NOCHE AUTOMOTRIZ
                            // -------------------------------------------------------------
                            CarAccordionCard(
                                title = "Control de Iluminación y Brillo",
                                subtitle = if (isAutoBrightness) "Automático Día/Noche Activado" else "Modo Manual",
                                icon = Icons.Default.WbSunny,
                                accentColor = Color(0xFFFFD600),
                                expanded = accordionBrightnessExpanded,
                                onToggle = { accordionBrightnessExpanded = !accordionBrightnessExpanded }
                            ) {
                                Surface(
                                    color = Color(0xFF161925),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Brillo Automático Día/Noche", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("Ajusta la visibilidad según horario solar", color = Color.Gray, fontSize = 9.sp)
                                        }
                                        Switch(
                                            checked = isAutoBrightness,
                                            onCheckedChange = {
                                                isAutoBrightness = it
                                                BrightnessManager.setAutoEnabled(context, it)
                                                BrightnessManager.applyBrightnessToActivity(context)
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFD600))
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("☀️ Nivel Diurno:", color = localTheme.accentOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("${(dayBrightness * 100).toInt()}%", color = localTheme.accentOrange, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                    }
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

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("🌙 Nivel Nocturno:", color = localTheme.accentPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("${(nightBrightness * 100).toInt()}%", color = localTheme.accentPurple, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                    }
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

                            // -------------------------------------------------------------
                            // 📂 ACORDEÓN 5: ECUALIZADOR Y ANIMACIÓN ESPECTRAL
                            // -------------------------------------------------------------
                            CarAccordionCard(
                                title = "Estilo de Ecualizador (${EqualizerStyle.values().size})",
                                subtitle = "Estilo: ${localEqStyle.displayName}",
                                icon = Icons.Default.GraphicEq,
                                accentColor = localTheme.accentCyan,
                                expanded = accordionEqExpanded,
                                onToggle = { accordionEqExpanded = !accordionEqExpanded }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    EqualizerStyle.values().toList().chunked(2).forEach { rowStyles ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            rowStyles.forEach { style ->
                                                val isSelected = style == localEqStyle
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isSelected) localTheme.accentCyan.copy(alpha = 0.25f) else Color(0xFF161925))
                                                        .border(
                                                            width = if (isSelected) 1.5.dp else 1.dp,
                                                            color = if (isSelected) localTheme.accentCyan else Color(0xFF262C3C),
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable { localEqStyle = style }
                                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        style.displayName,
                                                        color = if (isSelected) localTheme.accentCyan else Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    color = Color(0xFF161925),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Shuffle, contentDescription = null, tint = localTheme.accentCyan, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Rotación Aleatoria del Ecualizador", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Switch(
                                                checked = isAutoRotateEq,
                                                onCheckedChange = { isAutoRotateEq = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = localTheme.accentCyan)
                                            )
                                        }

                                        if (isAutoRotateEq) {
                                            Text("Intervalo de cambio automático:", color = Color.Gray, fontSize = 10.sp)
                                            val intervals = listOf(10 to "10s", 30 to "30s", 60 to "1m", 300 to "5m", 600 to "10m")
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                intervals.forEach { (sec, label) ->
                                                    val isSelected = autoRotateEqInterval == sec
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(if (isSelected) localTheme.accentCyan else Color(0xFF262C3C))
                                                            .clickable { autoRotateEqInterval = sec }
                                                            .padding(vertical = 8.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            color = if (isSelected) Color.Black else Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Black
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
// 🎨 MODAL MEZCLADOR 2D PROFESIONAL
// =========================================================================
@Composable
fun CustomColorMixerDialog(
    currentTheme: DashboardTheme,
    onDismiss: () -> Unit,
    onSave: (DashboardTheme) -> Unit
) {
    var themeName by remember { mutableStateOf("Mi Estilo Auto") }
    var selectedColorIndex by remember { mutableIntStateOf(1) }

    var colorPrimario by remember { mutableStateOf(Color(0xFF00E5FF)) }
    var colorLetras by remember { mutableStateOf(Color(0xFFD500F9)) }
    var colorNumeros by remember { mutableStateOf(Color(0xFFFF9100)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E121C),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorPrimario.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = colorPrimario, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("Laboratorio de Colores de Cabina", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = themeName,
                    onValueChange = { themeName = it },
                    label = { Text("Nombre de tu Estilo") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = colorPrimario
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple(1, "1️⃣ Bordes/Iconos", colorPrimario),
                        Triple(2, "2️⃣ Textos", colorLetras),
                        Triple(3, "3️⃣ Números/HUD", colorNumeros)
                    ).forEach { (idx, label, colorVal) ->
                        val isSelected = selectedColorIndex == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) colorVal.copy(alpha = 0.25f) else Color(0xFF1A1F2C))
                                .border(if (isSelected) 2.dp else 1.dp, if (isSelected) colorVal else Color(0xFF293044), RoundedCornerShape(10.dp))
                                .clickable { selectedColorIndex = idx }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(colorVal))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(label, color = if (isSelected) colorVal else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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

                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, colorPrimario),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("MÚSICA:", color = colorLetras, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("03:45", color = colorNumeros, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colorPrimario))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("VELOCIDAD:", color = colorLetras, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("120 KM/H", color = colorNumeros, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
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
                colors = ButtonDefaults.buttonColors(containerColor = colorPrimario, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("GUARDAR Y APLICAR", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.Gray, fontWeight = FontWeight.Bold) }
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
        containerColor = Color(0xFF101420),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Home, contentDescription = null, tint = theme.accentCyan)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Launcher Predeterminado de la Tablet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Selecciona Car Tablet Launcher como launcher de inicio permanente para que no vuelva a abrirse la interfaz antigua del vehículo al presionar Home.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = {
                        LauncherManager.forceAndroidChooser(context)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Stars, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ESTABLECER COMO PRINCIPAL", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    )
}

// =========================================================================
// 🌌 FONDO 3D INTERACTIVO CON AMBIENT NEÓN AUTOMOTRIZ
// =========================================================================
@Composable
fun Interactive3DBackground(
    theme: DashboardTheme,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.dashBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(theme.accentCyan.copy(alpha = 0.35f), theme.accentCyan.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = width * 0.55f
                )
            )

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(theme.accentOrange.copy(alpha = 0.28f), theme.accentOrange.copy(alpha = 0.05f), Color.Transparent),
                    center = Offset(width, height),
                    radius = width * 0.55f
                )
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(theme.accentPurple.copy(alpha = 0.20f), theme.accentPurple.copy(alpha = 0.04f), Color.Transparent),
                    center = Offset(width * 0.50f, height * 0.50f),
                    radius = width * 0.45f
                ),
                center = Offset(width * 0.50f, height * 0.50f),
                radius = width * 0.45f
            )

            val stripeSpacing = 40f
            val stripeColor = theme.accentCyan.copy(alpha = 0.04f)

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
                    colors = listOf(Color.Transparent, theme.dashBackground.copy(alpha = 0.88f)),
                    center = Offset(width / 2f, height / 2f),
                    radius = width * 0.65f
                )
            )
        }
        content()
    }
}

class FakeLauncherActivity : android.app.Activity()