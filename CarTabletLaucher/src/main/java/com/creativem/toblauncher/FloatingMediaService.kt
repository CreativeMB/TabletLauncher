package com.creativem.toblauncher

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class FloatingMediaService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingRootView: View? = null
    private var videoPlayerView: PlayerView? = null

    private val handler = Handler(Looper.getMainLooper())
    private var btnCloseView: ImageButton? = null
    private var btnExpandView: ImageButton? = null
    private var resizeHandleView: TextView? = null

    // Referencias para actualizar dinámicamente
    private var txtMusicTitleView: TextView? = null
    private var lastObservedTrackIndex = -1

    private var txtRadioTitleView: TextView? = null
    private var txtRadioSubView: TextView? = null
    private var lastObservedStationIndex = -1

    private var iptvVideoView: VideoView? = null
    private var lastObservedChannelIndex = -1

    private val hideControlsRunnable = Runnable {
        btnCloseView?.visibility = View.GONE
        btnExpandView?.visibility = View.GONE
        resizeHandleView?.visibility = View.GONE
    }

    // Bucle continuo para detectar cambios de Canción, IPTV y Radio
    private val mediaStateUpdateRunnable = object : Runnable {
        override fun run() {
            try {
                val musicPlayer = SmartMusicPlayer.getInstance(this@FloatingMediaService)
                val iptvPlayer = SmartIptvPlayer.getInstance(this@FloatingMediaService)
                val radioPlayer = SmartRadioManager.getInstance(this@FloatingMediaService)

                // 🎵 1. ACTUALIZAR NOMBRE DE CANCIÓN (SIN CARPETA)
                if (musicPlayer.currentTrackIndex != lastObservedTrackIndex) {
                    lastObservedTrackIndex = musicPlayer.currentTrackIndex
                    val track = musicPlayer.playlist.getOrNull(lastObservedTrackIndex)
                    txtMusicTitleView?.text = "🎵 ${track?.title ?: "Música USB"}"
                }

                // 📺 2. CAMBIAR CANAL IPTV DINÁMICAMENTE
                if (iptvPlayer.currentChannelIndex != lastObservedChannelIndex) {
                    lastObservedChannelIndex = iptvPlayer.currentChannelIndex
                    val channel = iptvPlayer.playlist.getOrNull(lastObservedChannelIndex)
                    if (channel != null && iptvVideoView != null) {
                        iptvVideoView?.stopPlayback()
                        iptvVideoView?.setVideoPath(channel.streamUrl)
                        iptvVideoView?.start()
                    }
                }

                // 📻 3. ACTUALIZAR EMISORA DE RADIO
                if (radioPlayer.currentStationIndex != lastObservedStationIndex) {
                    lastObservedStationIndex = radioPlayer.currentStationIndex
                    val station = radioPlayer.stationList.getOrNull(lastObservedStationIndex)
                    txtRadioTitleView?.text = "📻 ${station?.name ?: "Radio Online"}"
                    txtRadioSubView?.text = "${station?.freqLabel ?: ""} • ${station?.city ?: radioPlayer.selectedCountry}"
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
            handler.postDelayed(this, 1000L)
        }
    }

    private fun resetControlsTimer() {
        btnCloseView?.visibility = View.VISIBLE
        btnExpandView?.visibility = View.VISIBLE
        resizeHandleView?.visibility = View.VISIBLE
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 5000L)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val theme = ThemeManager.getSavedTheme(this)
        val primaryColorInt = theme.accentCyan.toAndroidColorInt()

        val videoPlayer = SmartVideoPlayer.getInstance(this)
        val musicPlayer = SmartMusicPlayer.getInstance(this)
        val radioPlayer = SmartRadioManager.getInstance(this)
        val iptvPlayer = SmartIptvPlayer.getInstance(this)

        val activeMode = when {
            videoPlayer.isPlaying || videoPlayer.exoPlayer?.isPlaying == true -> MediaMode.VIDEO
            iptvPlayer.isPlaying -> MediaMode.IPTV
            musicPlayer.isPlaying -> MediaMode.MUSIC
            radioPlayer.isPlaying -> MediaMode.RADIO
            else -> MediaMode.VIDEO
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val initialWidth = (screenWidth * 0.60f).toInt().coerceIn((200 * density).toInt(), (450 * density).toInt())
        val initialHeight = if (activeMode == MediaMode.VIDEO || activeMode == MediaMode.IPTV) {
            (initialWidth * 9) / 16
        } else {
            (100 * density).toInt()
        }

        val defaultX = screenWidth - initialWidth - (0 * density).toInt()
        val defaultY = (60 * density).toInt()

        val params = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
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
                setColor(theme.cardBackground.toAndroidColorInt())
                cornerRadius = 16 * density
                setStroke((2 * density).toInt(), primaryColorInt)
            }
            clipToOutline = true
        }

        setupContent(rootLayout, activeMode, videoPlayer, iptvPlayer, musicPlayer, radioPlayer, theme, density)

        // 🟢 BOTÓN CERRAR (Pausa la música porque el usuario quiere cerrar todo)
        val btnClose = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#AA000000"))
                shape = GradientDrawable.OVAL
            }
            setColorFilter(AndroidColor.parseColor("#FF5252"))
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                when (activeMode) {
                    MediaMode.VIDEO -> videoPlayer.pausePlayback()
                    MediaMode.IPTV -> iptvPlayer.pausePlayback()
                    MediaMode.MUSIC -> musicPlayer.pausePlayback()
                    MediaMode.RADIO -> radioPlayer.stopPlayback()
                }
                stopSelf()
            }
        }
        btnCloseView = btnClose
        val closeParams = FrameLayout.LayoutParams((60 * density).toInt(), (60 * density).toInt(), Gravity.TOP or Gravity.START).apply {
            setMargins((6 * density).toInt(), (6 * density).toInt(), 0, 0)
        }
        rootLayout.addView(btnCloseView, closeParams)

        // 🟢 BOTÓN EXPANDIR (Regresa al Launcher, NO PAUSA LA MÚSICA)
        val btnExpand = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            background = GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#AA000000"))
                shape = GradientDrawable.OVAL
            }
            setColorFilter(primaryColorInt)
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())

            setOnClickListener {
                try {
                    SmartMusicPlayer.getInstance(this@FloatingMediaService).notifyChange()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(intent)

                stopSelf() // Detiene el flotante, pero la música sigue porque no pusimos "pausePlayback()"
            }
        }
        btnExpandView = btnExpand
        val expandParams = FrameLayout.LayoutParams((60 * density).toInt(), (60 * density).toInt(), Gravity.TOP or Gravity.END).apply {
            setMargins(0, (6 * density).toInt(), (6 * density).toInt(), 0)
        }
        rootLayout.addView(btnExpandView, expandParams)

        // 🖐️ ARRASTRE FLOTANTE
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

                            params.x = params.x.coerceIn(0, screenWidth - params.width)
                            params.y = params.y.coerceIn(0, screenHeight - params.height)

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

        // ↘️ REDIMENSIONAR
        val handle = TextView(this).apply {
            text = " ↘ "
            setTextColor(primaryColorInt)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#88000000"))
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

                        val newW = (startW + deltaX).coerceIn((160 * density).toInt(), screenWidth)
                        val newH = if (activeMode == MediaMode.VIDEO || activeMode == MediaMode.IPTV) {
                            (newW * 9) / 16
                        } else {
                            (startH + deltaY).coerceIn((60 * density).toInt(), screenHeight)
                        }

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
        rootLayout.addView(resizeHandleView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, (4 * density).toInt(), (4 * density).toInt())
        })

        resetControlsTimer()

        // INICIAR MONITOR DE CAMBIOS
        handler.post(mediaStateUpdateRunnable)

        floatingRootView = rootLayout
        try {
            windowManager?.addView(floatingRootView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @OptIn(UnstableApi::class)
    private fun setupContent(
        root: FrameLayout,
        mode: MediaMode,
        video: SmartVideoPlayer,
        iptv: SmartIptvPlayer,
        music: SmartMusicPlayer,
        radio: SmartRadioManager,
        theme: DashboardTheme,
        density: Float
    ) {
        when (mode) {
            MediaMode.VIDEO -> {
                videoPlayerView = PlayerView(this).apply {
                    useController = false
                    player = video.exoPlayer
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                root.addView(videoPlayerView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }

            MediaMode.IPTV -> {
                lastObservedChannelIndex = iptv.currentChannelIndex
                val currentChannel = iptv.playlist.getOrNull(iptv.currentChannelIndex)
                if (currentChannel != null) {
                    val videoView = object : VideoView(this@FloatingMediaService) {
                        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                            val width = MeasureSpec.getSize(widthMeasureSpec)
                            val height = MeasureSpec.getSize(heightMeasureSpec)
                            setMeasuredDimension(width, height)
                        }
                    }.apply {
                        setVideoPath(currentChannel.streamUrl)
                        setOnPreparedListener { mp ->
                            try {
                                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            iptv.bindMediaPlayer(mp)
                            mp.start()
                        }
                        setOnErrorListener { _, _, _ -> true }
                    }
                    iptvVideoView = videoView
                    root.addView(videoView, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                    ))
                }
            }

            MediaMode.MUSIC -> {
                lastObservedTrackIndex = music.currentTrackIndex
                val track = music.playlist.getOrNull(music.currentTrackIndex)
                val info = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding((12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                }

                // 🎵 AQUÍ SE QUITÓ LA CARPETA, SOLO SE MUESTRA LA CANCIÓN
                val txtTitle = TextView(this).apply {
                    text = "🎵 ${track?.title ?: "Música USB"}"
                    setTextColor(AndroidColor.WHITE)
                    textSize = 14f // Lo hice un poco más grande ya que ahora es el único texto
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }

                txtMusicTitleView = txtTitle

                info.addView(txtTitle)
                root.addView(info, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }

            MediaMode.RADIO -> {
                lastObservedStationIndex = radio.currentStationIndex
                val station = radio.stationList.getOrNull(radio.currentStationIndex)
                val info = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding((12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                }

                val txtTitle = TextView(this).apply {
                    text = "📻 ${station?.name ?: "Radio Online"}"
                    setTextColor(AndroidColor.WHITE)
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                }

                val txtSub = TextView(this).apply {
                    text = "${station?.freqLabel ?: ""} • ${station?.city ?: radio.selectedCountry}"
                    setTextColor(theme.accentCyan.toAndroidColorInt())
                    textSize = 10f
                }

                txtRadioTitleView = txtTitle
                txtRadioSubView = txtSub

                info.addView(txtTitle)
                info.addView(txtSub)
                root.addView(info, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(hideControlsRunnable)
        handler.removeCallbacks(mediaStateUpdateRunnable)

        try {
            // 🚀 ESTO LIBERA LOS GESTOS (La sesión de medios suelta el control, pero la música sigue)
            SmartMusicPlayer.getInstance(this).deactivateMediaSession()
            SmartVideoPlayer.getInstance(this).forceRebind()

            if (floatingRootView != null) {
                iptvVideoView?.stopPlayback()
                iptvVideoView = null
                videoPlayerView?.player = null
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
                val intent = Intent(context, FloatingMediaService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, FloatingMediaService::class.java)
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