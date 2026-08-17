package com.creativem.toblauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.mapsforge.core.model.LatLong
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.*

data class RouteOption(
    val id: Int,
    val title: String,
    val distanceMeters: Double,
    val timeMillis: Long,
    val points: List<LatLong>,
    val isSelected: Boolean = false
) {
    val formattedDistance: String
        get() = if (distanceMeters >= 1000) {
            String.format(Locale.US, "%.1f km", distanceMeters / 1000.0)
        } else {
            "${distanceMeters.toInt()} m"
        }

    val formattedTime: String
        get() {
            val minutes = (timeMillis / 60000).toInt()
            return if (minutes >= 60) {
                "${minutes / 60} h ${minutes % 60} min"
            } else {
                "$minutes min"
            }
        }
}

// =========================================================================
// CLIENTE IPC NATIVO DE BROUTER
// =========================================================================
class BRouterServiceClient(private val remoteBinder: IBinder) {
    companion object {
        private const val DESCRIPTOR = "btools.routingapp.IBRouterService"
        private const val TRANSACTION_getTrackFromParams = IBinder.FIRST_CALL_TRANSACTION + 0
    }

    fun isAlive(): Boolean = remoteBinder.isBinderAlive && remoteBinder.pingBinder()

    fun getTrackFromParams(params: Bundle): String? {
        if (!isAlive()) return null

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(1)
            params.writeToParcel(data, 0)

            val success = remoteBinder.transact(TRANSACTION_getTrackFromParams, data, reply, 0)
            if (!success) return null

            reply.readException()
            reply.readString()
        } catch (e: DeadObjectException) {
            Log.e("BROUTER_IPC", "⚠️ Servicio BRouter murió.")
            null
        } catch (e: RemoteException) {
            Log.e("BROUTER_IPC", "⚠️ Error IPC: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e("BROUTER_IPC", "⚠️ Error: ${e.message}")
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}

// =========================================================================
// MOTOR DE ENRUTAMIENTO HÍBRIDO (ONLINE OSRM + OFFLINE BROUTER BLINDADO)
// =========================================================================
object BRouterEngine {
    private const val TAG = "BROUTER_ENGINE"

    @Volatile
    private var brouterClient: BRouterServiceClient? = null

    @Volatile
    var isBound = false
        private set

    private var appContext: Context? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service != null) {
                brouterClient = BRouterServiceClient(service)
                isBound = true
                Log.i(TAG, "✅ BRouter Conectado.")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            brouterClient = null
            isBound = false
            Log.w(TAG, "⚠️ BRouter Desconectado.")
            appContext?.let { bind(it) }
        }

        override fun onBindingDied(name: ComponentName?) {
            brouterClient = null
            isBound = false
            appContext?.let { bind(it) }
        }
    }

    fun bind(context: Context) {
        appContext = context.applicationContext
        if (isBound && brouterClient?.isAlive() == true) return

        try {
            val intent = Intent().apply {
                component = ComponentName("btools.routingapp", "btools.routingapp.BRouterService")
            }
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                val fallbackIntent = Intent("btools.routingapp.IBRouterService").apply {
                    setPackage("btools.routingapp")
                }
                context.bindService(fallbackIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al vincular BRouter: ${e.message}")
        }
    }

    fun unbind(context: Context) {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {}
            isBound = false
            brouterClient = null
        }
    }

