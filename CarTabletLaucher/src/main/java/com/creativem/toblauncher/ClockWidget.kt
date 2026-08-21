package com.creativem.toblauncher

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
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

    var digitalTimeDigits by remember { mutableStateOf("00:00") }
    var amPmText by remember { mutableStateOf("AM") }

    // ACTUALIZACIÓN EN TIEMPO REAL CADA SEGUNDO
    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            hours = cal.get(Calendar.HOUR)
            minutes = cal.get(Calendar.MINUTE)
            seconds = cal.get(Calendar.SECOND)

            digitalTimeDigits = SimpleDateFormat("hh:mm", Locale.getDefault()).format(cal.time)
            amPmText = SimpleDateFormat("a", Locale.getDefault()).format(cal.time).uppercase()

            delay(1000L)
        }
    }

    // =========================================================================
    // 🎨 FONDOS 100% DINÁMICOS BASADOS EN LOS 3 COLORES DEL TEMA ACTIVO
    // =========================================================================
    // 🌌 1. Fondo Exterior (Radiación del centro hacia afuera con Primario predominante)
    val dynamicOuterBackground = remember(theme) {
        Brush.radialGradient(
            colors = listOf(
                lerp(theme.cardBackground, theme.primaryColor, 0.35f), // Centro iluminado con Primario
                lerp(theme.dashBackground, theme.textColor, 0.15f),    // Halo medio con Color de Texto
                lerp(theme.dashBackground, theme.numberColor, 0.08f),  // Destello exterior con Números
                theme.dashBackground                                  // Borde fundido al fondo general
            ),
            radius = 650f
        )
    }

    // 🌌 2. Fondo Interior Radiante (Centro de alto impacto)
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

    // 🖼️ 3. Borde Dinámico con los 3 colores del tema
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

    // 🟠 PINTURA PARA NÚMEROS DEL DIAL (1 al 12 en theme.numberColor)
    val numberPaint = remember(theme, isBold, density, dashboardFont) {
        AndroidPaint().apply {
            color = theme.numberColor.toArgb()
            textSize = with(density) { 13.5.sp.toPx() }
            typeface = if (isBold) {
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            } else {
                Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            textAlign = AndroidPaint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(5f, 0f, 0f, theme.numberColor.toArgb())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // =========================================================================
        // 1. RELOJ ANALÓGICO (FONDO DINÁMICO IDÉNTICO AL VELOCÍMETRO)
        // =========================================================================
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .shadow(16.dp, CircleShape, spotColor = theme.primaryColor.copy(alpha = 0.25f))
                .clip(CircleShape)
                .background(dynamicOuterBackground)
                .border(1.8.dp, dynamicBorderBrush, CircleShape)
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

            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f - 2.dp.toPx()

                // =========================================================
                // 🔵 1. RAYOS HUD Y ANILLOS CONCÉNTRICOS
                // =========================================================
                val rayCount = 28
                val innerR = 12.dp.toPx()
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

                // Anillo 1: Exterior continuo (3 Colores)
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            theme.primaryColor.copy(alpha = 0.20f),
                            theme.textColor.copy(alpha = 0.15f),
                            theme.numberColor.copy(alpha = 0.10f),
                            theme.primaryColor.copy(alpha = 0.20f)
                        )
                    ),
                    radius = maxRadius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Anillo 2: Punteado Radar (Primario)
                drawCircle(
                    color = theme.primaryColor.copy(alpha = 0.5f),
                    radius = maxRadius - 5.dp.toPx(),
                    center = center,
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f)
                    )
                )

                // Anillo 3: Cuadrantes iluminados en 12, 3, 6 y 9 (Primario)
                for (quadrantAngle in listOf(0f, 90f, 180f, 270f)) {
                    drawArc(
                        color = theme.primaryColor,
                        startAngle = quadrantAngle - 12f,
                        sweepAngle = 24f,
                        useCenter = false,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                        size = Size((maxRadius - 2.dp.toPx()) * 2, (maxRadius - 2.dp.toPx()) * 2),
                        topLeft = Offset(center.x - (maxRadius - 2.dp.toPx()), center.y - (maxRadius - 2.dp.toPx()))
                    )
                }

                // Anillo 4: Halo interior (Color de Texto)
                val innerHaloRadius = maxRadius - 28.dp.toPx()
                drawCircle(
                    color = theme.textColor.copy(alpha = 0.25f),
                    radius = innerHaloRadius,
                    center = center,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
                    )
                )

                // =========================================================
                // ⏱️ 2. MARCAS (TICKS) Y NÚMEROS
                // =========================================================
                val ticksRadius = maxRadius - 9.dp.toPx()
                for (i in 0..11) {
                    val angleDeg = i * 30f - 90f
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val isMainTick = (i % 3 == 0)
                    val tickLen = if (isMainTick) 8.dp.toPx() else 4.5.dp.toPx()

                    val startX = center.x + (ticksRadius - tickLen) * cos(angleRad).toFloat()
                    val startY = center.y + (ticksRadius - tickLen) * sin(angleRad).toFloat()
                    val endX = center.x + ticksRadius * cos(angleRad).toFloat()
                    val endY = center.y + ticksRadius * sin(angleRad).toFloat()

                    drawLine(
                        color = if (isMainTick) theme.primaryColor.copy(alpha = 0.90f) else theme.primaryColor.copy(alpha = 0.40f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (isMainTick) 2.5.dp.toPx() else 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 🟠 Dibuja los números del reloj (1 al 12 en theme.numberColor)
                val textRadius = ticksRadius - 14.dp.toPx()
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
                // 🔵 AGUJA HORA (Primario)
                val hourAngleDeg = ((hours % 12) + minutes / 60f) * 30f - 90f
                val hourRad = Math.toRadians(hourAngleDeg.toDouble())
                val hourEnd = Offset(
                    x = center.x + (maxRadius * 0.45f * cos(hourRad)).toFloat(),
                    y = center.y + (maxRadius * 0.45f * sin(hourRad)).toFloat()
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.6f),
                    start = Offset(center.x + 2f, center.y + 2f),
                    end = Offset(hourEnd.x + 2f, hourEnd.y + 2f),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = theme.primaryColor,
                    start = center,
                    end = hourEnd,
                    strokeWidth = 3.2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // 🟣 AGUJA MINUTERO (Color de Texto)
                val minAngleDeg = (minutes + seconds / 60f) * 6f - 90f
                val minRad = Math.toRadians(minAngleDeg.toDouble())
                val minEnd = Offset(
                    x = center.x + (maxRadius * 0.68f * cos(minRad)).toFloat(),
                    y = center.y + (maxRadius * 0.68f * sin(minRad)).toFloat()
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.6f),
                    start = Offset(center.x + 2f, center.y + 2f),
                    end = Offset(minEnd.x + 2f, minEnd.y + 2f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = theme.textColor,
                    start = center,
                    end = minEnd,
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // 🟠 AGUJA SEGUNDERO (Color de Números)
                val secAngleDeg = seconds * 6f - 90f
                val secRad = Math.toRadians(secAngleDeg.toDouble())
                val secEnd = Offset(
                    x = center.x + (maxRadius * 0.82f * cos(secRad)).toFloat(),
                    y = center.y + (maxRadius * 0.82f * sin(secRad)).toFloat()
                )
                drawLine(
                    color = theme.numberColor,
                    start = center,
                    end = secEnd,
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Pivote central
                drawCircle(color = Color.Black, radius = 5.dp.toPx(), center = center)
                drawCircle(color = theme.primaryColor, radius = 3.5.dp.toPx(), center = center)
                drawCircle(color = Color.White, radius = 1.8.dp.toPx(), center = center)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // =========================================================================
        // 2. RELOJ DIGITAL (FONDO DINÁMICO IDÉNTICO AL VELOCÍMETRO DIGITAL)
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .shadow(16.dp, RoundedCornerShape(14.dp), spotColor = theme.primaryColor.copy(alpha = 0.2f))
                .clip(RoundedCornerShape(14.dp))
                .background(dynamicOuterBackground)
                .border(1.6.dp, dynamicBorderBrush, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // 🟠 Números del reloj digital (Color de Números)
                Text(
                    text = digitalTimeDigits,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = dashboardFont.fontFamily,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White,
                                theme.numberColor
                            )
                        )
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                // 🟣 Texto "A. M." / "P. M." (Color de Texto)
                Text(
                    text = amPmText,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = dashboardFont.fontFamily,
                    color = theme.textColor,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}