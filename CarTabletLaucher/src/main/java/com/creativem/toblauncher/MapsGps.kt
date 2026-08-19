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
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.toArgb
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
    STRAIGHT,
    SLIGHT_LEFT,
    TURN_LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    TURN_RIGHT,
    SHARP_RIGHT,
    UTURN,
    ARRIVAL
}

data class NavigationStatus(
    val nextManeuver: ManeuverType = ManeuverType.STRAIGHT,
    val maneuverInstruction: String = "Continúe recto",
    val distanceToManeuverMeters: Double = 0.0,
    val remainingDistanceMeters: Double = 0.0,
    val remainingTimeMillis: Long = 0L,
    val etaString: String = "--:--",
    val hasArrived: Boolean = false,
    val isOffRoute: Boolean = false,
    val remainingPoints: List<LatLong> = emptyList()
)

// =========================================================================
// DETECTOR DE NAVEGACIÓN EN VIVO (DETECTOR EXACTO DE GIROS POR INTERSECCIÓN)
// =========================================================================
class LiveNavigationTracker(private var route: RouteOption) {

    private var lastClosestIndex = 0

    fun updateRoute(newRoute: RouteOption) {
        this.route = newRoute
        this.lastClosestIndex = 0
    }

    fun updateProgress(currentLoc: LatLong): NavigationStatus {
        val points = route.points
        if (points.size < 2) return NavigationStatus()

        // 1. Encontrar el segmento más cercano al auto (Búsqueda inteligente hacia adelante)
        var closestSegmentIdx = lastClosestIndex
        var minDistance = Double.MAX_VALUE
        var projectedPoint = points[closestSegmentIdx.coerceIn(0, points.size - 1)]

        // Buscamos en una ventana cercana para evitar que salte al final de la ruta por error
        val searchStart = (lastClosestIndex - 2).coerceAtLeast(0)
        val searchEnd = (lastClosestIndex + 25).coerceAtMost(points.size - 1)

        for (i in searchStart until searchEnd) {
            val p1 = points[i]
            val p2 = points[i + 1]

            val dLat = p2.latitude - p1.latitude
            val dLon = p2.longitude - p1.longitude
            val l2 = dLat * dLat + dLon * dLon

            val proj = if (l2 == 0.0) p1 else {
                val t = (((currentLoc.latitude - p1.latitude) * dLat +
                        (currentLoc.longitude - p1.longitude) * dLon) / l2).coerceIn(0.0, 1.0)
                LatLong(p1.latitude + t * dLat, p1.longitude + t * dLon)
            }

            val dist = calculateDistance(currentLoc, proj)
            if (dist < minDistance) {
                minDistance = dist
                closestSegmentIdx = i
                projectedPoint = proj
            }
        }
        lastClosestIndex = closestSegmentIdx

        // 2. Detección de fuera de ruta (> 75m del trazado)
        if (minDistance > 75.0 && closestSegmentIdx < points.size - 3) {
            return NavigationStatus(
                isOffRoute = true,
                maneuverInstruction = "Recalculando ruta...",
                remainingPoints = points
            )
        }

        // 3. Llegada a destino
        val distToGoal = calculateDistance(currentLoc, points.last())
        if ((closestSegmentIdx >= points.size - 2 && distToGoal <= 35.0) || distToGoal <= 20.0) {
            return NavigationStatus(
                nextManeuver = ManeuverType.ARRIVAL,
                maneuverInstruction = "¡Has llegado a tu destino!",
                hasArrived = true,
                remainingPoints = emptyList()
            )
        }

        // 4. Polilínea restante para el mapa
        val remainingPts = ArrayList<LatLong>(points.size - closestSegmentIdx + 1)
        remainingPts.add(currentLoc)
        for (i in (closestSegmentIdx + 1) until points.size) {
            remainingPts.add(points[i])
        }

        // 5. Distancia restante total al destino final
        val nextPointIdx = (closestSegmentIdx + 1).coerceAtMost(points.size - 1)
        var remainingDist = calculateDistance(currentLoc, points[nextPointIdx])
        for (i in nextPointIdx until points.size - 1) {
            remainingDist += calculateDistance(points[i], points[i + 1])
        }

        // 6. DETECTOR REAL DE INTERSECCIONES Y ESQUINAS (Vértice a Vértice con Ventana Vectorial)
        var detectedManeuver = ManeuverType.STRAIGHT
        var distToTurn = remainingDist
        var turnFound = false
        var accumulatedDist = calculateDistance(currentLoc, points[nextPointIdx])

        for (i in nextPointIdx until (points.size - 1)) {
            // Rumbo con el que se entra a este vértice (mirando 15m atrás o segmento anterior)
            val inBearing = getIncomingBearing(points, i, 18.0)
            // Rumbo con el que se sale de este vértice (mirando 18m adelante)
            val outBearing = getOutgoingBearing(points, i, 18.0)

            var deltaAngle = (outBearing - inBearing) % 360.0
            if (deltaAngle > 180.0) deltaAngle -= 360.0
            if (deltaAngle < -180.0) deltaAngle += 360.0

            // Si en esta esquina la vía cambia más de 23 grados de dirección
            if (abs(deltaAngle) >= 23.0) {
                distToTurn = accumulatedDist
                turnFound = true

                detectedManeuver = when {
                    deltaAngle in 23.0..55.0 -> ManeuverType.SLIGHT_RIGHT
                    deltaAngle in 55.1..125.0 -> ManeuverType.TURN_RIGHT
                    deltaAngle in 125.1..165.0 -> ManeuverType.SHARP_RIGHT
                    deltaAngle in -55.0..-23.0 -> ManeuverType.SLIGHT_LEFT
                    deltaAngle in -125.0..-55.1 -> ManeuverType.TURN_LEFT
                    deltaAngle in -165.0..-125.1 -> ManeuverType.SHARP_LEFT
                    else -> ManeuverType.UTURN
                }
                break
            }

            accumulatedDist += calculateDistance(points[i], points[i + 1])
            if (accumulatedDist > 4000.0) break // Máxima anticipación: 4 km
        }

        // 7. TEXTO DE LA INSTRUCCIÓN VISUAL
        val maneuverInstructionText = if (turnFound) {
            val actionName = when (detectedManeuver) {
                ManeuverType.SLIGHT_RIGHT -> "Gire levemente a la derecha"
                ManeuverType.TURN_RIGHT -> "Gire a la derecha"
                ManeuverType.SHARP_RIGHT -> "Giro cerrado a la derecha"
                ManeuverType.SLIGHT_LEFT -> "Gire levemente a la izquierda"
                ManeuverType.TURN_LEFT -> "Gire a la izquierda"
                ManeuverType.SHARP_LEFT -> "Giro cerrado a la izquierda"
                ManeuverType.UTURN -> "Haga un giro en U"
                else -> "Continúe recto"
            }

            when {
                distToTurn <= 30.0 -> "$actionName ahora"
                distToTurn < 100.0 -> {
                    val tens = ((distToTurn / 10.0).roundToInt() * 10).coerceAtLeast(10)
                    "En $tens m $actionName"
                }
                distToTurn < 1000.0 -> {
                    val fifties = ((distToTurn / 50.0).roundToInt() * 50)
                    "En $fifties m $actionName"
                }
                else -> {
                    val km = String.format(Locale.US, "%.1f km", distToTurn / 1000.0)
                    "En $km $actionName"
                }
            }
        } else {
            if (remainingDist >= 1000.0) {
                val km = String.format(Locale.US, "%.1f km", remainingDist / 1000.0)
                "Continúe recto por $km"
            } else {
                val m = ((remainingDist / 50.0).roundToInt() * 50).coerceAtLeast(50)
                "Continúe recto por $m m"
            }
        }

        val speedMps = 40.0 * 1000.0 / 3600.0
        val remainingMs = ((remainingDist / speedMps) * 1000).toLong()
        val etaDate = Date(System.currentTimeMillis() + remainingMs)
        val etaStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(etaDate)

        return NavigationStatus(
            nextManeuver = detectedManeuver,
            maneuverInstruction = maneuverInstructionText,
            distanceToManeuverMeters = distToTurn,
            remainingDistanceMeters = remainingDist,
            remainingTimeMillis = remainingMs,
            etaString = etaStr,
            hasArrived = false,
            isOffRoute = false,
            remainingPoints = remainingPts
        )
    }

