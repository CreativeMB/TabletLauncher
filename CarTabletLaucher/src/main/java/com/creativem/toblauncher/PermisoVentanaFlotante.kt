package com.creativem.toblauncher

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

object AppLauncher {

    private const val TAG = "TOBLauncher_Debug"

    // 🟢 VARIABLE REACTIVA: Activa o desactiva el diálogo en Compose
    var showPermissionDialog by mutableStateOf(false)
    private var pendingPackageToLaunch: String = ""

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

        val isMediaActive = videoPlayer.isPlaying ||
                (videoPlayer.exoPlayer?.isPlaying == true) ||
                musicPlayer.isPlaying ||
                radioPlayer.isPlaying ||
                iptvPlayer.isPlaying

        Log.d(TAG, "🎥 [2] ¿Hay reproducción activa?: $isMediaActive")

        if (!isMediaActive) {
            launchNow(activity, packageName)
            return
        }

        // 1️⃣ Si ya tiene permiso, lanza las ventanas flotantes directamente
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(activity)) {
            Log.d(TAG, "📺 [3] Activando ventana flotante multimedia...")
            FloatingMediaService.start(activity)
            FloatingSpeedometerService.start(activity)
            launchNow(activity, packageName)
        }
        // 2️⃣ Si NO tiene permiso, ACTIVA EL DIÁLOGO EN PANTALLA
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.w(TAG, "⚠️ Falta permiso de Ventana Flotante -> Mostrando Diálogo...")
            pendingPackageToLaunch = packageName
            showPermissionDialog = true // 📍 ¡AQUÍ SE ACTIVA!
        } else {
            launchNow(activity, packageName)
        }
    }

    fun openOverlaySettings(context: Context) {
        showPermissionDialog = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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

// =========================================================================
// 🪟 DIÁLOGO DE PERMISO CON LOS 3 COLORES DEL TEMA
// =========================================================================
@Composable
fun OverlayPermissionHost() {
    val context = LocalContext.current
    if (AppLauncher.showPermissionDialog) {
        OverlayPermissionDialog(
            onDismiss = { AppLauncher.showPermissionDialog = false },
            onOpenSettings = { AppLauncher.openOverlaySettings(context) }
        )
    }
}

@Composable
fun OverlayPermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val theme = LocalDashboardTheme.current
    val dashboardFont = LocalDashboardFont.current

    val dynamicDialogBg = remember(theme) {
        Brush.radialGradient(
            colors = listOf(
                lerp(theme.cardBackground, theme.primaryColor, 0.35f),
                lerp(theme.dashBackground, theme.textColor, 0.12f),
                theme.dashBackground
            ),
            radius = 500f
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(dynamicDialogBg)
                .border(
                    1.8.dp,
                    Brush.sweepGradient(
                        listOf(
                            theme.primaryColor,
                            theme.textColor,
                            theme.numberColor,
                            theme.primaryColor
                        )
                    ),
                    RoundedCornerShape(22.dp)
                )
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(theme.primaryColor.copy(alpha = 0.20f))
                        .border(1.5.dp, theme.primaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = theme.primaryColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "REPRODUCTOR EN SEGUNDO PLANO",
                    fontSize = 13.5.sp,
                    fontFamily = dashboardFont.fontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.textColor,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Para continuar viendo tu video o velocímetro mientras navegas en GPS (Waze / Maps):",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("1.", color = theme.numberColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Activa el permiso de 'Mostrar sobre otras apps'.", color = Color.White, fontSize = 10.5.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("2.", color = theme.numberColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Regresa a Car Tablet Launcher.", color = Color.White, fontSize = 10.5.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CANCELAR", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primaryColor,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f).height(42.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ACTIVAR", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}