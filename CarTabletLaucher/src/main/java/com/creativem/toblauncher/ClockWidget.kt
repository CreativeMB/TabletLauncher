package com.creativem.toblauncher

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ModernClockWidget() {
    val theme = LocalDashboardTheme.current
    val isBold = LocalIsBoldText.current
    val dashboardFont = LocalDashboardFont.current
    val density = LocalDensity.current

    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }

    // ACTUALIZACIÓN EN TIEMPO REAL CADA SEGUNDO
    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            hours = cal.get(Calendar.HOUR)
            minutes = cal.get(Calendar.MINUTE)
            seconds = cal.get(Calendar.SECOND)
            delay(1000L)
        }
    }

    // =========================================================================
    // 🎨 FONDOS 100% DINÁMICOS BASADOS EN LOS 3 COLORES DEL TEMA
    // =========================================================================
    val dynamicOuterBackground = remember(theme) {
        Brush.radialGradient(
            colors = listOf(
                lerp(theme.cardBackground, theme.primaryColor, 0.35f),
                lerp(theme.dashBackground, theme.textColor, 0.15f),
                lerp(theme.dashBackground, theme.numberColor, 0.08f),
                theme.dashBackground
            ),
            radius = 650f
        )
    }

    val dynamicInnerBackground = remember(theme) {
        Brush.radialGradient(
            colors = listOf(
                lerp(theme.cardBackground, theme.primaryColor, 0.45f),
                lerp(theme.cardBackground, theme.primaryColor, 0.20f),
                lerp(theme.dashBackground, theme.textColor, 0.12f),
                theme.dashBackground
            ),
            radius = 450f
        )
    }

    val dynamicBorderBrush = remember(theme) {
        Brush.sweepGradient(
            listOf(
                theme.primaryColor.copy(alpha = 0.85f),
                theme.textColor.copy(alpha = 0.50f),
                theme.numberColor.copy(alpha = 0.40f),
                theme.primaryColor.copy(alpha = 0.85f)
            )
        )
    }

    // 🟠 PINTURA PARA NÚMEROS DEL DIAL (Adaptada para el tamaño maximizado)
    val numberPaint = remember(theme, isBold, density, dashboardFont) {
        AndroidPaint().apply {
            color = theme.numberColor.toArgb()
            textSize = with(density) { 15.5.sp.toPx() }
            typeface = if (isBold) {
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            } else {
                Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            textAlign = AndroidPaint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(6f, 0f, 0f, theme.numberColor.toArgb())
        }
    }

    // Contenedor que calcula el tamaño máximo disponible para mantener el círculo perfecto
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        val clockSize = min(maxWidth, maxHeight)

        Box(
            modifier = Modifier
                .size(clockSize)
                .shadow(16.dp, CircleShape, spotColor = theme.primaryColor.copy(alpha = 0.30f))
                .clip(CircleShape)
                .background(dynamicOuterBackground)
                .border(2.dp, dynamicBorderBrush, CircleShape)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            // Fondo interior radiante
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(dynamicInnerBackground)
            )

            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f - 2.dp.toPx()

                // =========================================================
                // 🔵 1. RAYOS HUD Y ANILLOS CONCÉNTRICOS
                // =========================================================
                val rayCount = 28
                val innerR = 14.dp.toPx()
                for (i in 0 until rayCount) {
                    val rayAngleDeg = i * (360f / rayCount)
                    val rayAngleRad = Math.toRadians(rayAngleDeg.toDouble())

                    val rayStart = Offset(
                        x = center.x + innerR * cos(rayAngleRad).toFloat(),
                        y = center.y + innerR * sin(rayAngleRad).toFloat()
                    )
                    val rayEnd = Offset(
                        x = center.x + maxRadius * cos(rayAngleRad).toFloat(),
                        y = center.y + maxRadius * sin(rayAngleRad).toFloat()
                    )

                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                theme.primaryColor.copy(alpha = 0.20f),
                                theme.textColor.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            start = rayStart,
                            end = rayEnd
                        ),
                        start = rayStart,
                        end = rayEnd,
                        strokeWidth = 1.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Anillo 1: Exterior continuo
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            theme.primaryColor.copy(alpha = 0.25f),
                            theme.textColor.copy(alpha = 0.15f),
                            theme.numberColor.copy(alpha = 0.10f),
                            theme.primaryColor.copy(alpha = 0.25f)
                        )
                    ),
                    radius = maxRadius,
                    center = center,
                    style = Stroke(width = 1.8.dp.toPx())
                )

                // Anillo 2: Punteado Radar
                drawCircle(
                    color = theme.primaryColor.copy(alpha = 0.55f),
                    radius = maxRadius - 6.dp.toPx(),
                    center = center,
                    style = Stroke(
                        width = 1.4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f)
                    )
                )

                // Anillo 3: Cuadrantes iluminados en 12, 3, 6 y 9
                for (quadrantAngle in listOf(0f, 90f, 180f, 270f)) {
                    drawArc(
                        color = theme.primaryColor,
                        startAngle = quadrantAngle - 12f,
                        sweepAngle = 24f,
                        useCenter = false,
                        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round),
                        size = Size((maxRadius - 2.dp.toPx()) * 2, (maxRadius - 2.dp.toPx()) * 2),
                        topLeft = Offset(center.x - (maxRadius - 2.dp.toPx()), center.y - (maxRadius - 2.dp.toPx()))
                    )
                }

                // Anillo 4: Halo interior
                val innerHaloRadius = maxRadius - 32.dp.toPx()
                drawCircle(
                    color = theme.textColor.copy(alpha = 0.25f),
                    radius = innerHaloRadius,
                    center = center,
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
                    )
                )

                // =========================================================
                // ⏱️ 2. MARCAS (TICKS) Y NÚMEROS
                // =========================================================
                val ticksRadius = maxRadius - 10.dp.toPx()
                for (i in 0..11) {
                    val angleDeg = i * 30f - 90f
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val isMainTick = (i % 3 == 0)
                    val tickLen = if (isMainTick) 9.dp.toPx() else 5.dp.toPx()

                    val startX = center.x + (ticksRadius - tickLen) * cos(angleRad).toFloat()
                    val startY = center.y + (ticksRadius - tickLen) * sin(angleRad).toFloat()
                    val endX = center.x + ticksRadius * cos(angleRad).toFloat()
                    val endY = center.y + ticksRadius * sin(angleRad).toFloat()

                    drawLine(
                        color = if (isMainTick) theme.primaryColor.copy(alpha = 0.95f) else theme.primaryColor.copy(alpha = 0.45f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (isMainTick) 2.8.dp.toPx() else 1.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 🟠 Números del dial (1 al 12)
                val textRadius = ticksRadius - 16.dp.toPx()
                for (i in 1..12) {
                    val angleDeg = i * 30f - 90f
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val x = center.x + textRadius * cos(angleRad).toFloat()
                    val y = center.y + textRadius * sin(angleRad).toFloat() + (numberPaint.textSize * 0.35f)

                    drawContext.canvas.nativeCanvas.drawText(
                        i.toString(),
                        x,
                        y,
                        numberPaint
                    )
                }

                // =========================================================
                // 🎯 3. AGUJAS
                // =========================================================
                // 🔵 HORA (Primario)
                val hourAngleDeg = ((hours % 12) + minutes / 60f) * 30f - 90f
                val hourRad = Math.toRadians(hourAngleDeg.toDouble())
                val hourEnd = Offset(
                    x = center.x + (maxRadius * 0.46f * cos(hourRad)).toFloat(),
                    y = center.y + (maxRadius * 0.46f * sin(hourRad)).toFloat()
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.6f),
                    start = Offset(center.x + 2f, center.y + 2f),
                    end = Offset(hourEnd.x + 2f, hourEnd.y + 2f),
                    strokeWidth = 4.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = theme.primaryColor,
                    start = center,
                    end = hourEnd,
                    strokeWidth = 3.6.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // 🟣 MINUTERO (Color de Texto)
                val minAngleDeg = (minutes + seconds / 60f) * 6f - 90f
                val minRad = Math.toRadians(minAngleDeg.toDouble())
                val minEnd = Offset(
                    x = center.x + (maxRadius * 0.70f * cos(minRad)).toFloat(),
                    y = center.y + (maxRadius * 0.70f * sin(minRad)).toFloat()
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.6f),
                    start = Offset(center.x + 2f, center.y + 2f),
                    end = Offset(minEnd.x + 2f, minEnd.y + 2f),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = theme.textColor,
                    start = center,
                    end = minEnd,
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // 🟠 SEGUNDERO (Color de Números)
                val secAngleDeg = seconds * 6f - 90f
                val secRad = Math.toRadians(secAngleDeg.toDouble())
                val secEnd = Offset(
                    x = center.x + (maxRadius * 0.84f * cos(secRad)).toFloat(),
                    y = center.y + (maxRadius * 0.84f * sin(secRad)).toFloat()
                )
                drawLine(
                    color = theme.numberColor,
                    start = center,
                    end = secEnd,
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Pivote central
                drawCircle(color = Color.Black, radius = 5.5.dp.toPx(), center = center)
                drawCircle(color = theme.primaryColor, radius = 3.8.dp.toPx(), center = center)
                drawCircle(color = Color.White, radius = 2.0.dp.toPx(), center = center)
            }
        }
    }
}