    /**
     * Calcula el rumbo de entrada hacia un vértice acumulando metros hacia atrás
     */
    private fun getIncomingBearing(points: List<LatLong>, vertexIdx: Int, lookBackMeters: Double): Double {
        var acc = 0.0
        var sourceIdx = (vertexIdx - 1).coerceAtLeast(0)
        for (i in vertexIdx downTo 1) {
            acc += calculateDistance(points[i - 1], points[i])
            sourceIdx = i - 1
            if (acc >= lookBackMeters) break
        }
        return calculateBearing(points[sourceIdx], points[vertexIdx])
    }

    /**
     * Calcula el rumbo de salida desde un vértice acumulando metros hacia adelante
     */
    private fun getOutgoingBearing(points: List<LatLong>, vertexIdx: Int, lookAheadMeters: Double): Double {
        var acc = 0.0
        var targetIdx = (vertexIdx + 1).coerceAtMost(points.size - 1)
        for (i in vertexIdx until points.size - 1) {
            acc += calculateDistance(points[i], points[i + 1])
            targetIdx = i + 1
            if (acc >= lookAheadMeters) break
        }
        return calculateBearing(points[vertexIdx], points[targetIdx])
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
        // 🔧 CORREGIDO: Se agregó "* cos(lat2)" que faltaba en la fórmula de Haversine
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}
// =========================================================================
// ESTADO GLOBAL DE NAVEGACIÓN
// =========================================================================
object NavigationStateHolder {
    var calculatedRoutes by mutableStateOf<List<RouteOption>>(emptyList())
    var selectedRouteId by mutableStateOf(0)
    var isNavigatingActive by mutableStateOf(false)
    var liveTracker: LiveNavigationTracker? = null
    var navStatus by mutableStateOf(NavigationStatus())
    var destinationLocation by mutableStateOf<LatLong?>(null)
    var isCalculatingRoute by mutableStateOf(false)
    var isSilentRecalculating = false
    var calculationJob: kotlinx.coroutines.Job? = null

    var lastKnownLocation by mutableStateOf<LatLong?>(null)
    var lastKnownBearing by mutableStateOf(0f)

    fun clear() {
        calculationJob?.cancel()
        calculationJob = null
        calculatedRoutes = emptyList()
        selectedRouteId = 0
        isNavigatingActive = false
        liveTracker = null
        navStatus = NavigationStatus()
        destinationLocation = null
        isCalculatingRoute = false
        isSilentRecalculating = false
    }
}

// ==========================================
// REPOSITORIO DE CÁMARAS
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

    var isAudioAlertsEnabled: Boolean = true

