package com.creativem.toblauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// COLORES TEMA AUTOMOTRIZ DEPORTIVO (COMPARTIDOS)
val DashBackground = Color(0xFF090B0E)
val CardBackground = Color(0xFF141820)
val CardBorder = Color(0xFF232B3B)
val AccentCyan = Color(0xFF00F2FE)
val AccentPurple = Color(0xFF4FACFE)
val AccentOrange = Color(0xFFFF5252)

@Composable
fun ModernDashboardCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    headerAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val theme = LocalDashboardTheme.current // OBTIENE EL TEMA DINÁMICO
    val paddingValue = if (title.isNullOrEmpty()) 8.dp else 12.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(theme.cardBackground) // FONDO CAMBIA CON EL TEMA
            .border(1.5.dp, theme.cardBorder, RoundedCornerShape(20.dp)) // BORDE VIVIDO
            .padding(paddingValue)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!title.isNullOrEmpty() && icon != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = theme.accentCyan, // ICONO DE ENCABEZADO CAMBIA
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title,
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    headerAction?.invoke()
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}