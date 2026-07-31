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
        // 1. RELOJ ANALÓGICO 3D REALISTA CON AGUJA LARGA PRECISA
        // ==========================================
        Box(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight()
                .shadow(12.dp, CircleShape, spotColor = Color.Black)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF2E384D), Color(0xFF181F2C), Color(0xFF0D1017))
                    )
                )
                .border(
                    2.5.dp,
                    Brush.sweepGradient(
                        listOf(theme.accentCyan, theme.accentPurple, theme.accentOrange, theme.accentCyan)
                    ),
                    CircleShape
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color.Transparent, Color.Black.copy(0.75f))
                        )
                    )
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = (size.minDimension / 2f) - 2.dp.toPx()
                val strokeWidthPx = 18.dp.toPx() // Franja gorda exterior

                val arcRadius = maxRadius - (strokeWidthPx / 2f)
                val innerArcEdge = arcRadius - (strokeWidthPx / 2f) // Borde interno exacto de la franja gorda

                val ticksRadius = innerArcEdge - 1.dp.toPx()
                val textRadius = ticksRadius - 32.dp.toPx() // Números separados de las rayas

                // AGUJA REALISTA: Se extiende casi hasta la franja gorda sin llegar a tocarla (justo sobre las rayas)
                val needleLength = innerArcEdge - 30.dp.toPx()

                // 1. ARCO DE FONDO BASE GRUESO (2do Color del Tema)
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            theme.accentPurple.copy(alpha = 0.4f),
                            theme.accentCyan.copy(alpha = 0.4f)
                        )
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                )

                // 2. ARCO DE FRANJA ALTA VELOCIDAD (3er Color del Tema)
                val totalSpeed = 150
                val redZoneStartAngle = 135f + (120f / totalSpeed.toFloat()) * 270f
                val redZoneSweepAngle = (30f / totalSpeed.toFloat()) * 270f

                drawArc(
                    color = theme.accentOrange,
                    startAngle = redZoneStartAngle,
                    sweepAngle = redZoneSweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                )

                val speedProgress = (animatedSpeed / totalSpeed.toFloat()).coerceIn(0f, 1f)

                // 3. BARRA DE VELOCIDAD ACTIVA
                drawArc(
                    color = dynamicSpeedColor.copy(alpha = 0.4f),
                    startAngle = 135f,
                    sweepAngle = 270f * speedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx * 1.4f, cap = StrokeCap.Round),
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

                // 4. CONFIGURACIÓN DE NÚMEROS
                val textPaintDimmed = AndroidPaint().apply {
                    color = android.graphics.Color.LTGRAY
                    textSize = 12.sp.toPx()
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                }

                val textPaintActive = AndroidPaint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 14.sp.toPx()
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                }

                // 5. RAYAS INDICADORAS GORDAS Y LARGAS
                for (s in 0..totalSpeed step 5) {
                    val angleDeg = 135f + (s.toFloat() / totalSpeed.toFloat()) * 270f
                    val angleRad = Math.toRadians(angleDeg.toDouble())

                    val isMainTick = (s % 20 == 0 || s == 150)
                    val isMediumTick = (s % 10 == 0 && !isMainTick)

                    val tickLength = when {
                        isMainTick -> 14.dp.toPx()
                        isMediumTick -> 8.dp.toPx()
                        else -> 4.5.dp.toPx()
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
                        else -> theme.accentPurple.copy(alpha = 0.85f)
                    }

                    // Sombra anti-reflejo bajo las rayas
                    drawLine(
                        color = Color.Black.copy(alpha = 0.85f),
                        start = Offset(startX + 1.2f, startY + 1.2f),
                        end = Offset(endX + 1.2f, endY + 1.2f),
                        strokeWidth = if (isMainTick) 5.dp.toPx() else 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Raya
                    drawLine(
                        color = tickColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (isMainTick) 4.2.dp.toPx() else if (isMediumTick) 2.5.dp.toPx() else 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Números cada 20 km/h (Ubicados en la zona interna limpia)
                    if (isMainTick) {
                        val numX = center.x + textRadius * cos(angleRad).toFloat()
                        val numY = center.y + textRadius * sin(angleRad).toFloat() + 4.5.dp.toPx()

                        drawContext.canvas.nativeCanvas.drawText(
                            s.toString(),
                            numX,
                            numY,
                            if (isPassed) textPaintActive else textPaintDimmed
                        )
                    }
                }

                // 6. AGUJA DE GRAN ALCANCE REALISTA (Punta rozando la franja gorda)
                val needleAngleRad = Math.toRadians((135f + (270f * speedProgress)).toDouble())
                val needleEnd = Offset(
                    x = center.x + (needleLength * cos(needleAngleRad)).toFloat(),
                    y = center.y + (needleLength * sin(needleAngleRad)).toFloat()
                )

                // Sombra de la aguja para profundidad 3D
                drawLine(
                    color = Color.Black.copy(0.85f),
                    start = Offset(center.x + 2f, center.y + 2f),
                    end = Offset(needleEnd.x + 2f, needleEnd.y + 2f),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Cuerpo Principal de la Aguja
                drawLine(
                    color = dynamicSpeedColor,
                    start = center,
                    end = needleEnd,
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Centro Pivot Estilizado
                drawCircle(color = theme.accentCyan, radius = 6.5.dp.toPx(), center = center)
                drawCircle(color = dynamicSpeedColor, radius = 3.2.dp.toPx(), center = center)
            }
        }

        // ==========================================
        // 2. VELOCIDAD DIGITAL UNIFICADA EXPANDIDA
        // ==========================================
        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
                .shadow(10.dp, RoundedCornerShape(24.dp), spotColor = Color.Black)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF222A3A), Color(0xFF0F131C))))
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(theme.accentCyan, Color.Transparent, theme.accentPurple)),
                    RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${animatedSpeed.toInt()}",
                    fontSize = 72.sp,
                    fontWeight = activeFontWeight,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    letterSpacing = (-2.0).sp
                )
                Text(
                    text = "KM / H",
                    fontSize = 22.sp,
                    fontWeight = activeFontWeight,
                    color = dynamicSpeedColor,
                    letterSpacing = 2.0.sp
                )
            }
        }
    }
}