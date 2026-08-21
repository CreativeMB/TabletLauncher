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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interpola suavemente a través de la paleta del tema seleccionado
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
    val dashboardFont = LocalDashboardFont.current
    val totalSpeed = 160f // Escala exacta 0 a 160 km/h

    val animatedSpeed by animateFloatAsState(
        targetValue = speedKmH,
        animationSpec = tween(durationMillis = 600),
        label = "SpeedAnimation"
    )

    val speedProgress = (animatedSpeed / totalSpeed).coerceIn(0f, 1f)

    // =========================================================================
    // 🎨 MEZCLA DINÁMICA DE 3 COLORES BASADA 100% EN EL TEMA
    // =========================================================================
    val dynamicSpeedColor = remember(animatedSpeed, theme) {
        val themeSpectrum = listOf(
            theme.primaryColor,
            theme.textColor,
            theme.numberColor
        )
        getSmoothProgressiveColor(themeSpectrum, speedProgress)
    }

    val dynamicNumberColor = remember(animatedSpeed, theme) {
        lerp(theme.numberColor, Color.White, 0.20f)
    }

    // 🌌 1. FONDO EXTERIOR DINÁMICO (De adentro hacia afuera - Primario predominante)
    val dynamicOuterBackground = remember(theme) {
        Brush.radialGradient(
            colors = listOf(
                lerp(theme.cardBackground, theme.primaryColor, 0.35f), // Centro más iluminado con el Primario
                lerp(theme.dashBackground, theme.textColor, 0.15f),    // Halo medio con el color de texto
                lerp(theme.dashBackground, theme.numberColor, 0.08f),  // Destello exterior con el de números
                theme.dashBackground                                  // Borde fundido al fondo general
            ),
            radius = 650f
        )
    }

    // 🌌 2. FONDO INTERIOR DINÁMICO (Núcleo radiante del centro hacia afuera)
    val dynamicInnerBackground = remember(theme) {
        Brush.radialGradient(
            colors = listOf(
                lerp(theme.cardBackground, theme.primaryColor, 0.45f), // Máxima intensidad en el centro
                lerp(theme.cardBackground, theme.primaryColor, 0.20f),
                lerp(theme.dashBackground, theme.textColor, 0.12f),
                theme.dashBackground
            ),
            radius = 450f
        )
    }

    // 🖼️ 3. BORDE DINÁMICO CON LOS 3 COLORES
    val dynamicBorderBrush = remember(theme, speedProgress) {
        Brush.sweepGradient(
            listOf(
                theme.primaryColor.copy(alpha = 0.85f),
                theme.textColor.copy(alpha = 0.50f),
                theme.numberColor.copy(alpha = 0.40f),
                theme.primaryColor.copy(alpha = 0.85f)
            )
        )
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // =========================================================================
        // 1. RELOJ ANÁLOGO (0 A 160 KM/H)
        // =========================================================================
        Box(
            modifier = Modifier
                .weight(1.35f)
                .fillMaxHeight()
                .shadow(16.dp, RoundedCornerShape(26.dp), spotColor = dynamicSpeedColor.copy(alpha = 0.35f * speedProgress + 0.15f))
                .clip(RoundedCornerShape(26.dp))
                .background(dynamicOuterBackground)
                .border(1.8.dp, dynamicBorderBrush, RoundedCornerShape(26.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            // Fondo interior radiante
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

                // 🔵 RAYOS HUD (Nacen desde el centro con los colores del tema)
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
                    val rayLineColor = if (isNearNeedle) dynamicSpeedColor else theme.primaryColor

                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                rayLineColor.copy(alpha = rayAlpha),
                                theme.textColor.copy(alpha = rayAlpha * 0.4f),
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

                // 🔵 ANILLOS DECORATIVOS CONCÉNTRICOS
                for (rOffset in listOf(8.dp.toPx(), 18.dp.toPx(), 28.dp.toPx())) {
                    val ringRadius = arcRadius + (strokeWidthPx / 2f) + rOffset
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                theme.primaryColor.copy(alpha = 0.20f),
                                theme.textColor.copy(alpha = 0.15f),
                                theme.numberColor.copy(alpha = 0.10f),
                                theme.primaryColor.copy(alpha = 0.20f)
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

                // 1. ARCO BASE DECORATIVO
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            theme.textColor.copy(alpha = 0.25f),
                            theme.primaryColor.copy(alpha = 0.30f),
                            theme.numberColor.copy(alpha = 0.20f)
                        )
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                )

                // 2. BARRA DINÁMICA DE PROGRESO AL ACELERAR
                if (speedProgress > 0f) {
                    drawArc(
                        color = dynamicSpeedColor.copy(alpha = 0.35f + (0.25f * speedProgress)),
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

                // 🟣 3. TEXTO "KM / H" EN LA APERTURA INFERIOR (Color de Texto)
                val dialLabelPaint = AndroidPaint().apply {
                    color = theme.textColor.toArgb()
                    textSize = 8.5.sp.toPx()
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                    letterSpacing = 0.20f
                }
                val labelPosY = center.y + (arcRadius * 0.78f)
                drawContext.canvas.nativeCanvas.drawText("KM / H", center.x, labelPosY, dialLabelPaint)

                // 🟠 4. PINTURAS PARA NÚMEROS DEL DIAL (Color de Números)
                val textPaintDimmed = AndroidPaint().apply {
                    color = theme.numberColor.copy(alpha = 0.75f).toArgb()
                    textSize = 11.sp.toPx()
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                }

                val textPaintActive = AndroidPaint().apply {
                    color = Color.White.toArgb()
                    textSize = 15.sp.toPx()
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                    setShadowLayer(8f, 0f, 0f, theme.numberColor.toArgb())
                }

                // 5. TICKS PROGRESIVOS (0 a 160)
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
                        isMainTick -> theme.primaryColor.copy(alpha = 0.90f)
                        else -> theme.primaryColor.copy(alpha = 0.40f)
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
                        val numY = center.y + textRadius * sin(angleRad).toFloat() + 4.dp.toPx()

                        drawContext.canvas.nativeCanvas.drawText(
                            s.toString(),
                            numX,
                            numY,
                            if (isPassed) textPaintActive else textPaintDimmed
                        )
                    }
                }

                // 6. AGUJA DINÁMICA Y EJE CENTRAL
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

        // =========================================================================
        // 2. VELOCIDAD DIGITAL (DÍA COMPLETO + FECHA NUMÉRICA: Ej. "VIERNES, 21/08/2026")
        // =========================================================================
        val currentDate = remember {
            val sdf = java.text.SimpleDateFormat("EEEE, dd/MM/yyyy", java.util.Locale.getDefault())
            sdf.format(java.util.Date()).uppercase()
        }

        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
                .shadow(16.dp, RoundedCornerShape(26.dp), spotColor = dynamicSpeedColor.copy(alpha = 0.35f * speedProgress + 0.15f))
                .clip(RoundedCornerShape(26.dp))
                .background(dynamicOuterBackground)
                .border(1.8.dp, dynamicBorderBrush, RoundedCornerShape(26.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            // Fondo interior radiante idéntico al análogo
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(dynamicInnerBackground)
            )

            // Canvas con Rayos HUD y Aros concéntricos generados del centro hacia afuera
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerBoxExtent = size.maxDimension

                // Rayos HUD del centro hacia afuera
                val rayCount = 28
                val innerR = 18.dp.toPx()
                for (i in 0 until rayCount) {
                    val rayAngleDeg = i * (360f / rayCount)
                    val rayAngleRad = Math.toRadians(rayAngleDeg.toDouble())

                    val rayStart = Offset(
                        x = center.x + innerR * cos(rayAngleRad).toFloat(),
                        y = center.y + innerR * sin(rayAngleRad).toFloat()
                    )
                    val rayEnd = Offset(
                        x = center.x + outerBoxExtent * cos(rayAngleRad).toFloat(),
                        y = center.y + outerBoxExtent * sin(rayAngleRad).toFloat()
                    )

                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                theme.primaryColor.copy(alpha = 0.18f),
                                theme.textColor.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            start = rayStart,
                            end = rayEnd
                        ),
                        start = rayStart,
                        end = rayEnd,
                        strokeWidth = 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Anillos concéntricos punteados
                for (rOffset in listOf(35.dp.toPx(), 65.dp.toPx(), 95.dp.toPx())) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                theme.primaryColor.copy(alpha = 0.18f),
                                theme.textColor.copy(alpha = 0.14f),
                                theme.numberColor.copy(alpha = 0.10f),
                                theme.primaryColor.copy(alpha = 0.18f)
                            )
                        ),
                        radius = rOffset,
                        center = center,
                        style = Stroke(
                            width = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f), 0f)
                        )
                    )
                }
            }

            // 🟣 1. FECHA NUMÉRICA SIMPLIFICADA AL TOPE SUPERIOR
            Text(
                text = currentDate,
                fontSize = 7.sp, // 💡 Mucho más grande y visible
                fontWeight = FontWeight.ExtraBold,
                fontFamily = dashboardFont.fontFamily,
                color = theme.textColor,
                letterSpacing = 1.2.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp, start = 4.dp, end = 4.dp) // 📍 Pegada al ras superior
            )

            // 🟠 2. NÚMERO DIGITAL GIGANTE (Centrado)
            Text(
                text = "${animatedSpeed.toInt()}",
                fontSize = 86.sp,
                fontWeight = FontWeight.Black,
                fontFamily = dashboardFont.fontFamily,
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

            // 🟣 3. UNIDAD "KM / H" PEGADA BIEN ABAJO A LA ORILLA
            Text(
                text = "KM / H",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = dashboardFont.fontFamily,
                color = theme.textColor,
                letterSpacing = 2.5.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
            )
        }
    }
}