    fun isBRouterInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("btools.routingapp", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Calcula rutas garantizadas (Intento Online primero, si no hay red usa BRouter con radio de 1000m)
     */
    suspend fun getTop3Routes(
        start: LatLong,
        destination: LatLong
    ): List<RouteOption> = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()

        // 1. INTENTO ONLINE: OSRM (Calcula en 100ms y tiene auto-atracción a la avenida)
        try {
            val onlineRoutes = calculateOnlineOSRM(start, destination)
            if (onlineRoutes.isNotEmpty()) {
                val elapsed = System.currentTimeMillis() - startTimeMs
                Log.i(TAG, "⚡ ${onlineRoutes.size} ruta(s) obtenida(s) ONLINE (OSRM) en ${elapsed} ms.")
                return@withContext onlineRoutes
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ OSRM Online no disponible, usando BRouter Offline: ${e.message}")
        }

        // 2. INTENTO OFFLINE: BRouter con rango de búsqueda ampliado (1000m)
        val client = brouterClient
        if (client == null || !client.isAlive()) {
            Log.e(TAG, "❌ BRouter no está vinculado.")
            return@withContext emptyList()
        }

        val lonlats = String.format(
            Locale.US,
            "%.6f,%.6f|%.6f,%.6f",
            start.longitude, start.latitude,
            destination.longitude, destination.latitude
        )

        val routes = mutableListOf<RouteOption>()
        val mainRoute = queryBRouterTrack(client, lonlats, altIndex = 0, profile = "car-eco")
            ?: queryBRouterTrack(client, lonlats, altIndex = 0, profile = "car-fast")
            ?: queryBRouterTrack(client, lonlats, altIndex = 0, profile = "car-test")

        if (mainRoute != null) {
            routes.add(mainRoute)

            // Alternativas secundarias
            for (altIndex in 1..2) {
                try {
                    val alt = queryBRouterTrack(client, lonlats, altIndex = altIndex, profile = "car-eco")
                    if (alt != null) {
                        val isDuplicate = routes.any { abs(it.distanceMeters - alt.distanceMeters) < 25.0 }
                        if (!isDuplicate) routes.add(alt)
                    }
                } catch (e: Exception) {}
            }
        }

        val elapsed = System.currentTimeMillis() - startTimeMs
        Log.i(TAG, "⚡ ${routes.size} ruta(s) obtenida(s) OFFLINE (BRouter) en ${elapsed} ms.")

        routes
    }

    private fun queryBRouterTrack(
        client: BRouterServiceClient,
        lonlats: String,
        altIndex: Int,
        profile: String
    ): RouteOption? {
        val params = Bundle().apply {
            putString("trackFormat", "gpx")
            putString("v", "motorcar")
            putString("profile", profile)
            putString("lonlats", lonlats)
            putInt("alternativeidx", altIndex)
            // 👇 ESTE ES EL SECRETO: Expande el radio de búsqueda hasta 1000m para atrapar avenidas lejanas al GPS
            putInt("waypointCatchingRange", 1000)
            putString("waypointCatchingRange", "1000")
        }

        val result = client.getTrackFromParams(params) ?: return null

        return if (result.contains("<gpx", ignoreCase = true) && result.contains("<trkpt")) {
            parseGpxFast(result, altIndex)
        } else {
            Log.w(TAG, "⚠️ BRouter mensaje ($profile): $result")
            null
        }
    }

    private fun parseGpxFast(gpxXml: String, index: Int): RouteOption? {
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(gpxXml))

            val points = ArrayList<LatLong>(1024)
            var totalDistance = 0.0
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "trkpt") {
                    val latStr = parser.getAttributeValue(null, "lat")
                    val lonStr = parser.getAttributeValue(null, "lon")

                    if (latStr != null && lonStr != null) {
                        val lat = latStr.toDoubleOrNull()
                        val lon = lonStr.toDoubleOrNull()

                        if (lat != null && lon != null) {
                            val current = LatLong(lat, lon)
                            if (points.isNotEmpty()) {
                                totalDistance += fastHaversine(points[points.size - 1], current)
                            }
                            points.add(current)
                        }
                    }
                }
                eventType = parser.next()
            }

            if (points.isEmpty()) return null

            val speedMetersPerSec = 50.0 * 1000.0 / 3600.0
            val estimatedTimeMs = ((totalDistance / speedMetersPerSec) * 1000.0).toLong()

            val title = when (index) {
                0 -> "Ruta Principal"
                1 -> "Alternativa 1"
                else -> "Alternativa 2"
            }

            RouteOption(
                id = index,
                title = title,
                distanceMeters = totalDistance,
                timeMillis = estimatedTimeMs,
                points = points,
                isSelected = (index == 0)
            )
        } catch (e: Exception) {
            null
        }
    }

    // =========================================================================
    // CONSULTA OSRM (100 MS CON AUTO-SNAP A CARRETERAS)
    // =========================================================================
    private fun calculateOnlineOSRM(start: LatLong, destination: LatLong): List<RouteOption> {
        val routes = mutableListOf<RouteOption>()
        val urlStr = String.format(
            Locale.US,
            "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&alternatives=true",
            start.longitude, start.latitude,
            destination.longitude, destination.latitude
        )

        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("User-Agent", "CarTabletLauncher/15.0")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(jsonStr)
                val routesJson = root.optJSONArray("routes") ?: return emptyList()

                for (i in 0 until routesJson.length().coerceAtMost(3)) {
                    val rObj = routesJson.getJSONObject(i)
                    val distance = rObj.optDouble("distance", 0.0)
                    val durationSeconds = rObj.optDouble("duration", 0.0)
                    val geometry = rObj.optJSONObject("geometry") ?: continue
                    val coords = geometry.optJSONArray("coordinates") ?: continue

                    val latLongList = mutableListOf<LatLong>()
                    for (j in 0 until coords.length()) {
                        val c = coords.getJSONArray(j)
                        val lon = c.getDouble(0)
                        val lat = c.getDouble(1)
                        latLongList.add(LatLong(lat, lon))
                    }

                    val title = when (i) {
                        0 -> "Ruta Principal"
                        1 -> "Alternativa 1"
                        else -> "Alternativa 2"
                    }

                    routes.add(
                        RouteOption(
                            id = i,
                            title = title,
                            distanceMeters = distance,
                            timeMillis = (durationSeconds * 1000).toLong(),
                            points = latLongList,
                            isSelected = (i == 0)
                        )
                    )
                }
            }
        } finally {
            connection?.disconnect()
        }

        return routes
    }

    private fun fastHaversine(p1: LatLong, p2: LatLong): Double {
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val sinDLat = sin(dLat * 0.5)
        val sinDLon = sin(dLon * 0.5)
        val a = sinDLat * sinDLat + cos(lat1Rad) * cos(lat2Rad) * sinDLon * sinDLon

        return 6371000.0 * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }
}