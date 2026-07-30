package com.creativem.toblauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.material.icons.filled.Explore
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
    bearing: Float,
    onRequestAppSelection: (slot: Int) -> Unit
) {
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current
    val isBold = LocalIsBoldText.current
    val activeFontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Normal

    val prefs = remember { context.getSharedPreferences("speedometer_apps_prefs", Context.MODE_PRIVATE) }
    val slot1Pkg = remember(prefs.getString("slot_1", null)) { prefs.getString("slot_1", "com.spotify.music") ?: "com.spotify.music" }
    val slot2Pkg = remember(prefs.getString("slot_2", null)) { prefs.getString("slot_2", "com.google.android.apps.maps") ?: "com.google.android.apps.maps" }

    var hardwareCompassBearing by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val azimuthDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    hardwareCompassBearing = (azimuthDegrees + 360f) % 360f
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        rotationSensor?.let { sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    val effectiveBearing = if (speedKmH > 3f && bearing != 0f) bearing else hardwareCompassBearing

    val animatedSpeed by animateFloatAsState(
        targetValue = speedKmH,
        animationSpec = tween(durationMillis = 800),
        label = "SpeedAnimation"
    )

    val dynamicSpeedColor = remember(animatedSpeed, theme) {
        when {
            animatedSpeed < 60f -> {
                val fraction = (animatedSpeed / 60f).coerceIn(0f, 1f)
                lerp(theme.accentCyan, theme.accentPurple, fraction)
            }
            else -> {
                val fraction = ((animatedSpeed - 60f) / 100f).coerceIn(0f, 1f)
                lerp(theme.accentPurple, theme.accentOrange, fraction)
            }
        }
    }

    val (cardinalFullText, cardinalDegrees) = remember(effectiveBearing) {
        val degrees = (effectiveBearing % 360 + 360) % 360
        val name = when (degrees) {
            in 22.5f..67.5f -> "NORDESTE (NE)"
            in 67.5f..112.5f -> "ESTE (E)"
            in 112.5f..157.5f -> "SUDESTE (SE)"
            in 157.5f..202.5f -> "SUR (S)"
            in 202.5f..247.5f -> "SUROESTE (SO)"
            in 247.5f..292.5f -> "OESTE (O)"
            in 292.5f..337.5f -> "NOROESTE (NO)"
            else -> "NORTE (N)"
        }
        Pair(name, degrees.toInt())
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // ==========================================
        // 1. RELOJ ANALÓGICO 3D HUNDIDO (50%)
        // ==========================================
        Box(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            // Sombra interior falsa dibujada en fondo
            Box(modifier = Modifier
                .fillMaxSize(0.9f)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.Black.copy(0.4f), Color.Transparent)))
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f
                val strokeWidthPx = 12.dp.toPx()

                val arcRadius = maxRadius - (strokeWidthPx / 2f)
                val ticksRadius = arcRadius - 10.dp.toPx()
                val textRadius = ticksRadius - 16.dp.toPx()

                // Arco Base 3D (Simula un carril físico)
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color(0xFF0A0C12), Color(0xFF1E2638))),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                )

                val speedProgress = (animatedSpeed / 220f).coerceIn(0f, 1f)

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

                val totalSpeed = 220
                val mainStep = 20

                val textPaintDimmed = AndroidPaint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 10.dp.toPx()
                    typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                }

                val textPaintActive = AndroidPaint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 12.dp.toPx()
                    typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    textAlign = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                }

                for (s in 0..totalSpeed step 10) {
                    val angleDeg = 135f + (s.toFloat() / totalSpeed.toFloat()) * 270f
                    val angleRad = Math.toRadians(angleDeg.toDouble())

                    val isMainTick = (s % mainStep == 0)
                    val tickStartR = ticksRadius
                    val tickEndR = if (isMainTick) ticksRadius - 7.dp.toPx() else ticksRadius - 4.dp.toPx()

                    val startX = center.x + tickStartR * cos(angleRad).toFloat()
                    val startY = center.y + tickStartR * sin(angleRad).toFloat()
                    val endX = center.x + tickEndR * cos(angleRad).toFloat()
                    val endY = center.y + tickEndR * sin(angleRad).toFloat()

                    val isPassed = s <= animatedSpeed

                    drawLine(
                        color = if (isPassed) dynamicSpeedColor else Color(0xFF2C3545),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (isMainTick) 2.5.dp.toPx() else 1.2.dp.toPx(),
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

                val needleAngleRad = Math.toRadians((135f + (270f * speedProgress)).toDouble())
                val needleLength = arcRadius - 2.dp.toPx()
                val needleEnd = Offset(
                    x = center.x + (needleLength * cos(needleAngleRad)).toFloat(),
                    y = center.y + (needleLength * sin(needleAngleRad)).toFloat()
                )

                // Aguja con relieve
                drawLine(
                    color = Color.Black.copy(0.5f),
                    start = Offset(center.x + 2f, center.y + 2f),
                    end = Offset(needleEnd.x + 2f, needleEnd.y + 2f),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = dynamicSpeedColor,
                    start = center,
                    end = needleEnd,
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = center)
                drawCircle(color = dynamicSpeedColor, radius = 3.dp.toPx(), center = center)
            }
        }

        // ==========================================
        // 2. VELOCIDAD DIGITAL Y BRÚJULA (EFECTO BOTÓN FÍSICO 3D)
        // ==========================================
        Column(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // PANEL LCD DIGITAL 3D
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = Color.Black)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF222A3A), Color(0xFF0F131C))))
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(Color.White.copy(0.3f), Color.Transparent, Color.Black)),
                        RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${animatedSpeed.toInt()}",
                        fontSize = 44.sp,
                        fontWeight = activeFontWeight,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = "KM / H",
                        fontSize = 11.sp,
                        fontWeight = activeFontWeight,
                        color = dynamicSpeedColor,
                        letterSpacing = 1.sp
                    )
                }
            }

            // PANEL BRÚJULA 3D
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF1E2636), Color(0xFF0A0D14))))
                    .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(0.15f), Color.Black)), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Brújula",
                        tint = dynamicSpeedColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = cardinalFullText,
                            fontSize = 11.sp,
                            fontWeight = activeFontWeight,
                            color = Color.White
                        )
                        Text(
                            text = "RUMBO: $cardinalDegrees°",
                            fontSize = 10.sp,
                            fontWeight = activeFontWeight,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // ==========================================
        // 3. APPS CUADRITOS 3D
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