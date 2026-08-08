package com.creativem.toblauncher

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ModernSpeedometerWidget(
    speedKmH: Float,
    bearing: Float = 0f,
    onRequestAppSelection: (slot: Int) -> Unit = {}
) {
    val theme = LocalDashboardTheme.current
    val isBold = LocalIsBoldText.current
    val activeFontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Normal

    val animatedSpeed by animateFloatAsState(
        targetValue = speedKmH,
        animationSpec = tween(durationMillis = 800),
        label = "SpeedAnimation"
    )

    val dynamicSpeedColor = remember(animatedSpeed, theme) {
        when {
            animatedSpeed < 50f -> {
                val fraction = (animatedSpeed / 50f).coerceIn(0f, 1f)
                lerp(theme.accentCyan, theme.accentPurple, fraction)
            }
            else -> {
                val fraction = ((animatedSpeed - 50f) / 100f).coerceIn(0f, 1f)
                lerp(theme.accentPurple, theme.accentOrange, fraction)
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ==========================================
        // 1. RELOJ ANÁLOGO ENMARCADO EN CUADRO 3D TECH WITH LINE GRADIENT
        // ==========================================
        Box(
            modifier = Modifier
                .weight(1.35f)
                .fillMaxHeight()
                .shadow(16.dp, RoundedCornerShape(26.dp), spotColor = Color.Black)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF222B3D), Color(0xFF111722), Color(0xFF07090E))
                    )
                )
                .border(
                    1.8.dp,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.3f),
                            theme.accentCyan.copy(alpha = 0.6f),
                            theme.accentPurple.copy(alpha = 0.6f),
                            Color.Black
                        )
                    ),
                    RoundedCornerShape(26.dp)
                )
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            // Fondo cóncavo base
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF1A2232), Color(0xFF0A0E16)),
                            radius = 450f
                        )
                    )
            )

            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = (size.minDimension / 2f) - 4.dp.toPx()
                val strokeWidthPx = 12.dp.toPx()

                val arcRadius = maxRadius - (strokeWidthPx / 2f)
                val innerArcEdge = arcRadius - (strokeWidthPx / 2f)
                val ticksRadius = innerArcEdge - 2.dp.toPx()
                val textRadius = ticksRadius - 36.dp.toPx()
                val needleLength = innerArcEdge - 14.dp.toPx()

                val totalSpeed = 150

                // =========================================================================
                // 🌌 LÍNEAS EN GRADIENTE DESDE EL CÍRCULO HASTA EL BORDE DEL CUADRO (EFECTO 3D/HUD)
                // =========================================================================
                val rayCount = 36
                val rayStartRadius = arcRadius + (strokeWidthPx / 2f) + 2.dp.toPx()
                val outerBoxExtent = size.maxDimension // Extensión hacia bordes del cuadro

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

                    // Opacidad de rayo según cercanía a la zona activa de velocidad
                    val currentSpeedAngle = 135f + (270f * (animatedSpeed / totalSpeed.toFloat()).coerceIn(0f, 1f))
                    val angleDiff = Math.abs(rayAngleDeg - currentSpeedAngle)
                    val isNearNeedle = angleDiff < 30f || (360f - angleDiff) < 30f

                    val rayAlpha = if (isNearNeedle) 0.35f else 0.12f

                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                if (isNearNeedle) dynamicSpeedColor.copy(alpha = rayAlpha) else theme.accentCyan.copy(alpha = rayAlpha),
                                theme.accentPurple.copy(alpha = rayAlpha * 0.5f),
                                Color.Transparent
                            ),
                            start = rayStart,
                            end = rayEnd
                        ),
                        start = rayStart,
                        end = rayEnd,
                        strokeWidth = if (isNearNeedle) 1.8.dp.toPx() else 1.0.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // ANILLOS CONCÉNTRICOS DE PROFUNDIDAD ENTRE EL DIAL Y EL MARCO
                for (rOffset in listOf(8.dp.toPx(), 18.dp.toPx(), 28.dp.toPx())) {
                    val ringRadius = arcRadius + (strokeWidthPx / 2f) + rOffset
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                theme.accentCyan.copy(alpha = 0.12f),
                                theme.accentPurple.copy(alpha = 0.18f),
                                theme.accentOrange.copy(alpha = 0.08f),
                                theme.accentCyan.copy(alpha = 0.12f)
                            )
                        ),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(
                            width = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f), 0f)
                        )
                    )
                }

                // =========================================================================
                // 1. ARCO DE FONDO BASE
                // =========================================================================
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            theme.accentPurple.copy(alpha = 0.25f),
                            theme.accentCyan.copy(alpha = 0.25f)
                        )
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                )

                // 2. ARCO DE ALTA VELOCIDAD
                val redZoneStartAngle = 135f + (120f / totalSpeed.toFloat()) * 270f
                val redZoneSweepAngle = (30f / totalSpeed.toFloat()) * 270f

                drawArc(
                    color = theme.accentOrange.copy(alpha = 0.8f),
                    startAngle = redZoneStartAngle,
                    sweepAngle = redZoneSweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                )

                val speedProgress = (animatedSpeed / totalSpeed.toFloat()).coerceIn(0f, 1f)

                // 3. BARRA DE VELOCIDAD DINÁMICA ACTIVA
                if (speedProgress > 0f) {
                    drawArc(
                        color = dynamicSpeedColor.copy(alpha = 0.35f),
                        startAngle = 135f,
                        sweepAngle = 270f * speedProgress,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx * 1.5f, cap = StrokeCap.Round),
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

                // 4. ESTILOS DE NÚMEROS
                val textPaintDimmed = AndroidPaint().apply {
                    color = android.graphics.Color.argb(180, 180, 195, 210)
                    textSize = 14.5.sp.toPx()
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                }

                val textPaintActive = AndroidPaint().apply {
                    color = android.graphics.Color.WHITE
                    textSize =16.sp.toPx()
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                }

                // 5. TICKS Y NÚMEROS
                for (s in 0..totalSpeed step 5) {
                    val angleDeg = 135f + (s.toFloat() / totalSpeed.toFloat()) * 270f
                    val angleRad = Math.toRadians(angleDeg.toDouble())

                    val isMainTick = (s % 20 == 0 || s == 150)
                    val isMediumTick = (s % 10 == 0 && !isMainTick)

                    val tickLength = when {
                        isMainTick -> 10.dp.toPx()
                        isMediumTick -> 6.dp.toPx()
                        else -> 3.5.dp.toPx()
                    }

                    val tickStartR = ticksRadius
                    val tickEndR = ticksRadius - tickLength

                    val startX = center.x + tickStartR * cos(angleRad).toFloat()
                    val startY = center.y + tickStartR * sin(angleRad).toFloat()
                    val endX = center.x + tickEndR * cos(angleRad).toFloat()
                    val endY = center.y + tickEndR * sin(angleRad).toFloat()

                    val isPassed = s <= animatedSpeed

                    val tickColor = when {
                        s >= 120 -> theme.accentOrange
                        isPassed -> dynamicSpeedColor
                        isMainTick -> Color.White
                        else -> Color.Gray.copy(alpha = 0.5f)
                    }

                    drawLine(
                        color = Color.Black.copy(alpha = 0.7f),
                        start = Offset(startX + 1f, startY + 1f),
                        end = Offset(endX + 1f, endY + 1f),
                        strokeWidth = if (isMainTick) 3.5.dp.toPx() else 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    drawLine(
                        color = tickColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (isMainTick) 3.dp.toPx() else if (isMediumTick) 1.8.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    if (isMainTick) {
                        val numX = center.x + textRadius * cos(angleRad).toFloat()
                        val numY = center.y + textRadius * sin(angleRad).toFloat() + 3.5.dp.toPx()

                        drawContext.canvas.nativeCanvas.drawText(
                            s.toString(),
                            numX,
                            numY,
                            if (isPassed) textPaintActive else textPaintDimmed
                        )
                    }
                }

                // 6. AGUJA Y PIVOTE 3D
                val needleAngleRad = Math.toRadians((135f + (270f * speedProgress)).toDouble())
                val needleEnd = Offset(
                    x = center.x + (needleLength * cos(needleAngleRad)).toFloat(),
                    y = center.y + (needleLength * sin(needleAngleRad)).toFloat()
                )

                drawLine(
                    color = Color.Black.copy(0.7f),
                    start = Offset(center.x + 3f, center.y + 3f),
                    end = Offset(needleEnd.x + 3f, needleEnd.y + 3f),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = dynamicSpeedColor,
                    start = center,
                    end = needleEnd,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawCircle(color = Color.Black, radius = 7.dp.toPx(), center = center)
                drawCircle(color = theme.accentCyan, radius = 5.dp.toPx(), center = center)
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = center)
            }
        }

        // ==========================================
        // 2. VELOCIDAD DIGITAL
        // ==========================================
        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
                .shadow(12.dp, RoundedCornerShape(26.dp), spotColor = Color.Black)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF222B3D), Color(0xFF07090E))))
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(theme.accentCyan, Color.Transparent, theme.accentPurple)),
                    RoundedCornerShape(26.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${animatedSpeed.toInt()}",
                    fontSize = 68.sp,
                    fontWeight = activeFontWeight,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    letterSpacing = (-2.0).sp
                )
                Text(
                    text = "KM / H",
                    fontSize = 20.sp,
                    fontWeight = activeFontWeight,
                    color = dynamicSpeedColor,
                    letterSpacing = 2.0.sp
                )
            }
        }
    }
}