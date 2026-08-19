package com.creativem.toblauncher

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.mapsforge.core.model.LatLong
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import kotlin.math.*

data class SearchResultItem(
    val title: String,
    val description: String,
    val latLong: LatLong,
    val distanceMeters: Double? = null,
    val isOnline: Boolean = false
) {
    val formattedDistance: String
        get() {
            val dist = distanceMeters ?: return ""
            return if (dist >= 1000) {
                String.format(Locale.US, "%.1f km", dist / 1000.0)
            } else {
                "${dist.toInt()} m"
            }
        }
}

// =========================================================================
// NORMALIZADOR INTELIGENTE (ESPECIALIZADO EN COLOMBIA)
// =========================================================================
object AddressSmartParser {

    /**
     * Convierte el texto del usuario en comodines SQL.
     * Ejemplo: "cra" se convierte en buscar "c_rr_r_", "kr", "cr", "cra".
     */
    fun tokenizeAndWildcard(raw: String): List<List<String>> {
        val normalized = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

        val clean = normalized.replace(Regex("[,.#\\-]"), " ")
        val rawTokens = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }

        val sqlTokenGroups = mutableListOf<List<String>>()

        for (token in rawTokens) {
            // Evitar letras sueltas basura
            if (token.length == 1 && !token[0].isDigit() && token !in listOf("c", "k", "s", "n", "e", "o")) continue
            sqlTokenGroups.add(getColombianStreetWildcards(token))
        }
        return sqlTokenGroups
    }

    private fun getColombianStreetWildcards(token: String): List<String> {
        return when (token) {
            "calle", "cll", "cl", "c" -> listOf("c_ll_", "cl %", "cll %")
            "carrera", "cra", "kr", "cr", "k" -> listOf("c_rr_r_", "cra %", "kr %", "cr %")
            "diagonal", "dg", "diag" -> listOf("d__g_n_l", "dg %", "diag %")
            "transversal", "tv", "tr", "transv" -> listOf("tr_nsv_rs_l", "tv %", "tr %", "transv%")
            "avenida", "av", "ave" -> listOf("_v_n_d_", "av %")
            "autopista", "auto" -> listOf("__t_p_st_", "auto %")
            "sur", "s" -> listOf("s_r", " s")
            "norte", "n" -> listOf("n_rt_", " n")
            "bis" -> listOf("b_s")
            else -> {
                // Si es un número o palabra normal, reemplaza vocales con '_' para evadir tildes
                if (token.any { it.isDigit() }) listOf(token)
                else listOf(token.replace(Regex("[aeiou]"), "_"))
            }
        }
    }

    fun formatCapitalized(raw: String): String {
        return raw.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }
}

