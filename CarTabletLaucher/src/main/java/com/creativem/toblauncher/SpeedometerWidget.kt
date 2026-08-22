package com.creativem.toblauncher

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🌈 MEZCLA FLUIDA DE COLOR CONTINUA:
 * Inicia 100% con 'baseNumberColor' (theme.numberColor) a 0 km/h y muta a rojo alerta.
 */
fun getDynamicSpeedAlertColor(speedKmH: Float, baseNumberColor: Color, maxSpeed: Float = 160f): Color {
    val speed = speedKmH.coerceIn(0f, maxSpeed)

    val colorStops = listOf(
        0f   to baseNumberColor,                                     // 0 km/h: Color exacto de NÚMEROS del tema
        20f  to lerp(baseNumberColor, Color(0xFFFFD54F), 0.45f),      // 20 km/h: Gana tinte dorado
        45f  to Color(0xFFFFEA00),                                   // 45 km/h: Amarillo brillante
        75f  to Color(0xFFFF9100),                                   // 75 km/h: Naranja intenso
        100f to Color(0xFFFF3D00),                                   // 100 km/h: Naranja-Rojo vivo
        130f to Color(0xFFFF1744),                                   // 130 km/h: Rojo deportivo
        160f to Color(0xFFFF0033)                                    // 160 km/h: Rojo neón alerta máxima
    )

    for (i in 0 until colorStops.size - 1) {
        val (speedStart, colorStart) = colorStops[i]
        val (speedEnd, colorEnd) = colorStops[i + 1]

        if (speed in speedStart..speedEnd) {
            val fraction = (speed - speedStart) / (speedEnd - speedStart)
            return lerp(colorStart, colorEnd, fraction)
        }
    }

    return colorStops.last().second
}

