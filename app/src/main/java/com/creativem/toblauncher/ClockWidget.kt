package com.creativem.toblauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
    val theme = LocalDashboardTheme.current // TEMA DE COLORES
    val isBold = LocalIsBoldText.current     // ESTADO DE NEGRITA

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
            currentDateText = SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(cal.time).uppercase()

            delay(1000L)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // ==========================================
        // 1. CAPA DE FONDO: RELOJ ANALÓGICO ELEGANTE EN CANVAS
        // ==========================================
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 4.dp.toPx()

            // A) MARCAS DE LAS 12 HORAS
            for (i in 0..11) {
                val angleDeg = i * 30f - 90f
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val innerR = radius - 6.dp.toPx()
                val outerR = radius

                val startX = center.x + innerR * cos(angleRad).toFloat()
                val startY = center.y + innerR * sin(angleRad).toFloat()
                val endX = center.x + outerR * cos(angleRad).toFloat()
                val endY = center.y + outerR * sin(angleRad).toFloat()

                drawLine(
                    color = theme.cardBorder.copy(alpha = 0.8f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // B) AGUJA HORA
            val hourAngleDeg = ((hours % 12) + minutes / 60f) * 30f - 90f
            val hourRad = Math.toRadians(hourAngleDeg.toDouble())
            val hourEnd = Offset(
                x = center.x + (radius * 0.45f * cos(hourRad)).toFloat(),
                y = center.y + (radius * 0.45f * sin(hourRad)).toFloat()
            )
            drawLine(
                color = theme.accentCyan.copy(alpha = 0.35f),
                start = center,
                end = hourEnd,
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // C) AGUJA MINUTERO
            val minAngleDeg = (minutes + seconds / 60f) * 6f - 90f
            val minRad = Math.toRadians(minAngleDeg.toDouble())
            val minEnd = Offset(
                x = center.x + (radius * 0.70f * cos(minRad)).toFloat(),
                y = center.y + (radius * 0.70f * sin(minRad)).toFloat()
            )
            drawLine(
                color = theme.accentPurple.copy(alpha = 0.35f),
                start = center,
                end = minEnd,
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // D) AGUJA SEGUNDERO
            val secAngleDeg = seconds * 6f - 90f
            val secRad = Math.toRadians(secAngleDeg.toDouble())
            val secEnd = Offset(
                x = center.x + (radius * 0.82f * cos(secRad)).toFloat(),
                y = center.y + (radius * 0.82f * sin(secRad)).toFloat()
            )
            drawLine(
                color = theme.accentOrange.copy(alpha = 0.55f),
                start = center,
                end = secEnd,
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // ==========================================
        // 2. CAPA SUPERIOR: FECHA ARRIBA + HORA DIGITAL DEBAJO
        // ==========================================
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. FECHA PEQUEÑA ARRIBA
            Text(
                text = currentDateText,
                fontSize = 11.sp,
                fontWeight = dateWeight,
                color = theme.accentOrange,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 2. HORA DIGITAL GIGANTE DEBAJO CON AM/PM PEQUEÑO
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = digitalTimeDigits,
                    fontSize = 42.sp,
                    fontWeight = timeWeight,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    letterSpacing = (-1).sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = amPmText,
                    fontSize = 12.sp,
                    fontWeight = dateWeight,
                    color = theme.accentCyan,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}