package com.creativem.toblauncher

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
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
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.MapPosition
import org.mapsforge.map.android.graphics.AndroidBitmap
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.model.common.Observer
import org.mapsforge.map.reader.MapFile
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*
import androidx.compose.ui.graphics.toArgb
// ==========================================
// MODELO Y RESULTADO DE CÁMARAS
// ==========================================
data class SpeedCamera(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val maxSpeed: String
)

sealed class CameraSyncResult {
    data class Success(
        val cameras: List<SpeedCamera>,
        val previousCount: Int,
        val addedCount: Int
    ) : CameraSyncResult()
    data class Error(val message: String) : CameraSyncResult()
}

// ==========================================
// MODELO Y DETECTOR DE NAVEGACIÓN EN VIVO
// ==========================================
enum class ManeuverType {
    STRAIGHT, TURN_LEFT, TURN_RIGHT, SHARP_LEFT, SHARP_RIGHT, UTURN, ARRIVAL
}

data class NavigationStatus(
    val nextManeuver: ManeuverType = ManeuverType.STRAIGHT,
    val maneuverInstruction: String = "Continúe recto",
    val distanceToManeuverMeters: Double = 0.0,
    val remainingDistanceMeters: Double = 0.0,
    val remainingTimeMillis: Long = 0L,
    val etaString: String = "--:--",
    val hasArrived: Boolean = false
)

class LiveNavigationTracker(private val route: RouteOption) {
    fun updateProgress(currentLoc: LatLong): NavigationStatus {
        val points = route.points
        if (points.isEmpty()) return NavigationStatus()

        var closestIdx = 0
        var minDistance = Double.MAX_VALUE
        for (i in points.indices) {
            val dist = calculateDistance(currentLoc, points[i])
            if (dist < minDistance) {
                minDistance = dist
                closestIdx = i
            }
        }

        val distToGoal = calculateDistance(currentLoc, points.last())
        if (closestIdx >= points.size - 2 || distToGoal <= 25.0) {
            return NavigationStatus(
                nextManeuver = ManeuverType.ARRIVAL,
                maneuverInstruction = "¡Has llegado a tu destino!",
                distanceToManeuverMeters = 0.0,
                remainingDistanceMeters = 0.0,
                remainingTimeMillis = 0L,
                etaString = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                hasArrived = true
            )
        }

        var remainingDist = calculateDistance(currentLoc, points[closestIdx])
        for (i in closestIdx until points.size - 1) {
            remainingDist += calculateDistance(points[i], points[i + 1])
        }

        var maneuver = ManeuverType.STRAIGHT
        var distToTurn = remainingDist
        var turnInstruction = "Continúe por esta vía"

        for (i in closestIdx until (points.size - 2).coerceAtMost(closestIdx + 30)) {
            val p1 = if (i == closestIdx) currentLoc else points[i]
            val p2 = points[i + 1]
            val p3 = points[i + 2]

            val bearing1 = calculateBearing(p1, p2)
            val bearing2 = calculateBearing(p2, p3)
            var delta = (bearing2 - bearing1) % 360
            if (delta > 180) delta -= 360
            if (delta < -180) delta += 360

            if (abs(delta) >= 35.0) {
                var distFromCurrentToTurn = calculateDistance(currentLoc, points[closestIdx])
                for (j in closestIdx until i) {
                    distFromCurrentToTurn += calculateDistance(points[j], points[j + 1])
                }
                distToTurn = distFromCurrentToTurn

                maneuver = when {
                    delta in 35.0..65.0 -> ManeuverType.TURN_RIGHT
                    delta > 65.0 && delta <= 125.0 -> ManeuverType.TURN_RIGHT
                    delta > 125.0 -> ManeuverType.SHARP_RIGHT
                    delta in -65.0..-35.0 -> ManeuverType.TURN_LEFT
                    delta < -65.0 && delta >= -125.0 -> ManeuverType.TURN_LEFT
                    delta < -125.0 && delta >= -160.0 -> ManeuverType.SHARP_LEFT
                    else -> ManeuverType.UTURN
                }

                val distFormat = if (distToTurn >= 1000) {
                    String.format(Locale.US, "%.1f km", distToTurn / 1000)
                } else {
                    "${(distToTurn / 10).roundToInt() * 10} m"
                }

                val actionText = when (maneuver) {
                    ManeuverType.TURN_RIGHT -> "Gire a la derecha"
                    ManeuverType.SHARP_RIGHT -> "Giro cerrado a la derecha"
                    ManeuverType.TURN_LEFT -> "Gire a la izquierda"
                    ManeuverType.SHARP_LEFT -> "Giro cerrado a la izquierda"
                    ManeuverType.UTURN -> "Haga un giro en U"
                    else -> "Continúe recto"
                }

                turnInstruction = "En $distFormat $actionText"
                break
            }
        }

        val speedMps = 40.0 * 1000 / 3600
        val remainingMs = ((remainingDist / speedMps) * 1000).toLong()
        val etaDate = Date(System.currentTimeMillis() + remainingMs)
        val etaStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(etaDate)

        return NavigationStatus(
            nextManeuver = maneuver,
            maneuverInstruction = turnInstruction,
            distanceToManeuverMeters = distToTurn,
            remainingDistanceMeters = remainingDist,
            remainingTimeMillis = remainingMs,
            etaString = etaStr,
            hasArrived = false
        )
    }

    private fun calculateDistance(p1: LatLong, p2: LatLong): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun calculateBearing(p1: LatLong, p2: LatLong): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
}

// =========================================================================
// ESTADO GLOBAL DE NAVEGACIÓN (PERSISTE ENTRE WIDGET Y PANTALLA COMPLETA)
// =========================================================================
object NavigationStateHolder {
    var calculatedRoutes by mutableStateOf<List<RouteOption>>(emptyList())
    var selectedRouteId by mutableStateOf(0)
    var isNavigatingActive by mutableStateOf(false)
    var liveTracker: LiveNavigationTracker? = null
    var navStatus by mutableStateOf(NavigationStatus())
    var destinationLocation by mutableStateOf<LatLong?>(null)
    var isCalculatingRoute by mutableStateOf(false)

