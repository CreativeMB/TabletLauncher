package com.creativem.toblauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val paddingValue = if (title.isNullOrEmpty()) 6.dp else 10.dp

    // 🌌 CONTENEDOR FLOTANTE 3D CON DESVANECIMIENTO PERIMETRAL
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
    ) {
        // 🎨 CAPA DE COLOR AMBIENTAL: PRIMER COLOR FUERTE + DIFUMINADOS SECUNDARIOS
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. BASE DE COLOR 1 PREDOMINANTE (Fuerte en el centro, desvanece a los bordes)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,                                          // 0% Arriba
                        theme.accentCyan.copy(alpha = 0.28f),                       // Color 1 Fuerte
                        theme.cardBackground.copy(alpha = 0.90f),                   // Profundidad oscura
                        theme.accentCyan.copy(alpha = 0.18f),
                        Color.Transparent                                           // 0% Abajo
                    ),
                    startY = 0f,
                    endY = h
                )
            )

            // 2. DOMO CENTRAL DEL 1ER COLOR (Luz principal de alto impacto)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        theme.accentCyan.copy(alpha = 0.42f),                       // Núcleo brillante
                        theme.accentCyan.copy(alpha = 0.20f),
                        theme.accentCyan.copy(alpha = 0.05f),
                        Color.Transparent                                           // Se desvanece antes del borde
                    ),
                    center = Offset(w * 0.45f, h * 0.35f),
                    radius = w * 0.58f
                ),
                center = Offset(w * 0.45f, h * 0.35f),
                radius = w * 0.58f
            )

            // 3. DIFUMINADO DEL 2DO COLOR (Púrpura ambiental hacia la derecha)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        theme.accentPurple.copy(alpha = 0.28f),
                        theme.accentPurple.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.85f, h * 0.55f),
                    radius = w * 0.50f
                ),
                center = Offset(w * 0.85f, h * 0.55f),
                radius = w * 0.50f
            )

            // 4. DESTELLO DEL 3ER COLOR (Naranja deportivo difuminado en la base)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        theme.accentOrange.copy(alpha = 0.22f),
                        theme.accentOrange.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.70f, h * 0.90f),
                    radius = w * 0.45f
                ),
                center = Offset(w * 0.70f, h * 0.90f),
                radius = w * 0.45f
            )

            // 5. VIÑETA LATERAL (Garantiza que izquierda y derecha también desaparezcan a 0%)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    startX = 0f,
                    endX = w
                )
            )

            // 6. FILAMENTO DE LUZ SUPERIOR (Resplandor fino flotante que se apaga a los lados)
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        theme.accentCyan.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.90f),                            // Destello blanco central
                        theme.accentPurple.copy(alpha = 0.75f),
                        Color.Transparent
                    )
                ),
                start = Offset(w * 0.12f, 1f),
                end = Offset(w * 0.88f, 1f),
                strokeWidth = 2.dp.toPx()
            )
        }

        // 📦 ESTRUCTURA INTERNA DEL GADGET
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValue)
        ) {
            // ENCABEZADO MINIMALISTA FLOTANTE
            if (!title.isNullOrEmpty() && icon != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = theme.accentCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }

                    headerAction?.invoke()
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // CONTENIDO DEL GADGET (MAPA, MÚSICA, VELOCÍMETRO, ETC.)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
            ) {
                content()
            }
        }
    }
}