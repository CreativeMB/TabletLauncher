package com.creativem.toblauncher

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object AppLauncher {

    private const val TAG = "TOBLauncher_Debug"

    fun launchKeepingVideo(context: Context, packageName: String) {
        Log.d(TAG, "📲 [1] Iniciando launchKeepingVideo para: $packageName")

        if (packageName.isEmpty()) return

        val activity = context.findActivity()
        if (activity == null) {
            launchNow(context, packageName)
            return
        }

        val videoPlayer = SmartVideoPlayer.getInstance(activity)
        val musicPlayer = SmartMusicPlayer.getInstance(activity)
        val radioPlayer = SmartRadioManager.getInstance(activity)
        val iptvPlayer = SmartIptvPlayer.getInstance(activity)

        // 🟢 COMPROBAR SI CUALQUIERA DE LOS 4 REPRODUCTORES ESTÁ REPRODUCIENDO
        val isMediaActive = videoPlayer.isPlaying ||
                (videoPlayer.exoPlayer?.isPlaying == true) ||
                musicPlayer.isPlaying ||
                radioPlayer.isPlaying ||
                iptvPlayer.isPlaying

        Log.d(TAG, "🎥 [2] ¿Hay reproducción activa en algún widget?: $isMediaActive")

        if (!isMediaActive) {
            launchNow(activity, packageName)
            return
        }

        // Si tenemos permiso de ventana flotante en la radio, activamos el recuadro sobre Waze
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(activity)) {
            Log.d(TAG, "📺 [3] Activando ventana flotante multimedia...")
            FloatingMediaService.start(activity)
            FloatingSpeedometerService.start(activity)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.w(TAG, "⚠️ Falta el permiso de Ventana Flotante. Solicitándolo...")
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
                activity.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        Log.d(TAG, "🚀 [4] Lanzando aplicación: $packageName")
        launchNow(activity, packageName)
    }

    fun launchNow(context: Context, packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                Log.e(TAG, "❌ No se encontró la app para $packageName")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
