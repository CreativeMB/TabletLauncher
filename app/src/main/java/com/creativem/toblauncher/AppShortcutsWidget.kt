package com.creativem.toblauncher

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AppShortcut(val name: String, val icon: ImageVector, val packageName: String, val tintColor: Color)

@Composable
fun ModernAppShortcutsWidget(context: Context) {
    val theme = LocalDashboardTheme.current // TEMA DINÁMICO

    // CADA APLICACIÓN USA UNO DE LOS 3 COLORES DEL TEMA
    val apps = listOf(
        AppShortcut("Maps", Icons.Default.Place, "com.google.android.apps.maps", theme.accentCyan),
        AppShortcut("Spotify", Icons.Default.GraphicEq, "com.spotify.music", theme.accentPurple),
        AppShortcut("YouTube", Icons.Default.SmartDisplay, "com.google.android.youtube", theme.accentOrange),
        AppShortcut("Ajustes", Icons.Default.Settings, "com.android.settings", theme.accentCyan)
    )

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.forEach { app ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) context.startActivity(intent)
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E2634)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(app.icon, contentDescription = app.name, tint = app.tintColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(app.name, color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}