package com.creativem.toblauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar

object BrightnessManager {

    private const val PREFS_NAME = "brightness_manager_prefs"
    private const val KEY_AUTO_ENABLED = "auto_brightness_enabled"
    private const val KEY_DAY_BRIGHTNESS = "day_brightness_val"
    private const val KEY_NIGHT_BRIGHTNESS = "night_brightness_val"

    private const val DAY_START_HOUR = 6   // 06:00 AM
    private const val NIGHT_START_HOUR = 18 // 06:00 PM (18:00 HS)

    var isAutoBrightnessEnabled by mutableStateOf(true)
        private set

    var dayBrightnessValue by mutableFloatStateOf(0.9f)
        private set

    var nightBrightnessValue by mutableFloatStateOf(0.15f)
        private set

    // CAPA NEGRA DE FILTRO DE NOCHE PROGRESIVA (0.0f a 0.85f)
    var nightOverlayAlpha by mutableFloatStateOf(0.0f)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isAutoBrightnessEnabled = prefs.getBoolean(KEY_AUTO_ENABLED, true)
        dayBrightnessValue = prefs.getFloat(KEY_DAY_BRIGHTNESS, 0.9f)
        nightBrightnessValue = prefs.getFloat(KEY_NIGHT_BRIGHTNESS, 0.15f)

        recalculateBrightness(context)
    }

    fun isNightTime(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return currentHour < DAY_START_HOUR || currentHour >= NIGHT_START_HOUR
    }

    fun hasWriteSettingsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    fun requestWriteSettingsPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(context)) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun applyBrightnessToActivity(context: Context) {
        recalculateBrightness(context)
    }

    // =========================================================================
    // CURVA PROGRESIVA CONTINUA (SIN SALTOS Y CON OSCURIDAD PROFUNDA AL 85%)
    // =========================================================================
    private fun applyNightBrightnessHybrid(context: Context, targetValue: Float) {
        // 1. El brillo físico de hardware baja suavemente de 20% a 5% (nunca menos de 5% para proteger la pantalla)
        val hwBrightness = targetValue.coerceIn(0.05f, 1.0f)
        applyHardwareBrightness(context, hwBrightness)

        // 2. El filtro oscuro por software se intensifica suavemente milímetro a milímetro del 20% al 0%
        if (targetValue < 0.20f) {
            val factorProgresivo = (0.20f - targetValue) / 0.20f // Va de 0.0 (en 20%) a 1.0 (en 0%)
            nightOverlayAlpha = factorProgresivo * 0.85f // Máxima oscuridad del 85% al llegar a 0%
        } else {
            nightOverlayAlpha = 0.0f
        }
    }

    fun applyHardwareBrightness(context: Context, brightnessFloat: Float) {
        val val255 = (brightnessFloat * 255).toInt().coerceIn(12, 255)

        if (hasWriteSettingsPermission(context)) {
            try {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    val255
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        (context as? Activity)?.let { activity ->
            try {
                val layoutParams = activity.window.attributes
                layoutParams.screenBrightness = brightnessFloat.coerceIn(0.05f, 1.0f)
                activity.window.attributes = layoutParams
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setAutoEnabled(context: Context, enabled: Boolean) {
        isAutoBrightnessEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_ENABLED, enabled).apply()
        recalculateBrightness(context)
    }

    fun setDayBrightness(context: Context, value: Float) {
        dayBrightnessValue = value.coerceIn(0.05f, 1.0f)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_DAY_BRIGHTNESS, dayBrightnessValue).apply()

        recalculateBrightness(context)
    }

    fun setNightBrightness(context: Context, value: Float) {
        nightBrightnessValue = value.coerceIn(0.0f, 1.0f)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_NIGHT_BRIGHTNESS, nightBrightnessValue).apply()

        recalculateBrightness(context)
    }

    private fun recalculateBrightness(context: Context) {
        if (isNightTime() && isAutoBrightnessEnabled) {
            applyNightBrightnessHybrid(context, nightBrightnessValue)
        } else if (isAutoBrightnessEnabled) {
            nightOverlayAlpha = 0.0f
            applyHardwareBrightness(context, dayBrightnessValue)
        } else {
            nightOverlayAlpha = 0.0f
        }
    }
}