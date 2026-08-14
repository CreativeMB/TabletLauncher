package com.creativem.toblauncher

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.location.Location
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.MapPosition
import org.mapsforge.map.model.common.Observer
import org.mapsforge.map.android.graphics.AndroidBitmap
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Marker
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

// ==========================================
// MODELO Y REPOSITORIO DE CÁMARAS
// ==========================================
data class SpeedCamera(
    val lat: Double,
    val lon: Double,
    val maxSpeed: String
)

object CameraRepository {
    private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
    private const val OVERPASS_QUERY = """[out:json][timeout:45];
(
  node["highway"="speed_camera"](-4.5, -79.5, 13.5, -66.8);
  node["enforcement"="maxspeed"](-4.5, -79.5, 13.5, -66.8);
  node["device"="speed_camera"](-4.5, -79.5, 13.5, -66.8);
);
out body;"""

    fun getCachedCameras(context: Context): List<SpeedCamera> {
        val cacheFile = File(context.filesDir, "speed_cameras_colombia.json")
        val cameraList = mutableListOf<SpeedCamera>()
        if (cacheFile.exists()) {
            try {
                val jsonString = cacheFile.readText()
                cameraList.addAll(parseJson(jsonString))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        //camara de prueva para culaquier configuracion de la app
//        cameraList.add(SpeedCamera(4.735566, -74.098225, "50"))
        return cameraList
    }

    suspend fun updateCamerasOnlineInBackground(context: Context): List<SpeedCamera>? = withContext(Dispatchers.IO) {
        try {
            val url = URL(OVERPASS_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5000
                readTimeout = 8000
                val postData = "data=" + URLEncoder.encode(OVERPASS_QUERY, "UTF-8")
                outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }
            }
            if (conn.responseCode == 200) {
                val jsonString = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = parseJson(jsonString)
                if (parsed.isNotEmpty()) {
                    val cacheFile = File(context.filesDir, "speed_cameras_colombia.json")
                    cacheFile.writeText(jsonString)
                    val result = parsed.toMutableList()
                    result.add(SpeedCamera(4.735566, -74.098225, "50"))
                    return@withContext result
                }
            }
        } catch (e: Exception) {}
        return@withContext null
    }

    private fun parseJson(jsonString: String): List<SpeedCamera> {
        val list = mutableListOf<SpeedCamera>()
        try {
            val root = JSONObject(jsonString)
            val elements = root.optJSONArray("elements") ?: return emptyList()
            for (i in 0 until elements.length()) {
                val elem = elements.getJSONObject(i)
                val lat = elem.getDouble("lat")
                val lon = elem.getDouble("lon")
                val tags = elem.optJSONObject("tags")
                val speed = tags?.optString("maxspeed", "Radar") ?: "Radar"
                list.add(SpeedCamera(lat, lon, speed))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}

// ==========================================
// CLASE CONTENEDORA DE REFERENCIAS
// ==========================================
class MapRefs {
    var mapView: MapView? = null
    var marker: Marker? = null
    var locationCallback: LocationCallback? = null
    var currentAnimator: ValueAnimator? = null
    val cameraMarkers = mutableListOf<Marker>()
    var cameras: List<SpeedCamera> = emptyList()
    var alertManager: ProximityAlertManager? = null
    var zoomObserver: Observer? = null
}

// ==========================================
// GESTOR DE AUDIO Y ALERTAS POR PROXIMIDAD
// ==========================================
class ProximityAlertManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private val alertedCameras = mutableSetOf<String>()
    private var lastAlertTime = 0L

    var isAudioAlertsEnabled: Boolean = true

    fun checkProximity(currentLat: Double, currentLng: Double, cameras: List<SpeedCamera>) {
        if (!isAudioAlertsEnabled || cameras.isEmpty()) return
        val alertDistanceMeters = 400f
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < 8000) return

        val results = FloatArray(1)
        for (cam in cameras) {
            val camId = "${cam.lat}_${cam.lon}"
            if (alertedCameras.contains(camId)) continue

            Location.distanceBetween(currentLat, currentLng, cam.lat, cam.lon, results)
            val distance = results[0]
            if (distance <= alertDistanceMeters) {
                Log.d("GPS_ALERT", "🚨 ¡CÁMARA EN RANGO! A ${distance.toInt()}m")
                alertedCameras.add(camId)
                lastAlertTime = now
                playAudioAlert()
                break
            }
        }
        if (alertedCameras.size > 50) alertedCameras.clear()
    }

    private fun playAudioAlert() {
        if (!isAudioAlertsEnabled) return
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            val afd = context.resources.openRawResourceFd(R.raw.alerta_camara) ?: return
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                .build()

            solicitarAudioFocus(audioAttributes)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                @Suppress("DEPRECATION")
                setAudioStreamType(AudioManager.STREAM_NOTIFICATION)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                setVolume(1.0f, 1.0f) // Reproduce al nivel exacto que tenga puesto el usuario
                setOnCompletionListener { mp ->
                    mp.release()
                    mediaPlayer = null
                    liberarAudioFocus()
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    mediaPlayer = null
                    liberarAudioFocus()
                    true
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            liberarAudioFocus()
        }
    }

    private fun solicitarAudioFocus(attributes: AudioAttributes) {
        if (audioManager == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { }
                .build().also { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_NOTIFICATION, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun liberarAudioFocus() {
        if (audioManager == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    fun destroy() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            liberarAudioFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// ==========================================
// DIBUJADO DE ÍCONOS
// ==========================================
fun createStaticGpsBitmap(): Bitmap {
    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val innerDotRadius = 14f

    val pulsePaint = Paint().apply {
        color = android.graphics.Color.argb(70, 66, 133, 244)
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    val pulseStrokePaint = Paint().apply {
        color = android.graphics.Color.argb(120, 66, 133, 244)
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val dotPaint = Paint().apply {
        color = android.graphics.Color.rgb(66, 133, 244)
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    val whiteBorderPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    canvas.drawCircle(center, center, 30f, pulsePaint)
    canvas.drawCircle(center, center, 30f, pulseStrokePaint)
    canvas.drawCircle(center, center, innerDotRadius, dotPaint)
    canvas.drawCircle(center, center, innerDotRadius, whiteBorderPaint)
    return bitmap
}

fun createCameraMarkerBitmap(zoomLevel: Float = 16f): Bitmap {
    val size = when {
        zoomLevel < 12f -> 22
        zoomLevel < 14f -> 32
        zoomLevel < 16f -> 44
        else -> 58
    }

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val scale = size / 64f
    val offsetY = -6f * scale

    val colorCamaraSombra = android.graphics.Color.parseColor("#2B4155")
    val colorCamaraFondo = android.graphics.Color.parseColor("#B3E5FC")
    val colorLente = android.graphics.Color.parseColor("#1565C0")
    val colorBorde = android.graphics.Color.parseColor("#1B2936")

    val paintFill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    val paintStroke = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = (2.2f * scale).coerceAtLeast(1.2f)
        color = colorBorde
    }
    val paintWhite = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }

    // Soporte
    val soporteRect = RectF(center - (2.5f * scale), center + (8f * scale) + offsetY, center + (2.5f * scale), size.toFloat())
    paintFill.color = colorCamaraSombra
    canvas.drawRect(soporteRect, paintFill)

    // Cuerpo
    val cameraBodyRect = RectF(center - (18f * scale), center - (20f * scale) + offsetY, center + (18f * scale), center + (14f * scale) + offsetY)
    val cameraTopShadow = RectF(cameraBodyRect.left, cameraBodyRect.top, cameraBodyRect.right, cameraBodyRect.top + (9f * scale))
    paintFill.color = colorCamaraSombra
    canvas.drawRoundRect(cameraTopShadow, 5f * scale, 5f * scale, paintFill)

    val cameraBodyMain = RectF(cameraBodyRect.left, cameraBodyRect.top + (3f * scale), cameraBodyRect.right, cameraBodyRect.bottom)
    paintFill.color = colorCamaraFondo
    canvas.drawRoundRect(cameraBodyMain, 5f * scale, 5f * scale, paintFill)
    canvas.drawRoundRect(cameraBodyRect, 5f * scale, 5f * scale, paintStroke)

    // Lente
    val lensCenterY = (center - (1f * scale)) + offsetY
    canvas.drawCircle(center, lensCenterY, 7.5f * scale, paintFill)
    canvas.drawCircle(center, lensCenterY, 7.5f * scale, paintStroke)

    paintFill.color = colorLente
    canvas.drawCircle(center + (1f * scale), lensCenterY - (1f * scale), 4.5f * scale, paintFill)
    canvas.drawCircle(center - (2f * scale), lensCenterY - (3f * scale), 1.2f * scale, paintWhite)

    // Velocímetro
    val speedoX = center + (17f * scale)
    val speedoY = (center + (5f * scale)) + offsetY
    val speedoRadius = 10f * scale

    val paintSpeedoStroke = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = (2.2f * scale).coerceAtLeast(1.2f)
        color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(speedoX, speedoY, speedoRadius, paintSpeedoStroke)

    paintStroke.strokeWidth = (1.5f * scale).coerceAtLeast(1f)
    canvas.drawCircle(speedoX, speedoY, speedoRadius - (2f * scale), paintStroke)

    val needlePath = Path().apply {
        moveTo(speedoX, speedoY)
        lineTo(speedoX - (4.5f * scale), speedoY - (3.5f * scale))
    }
    paintSpeedoStroke.strokeWidth = (1.8f * scale).coerceAtLeast(1f)
    canvas.drawPath(needlePath, paintSpeedoStroke)
    canvas.drawCircle(speedoX, speedoY, 1.2f * scale, paintWhite)

    return bitmap
}

// ==========================================
// COMPOSABLE DEL MAPA (FLUÍDO COMO WAZE)
// ==========================================
@SuppressLint("RememberReturnType", "ClickableViewAccessibility")
@Composable
fun MapsforgeWidget(
    mapRefs: MapRefs,
    isMapAvailable: Boolean,
    isAutoCenterEnabled: Boolean,
    isNightMode: Boolean,
    isCameraAudioEnabled: Boolean,
    targetMapFile: File,
    onLocationUpdated: (LatLong) -> Unit,
    onDisableAutoCenter: () -> Unit,
    mapPickerLauncher: ActivityResultLauncher<String>,
    onNoFileManagerError: () -> Unit,
    onMapLoadError: (String) -> Unit,
    onOpenCustomExplorer: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    BackHandler { onClose() }
    val context = LocalContext.current
    val currentAutoCenterEnabled by rememberUpdatedState(isAutoCenterEnabled)

    val microBitmap = remember { AndroidBitmap(createCameraMarkerBitmap(10f)) }
    val smallBitmap = remember { AndroidBitmap(createCameraMarkerBitmap(13f)) }
    val mediumBitmap = remember { AndroidBitmap(createCameraMarkerBitmap(15f)) }
    val largeBitmap = remember { AndroidBitmap(createCameraMarkerBitmap(17f)) }

    fun getMarkerForZoom(zoom: Int): AndroidBitmap {
        return when {
            zoom < 12 -> microBitmap
            zoom < 14 -> smallBitmap
            zoom < 16 -> mediumBitmap
            else -> largeBitmap
        }
    }

    LaunchedEffect(isCameraAudioEnabled) {
        mapRefs.alertManager?.isAudioAlertsEnabled = isCameraAudioEnabled
    }

    LaunchedEffect(mapRefs.mapView) {
        val mapView = mapRefs.mapView ?: return@LaunchedEffect
        val mainHandler = android.os.Handler(Looper.getMainLooper())

        if (mapRefs.alertManager == null) {
            mapRefs.alertManager = ProximityAlertManager(context).apply {
                isAudioAlertsEnabled = isCameraAudioEnabled
            }
        }

        val refreshCamerasRunnable = Runnable {
            if (mapRefs.cameras.isNotEmpty() && mapRefs.mapView != null) {
                val currentZoom = mapView.model.mapViewPosition.zoomLevel.toInt()
                val targetBitmap = getMarkerForZoom(currentZoom)

                for (oldMarker in mapRefs.cameraMarkers) {
                    mapView.layerManager.layers.remove(oldMarker)
                }
                mapRefs.cameraMarkers.clear()

                for (cam in mapRefs.cameras) {
                    val camMarker = Marker(LatLong(cam.lat, cam.lon), targetBitmap, 0, 0)
                    mapRefs.cameraMarkers.add(camMarker)
                    mapView.layerManager.layers.add(camMarker)
                }

                mapRefs.marker?.latLong?.let { pos ->
                    mapRefs.alertManager?.checkProximity(pos.latitude, pos.longitude, mapRefs.cameras)
                }

                mapView.layerManager.redrawLayers()
            }
        }

        mapRefs.zoomObserver?.let { mapView.model.mapViewPosition.removeObserver(it) }
        val zoomObserver = Observer {
            mainHandler.removeCallbacks(refreshCamerasRunnable)
            mainHandler.postDelayed(refreshCamerasRunnable, 150L)
        }
        mapRefs.zoomObserver = zoomObserver
        mapView.model.mapViewPosition.addObserver(zoomObserver)

        withContext(Dispatchers.IO) {
            val cachedCameras = CameraRepository.getCachedCameras(context)
            mapRefs.cameras = cachedCameras
            withContext(Dispatchers.Main) { refreshCamerasRunnable.run() }

            val updatedOnline = CameraRepository.updateCamerasOnlineInBackground(context)
            if (updatedOnline != null) {
                mapRefs.cameras = updatedOnline
                withContext(Dispatchers.Main) { refreshCamerasRunnable.run() }
            }
        }
    }

    LaunchedEffect(isNightMode) {
        val mapView = mapRefs.mapView ?: return@LaunchedEffect
        if (isNightMode) {
            val paint = Paint()
            val matrix = ColorMatrix(floatArrayOf(
                -0.8f,  0f,    0f,    0f, 245f,
                0f,   -0.8f,  0f,    0f, 245f,
                0f,    0f,   -0.8f,  0f, 255f,
                0f,    0f,    0f,    1f,   0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(matrix)
            mapView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        } else {
            mapView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        mapView.repaint()
    }

    DisposableEffect(Unit) {
        onDispose {
            mapRefs.alertManager?.destroy()
            mapRefs.alertManager = null
            mapRefs.currentAnimator?.cancel()
            mapRefs.locationCallback?.let { callback ->
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.removeLocationUpdates(callback)
            }
            mapRefs.mapView?.let { mv ->
                try {
                    mapRefs.zoomObserver?.let { mv.model.mapViewPosition.removeObserver(it) }
                    mv.destroyAll()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            mapRefs.mapView = null
            mapRefs.marker = null
            mapRefs.locationCallback = null
            mapRefs.currentAnimator = null
            mapRefs.zoomObserver = null
            mapRefs.cameraMarkers.clear()
        }
    }

    Box(modifier = Modifier.fillMaxSize().clipToBounds().clip(RoundedCornerShape(16.dp))) {
        if (isMapAvailable && targetMapFile.exists() && targetMapFile.length() > 0) {
            key(targetMapFile.absolutePath, targetMapFile.lastModified(), targetMapFile.length()) {
                AndroidView(
                    factory = { ctx ->
                        try {
                            AndroidGraphicFactory.createInstance(ctx.applicationContext)
                        } catch (e: Exception) {}

                        val mapView = MapView(ctx).apply {
                            keepScreenOn = true
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            // 🚀 Optimización de GPU: reduce sobregiro y ajusta el tamaño nativo
                            model.frameBufferModel.overdrawFactor = 1.05
                            model.displayModel.userScaleFactor = 1.2f

                            setZoomLevelMin(3.toByte())
                            setZoomLevelMax(18.toByte())
                            model.mapViewPosition.setMapPosition(
                                MapPosition(LatLong(4.6018403, -74.0796899), 16.toByte())
                            )
                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        }

                        var startTouchX = 0f
                        var startTouchY = 0f
                        mapView.setOnTouchListener { _, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    startTouchX = event.x
                                    startTouchY = event.y
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val dx = abs(event.x - startTouchX)
                                    val dy = abs(event.y - startTouchY)
                                    if (dx > 10f || dy > 10f) {
                                        onDisableAutoCenter()
                                    }
                                }
                            }
                            false
                        }

                        try {
                            mapView.mapScaleBar.setVisible(false)
                            mapView.mapZoomControls.setShowMapZoomControls(false)
                        } catch (e: Exception) {}

                        val mapFile = try {
                            if (targetMapFile.exists() && targetMapFile.length() > 0) {
                                org.mapsforge.map.reader.MapFile(targetMapFile)
                            } else null
                        } catch (e: Exception) {
                            onMapLoadError("El archivo seleccionado no es válido.")
                            null
                        }

                        if (mapFile != null) {
                            // 🏎️ Caché ampliada a 1.5 para transiciones hiper-fluidas sin pausas de lectura
                            val tileCache = AndroidUtil.createTileCache(
                                ctx,
                                "mapcache",
                                mapView.model.displayModel.tileSize,
                                1f,
                                1.5
                            )

                            val rendererLayer = org.mapsforge.map.layer.renderer.TileRendererLayer(
                                tileCache,
                                mapFile,
                                mapView.model.mapViewPosition,
                                AndroidGraphicFactory.INSTANCE
                            ).apply {
                                setXmlRenderTheme(org.mapsforge.map.rendertheme.InternalRenderTheme.DEFAULT)
                                setTextScale(1.2f)
                            }

                            mapView.layerManager.layers.add(rendererLayer)

                            try {
                                val startPos = mapFile.mapFileInfo?.startPosition ?: mapFile.mapFileInfo?.boundingBox?.centerPoint
                                if (startPos != null) {
                                    mapView.model.mapViewPosition.setMapPosition(MapPosition(startPos, 17.toByte()))
                                }
                            } catch (e: Exception) {}
                        }

                        val staticGpsBitmap = createStaticGpsBitmap()
                        val mapsforgeDrawable = AndroidBitmap(staticGpsBitmap)

                        val cartMarker = Marker(
                            LatLong(4.6018403, -74.0796899),
                            mapsforgeDrawable,
                            0,
                            0
                        )

                        mapView.layerManager.layers.add(cartMarker)

                        val callback = startSmoothLocationTracking(
                            ctx,
                            mapRefs,
                            cartMarker,
                            isAutoCenterSupplier = { currentAutoCenterEnabled },
                            onLocationUpdated = onLocationUpdated
                        )

                        mapRefs.mapView = mapView
                        mapRefs.marker = cartMarker
                        mapRefs.locationCallback = callback

                        mapView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Botones Flotantes de Zoom
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        onDisableAutoCenter()
                        mapRefs.mapView?.model?.mapViewPosition?.zoomIn()
                    },
                    containerColor = Color(0xCC1E1E1E),
                    contentColor = Color.White,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Acercar", modifier = Modifier.size(24.dp))
                }

                FloatingActionButton(
                    onClick = {
                        onDisableAutoCenter()
                        mapRefs.mapView?.model?.mapViewPosition?.zoomOut()
                    },
                    containerColor = Color(0xCC1E1E1E),
                    contentColor = Color.White,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Alejar", modifier = Modifier.size(24.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "🗺️ Configura tus Mapas Offline", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Selecciona tu archivo principal de mapa con extensión '.map' para iniciar el navegador.", color = Color.Gray, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onOpenCustomExplorer() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC5))
                ) {
                    Text("Seleccionar archivo .map", color = Color.Black)
                }
            }
        }
    }
}

// ==========================================
// CONTENEDOR PRINCIPAL DEL MAPA
// ==========================================
@Composable
fun MapContainerWidget(
    onExpandClicked: () -> Unit = {}
) {
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("toblauncher_prefs", Context.MODE_PRIVATE) }

    var isOnlineNavActive by remember {
        mutableStateOf(prefs.getBoolean("online_nav_active_mode", false))
    }

    var showMenu by remember { mutableStateOf(false) }
    var isNightMode by remember { mutableStateOf(prefs.getBoolean("night_mode", false)) }
    var isAutoCenterEnabled by remember { mutableStateOf(prefs.getBoolean("auto_center", true)) }

    var isCameraAudioEnabled by remember {
        mutableStateOf(prefs.getBoolean("camera_audio_alert_enabled", true))
    }

    var customExplorerType by remember { mutableStateOf<String?>(null) }

    val targetMapFile = remember { OfflineMapManager.getMapFile(context) }
    val targetPoiFile = remember { OfflineMapManager.getPoiFile(context) }

    var isMapAvailable by remember { mutableStateOf(false) }
    var isPoiAvailable by remember { mutableStateOf(false) }
    var isCheckingFiles by remember { mutableStateOf(true) }
    var isLoadingFile by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var showNoFileManagerError by remember { mutableStateOf(false) }

    var mapLoadError by remember { mutableStateOf<String?>(null) }
    var lastKnownLocation by remember { mutableStateOf<LatLong?>(null) }
    val mapRefs = remember { MapRefs() }

    LaunchedEffect(Unit) {
        isMapAvailable = withContext(Dispatchers.IO) { OfflineMapManager.isMapDownloaded(context) }
        isPoiAvailable = withContext(Dispatchers.IO) { OfflineMapManager.isPoiDownloaded(context) }
        isCheckingFiles = false
    }

    fun safeLaunchPicker(launcher: ActivityResultLauncher<String>, onNotFound: () -> Unit) {
        try {
            launcher.launch("*/*")
        } catch (e: Exception) {
            onNotFound()
        }
    }

    fun processSelectedFile(file: File, isPoi: Boolean) {
        isLoadingFile = true
        mapLoadError = null
        loadingMessage = if (isPoi) "Cargando Puntos de Interés (POI)..." else "Cargando mapa en la memoria del auto..."

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val destFile = if (isPoi) targetPoiFile else targetMapFile
                file.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                withContext(Dispatchers.Main) {
                    if (isPoi) {
                        isPoiAvailable = OfflineMapManager.isPoiDownloaded(context)
                    } else {
                        isMapAvailable = OfflineMapManager.isMapDownloaded(context)
                    }
                    isLoadingFile = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoadingFile = false
                    if (!isPoi) mapLoadError = "Error al copiar archivo: ${e.localizedMessage}"
                }
            }
        }
    }

    val mapPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri ->
            isLoadingFile = true
            mapLoadError = null
            loadingMessage = "Cargando mapa en la memoria del auto..."
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {}
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    FileOutputStream(targetMapFile).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                withContext(Dispatchers.Main) {
                    if (OfflineMapManager.isMapDownloaded(context)) {
                        isMapAvailable = true
                    }
                    isLoadingFile = false
                }
            }
        }
    }

    val poiPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri ->
            isLoadingFile = true
            loadingMessage = "Cargando Puntos de Interés (POI)..."
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {}
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    FileOutputStream(targetPoiFile).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                withContext(Dispatchers.Main) {
                    if (OfflineMapManager.isPoiDownloaded(context)) isPoiAvailable = true
                    isLoadingFile = false
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        if (isCheckingFiles) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)))
        } else if (isLoadingFile) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFF121212)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = theme.accentCyan)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = loadingMessage, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {

                if (isOnlineNavActive) {
                    NativeAppGaugeWidget(modifier = Modifier.fillMaxSize())
                } else {
                    MapsforgeWidget(
                        mapRefs = mapRefs,
                        isMapAvailable = isMapAvailable && (mapLoadError == null),
                        isAutoCenterEnabled = isAutoCenterEnabled,
                        isNightMode = isNightMode,
                        isCameraAudioEnabled = isCameraAudioEnabled,
                        targetMapFile = targetMapFile,
                        onLocationUpdated = { loc -> lastKnownLocation = loc },
                        onDisableAutoCenter = {
                            if (isAutoCenterEnabled) {
                                isAutoCenterEnabled = false
                                prefs.edit().putBoolean("auto_center", false).apply()
                            }
                        },
                        mapPickerLauncher = mapPickerLauncher,
                        onNoFileManagerError = { showNoFileManagerError = true },
                        onMapLoadError = { error -> mapLoadError = error },
                        onOpenCustomExplorer = { customExplorerType = "map" }
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showMenu,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showMenu = false }
                    )
                }

                // Panel de Control
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 6.dp, bottom = 6.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FloatingActionButton(
                            onClick = { showMenu = !showMenu },
                            containerColor = Color(0xAA1E1E1E),
                            contentColor = theme.accentCyan,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Configuración", modifier = Modifier.size(22.dp))
                        }

                        if (!isOnlineNavActive && isMapAvailable && mapLoadError == null) {
                            FloatingActionButton(
                                onClick = {
                                    isAutoCenterEnabled = true
                                    prefs.edit().putBoolean("auto_center", true).apply()
                                    lastKnownLocation?.let { location ->
                                        mapRefs.mapView?.model?.mapViewPosition?.center = location
                                        mapRefs.mapView?.repaint()
                                    }
                                },
                                containerColor = if (isAutoCenterEnabled) theme.accentCyan else Color(0xAA1E1E1E),
                                contentColor = if (isAutoCenterEnabled) Color.Black else Color.White,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Centrar Auto", modifier = Modifier.size(22.dp))
                            }
                        }

                        if (!isOnlineNavActive) {
                            FloatingActionButton(
                                onClick = onExpandClicked,
                                containerColor = Color(0xAA1E1E1E),
                                contentColor = theme.accentCyan,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Pantalla Completa", modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                // MENÚ LATERAL
                androidx.compose.animation.AnimatedVisibility(
                    visible = showMenu,
                    enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it }) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it }) + androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxHeight().width(280.dp),
                        color = Color(0xFF1E1E1E),
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (isOnlineNavActive) "Navegación Online" else "Configuración Offline",
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 10.sp,
                                color = Color.White
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x0DFFFFFF), shape = MaterialTheme.shapes.medium)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        isOnlineNavActive = !isOnlineNavActive
                                        prefs.edit().putBoolean("online_nav_active_mode", isOnlineNavActive).apply()
                                        showMenu = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isOnlineNavActive) theme.accentCyan else Color(0xFF33CCFF),
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (isOnlineNavActive) "🗺️ Volver a Mapa Offline" else "🌐 Navegación Online (Waze)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (!isOnlineNavActive) {
                                    Divider(color = Color.Gray.copy(alpha = 0.2f), thickness = 0.5.dp)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Modo Noche", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontSize = 8.sp)
                                        Switch(
                                            checked = isNightMode,
                                            onCheckedChange = {
                                                isNightMode = it
                                                prefs.edit().putBoolean("night_mode", it).apply()
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = theme.accentCyan)
                                        )
                                    }

                                    Divider(color = Color.Gray.copy(alpha = 0.2f), thickness = 0.5.dp)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Audio de Radares", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontSize = 8.sp)
                                        Switch(
                                            checked = isCameraAudioEnabled,
                                            onCheckedChange = {
                                                isCameraAudioEnabled = it
                                                prefs.edit().putBoolean("camera_audio_alert_enabled", it).apply()
                                                mapRefs.alertManager?.isAudioAlertsEnabled = it
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = theme.accentCyan)
                                        )
                                    }

                                    Divider(color = Color.Gray.copy(alpha = 0.2f), thickness = 0.5.dp)

                                    Button(
                                        onClick = {
                                            showMenu = false
                                            customExplorerType = "map"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x1A03DAC5), contentColor = theme.accentCyan),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("📁 Mapa (.map)", style = MaterialTheme.typography.bodyMedium, fontSize = 8.sp)
                                    }

                                    Button(
                                        onClick = {
                                            showMenu = false
                                            customExplorerType = "poi"
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isPoiAvailable) Color(0x1A03DAC5) else Color(0x1AFFFFFF),
                                            contentColor = if (isPoiAvailable) theme.accentCyan else Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (isPoiAvailable) "📁 Puntos (.poi)" else "➕ Cargar Puntos (.poi)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 8.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            TextButton(onClick = { showMenu = false }, modifier = Modifier.align(Alignment.End)) {
                                Text("Cerrar", color = Color.Gray)
                            }
                        }
                    }
                }

                if (!isOnlineNavActive && mapLoadError != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E1E))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "⚠️ Archivo no soportado", style = MaterialTheme.typography.titleLarge, color = Color(0xFFF44336))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = mapLoadError ?: "",
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { customExplorerType = "map" },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accentCyan)
                        ) {
                            Text("Seleccionar otro archivo .map", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showNoFileManagerError) {
            AlertDialog(
                onDismissRequest = { showNoFileManagerError = false },
                title = { Text("Explorador no disponible") },
                text = { Text("Esta radio no cuenta con un explorador de archivos instalado.") },
                confirmButton = { Button(onClick = { showNoFileManagerError = false }) { Text("Aceptar") } }
            )
        }

        if (customExplorerType != null) {
            val isPoi = customExplorerType == "poi"
            CustomUsbExplorerModal(
                extension = if (isPoi) ".poi" else ".map",
                onDismiss = { customExplorerType = null },
                onSystemPickerFallback = {
                    val type = customExplorerType
                    customExplorerType = null
                    if (type == "poi") {
                        safeLaunchPicker(poiPickerLauncher) { showNoFileManagerError = true }
                    } else {
                        safeLaunchPicker(mapPickerLauncher) { showNoFileManagerError = true }
                    }
                },
                onFileSelected = { selectedFile ->
                    customExplorerType = null
                    processSelectedFile(selectedFile, isPoi)
                }
            )
        }
    }
}

