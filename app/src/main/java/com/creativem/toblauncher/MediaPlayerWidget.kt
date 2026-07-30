package com.creativem.toblauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ModernMediaPlayerWidget(isVideoMode: Boolean) {
    val theme = LocalDashboardTheme.current // TEMA DINÁMICO

    if (isVideoMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = theme.accentOrange, modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.height(6.dp))
                Text("Reproductor de Video", color = Color.Gray, fontSize = 12.sp)
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // CARÁTULA CON GRADIENTE ENTRE EL 1ER Y 2DO COLOR DEL TEMA
            Box(
                modifier = Modifier
                    .size(75.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(theme.accentPurple, theme.accentCyan))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Pista de Conducción", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Audio Multimedia", color = Color.Gray, fontSize = 11.sp)

                Spacer(modifier = Modifier.height(6.dp))

                // BARRA DE PROGRESO CON EL 3ER COLOR DEL TEMA
                LinearProgressIndicator(
                    progress = { 0.4f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = theme.accentOrange,
                    trackColor = theme.cardBorder
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {}) { Icon(Icons.Default.SkipPrevious, null, tint = theme.accentOrange) }
                    IconButton(onClick = {}, modifier = Modifier.background(theme.accentCyan, CircleShape)) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                    }
                    IconButton(onClick = {}) { Icon(Icons.Default.SkipNext, null, tint = theme.accentOrange) }
                }
            }
        }
    }
}