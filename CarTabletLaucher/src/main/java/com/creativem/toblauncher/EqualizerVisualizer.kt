package com.creativem.toblauncher

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// =========================================================================
// 🎨 ENUM CON LA COLECCIÓN COMPLETA DE 12 ECUALIZADORES DE ALTA GAMA
// =========================================================================
enum class EqualizerStyle(val displayName: String) {
    CLASSIC_BARS("Barras Neón Pro"),
    MIRROR_BARS("Espejo Central Simétrico"),
    FLUID_WAVE("Ondas Líquidas Cyberpunk"),
    DOT_MATRIX("Matriz LED Digital"),
    NEON_SPECTRUM_LINE("Línea Láser Neón"),
    CIRCULAR_RADIAL("Radar HUD 360°"),
    PARTICLE_RAIN("Polvo de Estrellas"),
    DUAL_RIBBON("Cintas 3D Entrelazadas"),
    TELEMETRY_RPM_BEAM("Haz Telemetría RPM"),
    DIAMOND_CRYSTAL_PULSE("Pulso Diamante 3D"),
    LASER_GRID_SCOPE("Túnel Láser Espacial"),
    PLASMA_VORTEX("Vórtice de Plasma")
}

// =========================================================================
// 🎛️ REPRODUCTOR / VISUALIZADOR DE ECUALIZADOR PROFESIONAL 60FPS
// =========================================================================
@Composable
fun EqualizerVisualizer(
    isPlaying: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color = Color(0xFFFF9100),
    style: EqualizerStyle = EqualizerStyle.CLASSIC_BARS,
    modifier: Modifier = Modifier,
    barCount: Int = 26
) {
    // 🔄 MOTOR DELTA-TIME CONTINUO A 60 FPS (SIN TIRONES)
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameTime = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameTimeNanos ->
                val deltaTime = (frameTimeNanos - lastFrameTime) / 1_000_000_000f
                lastFrameTime = frameTimeNanos
                phase += deltaTime * 2.5f
            }
        }
    }

    // 📈 FÍSICA SUAVE DE ELEVACIÓN CON RESORTE
    val activeScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.05f,
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
            // 1. BARRAS NEÓN PRO CON PICOS FLOTANTES (3 COLORES)
            // -------------------------------------------------------------
            EqualizerStyle.CLASSIC_BARS -> {
                val barWidth = w / (barCount * 1.5f)
                val spacing = barWidth * 0.5f
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

                    // Resplandor de fondo
                    if (isPlaying) {
                        drawRoundRect(
                            color = primaryColor.copy(alpha = 0.18f),
                            topLeft = Offset(x - 2.dp.toPx(), y - 2.dp.toPx()),
                            size = Size(barWidth + 4.dp.toPx(), barH + 2.dp.toPx()),
                            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                        )
                    }

                    // Barra principal
                    drawRoundRect(
                        brush = barGradient,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                        alpha = if (isPlaying) 0.90f else 0.15f
                    )

                    // Pico flotante superior en color terciario
                    if (isPlaying) {
                        val peakY = (y - 4.dp.toPx()).coerceAtLeast(0f)
                        drawCircle(
                            color = tertiaryColor,
                            radius = (barWidth / 2.2f).coerceAtLeast(1.5f),
                            center = Offset(x + barWidth / 2f, peakY),
                            alpha = 0.85f
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // 2. ESPEJO CENTRAL SIMÉTRICO TRI-COLOR
            // -------------------------------------------------------------
            EqualizerStyle.MIRROR_BARS -> {
                val barWidth = w / (barCount * 1.5f)
                val spacing = barWidth * 0.5f
                val startX = (w - (barCount * (barWidth + spacing))) / 2f
                val centerY = h / 2f

                val mirrorGradient = Brush.verticalGradient(
                    colors = listOf(
                        tertiaryColor,
                        secondaryColor,
                        primaryColor,
                        secondaryColor,
                        tertiaryColor
                    )
                )

                for (i in 0 until barCount) {
                    val speed = 0.9f + (i % 4) * 0.3f
                    val sine = sin(phase * speed + i * 0.4f)
                    val normHeight = ((sine + 1f) / 2f) * 0.78f + 0.12f
                    val barH = (h / 2f) * normHeight * activeScale
                    val x = startX + i * (barWidth + spacing)

                    drawRoundRect(
                        brush = mirrorGradient,
                        topLeft = Offset(x, centerY - barH),
                        size = Size(barWidth, barH * 2f),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                        alpha = if (isPlaying) 0.88f else 0.12f
                    )
                }
            }

            // -------------------------------------------------------------
            // 3. ONDAS LÍQUIDAS CYBERPUNK (TRIPLE CAPA)
            // -------------------------------------------------------------
            EqualizerStyle.FLUID_WAVE -> {
                val path1 = Path().apply { moveTo(0f, h) }
                val path2 = Path().apply { moveTo(0f, h) }
                val path3 = Path().apply { moveTo(0f, h) }
                val points = 40
                val step = w / points.toFloat()

                for (i in 0..points) {
                    val cx = i * step
                    val s1 = sin(phase + i * 0.20f)
                    val s2 = sin(phase * 1.3f + i * 0.28f + 1.2f)
                    val s3 = cos(phase * 0.9f + i * 0.35f + 2.1f)

                    val y1 = h - ((h * 0.50f * ((s1 + 1f) / 2f) + h * 0.08f) * activeScale)
                    val y2 = h - ((h * 0.38f * ((s2 + 1f) / 2f) + h * 0.05f) * activeScale)
                    val y3 = h - ((h * 0.26f * ((s3 + 1f) / 2f) + h * 0.02f) * activeScale)

                    path1.lineTo(cx, y1)
                    path2.lineTo(cx, y2)
                    path3.lineTo(cx, y3)
                }

                path1.lineTo(w, h); path1.close()
                path2.lineTo(w, h); path2.close()
                path3.lineTo(w, h); path3.close()

                drawPath(path = path1, brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor)), alpha = if (isPlaying) 0.40f else 0.08f)
                drawPath(path = path2, brush = Brush.horizontalGradient(listOf(secondaryColor, tertiaryColor)), alpha = if (isPlaying) 0.30f else 0.06f)
                drawPath(path = path3, brush = Brush.horizontalGradient(listOf(tertiaryColor, primaryColor)), alpha = if (isPlaying) 0.25f else 0.04f)
            }

            // -------------------------------------------------------------
            // 4. MATRIZ DE PUNTOS LED DIGITAL AUTOMOTRIZ
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
                            alpha = if (isDotActive) (if (isPlaying) 0.88f else 0.15f) else 0.03f
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // 5. LÍNEA LÁSER NEÓN DE ALTA PRECISIÓN
            // -------------------------------------------------------------
            EqualizerStyle.NEON_SPECTRUM_LINE -> {
                val linePath = Path()
                val points = barCount * 2
                val step = w / (points - 1).toFloat()

                for (i in 0 until points) {
                    val speed = 0.95f + (i % 3) * 0.3f
                    val sine = sin(phase * speed + i * 0.32f)
                    val y = (h / 2f) + (sine * (h * 0.36f) * activeScale)
                    val x = i * step
                    if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                }

                if (isPlaying) {
                    drawPath(
                        path = linePath,
                        brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor, tertiaryColor, primaryColor)),
                        style = Stroke(width = 9.dp.toPx()),
                        alpha = 0.28f
                    )
                }

                drawPath(
                    path = linePath,
                    brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor, tertiaryColor, primaryColor)),
                    style = Stroke(width = 3.5.dp.toPx()),
                    alpha = if (isPlaying) 0.95f else 0.20f
                )
            }

            // -------------------------------------------------------------
            // 6. RADAR HUD 360° DE TELEMETRÍA
            // -------------------------------------------------------------
            EqualizerStyle.CIRCULAR_RADIAL -> {
                val center = Offset(w / 2f, h / 2f)
                val baseRadius = (w.coerceAtMost(h) / 2f) * 0.32f
                val rayCount = 40

                for (i in 0 until rayCount) {
                    val angle = (i * (2 * PI / rayCount)).toFloat()
                    val speed = 0.85f + (i % 4) * 0.25f
                    val sine = sin(phase * speed + i * 0.45f)
                    val rayLen = (baseRadius * 0.90f * ((sine + 1f) / 2f) + 8f) * activeScale

                    val start = Offset(center.x + baseRadius * cos(angle), center.y + baseRadius * sin(angle))
                    val end = Offset(center.x + (baseRadius + rayLen) * cos(angle), center.y + (baseRadius + rayLen) * sin(angle))

                    val rayBrush = Brush.linearGradient(
                        colors = listOf(primaryColor, secondaryColor, tertiaryColor),
                        start = start, end = end
                    )

                    drawLine(
                        brush = rayBrush,
                        start = start,
                        end = end,
                        strokeWidth = 3.2.dp.toPx(),
                        alpha = if (isPlaying) 0.88f else 0.15f
                    )
                }
            }

            // -------------------------------------------------------------
            // 7. POLVO DE ESTRELLAS CON DESTELLOS DE 3 COLORES
            // -------------------------------------------------------------
            EqualizerStyle.PARTICLE_RAIN -> {
                val particleCount = 55
                for (i in 0 until particleCount) {
                    val speed = 0.7f + (i % 5) * 0.25f
                    val posX = (w * ((i * 0.018f + phase * 0.032f * speed) % 1.0f))
                    val sineY = sin(phase * speed + i * 0.75f)
                    val posY = h - (h * 0.85f * ((sineY + 1f) / 2f) * activeScale)
                    val pSize = (2.8.dp.toPx() + (i % 4) * 2.dp.toPx())

                    val pColor = when (i % 3) {
                        0 -> primaryColor
                        1 -> secondaryColor
                        else -> tertiaryColor
                    }

                    drawCircle(
                        color = pColor,
                        radius = pSize,
                        center = Offset(posX, posY),
                        alpha = if (isPlaying) (0.35f + (i % 5) * 0.12f) else 0.06f
                    )
                }
            }

            // -------------------------------------------------------------
            // 8. CINTAS 3D ENTRELAZADAS DE ALTA VELOCIDAD
            // -------------------------------------------------------------
            EqualizerStyle.DUAL_RIBBON -> {
                val ribbon1 = Path()
                val ribbon2 = Path()
                val points = 30
                val step = w / points.toFloat()

                for (i in 0..points) {
                    val x = i * step
                    val y1 = (h / 2f) + (cos(phase + i * 0.22f) * (h * 0.32f) * activeScale)
                    val y2 = (h / 2f) + (sin(phase * 1.2f + i * 0.28f) * (h * 0.32f) * activeScale)

                    if (i == 0) { ribbon1.moveTo(x, y1); ribbon2.moveTo(x, y2) }
                    else { ribbon1.lineTo(x, y1); ribbon2.lineTo(x, y2) }
                }

                drawPath(path = ribbon1, brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor, tertiaryColor)), style = Stroke(width = 5.5.dp.toPx()), alpha = if (isPlaying) 0.85f else 0.15f)
                drawPath(path = ribbon2, brush = Brush.horizontalGradient(listOf(tertiaryColor, secondaryColor, primaryColor)), style = Stroke(width = 5.5.dp.toPx()), alpha = if (isPlaying) 0.85f else 0.15f)
            }

            // -------------------------------------------------------------
            // 9. HAZ TELEMETRÍA RPM (TACÓMETRO DE CARRERAS)
            // -------------------------------------------------------------
            EqualizerStyle.TELEMETRY_RPM_BEAM -> {
                val barWidth = w / (barCount * 1.35f)
                val spacing = barWidth * 0.35f
                val startX = (w - (barCount * (barWidth + spacing))) / 2f

                for (i in 0 until barCount) {
                    val sine = sin(phase * 1.6f + i * 0.32f)
                    val normHeight = ((sine + 1f) / 2f) * 0.90f + 0.10f
                    val barH = h * normHeight * activeScale
                    val x = startX + i * (barWidth + spacing)
                    val y = h - barH

                    val color = when {
                        i >= barCount * 0.75f -> tertiaryColor   // Zona roja / Alta velocidad
                        i >= barCount * 0.45f -> secondaryColor  // Zona media
                        else -> primaryColor                     // Zona base
                    }

                    drawRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH),
                        alpha = if (isPlaying) 0.92f else 0.18f
                    )
                }
            }

            // -------------------------------------------------------------
            // 10. PULSO DIAMANTE 3D REFLECTIVO
            // -------------------------------------------------------------
            EqualizerStyle.DIAMOND_CRYSTAL_PULSE -> {
                val diamonds = 12
                val spacing = w / diamonds.toFloat()
                for (i in 0 until diamonds) {
                    val sine = sin(phase * 1.3f + i * 0.55f)
                    val sizeD = (22.dp.toPx() * ((sine + 1f) / 2f) * activeScale).coerceAtLeast(5f)
                    val cx = (i + 0.5f) * spacing
                    val cy = h / 2f

                    val diamondPath = Path().apply {
                        moveTo(cx, cy - sizeD)
                        lineTo(cx + sizeD, cy)
                        lineTo(cx, cy + sizeD)
                        lineTo(cx - sizeD, cy)
                        close()
                    }

                    val color = when (i % 3) {
                        0 -> primaryColor
                        1 -> secondaryColor
                        else -> tertiaryColor
                    }

                    drawPath(path = diamondPath, color = color, alpha = if (isPlaying) 0.75f else 0.12f)
                    drawPath(path = diamondPath, color = Color.White, style = Stroke(1.5.dp.toPx()), alpha = if (isPlaying) 0.90f else 0.20f)
                }
            }

            // -------------------------------------------------------------
            // 11. TÚNEL LÁSER ESPACIAL EN PERSPECTIVA
            // -------------------------------------------------------------
            EqualizerStyle.LASER_GRID_SCOPE -> {
                val lines = 16
                val stepX = w / lines.toFloat()
                for (i in 0..lines) {
                    val x = i * stepX
                    val sine = sin(phase * 1.4f + i * 0.4f)
                    val offsetH = (h * 0.45f * ((sine + 1f) / 2f) * activeScale)

                    val color = when (i % 3) {
                        0 -> primaryColor
                        1 -> secondaryColor
                        else -> tertiaryColor
                    }

                    drawLine(
                        color = color,
                        start = Offset(x, (h / 2f) - offsetH),
                        end = Offset(x, (h / 2f) + offsetH),
                        strokeWidth = 3.dp.toPx(),
                        alpha = if (isPlaying) 0.85f else 0.15f
                    )
                }
            }

            // -------------------------------------------------------------
            // 12. VÓRTICE DE PLASMA ORBITAL
            // -------------------------------------------------------------
            EqualizerStyle.PLASMA_VORTEX -> {
                val rings = 6
                val center = Offset(w / 2f, h / 2f)
                for (i in 1..rings) {
                    val sine = sin(phase * 1.5f + i * 0.8f)
                    val r = (i * (h / (rings * 2.2f)) * ((sine + 1.2f) / 2.2f) * activeScale).coerceAtLeast(4f)

                    val color = when (i % 3) {
                        0 -> primaryColor
                        1 -> secondaryColor
                        else -> tertiaryColor
                    }

                    drawCircle(
                        color = color,
                        radius = r,
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx()),
                        alpha = if (isPlaying) (0.75f - i * 0.08f).coerceAtLeast(0.2f) else 0.10f
                    )
                }
            }
        }
    }
}