@Composable
fun ModernSpeedometerWidget(
    speedKmH: Float = 0f,
    bearing: Float = 0f,
    onRequestAppSelection: (slot: Int) -> Unit = {}
) {
    val theme = LocalDashboardTheme.current
    val dashboardFont = LocalDashboardFont.current
    val totalSpeed = 160f

    // 🚀 Suavizado de transición para lecturas de GPS real
    val displaySpeed by animateFloatAsState(
        targetValue = speedKmH,
        animationSpec = tween(durationMillis = 400),
        label = "GPSSpeedSmooth"
    )

    val speedProgress = (displaySpeed / totalSpeed).coerceIn(0f, 1f)

    // 🔥 COLOR DINÁMICO BASADO EN 'theme.numberColor'
    val dynamicSpeedColor = remember(displaySpeed, theme.numberColor) {
        getDynamicSpeedAlertColor(displaySpeed, theme.numberColor, totalSpeed)
    }

    val dynamicOuterBackground = remember(theme) {
        Brush.radialGradient(
            colors = listOf(
                lerp(theme.cardBackground, theme.primaryColor, 0.25f),
                theme.dashBackground
            ),
            radius = 650f
        )
    }

    val dynamicInnerBackground = remember(theme) {
        Brush.radialGradient(
            colors = listOf(
                lerp(theme.cardBackground, theme.primaryColor, 0.35f),
                theme.dashBackground
            ),
            radius = 450f
        )
    }

    val dynamicBorderBrush = remember(dynamicSpeedColor, theme) {
        Brush.sweepGradient(
            listOf(
                dynamicSpeedColor.copy(alpha = 0.7f),
                theme.primaryColor.copy(alpha = 0.4f),
                dynamicSpeedColor.copy(alpha = 0.7f)
            )
        )
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // =========================================================================
        // 1. VELOCÍMETRO ANÁLOGO
        // =========================================================================
        Box(
            modifier = Modifier
                .weight(1.35f)
                .fillMaxHeight()
                .shadow(16.dp, RoundedCornerShape(26.dp), spotColor = dynamicSpeedColor.copy(alpha = 0.45f))
                .clip(RoundedCornerShape(26.dp))
                .background(dynamicOuterBackground)
                .border(1.8.dp, dynamicBorderBrush, RoundedCornerShape(26.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(dynamicInnerBackground)
            )

            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = (size.minDimension / 2f) - 4.dp.toPx()
                val strokeWidthPx = 12.dp.toPx()

                val arcRadius = maxRadius - (strokeWidthPx / 2f)
                val innerArcEdge = arcRadius - (strokeWidthPx / 2f)
                val ticksRadius = innerArcEdge - 2.dp.toPx()
                val textRadius = ticksRadius - 34.dp.toPx()
                val needleLength = innerArcEdge - 14.dp.toPx()

                // Rayos HUD
                val rayCount = 36
                val rayStartRadius = arcRadius + (strokeWidthPx / 2f) + 2.dp.toPx()
                val outerBoxExtent = size.maxDimension

                for (i in 0 until rayCount) {
                    val rayAngleDeg = i * (360f / rayCount)
                    val rayAngleRad = Math.toRadians(rayAngleDeg.toDouble())

                    val rayStart = Offset(
                        x = center.x + rayStartRadius * cos(rayAngleRad).toFloat(),
                        y = center.y + rayStartRadius * sin(rayAngleRad).toFloat()
                    )
                    val rayEnd = Offset(
                        x = center.x + outerBoxExtent * cos(rayAngleRad).toFloat(),
                        y = center.y + outerBoxExtent * sin(rayAngleRad).toFloat()
                    )

                    val currentSpeedAngle = 135f + (270f * speedProgress)
                    val angleDiff = Math.abs(rayAngleDeg - currentSpeedAngle)
                    val isNearNeedle = angleDiff < 30f || (360f - angleDiff) < 30f
                    val rayAlpha = if (isNearNeedle) (0.28f + 0.35f * speedProgress) else 0.10f

                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                (if (isNearNeedle) dynamicSpeedColor else theme.primaryColor).copy(alpha = rayAlpha),
                                Color.Transparent
                            ),
                            start = rayStart,
                            end = rayEnd
                        ),
                        start = rayStart,
                        end = rayEnd,
                        strokeWidth = if (isNearNeedle) 2.0.dp.toPx() else 1.0.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Arco Base
                drawArc(
                    color = Color.White.copy(alpha = 0.08f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                )

                // Arco Dinámico de Progreso
                if (speedProgress > 0f) {
                    drawArc(
                        color = dynamicSpeedColor.copy(alpha = 0.30f),
                        startAngle = 135f,
                        sweepAngle = 270f * speedProgress,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx * 1.6f, cap = StrokeCap.Round),
                        size = Size(arcRadius * 2, arcRadius * 2),
                        topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                    )

                    drawArc(
                        color = dynamicSpeedColor,
                        startAngle = 135f,
                        sweepAngle = 270f * speedProgress,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                        size = Size(arcRadius * 2, arcRadius * 2),
                        topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                    )
                }

                // 🟣 TEXTO "KM / H" EN EL DIAL
                val dialLabelPaint = AndroidPaint().apply {
                    color = theme.textColor.toArgb()
                    textSize = 8.5.sp.toPx()
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText("KM / H", center.x, center.y + (arcRadius * 0.78f), dialLabelPaint)

                // 🟠 NÚMEROS DEL DIAL (0, 20, 40...)
                val textPaintDimmed = AndroidPaint().apply {
                    color = theme.numberColor.copy(alpha = 0.65f).toArgb()
                    textSize = 11.sp.toPx()
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                }

                val textPaintActive = AndroidPaint().apply {
                    color = dynamicSpeedColor.toArgb()
                    textSize = 14.sp.toPx()
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                    setShadowLayer(8f, 0f, 0f, dynamicSpeedColor.toArgb())
                }

                // Ticks marcadores
                for (s in 0..totalSpeed.toInt() step 5) {
                    val angleDeg = 135f + (s.toFloat() / totalSpeed) * 270f
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val isMainTick = (s % 20 == 0)
                    val isMediumTick = (s % 10 == 0 && !isMainTick)

                    val tickLength = when {
                        isMainTick -> 10.dp.toPx()
                        isMediumTick -> 6.dp.toPx()
                        else -> 3.5.dp.toPx()
                    }

                    val startX = center.x + ticksRadius * cos(angleRad).toFloat()
                    val startY = center.y + ticksRadius * sin(angleRad).toFloat()
                    val endX = center.x + (ticksRadius - tickLength) * cos(angleRad).toFloat()
                    val endY = center.y + (ticksRadius - tickLength) * sin(angleRad).toFloat()

                    val isPassed = s <= displaySpeed

                    drawLine(
                        color = if (isPassed) dynamicSpeedColor else theme.numberColor.copy(alpha = 0.35f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (isMainTick) 3.dp.toPx() else 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    if (isMainTick) {
                        val numX = center.x + textRadius * cos(angleRad).toFloat()
                        val numY = center.y + textRadius * sin(angleRad).toFloat() + 4.dp.toPx()
                        drawContext.canvas.nativeCanvas.drawText(
                            s.toString(),
                            numX,
                            numY,
                            if (isPassed) textPaintActive else textPaintDimmed
                        )
                    }
                }

                // Aguja
                val needleAngleRad = Math.toRadians((135f + (270f * speedProgress)).toDouble())
                val needleEnd = Offset(
                    x = center.x + (needleLength * cos(needleAngleRad)).toFloat(),
                    y = center.y + (needleLength * sin(needleAngleRad)).toFloat()
                )

                drawLine(
                    color = Color.Black.copy(0.6f),
                    start = Offset(center.x + 2f, center.y + 2f),
                    end = Offset(needleEnd.x + 2f, needleEnd.y + 2f),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = dynamicSpeedColor,
                    start = center,
                    end = needleEnd,
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawCircle(color = Color.Black, radius = 7.dp.toPx(), center = center)
                drawCircle(color = dynamicSpeedColor, radius = 5.dp.toPx(), center = center)
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = center)
            }
        }

        // =========================================================================
        // 2. VELOCIDAD DIGITAL
        // =========================================================================
        val currentDate = remember {
            val sdf = java.text.SimpleDateFormat("EEEE, dd/MM/yyyy", java.util.Locale.getDefault())
            sdf.format(java.util.Date()).uppercase()
        }

        val speedText = "${displaySpeed.toInt()}"

        val digitalFontSize = when (speedText.length) {
            3 -> 52.sp
            2 -> 74.sp
            else -> 84.sp
        }

        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
                .shadow(16.dp, RoundedCornerShape(26.dp), spotColor = dynamicSpeedColor.copy(alpha = 0.45f))
                .clip(RoundedCornerShape(26.dp))
                .background(dynamicOuterBackground)
                .border(1.8.dp, dynamicBorderBrush, RoundedCornerShape(26.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(dynamicInnerBackground)
            )

            // 🟣 FECHA SUPERIOR
            Text(
                text = currentDate,
                fontSize = 7.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = dashboardFont.fontFamily,
                color = theme.textColor,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp, start = 4.dp, end = 4.dp)
            )

            // 🟠 NÚMERO DIGITAL (CENTRADO EXACTO)
            Text(
                text = speedText,
                fontSize = digitalFontSize,
                fontWeight = FontWeight.Black,
                fontFamily = dashboardFont.fontFamily,
                textAlign = TextAlign.Center,
                letterSpacing = if (speedText.length == 3) (-1.0).sp else (-2.5).sp,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lerp(theme.numberColor, Color.White, 0.40f),
                            dynamicSpeedColor
                        )
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 4.dp)
            )

            // 🟣 KM / H INFERIOR
            Text(
                text = "KM / H",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = dashboardFont.fontFamily,
                color = theme.textColor,
                letterSpacing = 2.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            )
        }
    }
}