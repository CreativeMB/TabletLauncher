package com.creativem.toblauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ModernDashboardCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    headerAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val theme = LocalDashboardTheme.current
    val paddingValue = if (title.isNullOrEmpty()) 10.dp else 14.dp

    // 1. CAPA DE ELEVACIÓN Y SOMBRA 3D
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp, // Sombra profunda proyectada
                shape = RoundedCornerShape(24.dp),
                ambientColor = theme.accentCyan, // Resplandor del color del tema
                spotColor = Color.Black
            ),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent
    ) {
        // 2. CAJA DEL GADGET CON VOLUMEN FÍSICO
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Gradiente de curvatura (Más claro arriba, oscuro abajo)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            theme.cardBackground,
                            theme.cardBackground.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
                // Biselado 3D Exterior (Luz arriba a la izquierda, sombra abajo a la derecha)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f), // Reflejo de luz
                            theme.cardBorder.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.8f)  // Sombra
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                // Reflejo de Cristal Interno
                .padding(1.dp)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(23.dp)
                )
                .padding(paddingValue)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ENCABEZADO
                if (!title.isNullOrEmpty() && icon != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = theme.accentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = title,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        headerAction?.invoke()
                    }
                }

                // CONTENIDO
                Box(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
    }
}