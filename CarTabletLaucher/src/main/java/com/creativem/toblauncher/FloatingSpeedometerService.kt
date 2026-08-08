package com.creativem.toblauncher

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class FloatingSpeedometerService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingRootView: View? = null
    private var analogGaugeView: AnalogGaugeView? = null

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null

    private val handler = Handler(Looper.getMainLooper())
    private var btnCloseView: ImageButton? = null
    private var btnExpandView: ImageButton? = null
    private var resizeHandleView: TextView? = null

    private val hideControlsRunnable = Runnable {
        btnCloseView?.visibility = View.GONE
        btnExpandView?.visibility = View.GONE
        resizeHandleView?.visibility = View.GONE
    }

    private fun resetControlsTimer() {
        btnCloseView?.visibility = View.VISIBLE
        btnExpandView?.visibility = View.VISIBLE
        resizeHandleView?.visibility = View.VISIBLE
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 5000L)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        startForegroundServiceNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val savedTheme = ThemeManager.getSavedTheme(this)
        val primaryAccentInt = savedTheme.accentCyan.toAndroidColorInt()
        val secondaryAccentInt = savedTheme.accentPurple.toAndroidColorInt()
        val warningAccentInt = savedTheme.accentOrange.toAndroidColorInt()

        val isBold = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            .getBoolean("is_bold", true)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density

        val responsiveWidth = (screenWidth * 0.26f).toInt().coerceIn((200 * density).toInt(), (420 * density).toInt())
        val responsiveHeight = responsiveWidth

        val defaultX = screenWidth - responsiveWidth
        val defaultY = screenHeight - responsiveHeight

        val params = WindowManager.LayoutParams(
            responsiveWidth,
            responsiveHeight,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = defaultX
            y = defaultY
        }

        val rootLayout = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#07090E"))
                cornerRadius = 26 * density
                setStroke((1.8f * density).toInt(), primaryAccentInt)
            }
            clipToOutline = true
        }

        analogGaugeView = AnalogGaugeView(
            context = this,
            primaryColor = primaryAccentInt,
            secondaryColor = secondaryAccentInt,
            warningColor = warningAccentInt,
            isBoldText = isBold
        ).apply {
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
        }

        rootLayout.addView(
            analogGaugeView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        // Botón Cerrar
        val btnClose = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#CC000000"))
                shape = GradientDrawable.OVAL
            }
            setColorFilter(AndroidColor.parseColor("#FF5252"))
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                stopSelf()
            }
        }
        btnCloseView = btnClose
        val closeParams = FrameLayout.LayoutParams((28 * density).toInt(), (28 * density).toInt(), Gravity.TOP or Gravity.START).apply {
            setMargins((8 * density).toInt(), (8 * density).toInt(), 0, 0)
        }
        rootLayout.addView(btnCloseView, closeParams)

        // Botón Expandir
        val btnExpand = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            background = GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#CC000000"))
                shape = GradientDrawable.OVAL
            }
            setColorFilter(primaryAccentInt)
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(intent)
                stopSelf()
            }
        }
        btnExpandView = btnExpand
        val expandParams = FrameLayout.LayoutParams((32 * density).toInt(), (32 * density).toInt(), Gravity.TOP or Gravity.END).apply {
            setMargins(0, (8 * density).toInt(), (8 * density).toInt(), 0)
        }
        rootLayout.addView(btnExpandView, expandParams)

        // Drag Handler
        rootLayout.setOnTouchListener(object : View.OnTouchListener {
            private var dragWindowX = 0
            private var dragWindowY = 0
            private var dragTouchX = 0f
            private var dragTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                resetControlsTimer()
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dragWindowX = params.x
                        dragWindowY = params.y
                        dragTouchX = event.rawX
                        dragTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - dragTouchX).toInt()
                        val deltaY = (event.rawY - dragTouchY).toInt()

                        if (abs(deltaX) > 4 || abs(deltaY) > 4) {
                            isDragging = true
                        }

                        if (isDragging) {
                            params.x = dragWindowX + deltaX
                            params.y = dragWindowY + deltaY
                            try {
                                windowManager?.updateViewLayout(rootLayout, params)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            resetControlsTimer()
                        }
                        return true
                    }
                }
                return false
            }
        })

        // Redimensionador
        val handle = TextView(this).apply {
            text = " ↘ "
            setTextColor(primaryAccentInt)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#AA000000"))
                cornerRadius = 6 * density
            }
        }

        handle.setOnTouchListener(object : View.OnTouchListener {
            private var startW = 0
            private var startH = 0
            private var startTouchX = 0f
            private var startTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                resetControlsTimer()
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startW = params.width
                        startH = params.height
                        startTouchX = event.rawX
                        startTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - startTouchX).toInt()
                        val deltaY = (event.rawY - startTouchY).toInt()

                        val newW = (startW + deltaX).coerceIn((140 * density).toInt(), (520 * density).toInt())
                        val newH = (startH + deltaY).coerceIn((140 * density).toInt(), (520 * density).toInt())

                        params.width = newW
                        params.height = newH
                        try {
                            windowManager?.updateViewLayout(rootLayout, params)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        return true
                    }
                }
                return false
            }
        })

        resizeHandleView = handle
        val handleParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, (6 * density).toInt(), (6 * density).toInt())
        }
        rootLayout.addView(resizeHandleView, handleParams)

        resetControlsTimer()

        floatingRootView = rootLayout
        try {
            windowManager?.addView(floatingRootView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        startGpsUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceNotification()
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "speedometer_floating_channel"
        val channelName = "Velocímetro Flotante"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Velocímetro Activo")
            .setContentText("Capturando velocidad GPS")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            startForeground(1001, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startGpsUpdates() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return

        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
                .setMinUpdateIntervalMillis(250L)
                .setMinUpdateDistanceMeters(0f)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    processNewLocation(loc)
                }
            }

            fusedLocationClient?.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processNewLocation(location: Location) {
        var speedKmH = 0f

        if (location.hasSpeed() && location.speed > 0f) {
            speedKmH = location.speed * 3.6f
        } else if (lastLocation != null) {
            val distanceMeters = location.distanceTo(lastLocation!!)
            val timeSeconds = (location.time - lastLocation!!.time) / 1000f
            if (timeSeconds > 0) {
                speedKmH = (distanceMeters / timeSeconds) * 3.6f
            }
        }

        lastLocation = location

        if (speedKmH < 1.5f) speedKmH = 0f

        analogGaugeView?.currentSpeed = speedKmH
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(hideControlsRunnable)
        try {
            locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }

            if (floatingRootView != null) {
                windowManager?.removeViewImmediate(floatingRootView)
                floatingRootView = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, FloatingSpeedometerService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    val intent = Intent(context, FloatingSpeedometerService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(context, intent)
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, FloatingSpeedometerService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

private fun ComposeColor.toAndroidColorInt(): Int {
    return AndroidColor.argb(
        (this.alpha * 255).toInt(),
        (this.red * 255).toInt(),
        (this.green * 255).toInt(),
        (this.blue * 255).toInt()
    )
}

/**
 * 🌌 AnalogGaugeView con Alineación Geométrica y Estética Idéntica a ModernSpeedometerWidget
 */
class AnalogGaugeView(
    context: Context,
    var primaryColor: Int = AndroidColor.parseColor("#00E5FF"),
    var secondaryColor: Int = AndroidColor.parseColor("#AB47BC"),
    var warningColor: Int = AndroidColor.parseColor("#FF7043"),
    var isBoldText: Boolean = true
) : View(context) {

    var currentSpeed: Float = 0f
        set(value) {
            field = value
            postInvalidate()
        }

    private val baseTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    // Pinceles
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val textPaintDimmed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(180, 180, 195, 210)
        textAlign = Paint.Align.CENTER
        typeface = baseTypeface
    }
    private val textPaintActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textAlign = Paint.Align.CENTER
        typeface = baseTypeface
    }
    private val digitalSpeedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            letterSpacing = -0.05f
        }
    }
    private val digitalUnitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = baseTypeface
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            letterSpacing = 0.20f
        }
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    // Interpolación de colores equivalente a Compose lerp
    private fun lerpColor(colorStart: Int, colorEnd: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val a = (AndroidColor.alpha(colorStart) + f * (AndroidColor.alpha(colorEnd) - AndroidColor.alpha(colorStart))).toInt()
        val r = (AndroidColor.red(colorStart) + f * (AndroidColor.red(colorEnd) - AndroidColor.red(colorStart))).toInt()
        val g = (AndroidColor.green(colorStart) + f * (AndroidColor.green(colorEnd) - AndroidColor.green(colorStart))).toInt()
        val b = (AndroidColor.blue(colorStart) + f * (AndroidColor.blue(colorEnd) - AndroidColor.blue(colorStart))).toInt()
        return AndroidColor.argb(a, r, g, b)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = resources.displayMetrics.density
        val scaledDensity = resources.displayMetrics.scaledDensity

        val center = PointF(w / 2f, h / 2f)
        val minDim = Math.min(w, h)
        val maxDim = Math.max(w, h)

        // Exactas proporciones calculadas según ModernSpeedometerWidget (Canvas padding 4dp)
        val outerMargin = 4f * density
        val maxRadius = (minDim / 2f) - outerMargin
        val strokeWidthPx = 12f * density

        val arcRadius = maxRadius - (strokeWidthPx / 2f)
        val innerArcEdge = arcRadius - (strokeWidthPx / 2f)
        val ticksRadius = innerArcEdge - (2f * density)
        val textRadius = ticksRadius - (36f * density)
        val needleLength = innerArcEdge - (14f * density)

        val totalSpeed = 150f
        val speedProgress = (currentSpeed / totalSpeed).coerceIn(0f, 1f)

        // 🎨 1. CALCULAR COLOR DINÁMICO IDÉNTICO A COMPOSE
        val dynamicSpeedColor = if (currentSpeed < 50f) {
            val fraction = (currentSpeed / 50f).coerceIn(0f, 1f)
            lerpColor(primaryColor, secondaryColor, fraction)
        } else {
            val fraction = ((currentSpeed - 50f) / 100f).coerceIn(0f, 1f)
            lerpColor(secondaryColor, warningColor, fraction)
        }

        // 🌌 2. FONDO CÓNCAVO BASE
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                center.x, center.y, Math.max(maxRadius * 1.2f, 450f),
                intArrayOf(AndroidColor.parseColor("#1A2232"), AndroidColor.parseColor("#0A0E16")),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(center.x, center.y, maxRadius, bgPaint)

        // 🌌 3. RAYOS 3D / HUD DESDE EL CÍRCULO HASTA EL BORDES EXTERIORES
        val rayCount = 36
        val rayStartRadius = arcRadius + (strokeWidthPx / 2f) + (2f * density)
        val outerBoxExtent = maxDim

        for (i in 0 until rayCount) {
            val rayAngleDeg = i * (360f / rayCount)
            val rayAngleRad = Math.toRadians(rayAngleDeg.toDouble())

            val rx1 = center.x + rayStartRadius * cos(rayAngleRad).toFloat()
            val ry1 = center.y + rayStartRadius * sin(rayAngleRad).toFloat()
            val rx2 = center.x + outerBoxExtent * cos(rayAngleRad).toFloat()
            val ry2 = center.y + outerBoxExtent * sin(rayAngleRad).toFloat()

            val currentSpeedAngle = 135f + (270f * speedProgress)
            val angleDiff = abs(rayAngleDeg - currentSpeedAngle)
            val isNearNeedle = angleDiff < 30f || (360f - angleDiff) < 30f

            val rayAlpha = if (isNearNeedle) 0.35f else 0.12f
            val baseColor = if (isNearNeedle) dynamicSpeedColor else primaryColor

            linePaint.shader = LinearGradient(
                rx1, ry1, rx2, ry2,
                intArrayOf(
                    AndroidColor.argb((rayAlpha * 255).toInt(), AndroidColor.red(baseColor), AndroidColor.green(baseColor), AndroidColor.blue(baseColor)),
                    AndroidColor.argb((rayAlpha * 0.5f * 255).toInt(), AndroidColor.red(secondaryColor), AndroidColor.green(secondaryColor), AndroidColor.blue(secondaryColor)),
                    AndroidColor.TRANSPARENT
                ),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            linePaint.strokeWidth = if (isNearNeedle) 1.8f * density else 1.0f * density
            canvas.drawLine(rx1, ry1, rx2, ry2, linePaint)
        }
        linePaint.shader = null

        // 🌌 4. ANILLOS CONCÉNTRICOS DE PROFUNDIDAD
        val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            pathEffect = DashPathEffect(floatArrayOf(8f * density, 12f * density), 0f)
            shader = SweepGradient(
                center.x, center.y,
                intArrayOf(
                    AndroidColor.argb((0.12f * 255).toInt(), AndroidColor.red(primaryColor), AndroidColor.green(primaryColor), AndroidColor.blue(primaryColor)),
                    AndroidColor.argb((0.18f * 255).toInt(), AndroidColor.red(secondaryColor), AndroidColor.green(secondaryColor), AndroidColor.blue(secondaryColor)),
                    AndroidColor.argb((0.08f * 255).toInt(), AndroidColor.red(warningColor), AndroidColor.green(warningColor), AndroidColor.blue(warningColor)),
                    AndroidColor.argb((0.12f * 255).toInt(), AndroidColor.red(primaryColor), AndroidColor.green(primaryColor), AndroidColor.blue(primaryColor))
                ),
                null
            )
        }
        for (rOffset in floatArrayOf(8f * density, 18f * density, 28f * density)) {
            canvas.drawCircle(center.x, center.y, arcRadius + (strokeWidthPx / 2f) + rOffset, dashedPaint)
        }

        // 🌌 5. ARCO BASE DE FONDO
        val arcRect = RectF(center.x - arcRadius, center.y - arcRadius, center.x + arcRadius, center.y + arcRadius)
        arcPaint.strokeCap = Paint.Cap.ROUND
        arcPaint.strokeWidth = strokeWidthPx

        arcPaint.shader = SweepGradient(
            center.x, center.y,
            intArrayOf(
                AndroidColor.argb((0.25f * 255).toInt(), AndroidColor.red(secondaryColor), AndroidColor.green(secondaryColor), AndroidColor.blue(secondaryColor)),
                AndroidColor.argb((0.25f * 255).toInt(), AndroidColor.red(primaryColor), AndroidColor.green(primaryColor), AndroidColor.blue(primaryColor))
            ), null
        )
        canvas.drawArc(arcRect, 135f, 270f, false, arcPaint)
        arcPaint.shader = null

        // 🌌 6. ARCO DE ZONA DE ADVERTENCIA (> 120 KM/H)
        val redZoneStartAngle = 135f + (120f / totalSpeed) * 270f
        val redZoneSweepAngle = (30f / totalSpeed) * 270f
        arcPaint.color = AndroidColor.argb((0.8f * 255).toInt(), AndroidColor.red(warningColor), AndroidColor.green(warningColor), AndroidColor.blue(warningColor))
        canvas.drawArc(arcRect, redZoneStartAngle, redZoneSweepAngle, false, arcPaint)

        // 🌌 7. BARRA DE VELOCIDAD ACTIVA
        if (speedProgress > 0f) {
            // Resplandor (Glow)
            arcPaint.color = AndroidColor.argb((0.35f * 255).toInt(), AndroidColor.red(dynamicSpeedColor), AndroidColor.green(dynamicSpeedColor), AndroidColor.blue(dynamicSpeedColor))
            arcPaint.strokeWidth = strokeWidthPx * 1.5f
            canvas.drawArc(arcRect, 135f, 270f * speedProgress, false, arcPaint)

            // Arco Sólido
            arcPaint.color = dynamicSpeedColor
            arcPaint.strokeWidth = strokeWidthPx
            canvas.drawArc(arcRect, 135f, 270f * speedProgress, false, arcPaint)
        }

        // 🌌 8. TICKS Y NÚMEROS
        textPaintDimmed.textSize = 20.5f * scaledDensity
        textPaintActive.textSize = 22.0f * scaledDensity

        for (s in 0..totalSpeed.toInt() step 5) {
            val angleDeg = 135f + (s.toFloat() / totalSpeed) * 270f
            val angleRad = Math.toRadians(angleDeg.toDouble())

            val isMainTick = (s % 20 == 0 || s == 150)
            val isMediumTick = (s % 10 == 0 && !isMainTick)

            val tickLength = when {
                isMainTick -> 10f * density
                isMediumTick -> 6f * density
                else -> 3.5f * density
            }

            val tickStartR = ticksRadius
            val tickEndR = ticksRadius - tickLength

            val sx = center.x + tickStartR * cos(angleRad).toFloat()
            val sy = center.y + tickStartR * sin(angleRad).toFloat()
            val ex = center.x + tickEndR * cos(angleRad).toFloat()
            val ey = center.y + tickEndR * sin(angleRad).toFloat()

            val isPassed = s <= currentSpeed

            val tickColor = when {
                s >= 120 -> warningColor
                isPassed -> dynamicSpeedColor
                isMainTick -> AndroidColor.WHITE
                else -> AndroidColor.argb(128, 128, 128, 128)
            }

            // Sombra tick (0.7f alpha black)
            linePaint.color = AndroidColor.argb((0.7f * 255).toInt(), 0, 0, 0)
            linePaint.strokeWidth = if (isMainTick) 3.5f * density else 1.8f * density
            canvas.drawLine(sx + 1f, sy + 1f, ex + 1f, ey + 1f, linePaint)

            // Tick principal
            linePaint.color = tickColor
            linePaint.strokeWidth = if (isMainTick) 3.0f * density else if (isMediumTick) 1.8f * density else 1.0f * density
            canvas.drawLine(sx, sy, ex, ey, linePaint)

            // Números del Dial
            if (isMainTick) {
                val numX = center.x + textRadius * cos(angleRad).toFloat()
                val numY = center.y + textRadius * sin(angleRad).toFloat() + (3.5f * density)

                canvas.drawText(
                    s.toString(),
                    numX,
                    numY,
                    if (isPassed) textPaintActive else textPaintDimmed
                )
            }
        }

        // 🌌 9. AGUJA Y PIVOTE 3D
        val needleAngleRad = Math.toRadians((135f + (270f * speedProgress)).toDouble())
        val needleEndX = center.x + (needleLength * cos(needleAngleRad)).toFloat()
        val needleEndY = center.y + (needleLength * sin(needleAngleRad)).toFloat()

        // Sombra aguja
        needlePaint.color = AndroidColor.argb((0.7f * 255).toInt(), 0, 0, 0)
        needlePaint.strokeWidth = 4.0f * density
        canvas.drawLine(center.x + 3f * density, center.y + 3f * density, needleEndX + 3f * density, needleEndY + 3f * density, needlePaint)

        // Aguja
        needlePaint.color = dynamicSpeedColor
        needlePaint.strokeWidth = 3.0f * density
        canvas.drawLine(center.x, center.y, needleEndX, needleEndY, needlePaint)

        // Pivote Multicapa
        needlePaint.color = AndroidColor.BLACK
        canvas.drawCircle(center.x, center.y, 7.0f * density, needlePaint)
        needlePaint.color = primaryColor
        canvas.drawCircle(center.x, center.y, 5.0f * density, needlePaint)
        needlePaint.color = AndroidColor.WHITE
        canvas.drawCircle(center.x, center.y, 2.0f * density, needlePaint)

        // 🌌 10. TEXTO DIGITAL DE VELOCIDAD POSICIONADO EXACTAMENTE
        digitalSpeedPaint.textSize = minDim * 0.20f
        digitalUnitPaint.textSize = (minDim * 0.048f).coerceAtLeast(8f * density)
        digitalUnitPaint.color = dynamicSpeedColor

        val regionTop = center.y
        val regionBottom = center.y + arcRadius
        val regionMidpoint = (regionTop + regionBottom) / 2f

        val digitalText = "${currentSpeed.toInt()}"
        val speedBounds = Rect()
        digitalSpeedPaint.getTextBounds(digitalText, 0, digitalText.length, speedBounds)
        val digitalSpeedY = regionMidpoint + (speedBounds.height() / 2f)

        // Dibujar Número Gigante Centrado Geometricamente
        canvas.drawText(digitalText, center.x, digitalSpeedY, digitalSpeedPaint)

        // Dibujar "KM / H" Pegado Justo al Borde Inferior del Círculo
        val digitalUnitY = regionBottom - (strokeWidthPx / 2f) - (2f * density)
        canvas.drawText("KM / H", center.x, digitalUnitY, digitalUnitPaint)
    }
}