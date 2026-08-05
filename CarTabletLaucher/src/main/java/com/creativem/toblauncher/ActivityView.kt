package com.creativem.toblauncher

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

// ==========================================
// SERVICIO DE INTERCEPCIÓN DE INDICACIONES (WAZE)
// ==========================================
class WazeNotificationService : NotificationListenerService() {

    companion object {
        var isServiceConnected by mutableStateOf(false)
        var lastInterceptedPackage by mutableStateOf("Ninguna aún")
        var lastInterceptedTitle by mutableStateOf("")

        var isNavigating by mutableStateOf(false)
        var nextInstruction by mutableStateOf<String?>(null)
        var distanceToTurn by mutableStateOf<String?>(null)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceConnected = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        val packageName = sbn?.packageName ?: return
        lastInterceptedPackage = packageName

        if (packageName == "com.waze") {
            val notification = sbn.notification ?: return

            val remoteTexts = extractTextsFromRemoteViews(notification)
            val extras = notification.extras
            val extraValues = mutableListOf<String>()

            extras?.keySet()?.forEach { key ->
                val value = extras.get(key)
                if (value is CharSequence && !value.toString().startsWith("android.")) {
                    val strVal = value.toString().trim()
                    if (strVal.isNotEmpty() &&
                        strVal.lowercase() != "true" &&
                        strVal.lowercase() != "false" &&
                        !strVal.matches(Regex("^\\d+$"))) {
                        extraValues.add(strVal)
                    }
                }
            }

            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
            val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: ""
            val tickerText = notification.tickerText?.toString()?.trim() ?: ""

            val allRawTexts = (remoteTexts + extraValues + listOf(title, text, subText, tickerText))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            val validInstructions = allRawTexts.filterNot { textItem ->
                val lower = textItem.lowercase(Locale.getDefault())
                lower == "waze" ||
                        lower.contains("en ejecución") ||
                        lower.contains("en ejecucion") ||
                        lower.contains("toca para abrir") ||
                        lower.contains("running")
            }

            if (validInstructions.isNotEmpty()) {
                isNavigating = true
                val distanceCandidate = validInstructions.firstOrNull {
                    it.matches(Regex(".*\\d+\\s*(m|km|ft|mi|min|h).*"))
                }
                val instructionCandidate = validInstructions.firstOrNull { it != distanceCandidate }

                if (distanceCandidate != null && instructionCandidate != null) {
                    distanceToTurn = distanceCandidate
                    nextInstruction = instructionCandidate
                } else {
                    nextInstruction = validInstructions[0]
                    distanceToTurn = if (validInstructions.size > 1) validInstructions[1] else ""
                }
                lastInterceptedTitle = "$nextInstruction | $distanceToTurn"
            } else {
                if (!isNavigating) {
                    isNavigating = true
                    nextInstruction = "Waze en ejecución..."
                    distanceToTurn = "Activo"
                }
            }
        }
    }

    private fun extractTextsFromRemoteViews(notification: Notification): List<String> {
        val extractedTexts = mutableListOf<String>()
        val viewsToInspect = listOfNotNull(
            notification.contentView,
            notification.bigContentView,
            notification.headsUpContentView
        )

        for (remoteViews in viewsToInspect) {
            try {
                val inflatedView = remoteViews.apply(applicationContext, null)
                findTextViews(inflatedView, extractedTexts)
            } catch (e: Exception) {
                android.util.Log.e("WazeDebug", "Error inflando RemoteView: ${e.message}")
            }
        }
        return extractedTexts
    }

    private fun findTextViews(view: View, resultList: MutableList<String>) {
        if (view is TextView && !view.text.isNullOrEmpty()) {
            resultList.add(view.text.toString())
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findTextViews(view.getChildAt(i), resultList)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn?.packageName == "com.waze") {
            isNavigating = false
            nextInstruction = null
            distanceToTurn = null
        }
    }
}

// ==========================================
// COMPOSABLE DEL WIDGET (HUD / NAVEGACIÓN)
// ==========================================
@SuppressLint("MissingPermission")
@Composable
fun NativeAppGaugeWidget(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var hasNotificationPermission by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }

    val isConnected = WazeNotificationService.isServiceConnected
    val isNavigating = WazeNotificationService.isNavigating
    val nextInstruction = WazeNotificationService.nextInstruction
    val distanceToTurn = WazeNotificationService.distanceToTurn

    LaunchedEffect(Unit) {
        hasNotificationPermission = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        if (!hasNotificationPermission) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFFFB300), modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Acceso a Notificaciones Requerido", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Otorga acceso para mostrar las indicaciones en tiempo real.", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }) {
                    Text("Configurar Acceso")
                }
            }
        } else if (!isConnected) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Servicio Desconectado por Android", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Apaga y enciende el interruptor de notificaciones de la app.", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Reiniciar Enlace")
                }
            }
        } else {
            if (isNavigating) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Navigation, null, tint = Color(0xFF007AFF), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("INDICACIONES DE NAVEGACIÓN", color = Color(0xFF007AFF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = nextInstruction ?: "En ruta...",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 26.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = distanceToTurn ?: "",
                            color = Color(0xFF007AFF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Debug: ${WazeNotificationService.lastInterceptedTitle}",
                        color = Color.DarkGray,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Navigation, null, tint = Color(0x33FFFFFF), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("MARQUE SU RUTA EN WAZE", color = Color(0xFF007AFF), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Inicie un viaje en la app para visualizar las indicaciones.", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}