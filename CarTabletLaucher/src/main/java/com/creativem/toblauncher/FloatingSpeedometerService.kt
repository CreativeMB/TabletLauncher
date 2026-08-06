package com.creativem.toblauncher

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlin.math.cos
import kotlin.math.sin

class FloatingSpeedometerService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingRootView: View? = null
    private var analogGaugeView: AnalogGaugeView? = null

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var nativeLocationListener: LocationListener? = null

    private val handler = Handler(Looper.getMainLooper())
    private var btnCloseView: ImageButton? = null
    private var btnExpandView: ImageButton? = null
    private var resizeHandleView: TextView? = null

    // ⏱️ Ocultar los botones de las esquinas a los 5 segundos
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

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val savedTheme = ThemeManager.getSavedTheme(this)
        val primaryAccentInt = savedTheme.accentCyan.toAndroidColorInt()
        val secondaryAccentInt = savedTheme.accentPurple.toAndroidColorInt()
        val warningAccentInt = savedTheme.accentOrange.toAndroidColorInt()

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

        val responsiveWidth = (screenWidth * 0.22f).toInt().coerceIn((170 * density).toInt(), (380 * density).toInt())
        val responsiveHeight = responsiveWidth

        val marginX = (screenWidth * 0.03f).toInt()
        val marginY = (screenHeight * 0.10f).toInt()

        // Posición Inicial Abajo a la Derecha
        val defaultX = screenWidth - responsiveWidth - marginX
        val defaultY = screenHeight - responsiveHeight - marginY

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

        // 1. Contenedor Raíz Marco Tech (Sin barra negra superior)
        val rootLayout = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#121722"))
                cornerRadius = 18 * density
                setStroke((2 * density).toInt(), primaryAccentInt)
            }
            clipToOutline = true
        }

        // 2. Reloj Análogo Único
        analogGaugeView = AnalogGaugeView(
            context = this,
            primaryColor = primaryAccentInt,
            secondaryColor = secondaryAccentInt,
            warningColor = warningAccentInt
        ).apply {
            setPadding((6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
        }

        rootLayout.addView(analogGaugeView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // 🟢 3. BOTÓN MINIMALISTA CERRAR (Esquina Superior Izquierda)
        val btnClose = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#AA000000"))
                shape = GradientDrawable.OVAL
            }
            setColorFilter(AndroidColor.parseColor("#FF5252"))
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                stopSelf()
            }
        }
        btnCloseView = btnClose
        val closeParams = FrameLayout.LayoutParams((26 * density).toInt(), (26 * density).toInt(), Gravity.TOP or Gravity.START).apply {
            setMargins((6 * density).toInt(), (6 * density).toInt(), 0, 0)
        }
        rootLayout.addView(btnCloseView, closeParams)

        // 🟢 4. BOTÓN MINIMALISTA EXPANDIR / LAUNCHER (Esquina Superior Derecha)
        val btnExpand = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            background = GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#AA000000"))
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
        val expandParams = FrameLayout.LayoutParams((26 * density).toInt(), (26 * density).toInt(), Gravity.TOP or Gravity.END).apply {
            setMargins(0, (6 * density).toInt(), (6 * density).toInt(), 0)
        }
        rootLayout.addView(btnExpandView, expandParams)

        // 🖐️ 5. ARRASTRE DESDE CUALQUIER PARTE DE LA PANTALLA FLOTANTE
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

                        if (Math.abs(deltaX) > 4 || Math.abs(deltaY) > 4) {
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

        // ↘️ 6. MANIJA DE REDIMENSIONAR (Esquina Inferior Derecha)
        val handle = TextView(this).apply {
            text = " ↘ "
            setTextColor(primaryAccentInt)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#AA000000"))
                cornerRadius = 4 * density
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

                        val newW = (startW + deltaX).coerceIn((130 * density).toInt(), (500 * density).toInt())
                        val newH = (startH + deltaY).coerceIn((130 * density).toInt(), (500 * density).toInt())

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
            setMargins(0, 0, (4 * density).toInt(), (4 * density).toInt())
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

    private fun startGpsUpdates() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return

        try {
            val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
            nativeLocationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    processNewLocation(location)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, nativeLocationListener!!)
            }
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, nativeLocationListener!!)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 800L)
                .setMinUpdateIntervalMillis(400L)
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
        val speedKmH = if (location.hasSpeed()) {
            (location.speed * 3.6f).coerceAtLeast(0f)
        } else {
            0f
        }

        analogGaugeView?.currentSpeed = speedKmH
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(hideControlsRunnable)
        try {
            val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
            nativeLocationListener?.let { locationManager?.removeUpdates(it) }
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

class AnalogGaugeView(
    context: Context,
    var primaryColor: Int = AndroidColor.parseColor("#00E5FF"),
    var secondaryColor: Int = AndroidColor.parseColor("#AB47BC"),
    var warningColor: Int = AndroidColor.parseColor("#FF7043")
) : View(context) {

    var currentSpeed: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val arcPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    private val tickPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    private val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val centerSpeedPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
        typeface = Typeface.DEFAULT_BOLD
    }

    private val centerUnitPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val needlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f + 6f
        val radius = (Math.min(w, h) / 2f) - 16f
        val strokeW = 12f

        val dynamicColor = when {
            currentSpeed < 50f -> primaryColor
            currentSpeed < 90f -> secondaryColor
            else -> warningColor
        }

        val rect = android.graphics.RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        arcPaint.color = AndroidColor.parseColor("#222B3D")
        arcPaint.strokeWidth = strokeW
        canvas.drawArc(rect, 135f, 270f, false, arcPaint)

        val totalSpeed = 150f
        val progress = (currentSpeed / totalSpeed).coerceIn(0f, 1f)
        if (progress > 0f) {
            arcPaint.color = dynamicColor
            canvas.drawArc(rect, 135f, 270f * progress, false, arcPaint)
        }

        tickPaint.strokeWidth = 3f
        textPaint.textSize = (radius * 0.17f).coerceAtLeast(9f)

        for (s in 0..150 step 20) {
            val angleDeg = 135f + (s / totalSpeed) * 270f
            val angleRad = Math.toRadians(angleDeg.toDouble())

            val startR = radius - strokeW / 2f - 2f
            val endR = startR - 10f

            val sx = cx + startR * cos(angleRad).toFloat()
            val sy = cy + startR * sin(angleRad).toFloat()
            val ex = cx + endR * cos(angleRad).toFloat()
            val ey = cy + endR * sin(angleRad).toFloat()

            tickPaint.color = if (s <= currentSpeed) dynamicColor else AndroidColor.GRAY
            canvas.drawLine(sx, sy, ex, ey, tickPaint)

            val numR = endR - 14f
            val nx = cx + numR * cos(angleRad).toFloat()
            val ny = cy + numR * sin(angleRad).toFloat() + textPaint.textSize / 3f
            canvas.drawText(s.toString(), nx, ny, textPaint)
        }

        val centerSpeedText = currentSpeed.toInt().toString()
        centerSpeedPaint.textSize = radius * 0.40f

        val digitalTextY = cy + radius * 0.48f
        canvas.drawText(centerSpeedText, cx, digitalTextY, centerSpeedPaint)

        centerUnitPaint.textSize = radius * 0.15f
        centerUnitPaint.color = dynamicColor
        canvas.drawText("KM/H", cx, digitalTextY + radius * 0.20f, centerUnitPaint)

        val needleAngleRad = Math.toRadians((135f + 270f * progress).toDouble())
        val needleLen = radius - 20f
        val ex = cx + needleLen * cos(needleAngleRad).toFloat()
        val ey = cy + needleLen * sin(needleAngleRad).toFloat()

        needlePaint.color = dynamicColor
        needlePaint.strokeWidth = 5f
        canvas.drawLine(cx, cy, ex, ey, needlePaint)

        needlePaint.color = AndroidColor.WHITE
        canvas.drawCircle(cx, cy, 7f, needlePaint)
        needlePaint.color = dynamicColor
        canvas.drawCircle(cx, cy, 4f, needlePaint)
    }
}