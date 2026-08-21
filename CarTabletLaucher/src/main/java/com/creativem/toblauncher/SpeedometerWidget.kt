package com.creativem.toblauncher

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interpola suavemente a través de la paleta del tema seleccionado por el usuario
 */
fun getSmoothProgressiveColor(palette: List<Color>, progress: Float): Color {
    val clamped = progress.coerceIn(0f, 1f)
    if (clamped <= 0f) return palette.first()
    if (clamped >= 1f) return palette.last()

    val totalSegments = palette.size - 1
    val scaled = clamped * totalSegments
    val index = scaled.toInt().coerceIn(0, totalSegments - 1)
    val fraction = scaled - index

    return lerp(palette[index], palette[index + 1], fraction)
}

@Composable
fun ModernSpeedometerWidget(
    speedKmH: Float,
    bearing: Float = 0f,
    onRequestAppSelection: (slot: Int) -> Unit = {}
) {
    val theme = LocalDashboardTheme.current
    val totalSpeed = 160f // <-- Ajustado a 160 km/h para simetría exacta

    val animatedSpeed by animateFloatAsState(
        targetValue = speedKmH,
        animationSpec = tween(durationMillis = 600),
        label = "SpeedAnimation"
    )

    val speedProgress = (animatedSpeed / totalSpeed).coerceIn(0f, 1f)

    // =========================================================================
    // 🎨 PALETA CONTINUA BASADA 100% EN EL TEMA
    // =========================================================================
    val dynamicSpeedColor = remember(animatedSpeed, theme) {
        val themeSpectrum = listOf(
            lerp(Color.White, theme.accentCyan, 0.40f), // 0 km/h: Tono claro/suave
            theme.accentCyan,                          // Color primario
            theme.accentPurple,                        // Color secundario
            theme.accentOrange                         // Máxima velocidad (160 km/h)
        )
        getSmoothProgressiveColor(themeSpectrum, speedProgress)
    }

    // Color del número digital continuo
    val dynamicNumberColor = remember(animatedSpeed, theme, dynamicSpeedColor) {
        lerp(Color.White, dynamicSpeedColor, speedProgress)
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ==========================================
        // 1. RELOJ ANÁLOGO (0 A 160 KM/H)
        // ==========================================
        Box(
            modifier = Modifier
                .weight(1.35f)
                .fillMaxHeight()
                .shadow(16.dp, RoundedCornerShape(26.dp), spotColor = dynamicSpeedColor.copy(alpha = 0.35f * speedProgress + 0.1f))
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
                            dynamicSpeedColor.copy(alpha = 0.4f + (0.4f * speedProgress)),
                            theme.accentPurple.copy(alpha = 0.3f),
                            Color.Black
                        )
                    ),
                    RoundedCornerShape(26.dp)
                )
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
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

                // RAYOS HUD
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

                    val rayAlpha = if (isNearNeedle) (0.25f + 0.35f * speedProgress) else 0.08f

                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                dynamicSpeedColor.copy(alpha = rayAlpha),
                                theme.accentPurple.copy(alpha = rayAlpha * 0.4f),
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

                // ANILLOS DECORATIVOS
                for (rOffset in listOf(8.dp.toPx(), 18.dp.toPx(), 28.dp.toPx())) {
                    val ringRadius = arcRadius + (strokeWidthPx / 2f) + rOffset
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                dynamicSpeedColor.copy(alpha = 0.12f),
                                theme.accentPurple.copy(alpha = 0.15f),
                                dynamicSpeedColor.copy(alpha = 0.12f)
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

                // 1. ARCO BASE
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            theme.accentPurple.copy(alpha = 0.2f),
                            theme.accentCyan.copy(alpha = 0.2f)
                        )
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                )

                // 2. BARRA DINÁMICA
                if (speedProgress > 0f) {
                    drawArc(
                        color = dynamicSpeedColor.copy(alpha = 0.35f + (0.25f * speedProgress)),
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

                // 3. TEXTOS DEL DIAL
                val textPaintDimmed = AndroidPaint().apply {
                    color = android.graphics.Color.argb(160, 170, 185, 200)
                    textSize = 14.sp.toPx()
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                }

                val textPaintActive = AndroidPaint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 15.5.sp.toPx()
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                }

                // 4. TICKS PROGRESIVOS (0 a 160 exactos de 20 en 20)
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

                    val tickStartR = ticksRadius
                    val tickEndR = ticksRadius - tickLength

                    val startX = center.x + tickStartR * cos(angleRad).toFloat()
                    val startY = center.y + tickStartR * sin(angleRad).toFloat()
                    val endX = center.x + tickEndR * cos(angleRad).toFloat()
                    val endY = center.y + tickEndR * sin(angleRad).toFloat()

                    val isPassed = s <= animatedSpeed

                    val tickColor = when {
                        isPassed -> dynamicSpeedColor
                        isMainTick -> Color.White
                        else -> Color.Gray.copy(alpha = 0.45f)
                    }

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

                // 5. AGUJA DINÁMICA
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
                    strokeWidth = 3.2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawCircle(color = Color.Black, radius = 7.dp.toPx(), center = center)
                drawCircle(color = dynamicSpeedColor, radius = 5.dp.toPx(), center = center)
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = center)
            }
        }

        // ==========================================
        // 2. VELOCIDAD DIGITAL CON FECHA SUPERIOR
        // ==========================================

        // Obtiene la fecha actual formateada en mayúsculas (Ej: "JUEVES, 20 AGOSTO 2025")
        val currentDate = remember {
            val sdf = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.getDefault())
            sdf.format(java.util.Date()).uppercase()
        }

        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(26.dp),
                    spotColor = dynamicSpeedColor.copy(alpha = 0.2f + 0.35f * speedProgress)
                )
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF222B3D), Color(0xFF07090E))))
                .border(
                    1.6.dp,
                    Brush.linearGradient(
                        listOf(
                            dynamicSpeedColor.copy(alpha = 0.5f + (0.5f * speedProgress)),
                            Color.Transparent,
                            theme.accentPurple.copy(alpha = 0.4f)
                        )
                    ),
                    RoundedCornerShape(26.dp)
                )
        ) {
            // 📅 1. FECHA SUPERIOR (Toma el color dinámico del tema)
            Text(
                text = currentDate,
                fontSize =8.sp,
                fontWeight = FontWeight.Bold,
                color = dynamicSpeedColor,
                letterSpacing = 0.6.sp,           // Espaciado ajustado para que quepa cualquier mes largo
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, // Asegura el centrado exacto
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()              // Ocupa todo el ancho para centrar el texto correctamente
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 4.dp, end = 4.dp)
            )

            // ⚡ 2. NÚMERO GIGANTE Y GORDO (Centro)
            Text(
                text = "${animatedSpeed.toInt()}",
                fontSize = 84.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-4.0).sp,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White,
                            dynamicNumberColor
                        )
                    )
                ),
                modifier = Modifier.align(Alignment.Center)
            )

            // 🏎️ 3. UNIDAD "KM / H" (Abajo en la orilla)
            Text(
                text = "KM / H",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = dynamicSpeedColor,
                letterSpacing = 2.5.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            )
        }
    }
}