    fun clear() {
        calculatedRoutes = emptyList()
        selectedRouteId = 0
        isNavigatingActive = false
        liveTracker = null
        navStatus = NavigationStatus()
        destinationLocation = null
        isCalculatingRoute = false
    }
}

// ==========================================
// REPOSITORIO DE CÁMARAS ULTRA-RÁPIDO
// ==========================================
object CameraRepository {
    private const val TAG = "RADAR_DEBUG"
    private const val CACHE_FILE_NAME = "speed_cameras_colombia.json"

    private val OVERPASS_ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://lz4.overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter"
    )

    private fun buildFastQueryForBounds(bbox: BoundingBox): String {
        val s = String.format(Locale.US, "%.4f", bbox.minLatitude)
        val w = String.format(Locale.US, "%.4f", bbox.minLongitude)
        val n = String.format(Locale.US, "%.4f", bbox.maxLatitude)
        val e = String.format(Locale.US, "%.4f", bbox.maxLongitude)
        val bboxStr = "($s,$w,$n,$e)"

        return """[out:json][timeout:20];
(
  node["highway"="speed_camera"]$bboxStr;
  node["enforcement"="maxspeed"]$bboxStr;
  node["device"="speed_camera"]$bboxStr;
  node["enforcement"="speed_camera"]$bboxStr;
);
out body;"""
    }

    fun getCachedCameras(context: Context): List<SpeedCamera> {
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        if (!cacheFile.exists() || cacheFile.length() == 0L) return listOf()

        return try {
            val jsonString = cacheFile.readText(Charsets.UTF_8)
            parseJson(jsonString)
        } catch (e: Exception) {
            listOf()
        }
    }

    suspend fun updateCamerasOnline(context: Context, mapFileTarget: File): CameraSyncResult = withContext(Dispatchers.IO) {
        if (!mapFileTarget.exists() || mapFileTarget.length() == 0L) {
            return@withContext CameraSyncResult.Error("No hay ningún mapa cargado.")
        }

        val mapBounds = try {
            val reader = MapFile(mapFileTarget)
            val bounds = reader.mapFileInfo.boundingBox
            reader.close()
            bounds
        } catch (e: Exception) {
            return@withContext CameraSyncResult.Error("No se pudieron leer las coordenadas del mapa.")
        }

        val previousCameras = getCachedCameras(context)
        val previousCount = previousCameras.size
        var lastErrorDetail = "No se pudo conectar a ningún servidor"
        val query = buildFastQueryForBounds(mapBounds)
        val postData = ("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray(Charsets.UTF_8)

        for (endpoint in OVERPASS_ENDPOINTS) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 20000
                    useCaches = false
                    setRequestProperty("User-Agent", "TobLauncher/14.0")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    setFixedLengthStreamingMode(postData.size)
                }

                connection.outputStream.use { it.write(postData) }
                val responseCode = connection.responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonString = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val downloadedList = parseJson(jsonString)

                    if (downloadedList.isNotEmpty()) {
                        val saveOk = saveCamerasToCache(context, downloadedList)
                        if (saveOk) {
                            val newTotal = downloadedList.size
                            val addedCount = (newTotal - previousCount).coerceAtLeast(0)
                            return@withContext CameraSyncResult.Success(
                                cameras = downloadedList,
                                previousCount = previousCount,
                                addedCount = addedCount
                            )
                        }
                    }
                }
            } catch (ex: Exception) {
                lastErrorDetail = "${ex.javaClass.simpleName}: ${ex.localizedMessage}"
            } finally {
                connection?.disconnect()
            }
        }

        return@withContext CameraSyncResult.Error(lastErrorDetail)
    }

    private fun saveCamerasToCache(context: Context, cameras: List<SpeedCamera>): Boolean {
        return try {
            val finalJson = JSONObject().apply {
                val array = org.json.JSONArray()
                for (cam in cameras) {
                    val obj = JSONObject().apply {
                        put("id", cam.id)
                        put("lat", cam.lat)
                        put("lon", cam.lon)
                        put("tags", JSONObject().apply { put("maxspeed", cam.maxSpeed) })
                    }
                    array.put(obj)
                }
                put("elements", array)
            }

            val targetFile = File(context.filesDir, CACHE_FILE_NAME)
            val tempFile = File(context.filesDir, "$CACHE_FILE_NAME.tmp")
            tempFile.writeText(finalJson.toString(), Charsets.UTF_8)
            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun parseJson(jsonString: String): List<SpeedCamera> {
        val cameraMap = LinkedHashMap<String, SpeedCamera>()
        try {
            val root = JSONObject(jsonString)
            val elements = root.optJSONArray("elements") ?: return emptyList()

            for (i in 0 until elements.length()) {
                val elem = elements.optJSONObject(i) ?: continue
                val id = elem.optLong("id", 0L)
                val lat = elem.optDouble("lat", Double.NaN)
                val lon = elem.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue

                val tags = elem.optJSONObject("tags")
                val speed = tags?.optString("maxspeed")?.takeIf { it.isNotBlank() }
                    ?: tags?.optString("enforcement:maxspeed")?.takeIf { it.isNotBlank() }
                    ?: tags?.optString("speed_limit")?.takeIf { it.isNotBlank() }
                    ?: "Radar"

                val key = "${String.format(Locale.US, "%.5f", lat)}_${String.format(Locale.US, "%.5f", lon)}"
                cameraMap[key] = SpeedCamera(id, lat, lon, speed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parseando JSON: ${e.message}")
        }
        return cameraMap.values.toList()
    }
}

// ==========================================
// CLASE CONTENEDORA DE REFERENCIAS
// ==========================================
class MapRefs {
    var mapView: MapView? = null
    var marker: Marker? = null
    var destinationMarker: Marker? = null
    var locationCallback: LocationCallback? = null
    var currentAnimator: ValueAnimator? = null
    val cameraMarkers = mutableListOf<Marker>()
    var cameras: List<SpeedCamera> = emptyList()
    var alertManager: ProximityAlertManager? = null
    var zoomObserver: Observer? = null
    var routeLayerManager: RouteLayerManager? = null
}

// ==========================================
// GESTOR DE AUDIO Y ALERTAS POR PROXIMIDAD
// ==========================================
class ProximityAlertManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val cameraAlertLevels = mutableMapOf<String, Int>()
    private var lastAlertTime = 0L
    var isAudioAlertsEnabled: Boolean = true

    companion object {
        private const val DISTANCE_FAR_ALERT = 300f
        private const val DISTANCE_CLOSE_ALERT = 50f
        private const val DISTANCE_RESET = 500f
        private const val AUDIO_COOLDOWN_MS = 3000L
    }

    fun checkProximity(currentLat: Double, currentLng: Double, cameras: List<SpeedCamera>) {
        if (!isAudioAlertsEnabled || cameras.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastAlertTime < AUDIO_COOLDOWN_MS) return

        val results = FloatArray(1)

        for (cam in cameras) {
            val camId = "${cam.lat}_${cam.lon}"
            Location.distanceBetween(currentLat, currentLng, cam.lat, cam.lon, results)
            val distance = results[0]

            if (distance > DISTANCE_RESET) {
                cameraAlertLevels.remove(camId)
                continue
            }

            val currentLevel = cameraAlertLevels[camId] ?: 0

            if (distance <= DISTANCE_CLOSE_ALERT && currentLevel < 2) {
                cameraAlertLevels[camId] = 2
                lastAlertTime = now
                playAudioAlert(R.raw.alerta_cerca)
                break
            } else if (distance <= DISTANCE_FAR_ALERT && distance > DISTANCE_CLOSE_ALERT && currentLevel < 1) {
                cameraAlertLevels[camId] = 1
                lastAlertTime = now
                playAudioAlert(R.raw.alerta_camara)
                break
            }
        }
    }

    private fun playAudioAlert(@RawRes soundResId: Int) {
        if (!isAudioAlertsEnabled) return
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null

            val afd = context.resources.openRawResourceFd(soundResId) ?: return
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                .build()

            solicitarAudioFocus(audioAttributes)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setAudioStreamType(AudioManager.STREAM_NOTIFICATION)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                setVolume(1.0f, 1.0f)
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
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
            cameraAlertLevels.clear()
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

fun createDestinationMarkerBitmap(): Bitmap {
    val size = 90
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { isAntiAlias = true }

    paint.color = android.graphics.Color.parseColor("#E53935")
    paint.style = Paint.Style.FILL
    val path = Path().apply {
        moveTo(25f, 15f)
        lineTo(75f, 32f)
        lineTo(25f, 50f)
        close()
    }
    canvas.drawPath(path, paint)

    paint.color = android.graphics.Color.parseColor("#FFFFFF")
    paint.strokeWidth = 6f
    paint.style = Paint.Style.STROKE
    canvas.drawLine(25f, 15f, 25f, 80f, paint)

    paint.color = android.graphics.Color.parseColor("#E53935")
    paint.style = Paint.Style.FILL
    canvas.drawCircle(25f, 80f, 7f, paint)

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

    val soporteRect = RectF(center - (2.5f * scale), center + (8f * scale) + offsetY, center + (2.5f * scale), size.toFloat())
    paintFill.color = colorCamaraSombra
    canvas.drawRect(soporteRect, paintFill)

    val cameraBodyRect = RectF(center - (18f * scale), center - (20f * scale) + offsetY, center + (18f * scale), center + (14f * scale) + offsetY)
    val cameraTopShadow = RectF(cameraBodyRect.left, cameraBodyRect.top, cameraBodyRect.right, cameraBodyRect.top + (9f * scale))
    paintFill.color = colorCamaraSombra
    canvas.drawRoundRect(cameraTopShadow, 5f * scale, 5f * scale, paintFill)

    val cameraBodyMain = RectF(cameraBodyRect.left, cameraBodyRect.top + (3f * scale), cameraBodyRect.right, cameraBodyRect.bottom)
    paintFill.color = colorCamaraFondo
    canvas.drawRoundRect(cameraBodyMain, 5f * scale, 5f * scale, paintFill)
    canvas.drawRoundRect(cameraBodyRect, 5f * scale, 5f * scale, paintStroke)

    val lensCenterY = (center - (1f * scale)) + offsetY
    canvas.drawCircle(center, lensCenterY, 7.5f * scale, paintFill)
    canvas.drawCircle(center, lensCenterY, 7.5f * scale, paintStroke)

    paintFill.color = colorLente
    canvas.drawCircle(center + (1f * scale), lensCenterY - (1f * scale), 4.5f * scale, paintFill)
    canvas.drawCircle(center - (2f * scale), lensCenterY - (3f * scale), 1.2f * scale, paintWhite)

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
// COMPOSABLE DEL MAPA (OFFLINE PURO)
// ==========================================
@SuppressLint("RememberReturnType", "ClickableViewAccessibility")
@Composable
fun MapsforgeWidget(
    mapRefs: MapRefs,
    isMapAvailable: Boolean,
    isAutoCenterEnabled: Boolean,
    isCameraAudioEnabled: Boolean,
    targetMapFile: File,
    onLocationUpdated: (LatLong) -> Unit,
    onDisableAutoCenter: () -> Unit,
    onDestinationSelected: (LatLong) -> Unit,
    mapPickerLauncher: ActivityResultLauncher<String>,
    onNoFileManagerError: () -> Unit,
    onMapLoadError: (String) -> Unit,
    onOpenCustomExplorer: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    BackHandler { onClose() }
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current
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

    LaunchedEffect(mapRefs.mapView, theme.id) { // 👈 Se agrega theme.id para que reaccione al cambio de tema
        val mapView = mapRefs.mapView ?: return@LaunchedEffect
        val mainHandler = android.os.Handler(Looper.getMainLooper())

        if (mapRefs.alertManager == null) {
            mapRefs.alertManager = ProximityAlertManager(context).apply {
                isAudioAlertsEnabled = isCameraAudioEnabled
            }
        }

        var lastZoomBucket = -1

        val refreshCamerasRunnable = Runnable {
            if (mapRefs.cameras.isNotEmpty() && mapRefs.mapView != null) {
                val currentZoom = mapView.model.mapViewPosition.zoomLevel.toInt()
                val currentBucket = when {
                    currentZoom < 12 -> 1
                    currentZoom < 14 -> 2
                    currentZoom < 16 -> 3
                    else -> 4
                }

                if (currentBucket != lastZoomBucket || mapRefs.cameraMarkers.isEmpty()) {
                    lastZoomBucket = currentBucket
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
                }

                mapRefs.marker?.latLong?.let { pos ->
                    mapRefs.alertManager?.checkProximity(pos.latitude, pos.longitude, mapRefs.cameras)
                }
            }
        }

        mapRefs.zoomObserver?.let { mapView.model.mapViewPosition.removeObserver(it) }
        val zoomObserver = Observer {
            mainHandler.removeCallbacks(refreshCamerasRunnable)
            mainHandler.postDelayed(refreshCamerasRunnable, 200L)
        }
        mapRefs.zoomObserver = zoomObserver
        mapView.model.mapViewPosition.addObserver(zoomObserver)

        val cached = withContext(Dispatchers.IO) { CameraRepository.getCachedCameras(context) }
        if (cached.isNotEmpty()) {
            mapRefs.cameras = cached
            refreshCamerasRunnable.run()
        }

        // =========================================================================
        // RESTAURAR LÍNEAS Y DESTINO CON LOS COLORES DEL TEMA ACTIVO
        // =========================================================================
        if (NavigationStateHolder.calculatedRoutes.isNotEmpty()) {
            mapRefs.routeLayerManager?.renderRoutes(
                routes = NavigationStateHolder.calculatedRoutes,
                selectedRouteId = NavigationStateHolder.selectedRouteId,
                primaryColorInt = theme.accentCyan.toArgb(),   // 👈 Nativo y sin errores
                secondaryColorInt = theme.accentPurple.toArgb(), // 👈 Nativo y sin errores
                accentColorInt = theme.accentOrange.toArgb()    // 👈 Nativo y sin errores
            )
        }

        NavigationStateHolder.destinationLocation?.let { dest ->
            mapRefs.destinationMarker?.let { mapView.layerManager.layers.remove(it) }
            val flagBitmap = AndroidBitmap(createDestinationMarkerBitmap())
            val destMarker = Marker(dest, flagBitmap, 0, -35)
            mapRefs.destinationMarker = destMarker
            mapView.layerManager.layers.add(destMarker)
            mapView.repaint()
        }
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
            mapRefs.destinationMarker = null
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

                        val mapFile = try {
                            if (targetMapFile.exists() && targetMapFile.length() > 0) {
                                MapFile(targetMapFile)
                            } else null
                        } catch (e: Exception) {
                            onMapLoadError("El archivo seleccionado no es válido.")
                            null
                        }

                        val mapView = MapView(ctx).apply {
                            keepScreenOn = true
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.parseColor("#E5E0D8"))
                            model.frameBufferModel.overdrawFactor = 1.25
                            model.displayModel.userScaleFactor = 1.15f
                            setZoomLevelMin(3.toByte())
                            setZoomLevelMax(18.toByte())
                            model.mapViewPosition.zoomLevel = if (NavigationStateHolder.isNavigatingActive) 18.toByte() else 17.toByte()
                        }

                        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onLongPress(e: MotionEvent) {
                                // ⛔ BLOQUEO: Si se está calculando, si ya hay rutas mostradas o si se está navegando, no hace nada
                                if (NavigationStateHolder.isCalculatingRoute ||
                                    NavigationStateHolder.calculatedRoutes.isNotEmpty() ||
                                    NavigationStateHolder.isNavigatingActive) {
                                    return
                                }

                                val latLong = mapView.mapViewProjection.fromPixels(e.x.toDouble(), e.y.toDouble())
                                if (latLong != null) {
                                    mapView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                    onDisableAutoCenter()
                                    onDestinationSelected(latLong)
                                }
                            }
                        })

                        var startTouchX = 0f
                        var startTouchY = 0f
                        mapView.setOnTouchListener { _, event ->
                            gestureDetector.onTouchEvent(event)
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    startTouchX = event.x
                                    startTouchY = event.y
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val dx = abs(event.x - startTouchX)
                                    val dy = abs(event.y - startTouchY)
                                    if (dx > 15f || dy > 15f) {
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

                        if (mapFile != null) {
                            val tileCache = AndroidUtil.createTileCache(
                                ctx,
                                "mapcache",
                                mapView.model.displayModel.tileSize,
                                1.2f,
                                1.25
                            )

                            val rendererLayer = org.mapsforge.map.layer.renderer.TileRendererLayer(
                                tileCache,
                                mapFile,
                                mapView.model.mapViewPosition,
                                false,
                                true,
                                true,
                                AndroidGraphicFactory.INSTANCE
                            ).apply {
                                setXmlRenderTheme(org.mapsforge.map.rendertheme.InternalRenderTheme.DEFAULT)
                                setTextScale(1.15f)
                            }

                            mapView.layerManager.layers.add(rendererLayer)
                        }

                        val staticGpsBitmap = createStaticGpsBitmap()
                        val mapsforgeDrawable = AndroidBitmap(staticGpsBitmap)

                        val cartMarker = Marker(
                            LatLong(0.0, 0.0),
                            mapsforgeDrawable,
                            0,
                            0
                        )

                        mapView.layerManager.layers.add(cartMarker)

                        mapRefs.mapView = mapView
                        mapRefs.marker = cartMarker
                        mapRefs.routeLayerManager = RouteLayerManager(mapView)

                        val callback = startSmoothLocationTracking(
                            ctx,
                            mapView,
                            mapRefs,
                            cartMarker,
                            isAutoCenterSupplier = { currentAutoCenterEnabled },
                            onLocationUpdated = onLocationUpdated
                        )

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
                Text(text = "🗺️ Configura tus Mapas Offline", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Selecciona tu archivo principal de mapa con extensión '.map' para iniciar el navegador.", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onOpenCustomExplorer() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC5))
                ) {
                    Text("Seleccionar archivo .map", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}

// ==========================================
// CONTENEDOR PRINCIPAL DEL MAPA (OFFLINE)
// ==========================================
@Composable
fun MapContainerWidget(
    onExpandClicked: () -> Unit = {}
) {
    val context = LocalContext.current
    val theme = LocalDashboardTheme.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("toblauncher_prefs", Context.MODE_PRIVATE) }

    var isSyncingCameras by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isAutoCenterEnabled by remember { mutableStateOf(true) }
    var isCameraAudioEnabled by remember {
        mutableStateOf(prefs.getBoolean("camera_audio_alert_enabled", true))
    }

    val mapRefs = remember { MapRefs() }
    var cameraCountDisplay by remember { mutableStateOf(0) }
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

    var isBRouterInstalled by remember { mutableStateOf(false) }

    fun checkBRouter() {
        isBRouterInstalled = BRouterEngine.isBRouterInstalled(context)
    }

    LaunchedEffect(Unit) {
        checkBRouter()
        BRouterEngine.bind(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            BRouterEngine.unbind(context)
        }
    }

    LaunchedEffect(Unit) {
        isMapAvailable = withContext(Dispatchers.IO) { OfflineMapManager.isMapDownloaded(context) }
        isPoiAvailable = withContext(Dispatchers.IO) { OfflineMapManager.isPoiDownloaded(context) }
        val cached = withContext(Dispatchers.IO) { CameraRepository.getCachedCameras(context) }
        mapRefs.cameras = cached
        cameraCountDisplay = cached.size
        isCheckingFiles = false
    }

    LaunchedEffect(mapRefs.cameras.size) {
        cameraCountDisplay = mapRefs.cameras.size
    }

    fun openPlayStoreForBRouter() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=btools.routingapp")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=btools.routingapp")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    fun launchBRouterApp() {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("btools.routingapp")
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            openPlayStoreForBRouter()
        }
    }

    fun stopNavigation() {
        NavigationStateHolder.clear()
        mapRefs.routeLayerManager?.clearRoutes()
        mapRefs.mapView?.let { mv ->
            mapRefs.destinationMarker?.let { mv.layerManager.layers.remove(it) }
            mapRefs.destinationMarker = null
            mv.repaint()
        }
    }

    fun onRequestRouteTo(destination: LatLong) {
        if (NavigationStateHolder.isCalculatingRoute ||
            NavigationStateHolder.calculatedRoutes.isNotEmpty() ||
            NavigationStateHolder.isNavigatingActive) {
            return
        }
        val start = lastKnownLocation
        if (start == null) {
            android.widget.Toast.makeText(context, "⚠️ Esperando señal GPS del auto...", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        if (!BRouterEngine.isBRouterInstalled(context)) {
            openPlayStoreForBRouter()
            return
        }

        NavigationStateHolder.isCalculatingRoute = true
        NavigationStateHolder.isNavigatingActive = false
        NavigationStateHolder.destinationLocation = destination

        mapRefs.mapView?.let { mv ->
            mapRefs.destinationMarker?.let { mv.layerManager.layers.remove(it) }
            val flagBitmap = AndroidBitmap(createDestinationMarkerBitmap())
            val destMarker = Marker(destination, flagBitmap, 0, -35)
            mapRefs.destinationMarker = destMarker
            mv.layerManager.layers.add(destMarker)
            mv.repaint()
        }

        coroutineScope.launch {
            val routes = BRouterEngine.getTop3Routes(start, destination)
            NavigationStateHolder.isCalculatingRoute = false
            if (routes.isNotEmpty()) {
                NavigationStateHolder.calculatedRoutes = routes
                NavigationStateHolder.selectedRouteId = 0

                // 👇 AQUÍ SE PINTA POR PRIMERA VEZ CON LOS COLORES DEL TEMA
                mapRefs.routeLayerManager?.renderRoutes(
                    routes = routes,
                    selectedRouteId = 0,
                    primaryColorInt = theme.accentCyan.toArgb(),
                    secondaryColorInt = theme.accentPurple.toArgb(),
                    accentColorInt = theme.accentOrange.toArgb()
                )
            } else {
                android.widget.Toast.makeText(
                    context,
                    "⚠️ No se encontró vía offline aquí. Descarga tu zona en BRouter.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
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
                    if (OfflineMapManager.isMapDownloaded(context)) isMapAvailable = true
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

    fun processSelectedFile(file: File, isPoi: Boolean) {
        isLoadingFile = true
        mapLoadError = null
        loadingMessage = if (isPoi) "Cargando Puntos de Interés (POI)..." else "Cargando mapa en el auto..."

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
                    if (isPoi) isPoiAvailable = OfflineMapManager.isPoiDownloaded(context)
                    else isMapAvailable = OfflineMapManager.isMapDownloaded(context)
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
                Text(text = loadingMessage, fontSize = 12.sp, color = Color.White)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {

                MapsforgeWidget(
                    mapRefs = mapRefs,
                    isMapAvailable = isMapAvailable && (mapLoadError == null),
                    isAutoCenterEnabled = isAutoCenterEnabled,
                    isCameraAudioEnabled = isCameraAudioEnabled,
                    targetMapFile = targetMapFile,
                    onLocationUpdated = { loc ->
                        lastKnownLocation = loc
                        if (NavigationStateHolder.isNavigatingActive && NavigationStateHolder.liveTracker != null) {
                            val status = NavigationStateHolder.liveTracker!!.updateProgress(loc)
                            NavigationStateHolder.navStatus = status
                            if (status.hasArrived) {
                                android.widget.Toast.makeText(context, "🎉 ¡Has llegado a tu destino!", android.widget.Toast.LENGTH_LONG).show()
                                stopNavigation()
                            }
                        }
                    },
                    onDisableAutoCenter = {
                        if (isAutoCenterEnabled) {
                            isAutoCenterEnabled = false
                            prefs.edit().putBoolean("auto_center", false).apply()
                        }
                    },
                    onDestinationSelected = { dest -> onRequestRouteTo(dest) },
                    mapPickerLauncher = mapPickerLauncher,
                    onNoFileManagerError = { showNoFileManagerError = true },
                    onMapLoadError = { error -> mapLoadError = error },
                    onOpenCustomExplorer = { customExplorerType = "map" }
                )
// =========================================================================
// 🔍 BARRA DE BÚSQUEDA HÍBRIDA (ONLINE / POI OFFLINE)
// =========================================================================
                if (!NavigationStateHolder.isNavigatingActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        MapSearchBar(
                            currentLocation = lastKnownLocation,
                            poiFile = targetPoiFile,
                            theme = theme,
                            onLocationSelected = { selectedDest ->
                                // Centra el mapa en el lugar buscado y calcula las 3 rutas
                                mapRefs.mapView?.model?.mapViewPosition?.center = selectedDest
                                onRequestRouteTo(selectedDest)
                            }
                        )
                    }
                }


                // =========================================================================
// FUNCIÓN AUXILIAR PARA PASAR COLORES DEL TEMA A LA CAPA DEL MAPA
// =========================================================================
                fun Color.toAndroidColorInt(): Int = android.graphics.Color.argb(
                    (alpha * 255).toInt(),
                    (red * 255).toInt(),
                    (green * 255).toInt(),
                    (blue * 255).toInt()
                )

// =========================================================================
// 1. HUD SUPERIOR: PRÓXIMA MANIOBRA (PÍLDORA CENTRADA ARRIBA)
// =========================================================================
                if (NavigationStateHolder.isNavigatingActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Card(
                            modifier = Modifier.wrapContentSize(),
                            colors = CardDefaults.cardColors(containerColor = theme.cardBackground.copy(alpha = 0.95f)),
                            border = BorderStroke(1.dp, theme.cardBorder),
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.cardElevation(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(theme.accentCyan, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = getManeuverIcon(NavigationStateHolder.navStatus.nextManeuver),
                                        contentDescription = null,
                                        tint = theme.dashBackground,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = NavigationStateHolder.navStatus.maneuverInstruction,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

// =========================================================================
// 2. AVISO SUPERIOR: CALCULANDO RUTAS (FLOTANTE COMPACTO)
// =========================================================================
                if (NavigationStateHolder.isCalculatingRoute) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Card(
                            modifier = Modifier.wrapContentSize(),
                            colors = CardDefaults.cardColors(containerColor = theme.cardBackground.copy(alpha = 0.95f)),
                            border = BorderStroke(1.dp, theme.accentCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = theme.accentCyan,
                                    strokeWidth = 1.5.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Calculando rutas...",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

// =========================================================================
// 3. HUD INFERIOR DERECHO: TIEMPO / KM / LLEGADA (CONDUCCIÓN EN VIVO)
// =========================================================================
                if (NavigationStateHolder.isNavigatingActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 8.dp, bottom = 8.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Card(
                            modifier = Modifier.wrapContentSize(),
                            colors = CardDefaults.cardColors(containerColor = theme.cardBackground.copy(alpha = 0.95f)),
                            border = BorderStroke(1.dp, theme.accentCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val remainingMin = (NavigationStateHolder.navStatus.remainingTimeMillis / 60000).toInt()
                                val distFormatted = if (NavigationStateHolder.navStatus.remainingDistanceMeters >= 1000) {
                                    String.format(Locale.US, "%.1f km", NavigationStateHolder.navStatus.remainingDistanceMeters / 1000.0)
                                } else {
                                    "${NavigationStateHolder.navStatus.remainingDistanceMeters.toInt()} m"
                                }

                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = "$remainingMin min • $distFormatted",
                                        color = theme.accentCyan, // 1er Tono: Distancia y tiempo
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Llegada: ${NavigationStateHolder.navStatus.etaString}",
                                        color = theme.accentPurple, // 2do Tono: ETA
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Botón Salir (3er Tono: Naranja de Alerta)
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(theme.accentOrange)
                                        .clickable { stopNavigation() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Salir",
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

// =========================================================================
// 4. SELECTOR DE 3 RUTAS (ESQUINA INFERIOR DERECHA)
// =========================================================================
                if (!NavigationStateHolder.isNavigatingActive && NavigationStateHolder.calculatedRoutes.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 8.dp, bottom = 8.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        RouteOptionsCard(
                            routes = NavigationStateHolder.calculatedRoutes,
                            selectedRouteId = NavigationStateHolder.selectedRouteId,
                            theme = theme,
                            onRouteSelected = { id ->
                                NavigationStateHolder.selectedRouteId = id
                                mapRefs.routeLayerManager?.renderRoutes(
                                    routes = NavigationStateHolder.calculatedRoutes,
                                    selectedRouteId = id,
                                    primaryColorInt = theme.accentCyan.toAndroidColorInt(),
                                    secondaryColorInt = theme.accentPurple.toAndroidColorInt(),
                                    accentColorInt = theme.accentOrange.toAndroidColorInt()
                                )
                            },
                            onStartNavigation = {
                                val activeRoute = NavigationStateHolder.calculatedRoutes.find { it.id == NavigationStateHolder.selectedRouteId }
                                    ?: NavigationStateHolder.calculatedRoutes.first()
                                NavigationStateHolder.liveTracker = LiveNavigationTracker(activeRoute)
                                NavigationStateHolder.isNavigatingActive = true
                                isAutoCenterEnabled = true
                                prefs.edit().putBoolean("auto_center", true).apply()

                                mapRefs.mapView?.let { mv ->
                                    mv.model.mapViewPosition.zoomLevel = 18.toByte()
                                    lastKnownLocation?.let { mv.model.mapViewPosition.center = it }
                                    mv.repaint()
                                }
                            },
                            onCancel = { stopNavigation() }
                        )
                    }
                }

// =========================================================================
// 5. BOTONES FLOTANTES INFERIORES IZQUIERDOS (SIEMPRE VISIBLES)
// =========================================================================
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 6.dp, bottom = 6.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FloatingActionButton(
                            onClick = {
                                checkBRouter()
                                showMenu = !showMenu
                            },
                            containerColor = theme.cardBackground.copy(alpha = 0.9f),
                            contentColor = theme.accentCyan,
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Configuración", modifier = Modifier.size(18.dp))
                        }

                        if (isMapAvailable && mapLoadError == null) {
                            FloatingActionButton(
                                onClick = {
                                    isAutoCenterEnabled = true
                                    prefs.edit().putBoolean("auto_center", true).apply()
                                    lastKnownLocation?.let { location ->
                                        mapRefs.mapView?.model?.mapViewPosition?.center = location
                                        mapRefs.mapView?.repaint()
                                    }
                                },
                                containerColor = if (isAutoCenterEnabled) theme.accentCyan else theme.cardBackground.copy(alpha = 0.9f),
                                contentColor = if (isAutoCenterEnabled) Color.Black else Color.White,
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Centrar Auto", modifier = Modifier.size(18.dp))
                            }
                        }

                        FloatingActionButton(
                            onClick = onExpandClicked,
                            containerColor = theme.cardBackground.copy(alpha = 0.9f),
                            contentColor = theme.accentOrange, // 3er Tono: Pantalla completa
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Pantalla Completa", modifier = Modifier.size(20.dp))
                        }
                    }
                }

// =========================================================================
// 6. FONDO SOMBREADO PARA CIERRE DEL MENÚ LATERAL
// =========================================================================
                AnimatedVisibility(
                    visible = showMenu,
                    enter = fadeIn(),
                    exit = fadeOut()
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


                // ==========================================
                // MENÚ LATERAL (MÁS ANCHO: 400.dp Y TEXTOS 12.sp)
                // ==========================================
                AnimatedVisibility(
                    visible = showMenu,
                    enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxHeight().width(400.dp),
                        color = Color(0xFF1E1E1E),
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Navegación y Rutas Offline",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            // SECCIÓN BROUTER
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF262626)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Route, contentDescription = null, tint = theme.accentCyan, modifier = Modifier.size(22.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Motor BRouter", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }

                                        if (isBRouterInstalled) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Listo", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Falta instalar", color = Color(0xFFFF9800), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Text(
                                        text = if (isBRouterInstalled)
                                            "BRouter listo. Toca sostenido sobre el mapa para calcular 3 rutas."
                                        else
                                            "Instala BRouter para calcular rutas sin internet respetando contravías.",
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )

                                    if (!isBRouterInstalled) {
                                        Button(
                                            onClick = { openPlayStoreForBRouter() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC5), contentColor = Color.Black),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Instalar BRouter (Play Store)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = { launchBRouterApp() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x3303DAC5), contentColor = theme.accentCyan),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Abrir BRouter (Bajar Mapas)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // SECCIÓN RADARES Y ARCHIVOS
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x0DFFFFFF), shape = MaterialTheme.shapes.medium)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Audio de Radares", color = Color.White, fontSize = 12.sp)
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
                                        val currentMapFile = OfflineMapManager.getMapFile(context)
                                        if (!isMapAvailable || !currentMapFile.exists() || currentMapFile.length() <= 0L) {
                                            android.widget.Toast.makeText(context.applicationContext, "⚠️ Carga primero un mapa (.map).", android.widget.Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }

                                        if (!isSyncingCameras) {
                                            isSyncingCameras = true
                                            coroutineScope.launch {
                                                try {
                                                    val syncResult = CameraRepository.updateCamerasOnline(context, currentMapFile)
                                                    when (syncResult) {
                                                        is CameraSyncResult.Success -> {
                                                            val cameras = syncResult.cameras
                                                            mapRefs.cameras = cameras
                                                            cameraCountDisplay = cameras.size

                                                            mapRefs.mapView?.let { mv ->
                                                                val currentZoom = mv.model.mapViewPosition.zoomLevel.toInt()
                                                                val targetBitmap = when {
                                                                    currentZoom < 12 -> AndroidBitmap(createCameraMarkerBitmap(10f))
                                                                    currentZoom < 14 -> AndroidBitmap(createCameraMarkerBitmap(13f))
                                                                    currentZoom < 16 -> AndroidBitmap(createCameraMarkerBitmap(15f))
                                                                    else -> AndroidBitmap(createCameraMarkerBitmap(17f))
                                                                }

                                                                for (oldMarker in mapRefs.cameraMarkers) {
                                                                    mv.layerManager.layers.remove(oldMarker)
                                                                }
                                                                mapRefs.cameraMarkers.clear()

                                                                for (cam in cameras) {
                                                                    val camMarker = Marker(LatLong(cam.lat, cam.lon), targetBitmap, 0, 0)
                                                                    mapRefs.cameraMarkers.add(camMarker)
                                                                    mv.layerManager.layers.add(camMarker)
                                                                }
                                                                mv.layerManager.redrawLayers()
                                                                mv.repaint()
                                                            }

                                                            val mensaje = if (syncResult.addedCount > 0) {
                                                                "✅ ¡Radares actualizados!\nTotal: ${cameras.size} radares (+${syncResult.addedCount} nuevos)"
                                                            } else {
                                                                "✅ Base al día con ${cameras.size} radares para este mapa"
                                                            }
                                                            android.widget.Toast.makeText(context.applicationContext, mensaje, android.widget.Toast.LENGTH_LONG).show()
                                                        }
                                                        is CameraSyncResult.Error -> {
                                                            android.widget.Toast.makeText(context.applicationContext, "⚠️ ${syncResult.message}", android.widget.Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context.applicationContext, "Error: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isSyncingCameras = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isSyncingCameras,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC5), contentColor = Color.Black),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = "🔄 Actualizar Radares (${cameraCountDisplay})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        showMenu = false
                                        customExplorerType = "map"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x1A03DAC5), contentColor = theme.accentCyan),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("📁 Mapa (.map)", fontSize = 12.sp)
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
                                    Text(text = if (isPoiAvailable) "📁 Puntos (.poi)" else "➕ Cargar Puntos (.poi)", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            TextButton(onClick = { showMenu = false }, modifier = Modifier.align(Alignment.End)) {
                                Text("Cerrar", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (showNoFileManagerError) {
            AlertDialog(
                onDismissRequest = { showNoFileManagerError = false },
                title = { Text("Explorador no disponible", fontSize = 14.sp) },
                text = { Text("Esta radio no cuenta con un explorador de archivos instalado.", fontSize = 12.sp) },
                confirmButton = { Button(onClick = { showNoFileManagerError = false }) { Text("Aceptar", fontSize = 12.sp) } }
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
// FUNCIÓN AUXILIAR PARA SELECTOR DE ARCHIVOS
// ==========================================
fun safeLaunchPicker(
    launcher: ActivityResultLauncher<String>,
    onNotFound: () -> Unit
) {
    try {
        launcher.launch("*/*")
    } catch (e: Exception) {
        onNotFound()
    }
}

// ==========================================
// FUNCIÓN PARA OBTENER ÍCONO DE GIRO
// ==========================================
fun getManeuverIcon(type: ManeuverType): ImageVector {
    return when (type) {
        ManeuverType.TURN_LEFT, ManeuverType.SHARP_LEFT -> Icons.Default.ArrowBack
        ManeuverType.TURN_RIGHT, ManeuverType.SHARP_RIGHT -> Icons.Default.ArrowForward
        ManeuverType.UTURN -> Icons.Default.Refresh
        ManeuverType.ARRIVAL -> Icons.Default.CheckCircle
        else -> Icons.Default.Navigation
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
                    Text("Usar Explorador del Sistema", color = Color(0xFF03DAC5), fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = Color.Gray, fontSize = 12.sp)
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
                    Text("Seleccionar $extension", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Explorador de Memoria USB / Tablet", fontSize = 12.sp, color = Color.Gray)
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
                            Text("No se encontraron archivos $extension", color = Color.Gray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { currentDir = File("/storage") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222))
                            ) {
                                Text("Explorar Carpetas Manualmente", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Archivos Encontrados (${detectedFiles.size})", color = Color(0xFF03DAC5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { currentDir = File("/storage") }) {
                                    Text("Explorar USB >", color = Color.Gray, fontSize = 12.sp)
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
                Text(file.parent ?: "", color = Color.Gray, fontSize = 10.sp, maxLines = 1)
            }
        }
        Text("${sizeMb} MB", color = Color(0xFF03DAC5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// =================================================================
// MOTOR DE NAVEGACIÓN SUAVE (60 FPS SIN JALONES TIPO WAZE)
// =================================================================
@SuppressLint("MissingPermission")
fun startSmoothLocationTracking(
    context: Context,
    mapView: MapView,
    mapRefs: MapRefs,
    cartMarker: Marker,
    isAutoCenterSupplier: () -> Boolean,
    onLocationUpdated: (LatLong) -> Unit
): LocationCallback {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager

    var currentDisplayLat = 0.0
    var currentDisplayLng = 0.0
    var currentDisplayBearing = 0f
    var isFirstLocationFix = true
    var lastLocationTimestamp = 0L

    fun getShortestAngleDelta(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L).apply {
        setMinUpdateIntervalMillis(200L)
        setMaxUpdateDelayMillis(0L)
        setMinUpdateDistanceMeters(0.0f)
        setGranularity(Granularity.GRANULARITY_FINE)
        setWaitForAccurateLocation(false)
    }.build()

    fun applyFirstLocation(loc: Location) {
        if (!isFirstLocationFix) return
        isFirstLocationFix = false

        currentDisplayLat = loc.latitude
        currentDisplayLng = loc.longitude
        currentDisplayBearing = if (loc.hasBearing()) loc.bearing else 0f
        val firstPos = LatLong(loc.latitude, loc.longitude)

        cartMarker.latLong = firstPos
        onLocationUpdated(firstPos)

        val targetZoom = if (NavigationStateHolder.isNavigatingActive) 18.toByte() else 17.toByte()
        mapView.model.mapViewPosition.setMapPosition(MapPosition(firstPos, targetZoom))
        mapView.repaint()

        mapRefs.alertManager?.checkProximity(loc.latitude, loc.longitude, mapRefs.cameras)
    }

    try {
        val nativeLoc = locationManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            ?: locationManager?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            ?: locationManager?.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER)
        if (nativeLoc != null) applyFirstLocation(nativeLoc)
    } catch (e: Exception) {}

    try {
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null && isFirstLocationFix) applyFirstLocation(loc)
        }
    } catch (e: Exception) {}

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val targetLat = location.latitude
            val targetLng = location.longitude

            if (isFirstLocationFix) {
                applyFirstLocation(location)
                return
            }

            mapRefs.alertManager?.checkProximity(targetLat, targetLng, mapRefs.cameras)

            val now = SystemClock.elapsedRealtime()
            val timeDelta = if (lastLocationTimestamp == 0L) 1000L else (now - lastLocationTimestamp).coerceIn(300L, 1500L)
            lastLocationTimestamp = now

            val speedKmH = location.speed * 3.6f
            val isMoving = speedKmH >= 4.0f

            val rawTargetBearing = if (location.hasBearing() && isMoving) location.bearing else currentDisplayBearing
            val deltaBearing = if (isMoving) getShortestAngleDelta(currentDisplayBearing, rawTargetBearing) else 0f

            val startLat = currentDisplayLat
            val startLng = currentDisplayLng
            val startBearing = currentDisplayBearing

            mapRefs.currentAnimator?.cancel()

            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = timeDelta
                interpolator = LinearInterpolator()

                addUpdateListener { animation ->
                    val fraction = animation.animatedValue as Float

                    currentDisplayLat = startLat + (targetLat - startLat) * fraction
                    currentDisplayLng = startLng + (targetLng - startLng) * fraction

                    if (isMoving && Math.abs(deltaBearing) > 1.5f) {
                        currentDisplayBearing = (startBearing + (deltaBearing * fraction) + 360f) % 360f
                    }

                    val currentPos = LatLong(currentDisplayLat, currentDisplayLng)

                    cartMarker.latLong = currentPos
                    onLocationUpdated(currentPos)

                    if (isAutoCenterSupplier()) {
                        mapView.model.mapViewPosition.center = currentPos
                        if (isMoving) {
                            mapView.rotation = -currentDisplayBearing
                        }
                    }

                    mapView.repaint()
                }
            }

            animator.start()
            mapRefs.currentAnimator = animator
        }
    }

    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    return locationCallback
}