// ==========================================
// MODAL EXPLORADOR NATIVO USB
// ==========================================
@Composable
fun CustomUsbExplorerModal(
    extension: String,
    onDismiss: () -> Unit,
    onSystemPickerFallback: () -> Unit,
    onFileSelected: (File) -> Unit
) {
    var currentDir by remember { mutableStateOf<File?>(null) }
    var detectedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }

    LaunchedEffect(extension) {
        withContext(Dispatchers.IO) {
            val list = mutableListOf<File>()
            val searchRoots = listOf(
                File("/storage"),
                File("/sdcard"),
                File("/storage/emulated/0")
            )

            fun scanDir(dir: File, depth: Int = 0) {
                if (depth > 4 || !dir.canRead()) return
                val files = dir.listFiles() ?: return
                for (f in files) {
                    if (f.isDirectory && !f.name.startsWith(".")) {
                        scanDir(f, depth + 1)
                    } else if (f.isFile && f.name.endsWith(extension, ignoreCase = true)) {
                        list.add(f)
                    }
                }
            }

            for (root in searchRoots) {
                if (root.exists()) scanDir(root)
            }
            detectedFiles = list
            isScanning = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSystemPickerFallback) {
                    Text("Usar Explorador del Sistema", color = Color(0xFF03DAC5), fontSize = 11.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = Color.Gray, fontSize = 11.sp)
                }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = Color(0xFF03DAC5),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Seleccionar $extension", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Explorador de Memoria USB / Tablet", fontSize = 11.sp, color = Color.Gray)
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color(0xFF121212), shape = RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                if (isScanning) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF03DAC5))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Buscando archivos $extension...", color = Color.LightGray, fontSize = 12.sp)
                    }
                } else if (currentDir == null) {
                    if (detectedFiles.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("No se encontraron archivos $extension", color = Color.Gray, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { currentDir = File("/storage") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222))
                            ) {
                                Text("Explorar Carpetas Manualmente", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    } else {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Archivos Encontrados (${detectedFiles.size})", color = Color(0xFF03DAC5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { currentDir = File("/storage") }) {
                                    Text("Explorar USB >", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(detectedFiles) { file ->
                                    MapFileItemRow(file = file, onSelect = { onFileSelected(file) })
                                }
                            }
                        }
                    }
                } else {
                    val filesInDir = remember(currentDir) {
                        currentDir?.listFiles()?.filter { f ->
                            f.isDirectory || f.name.endsWith(extension, ignoreCase = true)
                        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val parent = currentDir?.parentFile
                                    if (parent != null && parent.path != "/") {
                                        currentDir = parent
                                    } else {
                                        currentDir = null
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentDir?.name ?: "Almacenamiento",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filesInDir) { f ->
                                if (f.isDirectory) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { currentDir = f }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(f.name, color = Color.White, fontSize = 12.sp)
                                    }
                                } else {
                                    MapFileItemRow(file = f, onSelect = { onFileSelected(f) })
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

@Composable
fun MapFileItemRow(file: File, onSelect: () -> Unit) {
    val sizeMb = remember(file) { file.length() / (1024 * 1024) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Map, contentDescription = null, tint = Color(0xFF03DAC5), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(file.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(file.parent ?: "", color = Color.Gray, fontSize = 9.sp, maxLines = 1)
            }
        }
        Text("${sizeMb} MB", color = Color(0xFF03DAC5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// =================================================================
// MOTOR DE NAVEGACIÓN FLUIDO (INTERPOLACIÓN ACTIVA TIPO WAZE)
// =================================================================
@SuppressLint("MissingPermission")
fun startSmoothLocationTracking(
    context: Context,
    mapRefs: MapRefs,
    cartMarker: Marker,
    isAutoCenterSupplier: () -> Boolean,
    onLocationUpdated: (LatLong) -> Unit
): LocationCallback {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    // Coordenadas actuales interpoladas
    var currentDisplayLat = cartMarker.latLong.latitude
    var currentDisplayLng = cartMarker.latLong.longitude
    var currentDisplayBearing = 0f

    var lastLocationTimestamp = 0L

    fun getShortestAngleDelta(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).apply {
        setMinUpdateIntervalMillis(500L)
        setMinUpdateDistanceMeters(0.0f)
        setGranularity(Granularity.GRANULARITY_FINE)
        setWaitForAccurateLocation(true)
    }.build()

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val targetLat = location.latitude
            val targetLng = location.longitude

            val now = SystemClock.elapsedRealtime()
            val timeDelta = if (lastLocationTimestamp == 0L) 1000L else (now - lastLocationTimestamp).coerceIn(400L, 1400L)
            lastLocationTimestamp = now

            mapRefs.alertManager?.checkProximity(targetLat, targetLng, mapRefs.cameras)

            val speedKmH = location.speed * 3.6f
            val isMoving = speedKmH > 3.0f

            // 🎯 Filtro paso-bajo (Low-Pass) para evitar vibración de brújula
            val rawTargetBearing = if (location.hasBearing() && isMoving) location.bearing else currentDisplayBearing
            val deltaBearing = if (isMoving) getShortestAngleDelta(currentDisplayBearing, rawTargetBearing) else 0f

            val startLat = currentDisplayLat
            val startLng = currentDisplayLng
            val startBearing = currentDisplayBearing

            mapRefs.currentAnimator?.cancel()

            // 🚗 Interpolación continua sin saltos de fotogramas
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = timeDelta
                interpolator = LinearInterpolator()

                addUpdateListener { animation ->
                    val fraction = animation.animatedValue as Float
                    currentDisplayLat = startLat + (targetLat - startLat) * fraction
                    currentDisplayLng = startLng + (targetLng - startLng) * fraction
                    currentDisplayBearing = startBearing + (deltaBearing * fraction)

                    val currentPos = LatLong(currentDisplayLat, currentDisplayLng)
                    cartMarker.latLong = currentPos
                    onLocationUpdated(currentPos)

                    val mapView = mapRefs.mapView
                    if (mapView != null && isAutoCenterSupplier()) {
                        mapView.model.mapViewPosition.center = currentPos
                        if (isMoving) {
                            mapView.rotation = -currentDisplayBearing
                        }
                    }
                }
            }

            animator.start()
            mapRefs.currentAnimator = animator
        }
    }

    try {
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                val startPos = LatLong(loc.latitude, loc.longitude)
                cartMarker.latLong = startPos
                currentDisplayLat = loc.latitude
                currentDisplayLng = loc.longitude
                if (isAutoCenterSupplier()) {
                    mapRefs.mapView?.model?.mapViewPosition?.center = startPos
                }
                mapRefs.alertManager?.checkProximity(loc.latitude, loc.longitude, mapRefs.cameras)
            }
        }
    } catch (e: Exception) {}

    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    return locationCallback
}