    companion object {
        private const val DISTANCE_FAR_ALERT = 300f
        private const val DISTANCE_CLOSE_ALERT = 50f
        private const val DISTANCE_RESET = 500f
        private const val AUDIO_COOLDOWN_MS = 5000L

        private val cameraAlertLevels = mutableMapOf<String, Int>()
        private var lastAlertTime = 0L
    }

    fun checkProximity(currentLat: Double, currentLng: Double, cameras: List<SpeedCamera>) {
        if (!isAudioAlertsEnabled || cameras.isEmpty()) return

        val results = FloatArray(1)
        val camerasInRange = mutableListOf<Pair<SpeedCamera, Float>>()

        for (cam in cameras) {
            val camId = "${cam.lat}_${cam.lon}"
            Location.distanceBetween(currentLat, currentLng, cam.lat, cam.lon, results)
            val distance = results[0]

            if (distance > DISTANCE_RESET) {
                cameraAlertLevels.remove(camId)
            } else {
                camerasInRange.add(cam to distance)
            }
        }

        if (camerasInRange.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastAlertTime < AUDIO_COOLDOWN_MS) return

        val closestCamEntry = camerasInRange.minByOrNull { it.second } ?: return
        val minDistance = closestCamEntry.second
        val closestId = "${closestCamEntry.first.lat}_${closestCamEntry.first.lon}"

        if (minDistance <= DISTANCE_CLOSE_ALERT) {
            val currentLevel = cameraAlertLevels[closestId] ?: 0
            if (currentLevel < 2) {
                for ((cam, dist) in camerasInRange) {
                    if (dist <= DISTANCE_FAR_ALERT) {
                        cameraAlertLevels["${cam.lat}_${cam.lon}"] = 2
                    }
                }
                lastAlertTime = now
                playAudioAlert(R.raw.alerta_cerca)
            }
        } else if (minDistance <= DISTANCE_FAR_ALERT) {
            val currentLevel = cameraAlertLevels[closestId] ?: 0
            if (currentLevel < 1) {
                for ((cam, dist) in camerasInRange) {
                    if (dist <= DISTANCE_FAR_ALERT) {
                        val lvl = cameraAlertLevels["${cam.lat}_${cam.lon}"] ?: 0
                        if (lvl < 1) {
                            cameraAlertLevels["${cam.lat}_${cam.lon}"] = 1
                        }
                    }
                }
                lastAlertTime = now
                playAudioAlert(R.raw.alerta_camara)
            }
        }
    }

