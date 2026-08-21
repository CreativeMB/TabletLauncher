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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ModernClockWidget() {
    val theme = LocalDashboardTheme.current // TEMA DE COLORES GLOBAL
    val isBold = LocalIsBoldText.current     // ESTADO DE NEGRITA GLOBAL
    val density = LocalDensity.current       // DENSIDAD (Incluye la escala de texto personalizada)

    val timeWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Bold
    val dateWeight = if (isBold) FontWeight.Bold else FontWeight.Medium

    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }

    var digitalTimeDigits by remember { mutableStateOf("00:00") }
    var amPmText by remember { mutableStateOf("AM") }
    var currentDateText by remember { mutableStateOf("") }

    // ACTUALIZACIÓN EN TIEMPO REAL CADA SEGUNDO
    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            hours = cal.get(Calendar.HOUR)
            minutes = cal.get(Calendar.MINUTE)
            seconds = cal.get(Calendar.SECOND)

            digitalTimeDigits = SimpleDateFormat("hh:mm", Locale.getDefault()).format(cal.time)
            amPmText = SimpleDateFormat("a", Locale.getDefault()).format(cal.time).uppercase()

            // ✅ FECHA COMPLETA CON EL DÍA COMPLETO (Ej: "MIÉRCOLES, 05 AGOSTO")
            currentDateText = SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault()).format(cal.time).uppercase()

            delay(1000L)
        }
    }

    val numberPaint = remember(theme, isBold, density) {
        AndroidPaint().apply {
            color = theme.accentCyan.toArgb()
            textSize = with(density) { 13.sp.toPx() }
            typeface = if (isBold) {
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            } else {
                Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            textAlign = AndroidPaint.Align.CENTER
            isAntiAlias = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ==========================================
        // 1. RELOJ ANALÓGICO (FONDO SÓLIDO TECH DE ALTO CONTRASTE)
        // ==========================================
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .shadow(20.dp, CircleShape, spotColor = Color.Black, ambientColor = Color.Black)
                .clip(CircleShape)
                // Marco exterior sólido (sin transparencias)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF2B364C), // Borde superior sólido iluminado
                            Color(0xFF182030), // Cuerpo sólido
                            Color(0xFF090D15)  // Base profunda
                        )
                    )
                )
                .border(
                    2.dp,
                    Brush.sweepGradient(
                        listOf(
                            theme.accentCyan,
                            theme.accentPurple,
                            theme.accentOrange,
                            theme.accentCyan
                        )
                    ),
                    CircleShape
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            // Fondo interior cóncavo 100% opaco
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1E283A), // Centro sólido
                                Color(0xFF101622), // Medio
                                Color(0xFF05070B)  // Fondo ultra oscuro
                            ),
                            radius = 380f
                        )
                    )
            )

            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f - 2.dp.toPx()

                // =========================================================
                // 🌌 1. ANILLOS CONCÉNTRICOS MODERNOS
                // =========================================================
                // Anillo 1: Exterior continuo
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            theme.accentCyan.copy(alpha = 0.5f),
                            theme.accentPurple.copy(alpha = 0.5f),
                            theme.accentOrange.copy(alpha = 0.5f),
                            theme.accentCyan.copy(alpha = 0.5f)
                        )
                    ),
                    radius = maxRadius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Anillo 2: Punteado Radar/Tech
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            theme.accentCyan.copy(alpha = 0.7f),
                            theme.accentPurple.copy(alpha = 0.7f),
                            theme.accentCyan.copy(alpha = 0.7f)
                        )
                    ),
                    radius = maxRadius - 5.dp.toPx(),
                    center = center,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f)
                    )
                )

                // Anillo 3: Cuadrantes iluminados en 12, 3, 6 y 9
                for (quadrantAngle in listOf(0f, 90f, 180f, 270f)) {
                    drawArc(
                        color = theme.accentCyan,
                        startAngle = quadrantAngle - 12f,
                        sweepAngle = 24f,
                        useCenter = false,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                        size = Size((maxRadius - 2.dp.toPx()) * 2, (maxRadius - 2.dp.toPx()) * 2),
                        topLeft = Offset(center.x - (maxRadius - 2.dp.toPx()), center.y - (maxRadius - 2.dp.toPx()))
                    )
                }

                // Anillo 4: Halo interior
                val innerHaloRadius = maxRadius - 28.dp.toPx()
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            theme.accentPurple.copy(alpha = 0.3f),
                            theme.accentCyan.copy(alpha = 0.3f),
                            theme.accentPurple.copy(alpha = 0.3f)
                        )
                    ),
                    radius = innerHaloRadius,
                    center = center,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
                    )
                )

                // =========================================================
                // ⏱️ 2. MARCAS Y NÚMEROS
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
                        color = if (isMainTick) theme.accentCyan else theme.accentPurple.copy(alpha = 0.7f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (isMainTick) 2.5.dp.toPx() else 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

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
                // AGUJA HORA
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
                    color = theme.accentCyan,
                    start = center,
                    end = hourEnd,
                    strokeWidth = 3.2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // AGUJA MINUTERO
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
                    color = theme.accentPurple,
                    start = center,
                    end = minEnd,
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // AGUJA SEGUNDERO
                val secAngleDeg = seconds * 6f - 90f
                val secRad = Math.toRadians(secAngleDeg.toDouble())
                val secEnd = Offset(
                    x = center.x + (maxRadius * 0.82f * cos(secRad)).toFloat(),
                    y = center.y + (maxRadius * 0.82f * sin(secRad)).toFloat()
                )
                drawLine(
                    color = theme.accentOrange,
                    start = center,
                    end = secEnd,
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Pivote central
                drawCircle(color = Color.Black, radius = 5.dp.toPx(), center = center)
                drawCircle(color = theme.accentCyan, radius = 3.5.dp.toPx(), center = center)
                drawCircle(color = Color.White, radius = 1.8.dp.toPx(), center = center)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ==========================================
        // 2. RELOJ DIGITAL (FONDO SÓLIDO PROFUNDO)
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .shadow(10.dp, RoundedCornerShape(14.dp), spotColor = Color.Black)
                .clip(RoundedCornerShape(14.dp))
                // Fondo totalmente sólido y blindado
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF222C3E),
                            Color(0xFF121724),
                            Color(0xFF07090F)
                        )
                    )
                )
                .border(
                    1.4.dp,
                    Brush.linearGradient(
                        listOf(
                            theme.accentCyan.copy(alpha = 0.7f),
                            Color(0xFF1E2838),
                            theme.accentPurple.copy(alpha = 0.7f)
                        )
                    ),
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = digitalTimeDigits,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    color = Color.White,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    softWrap = false
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = amPmText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.accentCyan,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }

}