// =========================================================================
// MOTOR HÍBRIDO (ONLINE + OFFLINE MAPSFORGE NATIVO OPTIMIZADO)
// =========================================================================
object HybridSearchEngine {
    private const val TAG = "SEARCH_ENGINE"

    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun search(
        context: Context,
        rawQuery: String,
        currentLocation: LatLong?,
        poiFile: File
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val clean = rawQuery.trim()
        if (clean.length < 2) return@withContext emptyList()

        // 1. ONLINE: Es perfecto para direcciones exactas si hay red
        if (isOnline(context)) {
            try {
                val onlineResults = searchOnline(clean, currentLocation)
                if (onlineResults.isNotEmpty()) {
                    return@withContext onlineResults
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Online falló, pasando a búsqueda offline.")
            }
        }

        // 2. OFFLINE: Usando la técnica del "Embudo de Precisión"
        searchMapsforgeOffline(clean, currentLocation, poiFile)
    }

    private fun searchOnline(rawQuery: String, currentLocation: LatLong?): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()
        var urlStr = "https://photon.komoot.io/api/?q=${URLEncoder.encode(rawQuery, "UTF-8")}&limit=8"
        if (currentLocation != null) {
            urlStr += "&lat=${currentLocation.latitude}&lon=${currentLocation.longitude}"
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("User-Agent", "TobLauncher/15.0")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(json)
                val features = root.optJSONArray("features") ?: JSONArray()

                for (i in 0 until features.length()) {
                    val feat = features.optJSONObject(i) ?: continue
                    val geom = feat.optJSONObject("geometry") ?: continue
                    val coords = geom.optJSONArray("coordinates") ?: continue
                    val lon = coords.optDouble(0, Double.NaN)
                    val lat = coords.optDouble(1, Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) continue

                    val props = feat.optJSONObject("properties") ?: JSONObject()
                    val name = props.optString("name", "")
                    val street = props.optString("street", "")
                    val houseNum = props.optString("housenumber", "")
                    val city = props.optString("city").ifBlank { props.optString("district", props.optString("state", "")) }

                    val itemTitle = when {
                        name.isNotBlank() && street.isNotBlank() -> "$name ($street # $houseNum)".replace("# ", "").trim()
                        name.isNotBlank() -> name
                        street.isNotBlank() -> if (houseNum.isNotBlank()) "$street # $houseNum" else street
                        else -> AddressSmartParser.formatCapitalized(rawQuery)
                    }

                    val targetPos = LatLong(lat, lon)
                    val dist = if (currentLocation != null) calculateDistance(currentLocation, targetPos) else null

                    results.add(
                        SearchResultItem(
                            title = itemTitle,
                            description = if (city.isNotBlank()) "$city • 🌐 Online" else "Ubicación exacta • 🌐 Online",
                            latLong = targetPos,
                            distanceMeters = dist,
                            isOnline = true
                        )
                    )
                }
            }
        } finally {
            connection?.disconnect()
        }
        return results
    }

    // ---------------------------------------------------------------------
    // 📦 TÉCNICA DEL "EMBUDO" PARA BÚSQUEDA OFFLINE DE DIRECCIONES
    // ---------------------------------------------------------------------
    private fun searchMapsforgeOffline(
        rawQuery: String,
        currentLocation: LatLong?,
        poiFile: File
    ): List<SearchResultItem> {
        if (!poiFile.exists() || poiFile.length() == 0L) return emptyList()

        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(poiFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

            val tokenGroups = AddressSmartParser.tokenizeAndWildcard(rawQuery)
            if (tokenGroups.isEmpty()) return emptyList()

            // ⚡ Filtro Espacial: Busca solo en tu ciudad (~60 km a la redonda) para que sea instantáneo
            var spatialWhere = ""
            val spatialArgs = mutableListOf<String>()
            if (currentLocation != null) {
                val delta = 0.55
                spatialWhere = " AND (i.lat BETWEEN ? AND ?) AND (i.lon BETWEEN ? AND ?)"
                spatialArgs.add((currentLocation.latitude - delta).toString())
                spatialArgs.add((currentLocation.latitude + delta).toString())
                spatialArgs.add((currentLocation.longitude - delta).toString())
                spatialArgs.add((currentLocation.longitude + delta).toString())
            }

            fun executeQuery(groups: List<List<String>>, prefix: String = ""): List<SearchResultItem> {
                val whereClauses = mutableListOf<String>()
                val args = mutableListOf<String>()

                for (group in groups) {
                    val orClauses = mutableListOf<String>()
                    for (wildcard in group) {
                        orClauses.add("d.data LIKE ?")
                        args.add("%$wildcard%")
                    }
                    whereClauses.add("(" + orClauses.joinToString(" OR ") + ")")
                }

                val textWhereSql = whereClauses.joinToString(" AND ")
                val finalArgs = (args + spatialArgs).toTypedArray()

                val sql = """
                    SELECT d.data AS raw_data, i.lat AS lat, i.lon AS lon, c.name AS category_name
                    FROM poi_data d
                    JOIN poi_index i ON d.id = i.id
                    LEFT JOIN poi_category_map cm ON d.id = cm.id
                    LEFT JOIN poi_categories c ON cm.category = c.id
                    WHERE $textWhereSql $spatialWhere
                    LIMIT 25
                """.trimIndent()

                val queryList = mutableListOf<SearchResultItem>()
                val cursor = db.rawQuery(sql, finalArgs)

                cursor.use { c ->
                    val dataIdx = c.getColumnIndexOrThrow("raw_data")
                    val latIdx = c.getColumnIndexOrThrow("lat")
                    val lonIdx = c.getColumnIndexOrThrow("lon")
                    val catIdx = c.getColumnIndexOrThrow("category_name")

                    while (c.moveToNext()) {
                        val latRaw = c.getDouble(latIdx)
                        val lonRaw = c.getDouble(lonIdx)
                        if (latRaw == 0.0 && lonRaw == 0.0) continue

                        val lat = if (abs(latRaw) > 90.0) latRaw / 1_000_000.0 else latRaw
                        val lon = if (abs(lonRaw) > 180.0) lonRaw / 1_000_000.0 else lonRaw

                        val cleanTitle = extractReadablePoiName(c.getString(dataIdx) ?: "", rawQuery)
                        val targetPos = LatLong(lat, lon)
                        val dist = if (currentLocation != null) calculateDistance(currentLocation, targetPos) else null

                        queryList.add(
                            SearchResultItem(
                                title = "$prefix$cleanTitle",
                                description = (c.getString(catIdx) ?: "Punto de Interés") + " • 📦 Offline",
                                latLong = targetPos,
                                distanceMeters = dist,
                                isOnline = false
                            )
                        )
                    }
                }
                return queryList
            }

            // ============================================================
            // 🌪️ EL EMBUDO DE BÚSQUEDA (Cae en cascada si no encuentra)
            // ============================================================

            // NIVEL 1: Intenta buscar la dirección exacta completa
            val exactResults = executeQuery(tokenGroups, "")
            if (exactResults.isNotEmpty()) {
                return exactResults.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            }

            // NIVEL 2: Quita el último número (la casa) y busca el cruce/esquina
            if (tokenGroups.size >= 3) {
                val intersectionResults = executeQuery(tokenGroups.dropLast(1), "Cruce aprox: ")
                if (intersectionResults.isNotEmpty()) {
                    return intersectionResults.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
                }
            }

            // NIVEL 3: Quita la vía cruzada, busca solo sobre la calle principal
            if (tokenGroups.size >= 2) {
                val streetResults = executeQuery(tokenGroups.take(2), "Sobre la vía: ")
                if (streetResults.isNotEmpty()) {
                    return streetResults.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fatal en Búsqueda Offline: ${e.message}")
        } finally {
            db?.close()
        }
        return emptyList()
    }

    private fun extractReadablePoiName(rawData: String, originalQuery: String): String {
        val lines = rawData.split("\n", "\r", "\u0000", "\u0001", "\t")
        var extractedName = ""
        var extractedStreet = ""

        for (line in lines) {
            val trim = line.trim()
            if (trim.startsWith("name=", ignoreCase = true)) extractedName = trim.substring(5).trim()
            if (trim.startsWith("addr:street=", ignoreCase = true)) extractedStreet = trim.substring(12).trim()
        }

        if (extractedName.isNotBlank() && extractedStreet.isNotBlank()) return "$extractedName ($extractedStreet)"
        if (extractedName.isNotBlank()) return extractedName
        if (extractedStreet.isNotBlank()) return extractedStreet

        val fallback = lines.firstOrNull { it.isNotBlank() && !it.contains("=") } ?: rawData
        val cleaned = fallback.replace(Regex("^[a-zA-Z0-9_:-]+="), "").trim()
        return if (cleaned.isNotBlank()) cleaned else AddressSmartParser.formatCapitalized(originalQuery)
    }

    private fun calculateDistance(p1: LatLong, p2: LatLong): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}