    private fun playAudioAlert(@RawRes soundResId: Int) {
        if (!isAudioAlertsEnabled) return
        try {
            stopAndReleasePlayer()

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
                setOnCompletionListener { stopAndReleasePlayer() }
                setOnErrorListener { _, _, _ ->
                    stopAndReleasePlayer()
                    true
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopAndReleasePlayer()
        }
    }

    private fun solicitarAudioFocus(attributes: AudioAttributes) {
        if (audioManager == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
            }
            focusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_NOTIFICATION, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun liberarAudioFocus() {
        if (audioManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let {
                    audioManager.abandonAudioFocusRequest(it)
                    focusRequest = null
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAndReleasePlayer() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            liberarAudioFocus()
        }
    }

    fun destroy() {
        stopAndReleasePlayer()
    }
}

fun createVehicleLocationBitmap(
    zoomLevel: Float = 17f,
    accentCyanInt: Int = android.graphics.Color.parseColor("#00E5FF"),
    accentPurpleInt: Int = android.graphics.Color.parseColor("#D500F9"),
    accentOrangeInt: Int = android.graphics.Color.parseColor("#FF6D00")
): Bitmap {
    // 📏 Escala de tamaño dinámico según el zoom del mapa
    val size = when {
        zoomLevel < 12f -> 32
        zoomLevel < 14f -> 50
        zoomLevel < 16f -> 72
        else -> 96
    }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val scale = size / 96f

    fun darken(color: Int, factor: Float): Int {
        val a = android.graphics.Color.alpha(color)
        val r = (android.graphics.Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (android.graphics.Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (android.graphics.Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return android.graphics.Color.argb(a, r, g, b)
    }

    // 1. Sombra en el suelo
    val shadowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = android.graphics.Color.argb(110, 0, 0, 0)
    }
    canvas.drawCircle(center, center + (4f * scale), 28f * scale, shadowPaint)

    // 2. Halo exterior de luz Neón
    val auraPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = android.graphics.Color.argb(60, android.graphics.Color.red(accentCyanInt), android.graphics.Color.green(accentCyanInt), android.graphics.Color.blue(accentCyanInt))
    }
    canvas.drawCircle(center, center, 32f * scale, auraPaint)

    val auraStroke = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = (2.5f * scale).coerceAtLeast(1f)
        color = android.graphics.Color.argb(160, android.graphics.Color.red(accentCyanInt), android.graphics.Color.green(accentCyanInt), android.graphics.Color.blue(accentCyanInt))
    }
    canvas.drawCircle(center, center, 32f * scale, auraStroke)

    // 3. Base de la pirámide (Nivel Naranja)
    val tier1Paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        shader = android.graphics.RadialGradient(
            center, center, (24f * scale).coerceAtLeast(1f),
            intArrayOf(accentOrangeInt, darken(accentOrangeInt, 0.4f)),
            floatArrayOf(0.2f, 1.0f),
            android.graphics.Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(center, center, 24f * scale, tier1Paint)

    // 4. Nivel medio (Púrpura)
    val tier2Paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        shader = android.graphics.RadialGradient(
            center, center - (2f * scale), (17f * scale).coerceAtLeast(1f),
            intArrayOf(accentPurpleInt, darken(accentPurpleInt, 0.4f)),
            floatArrayOf(0.2f, 1.0f),
            android.graphics.Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(center, center - (2f * scale), 17f * scale, tier2Paint)

    // 5. Nivel superior (Cyan brillante)
    val tier3Paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        shader = android.graphics.RadialGradient(
            center, center - (4f * scale), (11f * scale).coerceAtLeast(1f),
            intArrayOf(android.graphics.Color.WHITE, accentCyanInt, darken(accentCyanInt, 0.5f)),
            floatArrayOf(0.0f, 0.4f, 1.0f),
            android.graphics.Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(center, center - (4f * scale), 11f * scale, tier3Paint)

    // 6. Destello blanco en el ápice
    val glintPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(center - (1.5f * scale), center - (5.5f * scale), (2.5f * scale).coerceAtLeast(0.5f), glintPaint)

    return bitmap
}

fun createDestinationMarkerBitmap(zoomLevel: Float = 17f): Bitmap {
    val size = when {
        zoomLevel < 12f -> 30
        zoomLevel < 14f -> 44
        zoomLevel < 16f -> 60
        else -> 80
    }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val scale = size / 80f
    val paint = Paint().apply { isAntiAlias = true }

    paint.color = android.graphics.Color.parseColor("#E53935")
    paint.style = Paint.Style.FILL
    val path = Path().apply {
        moveTo(22f * scale, 12f * scale)
        lineTo(68f * scale, 28f * scale)
        lineTo(22f * scale, 44f * scale)
        close()
    }
    canvas.drawPath(path, paint)

    paint.color = android.graphics.Color.parseColor("#FFFFFF")
    paint.strokeWidth = 5f * scale
    paint.style = Paint.Style.STROKE
    canvas.drawLine(22f * scale, 12f * scale, 22f * scale, 72f * scale, paint)

    paint.color = android.graphics.Color.parseColor("#E53935")
    paint.style = Paint.Style.FILL
    canvas.drawCircle(22f * scale, 72f * scale, 6f * scale, paint)

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
// VISTA PRINCIPAL MAPSFORGE
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
    val currentOnLocationUpdated by rememberUpdatedState(onLocationUpdated)

    LaunchedEffect(Unit) {
        try { AndroidGraphicFactory.createInstance(context.applicationContext) } catch (e: Exception) {}
    }

    LaunchedEffect(isCameraAudioEnabled) {
        mapRefs.alertManager?.isAudioAlertsEnabled = isCameraAudioEnabled
    }

    // 🎨 Actualizar colores del tema en el ícono del GPS y en las rutas dinámicamente
    LaunchedEffect(theme.accentCyan, theme.accentPurple, theme.accentOrange, theme.id) {
        val cyanInt = theme.accentCyan.toArgb()
        val purpleInt = theme.accentPurple.toArgb()
        val orangeInt = theme.accentOrange.toArgb()

        // 🚗 1. Actualizar inmediatamente los colores del ícono de posición del auto
        mapRefs.mapView?.let { mv ->
            val currentZoom = mv.model.mapViewPosition.zoomLevel.toFloat()
            mapRefs.marker?.bitmap = AndroidBitmap(
                createVehicleLocationBitmap(
                    zoomLevel = currentZoom,
                    accentCyanInt = cyanInt,
                    accentPurpleInt = purpleInt,
                    accentOrangeInt = orangeInt
                )
            )
            mv.repaint()
        }

        // 🛣️ 2. Actualizar colores de las líneas de navegación
        mapRefs.routeLayerManager?.updateThemeColors(
            primaryColorInt = cyanInt,
            secondaryColorInt = purpleInt
        )
        if (NavigationStateHolder.calculatedRoutes.isNotEmpty()) {
            mapRefs.routeLayerManager?.renderRoutes(
                routes = NavigationStateHolder.calculatedRoutes,
                selectedRouteId = NavigationStateHolder.selectedRouteId,
                primaryColorInt = cyanInt,
                secondaryColorInt = purpleInt,
                accentColorInt = orangeInt
            )
        }
    }

    LaunchedEffect(mapRefs.mapView) {
        val mapView = mapRefs.mapView ?: return@LaunchedEffect
        val mainHandler = android.os.Handler(Looper.getMainLooper())

        if (mapRefs.alertManager == null) {
            mapRefs.alertManager = ProximityAlertManager(context).apply {
                isAudioAlertsEnabled = isCameraAudioEnabled
            }
        }

        var lastZoomBucket = -1
        val refreshElementsRunnable = Runnable {
            if (mapRefs.mapView != null) {
                val currentZoom = mapView.model.mapViewPosition.zoomLevel.toInt()
                val currentBucket = when {
                    currentZoom < 12 -> 1
                    currentZoom < 14 -> 2
                    currentZoom < 16 -> 3
                    else -> 4
                }

                if (currentBucket != lastZoomBucket) {
                    lastZoomBucket = currentBucket
                    val zoomFloat = when (currentBucket) {
                        1 -> 10f
                        2 -> 13f
                        3 -> 15f
                        else -> 17f
                    }

                    // 🚗 Redimensionar ícono del Auto según el Zoom
                    mapRefs.marker?.bitmap = AndroidBitmap(
                        createVehicleLocationBitmap(
                            zoomLevel = zoomFloat,
                            accentCyanInt = theme.accentCyan.toArgb(),
                            accentPurpleInt = theme.accentPurple.toArgb(),
                            accentOrangeInt = theme.accentOrange.toArgb()
                        )
                    )

                    // 🚩 Redimensionar Bandera de Destino
                    mapRefs.destinationMarker?.let { dm ->
                        dm.bitmap = AndroidBitmap(createDestinationMarkerBitmap(zoomFloat))
                        dm.verticalOffset = (-30 * (zoomFloat / 17f)).toInt()
                    }

                    // 📷 Redimensionar Cámaras
                    if (mapRefs.cameras.isNotEmpty()) {
                        val targetCamBitmap = AndroidBitmap(createCameraMarkerBitmap(zoomFloat))
                        for (oldMarker in mapRefs.cameraMarkers) {
                            mapView.layerManager.layers.remove(oldMarker)
                        }
                        mapRefs.cameraMarkers.clear()

                        for (cam in mapRefs.cameras) {
                            val camMarker = Marker(LatLong(cam.lat, cam.lon), targetCamBitmap, 0, 0)
                            mapRefs.cameraMarkers.add(camMarker)
                            mapView.layerManager.layers.add(camMarker)
                        }
                    }

                    // 🔝 Asegurar que el GPS siempre esté arriba de todo
                    mapRefs.marker?.let { m ->
                        mapView.layerManager.layers.remove(m)
                        mapView.layerManager.layers.add(m)
                    }

                    mapView.repaint()
                }

                mapRefs.marker?.latLong?.let { pos ->
                    mapRefs.alertManager?.checkProximity(pos.latitude, pos.longitude, mapRefs.cameras)
                }
            }
        }

        mapRefs.zoomObserver?.let { mapView.model.mapViewPosition.removeObserver(it) }
        val zoomObserver = Observer {
            mainHandler.removeCallbacks(refreshElementsRunnable)
            mainHandler.postDelayed(refreshElementsRunnable, 150L)
        }
        mapRefs.zoomObserver = zoomObserver
        mapView.model.mapViewPosition.addObserver(zoomObserver)

        val cached = withContext(Dispatchers.IO) { CameraRepository.getCachedCameras(context) }
        if (cached.isNotEmpty()) {
            mapRefs.cameras = cached
            refreshElementsRunnable.run()
        }

        if (NavigationStateHolder.calculatedRoutes.isNotEmpty()) {
            mapRefs.routeLayerManager?.renderRoutes(
                routes = NavigationStateHolder.calculatedRoutes,
                selectedRouteId = NavigationStateHolder.selectedRouteId,
                primaryColorInt = theme.accentCyan.toArgb(),
                secondaryColorInt = theme.accentPurple.toArgb(),
                accentColorInt = theme.accentOrange.toArgb()
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
                LocationServices.getFusedLocationProviderClient(context).removeLocationUpdates(callback)
            }
            mapRefs.mapView?.let { mv ->
                try {
                    mapRefs.zoomObserver?.let { mv.model.mapViewPosition.removeObserver(it) }
                    mv.destroyAll()
                } catch (e: Exception) {}
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().clipToBounds().clip(RoundedCornerShape(16.dp))) {
        if (isMapAvailable && targetMapFile.exists() && targetMapFile.length() > 0) {
            key(targetMapFile.absolutePath, targetMapFile.lastModified(), targetMapFile.length()) {
                AndroidView(
                    factory = { ctx ->
                        try { AndroidGraphicFactory.createInstance(ctx.applicationContext) } catch (e: Exception) {}

                        val mapFile = try {
                            if (targetMapFile.exists() && targetMapFile.length() > 0) MapFile(targetMapFile) else null
                        } catch (e: Exception) {
                            onMapLoadError("El archivo seleccionado no es válido.")
                            null
                        }

                        val targetZoom = if (NavigationStateHolder.isNavigatingActive) 18.toByte() else 17.toByte()
                        val initialPos = NavigationStateHolder.lastKnownLocation ?: LatLong(4.7110, -74.0721)

                        val mapView = MapView(ctx).apply {
                            keepScreenOn = true
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            setBackgroundColor(android.graphics.Color.parseColor("#E5E0D8"))
                            model.frameBufferModel.overdrawFactor = 1.25
                            model.displayModel.userScaleFactor = 1.15f
                            setZoomLevelMin(3.toByte())
                            setZoomLevelMax(18.toByte())
                            model.mapViewPosition.setMapPosition(MapPosition(initialPos, targetZoom))
                            if (NavigationStateHolder.lastKnownBearing != 0f) {
                                rotation = -NavigationStateHolder.lastKnownBearing
                            }
                        }

                        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onLongPress(e: MotionEvent) {
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
                                MotionEvent.ACTION_DOWN -> { startTouchX = event.x; startTouchY = event.y }
                                MotionEvent.ACTION_MOVE -> {
                                    if (abs(event.x - startTouchX) > 15f || abs(event.y - startTouchY) > 15f) {
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
                            val tileCache = AndroidUtil.createTileCache(ctx, "mapcache", mapView.model.displayModel.tileSize, 1.2f, 1.25)
                            val rendererLayer = org.mapsforge.map.layer.renderer.TileRendererLayer(
                                tileCache, mapFile, mapView.model.mapViewPosition, false, true, true, AndroidGraphicFactory.INSTANCE
                            ).apply {
                                setXmlRenderTheme(org.mapsforge.map.rendertheme.InternalRenderTheme.DEFAULT)
                                setTextScale(1.15f)
                            }
                            mapView.layerManager.layers.add(rendererLayer)
                        }

                        val initialMarkerBitmap = AndroidBitmap(
                            createVehicleLocationBitmap(
                                accentCyanInt = theme.accentCyan.toArgb(),
                                accentPurpleInt = theme.accentPurple.toArgb(),
                                accentOrangeInt = theme.accentOrange.toArgb()
                            )
                        )
                        val cartMarker = Marker(initialPos, initialMarkerBitmap, 0, 0)
                        mapView.layerManager.layers.add(cartMarker)

                        mapRefs.mapView = mapView
                        mapRefs.marker = cartMarker
                        mapRefs.routeLayerManager = RouteLayerManager(mapView)

                        val callback = startSmoothLocationTracking(
                            ctx, mapView, mapRefs, cartMarker,
                            isAutoCenterSupplier = { currentAutoCenterEnabled },
                            onLocationUpdated = { loc -> currentOnLocationUpdated(loc) }
                        )
                        mapRefs.locationCallback = callback

                        mapView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // BOTONES DE ZOOM
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        onDisableAutoCenter()
                        mapRefs.mapView?.let { mv ->
                            val cur = mv.model.mapViewPosition.zoomLevel
                            val max = mv.model.mapViewPosition.zoomLevelMax
                            if (cur < max) {
                                mv.model.mapViewPosition.zoomLevel = (cur + 1).toByte()
                                mv.repaint()
                            }
                        }
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
                        mapRefs.mapView?.let { mv ->
                            val cur = mv.model.mapViewPosition.zoomLevel
                            val min = mv.model.mapViewPosition.zoomLevelMin
                            if (cur > min) {
                                mv.model.mapViewPosition.zoomLevel = (cur - 1).toByte()
                                mv.repaint()
                            }
                        }
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
                modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)).padding(24.dp),
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
    val currentTheme by rememberUpdatedState(theme)
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
            mv.rotation = 0f
            mapRefs.destinationMarker?.let { mv.layerManager.layers.remove(it) }
            mapRefs.destinationMarker = null
            mv.repaint()
        }
    }

    fun triggerSilentRecalculation(currentLocation: LatLong) {
        val destination = NavigationStateHolder.destinationLocation ?: return
        if (NavigationStateHolder.isSilentRecalculating) return

        NavigationStateHolder.isSilentRecalculating = true

        coroutineScope.launch(Dispatchers.IO) {
            val routes = BRouterEngine.getTop3Routes(currentLocation, destination)
            if (routes.isNotEmpty()) {
                val fastestRoute = routes.first()
                withContext(Dispatchers.Main) {
                    NavigationStateHolder.calculatedRoutes = routes
                    NavigationStateHolder.selectedRouteId = fastestRoute.id
                    NavigationStateHolder.liveTracker?.updateRoute(fastestRoute)

                    mapRefs.routeLayerManager?.renderRoutes(
                        routes = routes,
                        selectedRouteId = fastestRoute.id,
                        primaryColorInt = currentTheme.accentCyan.toArgb(),
                        secondaryColorInt = currentTheme.accentPurple.toArgb(),
                        accentColorInt = currentTheme.accentOrange.toArgb()
                    )
                }
            }
            NavigationStateHolder.isSilentRecalculating = false
        }
    }

    fun onRequestRouteTo(destination: LatLong) {
        if (NavigationStateHolder.isCalculatingRoute || NavigationStateHolder.isNavigatingActive) {
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

        NavigationStateHolder.clear()
        NavigationStateHolder.isCalculatingRoute = true
        NavigationStateHolder.destinationLocation = destination

        mapRefs.mapView?.let { mv ->
            mapRefs.destinationMarker?.let { mv.layerManager.layers.remove(it) }
            val flagBitmap = AndroidBitmap(createDestinationMarkerBitmap())
            val destMarker = Marker(destination, flagBitmap, 0, -35)
            mapRefs.destinationMarker = destMarker
            mv.layerManager.layers.add(destMarker)
            mv.repaint()
        }

        NavigationStateHolder.calculationJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val routes = BRouterEngine.getTop3Routes(start, destination)
            withContext(Dispatchers.Main) {
                NavigationStateHolder.isCalculatingRoute = false
                if (routes.isNotEmpty()) {
                    val bestRoute = routes.first()
                    NavigationStateHolder.calculatedRoutes = routes
                    NavigationStateHolder.selectedRouteId = 0

                    // 🧭 PONER AL FRENTE DE INMEDIATO LA RUTA CALCULADA
                    val initialBearing = getInitialRouteBearing(bestRoute.points, lastKnownLocation)
                    NavigationStateHolder.lastKnownBearing = initialBearing

                    mapRefs.mapView?.let { mv ->
                        mv.rotation = -initialBearing
                        lastKnownLocation?.let { mv.model.mapViewPosition.center = it }
                        mv.repaint()
                    }

                    mapRefs.routeLayerManager?.renderRoutes(
                        routes = routes,
                        selectedRouteId = 0,
                        primaryColorInt = currentTheme.accentCyan.toArgb(),
                        secondaryColorInt = currentTheme.accentPurple.toArgb(),
                        accentColorInt = currentTheme.accentOrange.toArgb()
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
                            } else if (status.isOffRoute) {
                                triggerSilentRecalculation(loc)
                            } else {
                                mapRefs.routeLayerManager?.updateActiveRouteProgress(status.remainingPoints)
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
                                mapRefs.mapView?.model?.mapViewPosition?.center = selectedDest
                                onRequestRouteTo(selectedDest)
                            }
                        )
                    }
                }

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
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val distFormatted = if (NavigationStateHolder.navStatus.remainingDistanceMeters >= 1000) {
                                    String.format(Locale.US, "%.1f km", NavigationStateHolder.navStatus.remainingDistanceMeters / 1000.0)
                                } else {
                                    "${NavigationStateHolder.navStatus.remainingDistanceMeters.toInt()} m"
                                }

                                Text(
                                    text = distFormatted,
                                    color = theme.accentCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(theme.accentOrange)
                                        .clickable { stopNavigation() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Salir",
                                        tint = Color.Black,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }

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
                                val selectedRoute = NavigationStateHolder.calculatedRoutes.find { it.id == id }

                                // 🧭 Reorientar hacia la salida de la alternativa seleccionada
                                if (selectedRoute != null) {
                                    val routeBearing = getInitialRouteBearing(selectedRoute.points, lastKnownLocation)
                                    NavigationStateHolder.lastKnownBearing = routeBearing
                                    mapRefs.mapView?.let { mv ->
                                        mv.rotation = -routeBearing
                                        mv.repaint()
                                    }
                                }

                                mapRefs.routeLayerManager?.renderRoutes(
                                    routes = NavigationStateHolder.calculatedRoutes,
                                    selectedRouteId = id,
                                    primaryColorInt = currentTheme.accentCyan.toArgb(),
                                    secondaryColorInt = currentTheme.accentPurple.toArgb(),
                                    accentColorInt = currentTheme.accentOrange.toArgb()
                                )
                            },
                            onStartNavigation = {
                                val activeRoute = NavigationStateHolder.calculatedRoutes.find { it.id == NavigationStateHolder.selectedRouteId }
                                    ?: NavigationStateHolder.calculatedRoutes.first()

                                val tracker = LiveNavigationTracker(activeRoute)
                                NavigationStateHolder.liveTracker = tracker
                                NavigationStateHolder.isNavigatingActive = true
                                isAutoCenterEnabled = true
                                prefs.edit().putBoolean("auto_center", true).apply()

                                // ⚡ UBICACIÓN VÁLIDA GARANTIZADA: Si una es null, usa la otra o el punto inicial de la ruta
                                val currentLocation = lastKnownLocation
                                    ?: NavigationStateHolder.lastKnownLocation
                                    ?: activeRoute.points.first()

                                // 🚀 FORZAR EVALUACIÓN INSTANTÁNEA DEL PRIMER GIRO
                                val initialStatus = tracker.updateProgress(currentLocation)
                                NavigationStateHolder.navStatus = initialStatus

                                // 🧭 ALINEACIÓN FRONTAL INMEDIATA DEL MAPA AL FRENTE
                                val initialBearing = getInitialRouteBearing(activeRoute.points, currentLocation)
                                NavigationStateHolder.lastKnownBearing = initialBearing

                                mapRefs.mapView?.let { mv ->
                                    mv.model.mapViewPosition.zoomLevel = 18.toByte()
                                    mv.model.mapViewPosition.center = currentLocation
                                    if (mv.width > 0 && mv.height > 0) {
                                        mv.pivotX = mv.width / 2f
                                        mv.pivotY = mv.height / 2f
                                    }
                                    mv.rotation = -initialBearing
                                    mv.repaint()
                                }
                            },
                            onCancel = { stopNavigation() }
                        )
                    }
                }

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
                            contentColor = theme.accentOrange,
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Pantalla Completa", modifier = Modifier.size(20.dp))
                        }
                    }
                }

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
// SELECCIÓN Y MODAL USB
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

fun getManeuverIcon(type: ManeuverType): ImageVector {
    return when (type) {
        ManeuverType.TURN_LEFT, ManeuverType.SHARP_LEFT, ManeuverType.SLIGHT_LEFT -> Icons.Default.ArrowBack
        ManeuverType.TURN_RIGHT, ManeuverType.SHARP_RIGHT, ManeuverType.SLIGHT_RIGHT -> Icons.Default.ArrowForward
        ManeuverType.UTURN -> Icons.Default.Refresh
        ManeuverType.ARRIVAL -> Icons.Default.CheckCircle
        else -> Icons.Default.Navigation
    }
}

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

// =========================================================================
// UTILIDADES DE CÁLCULO ANGULAR Y RUMBO (ESTILO WAZE)
// =========================================================================

/**
 * Calcula la diferencia angular más corta entre dos rumbos (-180° a +180°).
 * Garantiza que la animación tome el camino más corto al rotar el mapa.
 */
private fun calculateShortestAngleDelta(from: Float, to: Float): Float {
    var delta = (to - from) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return delta
}

/**
 * Calcula el rumbo geográfico (Bearing) en grados (0° a 360°) entre dos puntos.
 */
private fun calculateBearingBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val dLon = Math.toRadians(lon2 - lon1)
    val y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2))
    val x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
            Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon)
    var bearing = Math.toDegrees(Math.atan2(y, x)).toFloat()
    return (bearing + 360f) % 360f
}

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

    var lastFixLat = NavigationStateHolder.lastKnownLocation?.latitude ?: 0.0
    var lastFixLng = NavigationStateHolder.lastKnownLocation?.longitude ?: 0.0
    var currentDisplayLat = lastFixLat
    var currentDisplayLng = lastFixLng
    var currentDisplayBearing = NavigationStateHolder.lastKnownBearing
    var hasValidInitialFix = (NavigationStateHolder.lastKnownLocation != null)
    var smoothedTargetBearing = currentDisplayBearing

    fun handleIncomingLocation(location: Location) {
        val targetLat = location.latitude
        val targetLng = location.longitude

        val distResults = FloatArray(1)
        if (hasValidInitialFix) {
            Location.distanceBetween(lastFixLat, lastFixLng, targetLat, targetLng, distResults)
        }
        val movedDistanceMeters = if (hasValidInitialFix) distResults[0] else 0f

        if (!hasValidInitialFix || movedDistanceMeters > 300f) {
            hasValidInitialFix = true
            lastFixLat = targetLat
            lastFixLng = targetLng
            currentDisplayLat = targetLat
            currentDisplayLng = targetLng

            val initialBearing = if (location.hasBearing() && location.bearing > 0.0f) location.bearing else currentDisplayBearing
            currentDisplayBearing = initialBearing
            smoothedTargetBearing = initialBearing

            val firstPos = LatLong(targetLat, targetLng)
            NavigationStateHolder.lastKnownLocation = firstPos
            NavigationStateHolder.lastKnownBearing = currentDisplayBearing

            cartMarker.latLong = firstPos
            onLocationUpdated(firstPos)

            val targetZoom = if (NavigationStateHolder.isNavigatingActive) 18.toByte() else 17.toByte()
            mapView.model.mapViewPosition.setMapPosition(MapPosition(firstPos, targetZoom))
            if (isAutoCenterSupplier()) {
                mapView.rotation = -currentDisplayBearing
            }
            mapView.repaint()
            return
        }

        // 🧭 Cálculo de Rumbo (inmune a fallos de hardware en radios de auto)
        var newCalculatedBearing = -1f
        if (location.hasBearing() && location.bearing > 0.0f) {
            newCalculatedBearing = location.bearing
        } else if (movedDistanceMeters >= 1.2f) {
            newCalculatedBearing = calculateBearingBetween(lastFixLat, lastFixLng, targetLat, targetLng)
        }

        if (newCalculatedBearing >= 0f) {
            val delta = calculateShortestAngleDelta(smoothedTargetBearing, newCalculatedBearing)
            smoothedTargetBearing = (smoothedTargetBearing + delta * 0.75f + 360f) % 360f
        }

        lastFixLat = targetLat
        lastFixLng = targetLng

        val startLat = currentDisplayLat
        val startLng = currentDisplayLng
        val startBearing = currentDisplayBearing
        val finalTargetBearing = smoothedTargetBearing

        mapRefs.currentAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            interpolator = LinearInterpolator()

            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float

                currentDisplayLat = startLat + (targetLat - startLat) * fraction
                currentDisplayLng = startLng + (targetLng - startLng) * fraction
                val currentPos = LatLong(currentDisplayLat, currentDisplayLng)

                val angleDelta = calculateShortestAngleDelta(startBearing, finalTargetBearing)
                currentDisplayBearing = (startBearing + (angleDelta * fraction) + 360f) % 360f

                NavigationStateHolder.lastKnownLocation = currentPos
                NavigationStateHolder.lastKnownBearing = currentDisplayBearing

                cartMarker.latLong = currentPos
                onLocationUpdated(currentPos)

                if (isAutoCenterSupplier()) {
                    mapView.model.mapViewPosition.center = currentPos
                    if (mapView.width > 0 && mapView.height > 0) {
                        // El pivot en el centro para rotar la perspectiva hacia el frente
                        mapView.pivotX = mapView.width / 2f
                        mapView.pivotY = mapView.height / 2f
                    }
                    mapView.rotation = -currentDisplayBearing
                }
                mapView.repaint()
            }
        }

        animator.start()
        mapRefs.currentAnimator = animator
    }

