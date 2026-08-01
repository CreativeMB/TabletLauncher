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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ==========================================
        // 1. RELOJ ANALÓGICO CON ESCALA DINÁMICA DE TEXTO
        // ==========================================
        Box(
            modifier = Modifier
                .weight(1.3f)
                .aspectRatio(1f)
                .shadow(8.dp, CircleShape, spotColor = Color.Black)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF222A3A), Color(0xFF0F131C))))
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(Color.White.copy(0.25f), Color.Transparent, Color.Black)),
                    CircleShape
                )
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.Black.copy(0.35f), Color.Transparent)))
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 2.dp.toPx()

                // A) MARCAS DE LAS HORAS
                for (i in 0..11) {
                    val angleDeg = i * 30f - 90f
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val innerR = radius - 4.dp.toPx()
                    val outerR = radius

                    val startX = center.x + innerR * cos(angleRad).toFloat()
                    val startY = center.y + innerR * sin(angleRad).toFloat()
                    val endX = center.x + outerR * cos(angleRad).toFloat()
                    val endY = center.y + outerR * sin(angleRad).toFloat()

                    drawLine(
                        color = theme.cardBorder.copy(alpha = 0.6f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // B) NÚMEROS DINÁMICOS CON ALINEACIÓN AJUSTADA
                val textRadius = radius - 15.dp.toPx()
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

                // C) AGUJA HORA
                val hourAngleDeg = ((hours % 12) + minutes / 60f) * 30f - 90f
                val hourRad = Math.toRadians(hourAngleDeg.toDouble())
                val hourEnd = Offset(
                    x = center.x + (radius * 0.45f * cos(hourRad)).toFloat(),
                    y = center.y + (radius * 0.45f * sin(hourRad)).toFloat()
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.4f),
                    start = Offset(center.x + 1.5f, center.y + 1.5f),
                    end = Offset(hourEnd.x + 1.5f, hourEnd.y + 1.5f),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = theme.accentCyan.copy(alpha = 0.85f),
                    start = center,
                    end = hourEnd,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // D) AGUJA MINUTERO
                val minAngleDeg = (minutes + seconds / 60f) * 6f - 90f
                val minRad = Math.toRadians(minAngleDeg.toDouble())
                val minEnd = Offset(
                    x = center.x + (radius * 0.70f * cos(minRad)).toFloat(),
                    y = center.y + (radius * 0.70f * sin(minRad)).toFloat()
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.4f),
                    start = Offset(center.x + 1.5f, center.y + 1.5f),
                    end = Offset(minEnd.x + 1.5f, minEnd.y + 1.5f),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = theme.accentPurple.copy(alpha = 0.85f),
                    start = center,
                    end = minEnd,
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // E) AGUJA SEGUNDERO
                val secAngleDeg = seconds * 6f - 90f
                val secRad = Math.toRadians(secAngleDeg.toDouble())
                val secEnd = Offset(
                    x = center.x + (radius * 0.82f * cos(secRad)).toFloat(),
                    y = center.y + (radius * 0.82f * sin(secRad)).toFloat()
                )
                drawLine(
                    color = theme.accentOrange,
                    start = center,
                    end = secEnd,
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Punto central de pivote
                drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = center)
                drawCircle(color = theme.accentOrange, radius = 1.8.dp.toPx(), center = center)
            }
        }

        // ==========================================
        // 2. RELOJ DIGITAL EN CUADRO 3D
        // ==========================================
        Box(
            modifier = Modifier
                .weight(0.8f)
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = Color.Black)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1E2636), Color(0xFF0A0D14))))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White.copy(0.18f), Color.Black)),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 1. HORA DIGITAL CON AM/PM
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = digitalTimeDigits,
                        fontSize = 28.sp,
                        fontWeight = timeWeight,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        letterSpacing = (-1).sp
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = amPmText,
                        fontSize = 11.sp,
                        fontWeight = dateWeight,
                        color = theme.accentCyan,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(1.dp))

                // 2. FECHA COMPLETA DEBAJO (Ej: "MIÉRCOLES, 05 AGOSTO")
                Text(
                    text = currentDateText,
                    fontSize = 10.sp,
                    fontWeight = dateWeight,
                    color = theme.accentOrange,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}