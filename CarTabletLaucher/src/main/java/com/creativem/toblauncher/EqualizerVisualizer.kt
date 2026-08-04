package com.creativem.toblauncher

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// =========================================================================
// 🎨 ENUM CON TODOS LOS ESTILOS Y NOMBRES PARA EL USUARIO
// =========================================================================
enum class EqualizerStyle(val displayName: String) {
    CLASSIC_BARS("Barras Clásicas"),
    MIRROR_BARS("Espejo Central"),
    FLUID_WAVE("Ondas Líquidas Neon"),
    DOT_MATRIX("Matriz LED Retro"),
    NEON_SPECTRUM_LINE("Línea de Espectro Neón"),
    CIRCULAR_RADIAL("Espectro Circular 360°"),
    PARTICLE_RAIN("Polvo de Estrellas Audio"),
    DUAL_RIBBON("Cintas 3D Entrelazadas")
}

// =========================================================================
// 🎛️ REPRODUCTOR / VISUALIZADOR DE ECUALIZADOR PROFESIONAL 60FPS
// =========================================================================
@Composable
fun EqualizerVisualizer(
    isPlaying: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color = primaryColor.copy(alpha = 0.4f),
    style: EqualizerStyle = EqualizerStyle.CLASSIC_BARS,
    modifier: Modifier = Modifier,
    barCount: Int = 26
) {
    // 🔄 MOTOR DE ANIMACIÓN CONTINUO SIN SALTOS NI ENTRECORTES (DELTA TIME)
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameTime = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameTimeNanos ->
                val deltaTime = (frameTimeNanos - lastFrameTime) / 1_000_000_000f
                lastFrameTime = frameTimeNanos
                // Avanza la fase continuamente a velocidad óptima de 60fps
                phase += deltaTime * 2.4f
            }
        }
    }

    // 📉 FÍSICA DE SUBIDA/BAJADA SUAVE CON RESORTE TIPO ESTÉREO
    val activeScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.04f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "activeScale"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (style) {
            // -------------------------------------------------------------
            // 1. BARRAS CLÁSICAS CON GRADIENTE Y CAPA DE RESPLANDOR (GLOW)
            // -------------------------------------------------------------
            EqualizerStyle.CLASSIC_BARS -> {
                val barWidth = w / (barCount * 1.6f)
                val spacing = barWidth * 0.6f
                val startX = (w - (barCount * (barWidth + spacing))) / 2f

                val barGradient = Brush.verticalGradient(
                    colors = listOf(tertiaryColor, secondaryColor, primaryColor)
                )

                for (i in 0 until barCount) {
                    val speed = 0.8f + (i % 5) * 0.25f
                    val sine = sin(phase * speed + i * 0.45f)
                    val normHeight = ((sine + 1f) / 2f) * 0.85f + 0.15f
                    val barH = h * normHeight * activeScale

                    val x = startX + i * (barWidth + spacing)
                    val y = h - barH

                    // Capa 1: Resplandor sutil de fondo (Glow)
                    if (isPlaying) {
                        drawRoundRect(
                            color = primaryColor.copy(alpha = 0.15f),
                            topLeft = Offset(x - 2.dp.toPx(), y - 2.dp.toPx()),
                            size = Size(barWidth + 4.dp.toPx(), barH + 2.dp.toPx()),
                            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                        )
                    }

                    // Capa 2: Barra principal
                    drawRoundRect(
                        brush = barGradient,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                        alpha = if (isPlaying) 0.38f else 0.10f
                    )
                }
            }

            // -------------------------------------------------------------
            // 2. BARRAS EN ESPEJO CENTRAL SIMÉTRICAS
            // -------------------------------------------------------------
            EqualizerStyle.MIRROR_BARS -> {
                val barWidth = w / (barCount * 1.6f)
                val spacing = barWidth * 0.6f
                val startX = (w - (barCount * (barWidth + spacing))) / 2f
                val centerY = h / 2f

                val mirrorGradient = Brush.verticalGradient(
                    colors = listOf(tertiaryColor, secondaryColor, primaryColor, secondaryColor, tertiaryColor)
                )

                for (i in 0 until barCount) {
                    val speed = 0.9f + (i % 4) * 0.3f
                    val sine = sin(phase * speed + i * 0.4f)
                    val normHeight = ((sine + 1f) / 2f) * 0.75f + 0.15f
                    val barH = (h / 2f) * normHeight * activeScale

                    val x = startX + i * (barWidth + spacing)

                    drawRoundRect(
                        brush = mirrorGradient,
                        topLeft = Offset(x, centerY - barH),
                        size = Size(barWidth, barH * 2f),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                        alpha = if (isPlaying) 0.32f else 0.08f
                    )
                }
            }

            // -------------------------------------------------------------
            // 3. ONDAS LÍQUIDAS NEÓN FLUIDAS
            // -------------------------------------------------------------
            EqualizerStyle.FLUID_WAVE -> {
                val path1 = Path().apply { moveTo(0f, h) }
                val path2 = Path().apply { moveTo(0f, h) }

                val points = 35
                val step = w / points.toFloat()

                for (i in 0..points) {
                    val currentX = i * step
                    val sine1 = sin(phase + i * 0.22f)
                    val sine2 = sin(phase * 1.3f + i * 0.3f)

                    val y1 = h - ((h * 0.45f * ((sine1 + 1f) / 2f) + h * 0.08f) * activeScale)
                    val y2 = h - ((h * 0.38f * ((sine2 + 1f) / 2f) + h * 0.04f) * activeScale)

                    path1.lineTo(currentX, y1)
                    path2.lineTo(currentX, y2)
                }

                path1.lineTo(w, h); path1.close()
                path2.lineTo(w, h); path2.close()

                drawPath(
                    path = path1,
                    brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor, tertiaryColor)),
                    alpha = if (isPlaying) 0.30f else 0.08f
                )
                drawPath(
                    path = path2,
                    brush = Brush.horizontalGradient(listOf(tertiaryColor, secondaryColor, primaryColor)),
                    alpha = if (isPlaying) 0.22f else 0.05f
                )
            }

            // -------------------------------------------------------------
            // 4. MATRIZ DE PUNTOS LED RETRO
            // -------------------------------------------------------------
            EqualizerStyle.DOT_MATRIX -> {
                val columns = barCount
                val rows = 12
                val dotSize = (w / (columns * 2.2f)).coerceAtMost(14f)
                val spacingX = dotSize * 0.8f
                val spacingY = dotSize * 0.5f
                val startX = (w - (columns * (dotSize + spacingX))) / 2f

                for (col in 0 until columns) {
                    val speed = 0.85f + (col % 5) * 0.25f
                    val sine = sin(phase * speed + col * 0.4f)
                    val activeRows = ((rows * ((sine + 1f) / 2f)) * activeScale).toInt()

                    val x = startX + col * (dotSize + spacingX)

                    for (row in 0 until rows) {
                        val y = h - (row + 1) * (dotSize + spacingY)
                        val isDotActive = row <= activeRows

                        val dotColor = when {
                            row >= rows - 2 -> tertiaryColor
                            row >= rows - 6 -> secondaryColor
                            else -> primaryColor
                        }

                        drawCircle(
                            color = if (isDotActive) dotColor else Color.White.copy(alpha = 0.03f),
                            radius = dotSize / 2f,
                            center = Offset(x + dotSize / 2f, y + dotSize / 2f),
                            alpha = if (isDotActive) (if (isPlaying) 0.48f else 0.12f) else 0.03f
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // 5. LÍNEA DE ESPECTRO NEÓN (OSCILOSCOPIO SUAVE)
            // -------------------------------------------------------------
            EqualizerStyle.NEON_SPECTRUM_LINE -> {
                val linePath = Path()
                val points = barCount * 2
                val step = w / (points - 1).toFloat()

                for (i in 0 until points) {
                    val speed = 0.9f + (i % 3) * 0.3f
                    val sine = sin(phase * speed + i * 0.3f)
                    val y = (h / 2f) + (sine * (h * 0.32f) * activeScale)
                    val x = i * step

                    if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                }

                // Capa 1: Resplandor (Glow)
                if (isPlaying) {
                    drawPath(
                        path = linePath,
                        brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor, tertiaryColor)),
                        style = Stroke(width = 8.dp.toPx()),
                        alpha = 0.20f
                    )
                }

                // Capa 2: Línea Neón Principal
                drawPath(
                    path = linePath,
                    brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor, tertiaryColor)),
                    style = Stroke(width = 3.5.dp.toPx()),
                    alpha = if (isPlaying) 0.55f else 0.15f
                )
            }

            // -------------------------------------------------------------
            // 6. ESPECTRO CIRCULAR RADIAL 360°
            // -------------------------------------------------------------
            EqualizerStyle.CIRCULAR_RADIAL -> {
                val center = Offset(w / 2f, h / 2f)
                val baseRadius = (w.coerceAtMost(h) / 2f) * 0.32f
                val rayCount = 36

                for (i in 0 until rayCount) {
                    val angle = (i * (2 * PI / rayCount)).toFloat()
                    val speed = 0.85f + (i % 4) * 0.25f
                    val sine = sin(phase * speed + i * 0.45f)
                    val rayLen = (baseRadius * 0.85f * ((sine + 1f) / 2f) + 8f) * activeScale

                    val start = Offset(
                        center.x + baseRadius * cos(angle),
                        center.y + baseRadius * sin(angle)
                    )
                    val end = Offset(
                        center.x + (baseRadius + rayLen) * cos(angle),
                        center.y + (baseRadius + rayLen) * sin(angle)
                    )

                    drawLine(
                        brush = Brush.linearGradient(
                            listOf(primaryColor, secondaryColor, tertiaryColor),
                            start = start, end = end
                        ),
                        start = start,
                        end = end,
                        strokeWidth = 3.dp.toPx(),
                        alpha = if (isPlaying) 0.42f else 0.10f
                    )
                }
            }

            // -------------------------------------------------------------
            // 7. POLVO DE ESTRELLAS / PARTICULAS AUDIO CONTINUAS
            // -------------------------------------------------------------
            EqualizerStyle.PARTICLE_RAIN -> {
                val particleCount = 45
                for (i in 0 until particleCount) {
                    val speed = 0.7f + (i % 5) * 0.25f
                    val posX = (w * ((i * 0.022f + phase * 0.035f * speed) % 1.0f))
                    val sineY = sin(phase * speed + i * 0.8f)
                    val posY = h - (h * 0.82f * ((sineY + 1f) / 2f) * activeScale)
                    val pSize = (3.dp.toPx() + (i % 4) * 2.dp.toPx())

                    val pColor = when (i % 3) {
                        0 -> primaryColor
                        1 -> secondaryColor
                        else -> tertiaryColor
                    }

                    drawCircle(
                        color = pColor,
                        radius = pSize,
                        center = Offset(posX, posY),
                        alpha = if (isPlaying) (0.18f + (i % 5) * 0.05f) else 0.05f
                    )
                }
            }

            // -------------------------------------------------------------
            // 8. CINTAS 3D ENTRELAZADAS SUAVES
            // -------------------------------------------------------------
            EqualizerStyle.DUAL_RIBBON -> {
                val ribbon1 = Path()
                val ribbon2 = Path()
                val points = 25
                val step = w / points.toFloat()

                for (i in 0..points) {
                    val x = i * step
                    val y1 = (h / 2f) + (cos(phase + i * 0.25f) * (h * 0.28f) * activeScale)
                    val y2 = (h / 2f) + (sin(phase * 1.2f + i * 0.3f) * (h * 0.28f) * activeScale)

                    if (i == 0) {
                        ribbon1.moveTo(x, y1)
                        ribbon2.moveTo(x, y2)
                    } else {
                        ribbon1.lineTo(x, y1)
                        ribbon2.lineTo(x, y2)
                    }
                }

                drawPath(
                    path = ribbon1,
                    brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor)),
                    style = Stroke(width = 5.dp.toPx()),
                    alpha = if (isPlaying) 0.38f else 0.10f
                )

                drawPath(
                    path = ribbon2,
                    brush = Brush.horizontalGradient(listOf(secondaryColor, tertiaryColor)),
                    style = Stroke(width = 5.dp.toPx()),
                    alpha = if (isPlaying) 0.38f else 0.10f
                )
            }
        }
    }
}

// =========================================================================
// 🎛️ MODAL PICKER PERFECCIONADO
// =========================================================================
@Composable
fun EqualizerStylePickerModal(
    currentStyle: EqualizerStyle,
    theme: DashboardTheme,
    onDismiss: () -> Unit,
    onStyleSelected: (EqualizerStyle) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141E),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = theme.accentCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Estilo de Ecualizador Visual",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            ) {
                items(EqualizerStyle.values()) { style ->
                    val isSelected = style == currentStyle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) theme.accentCyan.copy(alpha = 0.25f) else Color(0xFF1E1E28))
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) theme.accentCyan else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onStyleSelected(style) }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = style.displayName,
                            color = if (isSelected) theme.accentCyan else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = theme.accentCyan, fontWeight = FontWeight.Bold)
            }
        }
    )
}