    try {
        val nativeLoc = locationManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            ?: locationManager?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
        if (nativeLoc != null) handleIncomingLocation(nativeLoc)
    } catch (e: Exception) {}

    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L).apply {
        setMinUpdateIntervalMillis(200L)
        setMaxUpdateDelayMillis(0L)
        setMinUpdateDistanceMeters(0.0f)
        setGranularity(Granularity.GRANULARITY_FINE)
    }.build()

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleIncomingLocation(location)
        }
    }

    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    return locationCallback
}
/**
 * Calcula el rumbo exacto de salida de la ruta trazada (mirando ~30m hacia adelante).
 * Permite orientar el mapa al frente de inmediato aunque el auto esté estacionado.
 */
fun getInitialRouteBearing(routePoints: List<LatLong>, currentLocation: LatLong?): Float {
    if (routePoints.size < 2) return 0f
    val start = currentLocation ?: routePoints.first()

    var targetPoint = routePoints[1]
    var accumulatedDist = 0.0

    // Buscamos un punto a ~25-35 metros para evitar micro-curvas de inicio
    for (i in 0 until routePoints.size - 1) {
        val d = calculateDistanceBetweenPoints(routePoints[i], routePoints[i + 1])
        accumulatedDist += d
        if (accumulatedDist >= 25.0) {
            targetPoint = routePoints[i + 1]
            break
        }
    }

    return calculateBearingBetween(
        start.latitude, start.longitude,
        targetPoint.latitude, targetPoint.longitude
    )
}

private fun calculateDistanceBetweenPoints(p1: LatLong, p2: LatLong): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(p2.latitude - p1.latitude)
    val dLon = Math.toRadians(p2.longitude - p1.longitude)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) * sin(dLon / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}