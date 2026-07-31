package com.creativem.toblauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernSpeedometerWidget(
    speedKmH: Float,
    bearing: Float, // Parámetro restaurado para mantener compatibilidad con MainActivity
    onRequestAppSelection: (slot: Int) -> Unit
) {
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current
    val isBold = LocalIsBoldText.current
    val activeFontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Normal

    val prefs = remember { context.getSharedPreferences("speedometer_apps_prefs", Context.MODE_PRIVATE) }
    val slot1Pkg = remember(prefs.getString("slot_1", null)) { prefs.getString("slot_1", "com.spotify.music") ?: "com.spotify.music" }
    val slot2Pkg = remember(prefs.getString("slot_2", null)) { prefs.getString("slot_2", "com.google.android.apps.maps") ?: "com.google.android.apps.maps" }

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
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // ==========================================
        // 1. RELOJ ANALÓGICO CON EFECTO CONTRÁCTIL 3D (50%)
        // ==========================================
        Box(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF222A3A), Color(0xFF0F131C))))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White.copy(0.2f), Color.Transparent, Color.Black)),
                    RoundedCornerShape(24.dp)
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.Black.copy(0.4f), Color.Transparent)))
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = (size.minDimension / 2f) - 4.dp.toPx()
                val strokeWidthPx = 10.dp.toPx()

                val arcRadius = maxRadius - (strokeWidthPx / 2f)
                val ticksRadius = arcRadius - 8.dp.toPx()
                val textRadius = ticksRadius - 14.dp.toPx()

                // Arco Base 3D
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color(0xFF0A0C12), Color(0xFF1E2638))),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                )

                val totalSpeed = 150
                val speedProgress = (animatedSpeed / totalSpeed.toFloat()).coerceIn(0f, 1f)

                // Resplandor Aura Neón
                drawArc(
                    color = dynamicSpeedColor.copy(alpha = 0.3f),
                    startAngle = 135f,
                    sweepAngle = 270f * speedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx * 2f, cap = StrokeCap.Round),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                )

                // Barra de Velocidad Sólida
                drawArc(
                    color = dynamicSpeedColor,
                    startAngle = 135f,
                    sweepAngle = 270f * speedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx * 0.8f, cap = StrokeCap.Round),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                )

                val mainStep = 30 // Divisiones: 0, 30, 60, 90, 120, 150

                val textPaintDimmed = AndroidPaint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 30.dp.toPx()
                    typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                }

                val textPaintActive = AndroidPaint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 40.dp.toPx()
                    typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                }

                for (s in 0..totalSpeed step 10) {
                    val angleDeg = 135f + (s.toFloat() / totalSpeed.toFloat()) * 270f
                    val angleRad = Math.toRadians(angleDeg.toDouble())

                    val isMainTick = (s % mainStep == 0)
                    val tickStartR = ticksRadius
                    val tickEndR = if (isMainTick) ticksRadius - 6.dp.toPx() else ticksRadius - 3.dp.toPx()

                    val startX = center.x + tickStartR * cos(angleRad).toFloat()
                    val startY = center.y + tickStartR * sin(angleRad).toFloat()
                    val endX = center.x + tickEndR * cos(angleRad).toFloat()
                    val endY = center.y + tickEndR * sin(angleRad).toFloat()

                    val isPassed = s <= animatedSpeed

                    drawLine(
                        color = if (isPassed) dynamicSpeedColor else Color(0xFF2C3545),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (isMainTick) 2.2.dp.toPx() else 1.0.dp.toPx(),
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

                val needleAngleRad = Math.toRadians((135f + (270f * speedProgress)).toDouble())
                val needleLength = arcRadius - 2.dp.toPx()
                val needleEnd = Offset(
                    x = center.x + (needleLength * cos(needleAngleRad)).toFloat(),
                    y = center.y + (needleLength * sin(needleAngleRad)).toFloat()
                )

                // Aguja con relieve
                drawLine(
                    color = Color.Black.copy(0.5f),
                    start = Offset(center.x + 1.5f, center.y + 1.5f),
                    end = Offset(needleEnd.x + 1.5f, needleEnd.y + 1.5f),
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

                drawCircle(color = Color.White, radius = 5.dp.toPx(), center = center)
                drawCircle(color = dynamicSpeedColor, radius = 2.5.dp.toPx(), center = center)
            }
        }

        // ==========================================
        // 2. VELOCIDAD DIGITAL UNIFICADA (EFECTO LCD 3D GRANDE)
        // ==========================================
        Box(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight()
                .padding(horizontal = 6.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF222A3A), Color(0xFF0F131C))))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White.copy(0.3f), Color.Transparent, Color.Black)),
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
                    fontSize = 60.sp,
                    fontWeight = activeFontWeight,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    letterSpacing = (-1.5).sp
                )
                Text(
                    text = "KM / H",
                    fontSize = 20.sp,
                    fontWeight = activeFontWeight,
                    color = dynamicSpeedColor,
                    letterSpacing = 1.5.sp
                )
            }
        }

        // ==========================================
        // 3. ACCESOS DIRECTOS A APLICACIONES 3D
        // ==========================================
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppSlotSquare3D(
                modifier = Modifier.weight(1f),
                packageName = slot1Pkg,
                accentColor = dynamicSpeedColor,
                onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage(slot1Pkg)
                    if (intent != null) context.startActivity(intent) else onRequestAppSelection(1)
                },
                onLongClick = { onRequestAppSelection(1) }
            )

            AppSlotSquare3D(
                modifier = Modifier.weight(1f),
                packageName = slot2Pkg,
                accentColor = dynamicSpeedColor,
                onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage(slot2Pkg)
                    if (intent != null) context.startActivity(intent) else onRequestAppSelection(2)
                },
                onLongClick = { onRequestAppSelection(2) }
            )
        }
    }
}

// COMPONENTE AUXILIAR PARA BOTONES DE APPS 3D
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppSlotSquare3D(
    modifier: Modifier = Modifier,
    packageName: String,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bmp = Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF222A3A), Color(0xFF0F131C))))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(0.25f), Color.Black)), RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = "App Shortcut",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Default.Apps, contentDescription = "Elegir App", tint = accentColor, modifier = Modifier.fillMaxSize(0.7f))
        }
    }
}