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

data class ParsedAddress(
    val isGridAddress: Boolean,
    val formattedFullTitle: String,
    val mainVia: String = "",
    val crossVia: String = "",
    val houseNumber: String = "",
    val searchTokens: List<String> = emptyList()
)

// =========================================================================
// 1. NORMALIZADOR INTELIGENTE (COLOMBIA / LATINOAMÉRICA)
// =========================================================================
object AddressSmartParser {

    fun stripAccents(str: String): String {
        val normalized = Normalizer.normalize(str, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    fun parse(raw: String): ParsedAddress {
        var q = stripAccents(raw.trim().lowercase(Locale.ROOT))

        q = q.replace(Regex("[.,°ª_-]"), " ")
            .replace(Regex("\\b(nro|num|numero|no)\\b"), "#")
            .replace(Regex("\\s+"), " ")
            .trim()

        q = q.replace(Regex("\\b(cll|cl|c/|c)\\b"), "calle")
            .replace(Regex("\\b(cra|cr|kr|k|crta|ctra)\\b"), "carrera")
            .replace(Regex("\\b(diag|dg)\\b"), "diagonal")
            .replace(Regex("\\b(transv|tv|tr)\\b"), "transversal")
            .replace(Regex("\\b(av|ak|ac|aven)\\b"), "avenida")
            .replace(Regex("\\b(autonorte|auto norte)\\b"), "autopista norte")
            .replace(Regex("\\b(autosur|auto sur)\\b"), "autopista sur")
            .replace(Regex("\\b(circ|cq)\\b"), "circular")

        val numPattern = """(\d+\s*[a-z]?(?:\s*bis)?(?:\s*(?:sur|este|norte))?)"""

        // CASO 1: "calle 50a # 10b - 20"
        val hashRegex = Regex("^(calle|carrera|diagonal|transversal|avenida|autopista|circular)\\s+$numPattern\\s*#\\s*$numPattern(?:\\s*[- ]\\s*(\\d+))?", RegexOption.IGNORE_CASE)
        val hashMatch = hashRegex.find(q)

        if (hashMatch != null) {
            val via = hashMatch.groupValues[1].capitalizeWords()
            val num1 = hashMatch.groupValues[2].trim()
            val num2 = hashMatch.groupValues[3].trim()
            val num3 = hashMatch.groupValues[4].trim()

            val crossType = if (via.equals("calle", true)) "Carrera" else "Calle"
            val fullTitle = if (num3.isNotBlank()) "$via $num1 # $num2-$num3" else "$via $num1 # $num2"

            return ParsedAddress(
                isGridAddress = true,
                formattedFullTitle = fullTitle,
                mainVia = "$via $num1",
                crossVia = "$crossType $num2",
                houseNumber = num3,
                searchTokens = listOf(via, num1, num2).filter { it.isNotBlank() }
            )
        }

        // CASO 2: Cruce o dirección sin "#" (ej: "calle 50 10 20" o "carrera 7 72")
        val threeNumRegex = Regex("^(calle|carrera|diagonal|transversal|avenida)\\s+(\\d+[a-z]?)\\s+(\\d+[a-z]?)(?:\\s+(\\d+))?\$", RegexOption.IGNORE_CASE)
        val threeMatch = threeNumRegex.find(q)
        if (threeMatch != null) {
            val via = threeMatch.groupValues[1].capitalizeWords()
            val num1 = threeMatch.groupValues[2]
            val num2 = threeMatch.groupValues[3]
            val num3 = threeMatch.groupValues[4]
            val crossType = if (via.equals("calle", true)) "Carrera" else "Calle"
            val fullTitle = if (num3.isNotBlank()) "$via $num1 # $num2-$num3" else "$via $num1 con $crossType $num2"

            return ParsedAddress(
                isGridAddress = true,
                formattedFullTitle = fullTitle,
                mainVia = "$via $num1",
                crossVia = "$crossType $num2",
                houseNumber = num3,
                searchTokens = listOf(via, num1, num2).filter { it.isNotBlank() }
            )
        }

        // CASO 3: Nombres de lugares, negocios, barrios (ej: "Exito calle 80", "Hospital Kennedy")
        val tokens = q.split(" ").filter { it.length >= 2 }
        val cleanWords = tokens.joinToString(" ") { it.capitalizeWords() }

        return ParsedAddress(
            isGridAddress = false,
            formattedFullTitle = if (cleanWords.isNotBlank()) cleanWords else raw,
            searchTokens = tokens
        )
    }

    private fun String.capitalizeWords(): String {
        return this.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }
}

// =========================================================================
// 2. MOTOR HÍBRIDO (ONLINE + OFFLINE MAPSFORGE NATIVO)
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

        val parsed = AddressSmartParser.parse(clean)
        Log.d(TAG, "🔎 Consulta: '$rawQuery' -> Título: '${parsed.formattedFullTitle}'")

        // 1. INTENTO ONLINE
        if (isOnline(context)) {
            try {
                val onlineResults = searchOnline(parsed, clean, currentLocation)
                if (onlineResults.isNotEmpty()) {
                    return@withContext onlineResults
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Online falló o sin red, usando offline: ${e.message}")
            }
        }

        // 2. BÚSQUEDA OFFLINE MAPSFORGE POI
        searchMapsforgeOffline(parsed, clean, currentLocation, poiFile)
    }

    // ---------------------------------------------------------------------
    // 🌐 BÚSQUEDA ONLINE
    // ---------------------------------------------------------------------
    private fun searchOnline(
        parsed: ParsedAddress,
        rawQuery: String,
        currentLocation: LatLong?
    ): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()
        val queryStr = if (parsed.isGridAddress) parsed.formattedFullTitle else rawQuery

        var urlStr = "https://photon.komoot.io/api/?q=${URLEncoder.encode(queryStr, "UTF-8")}&limit=8"
        if (currentLocation != null) {
            urlStr += "&lat=${currentLocation.latitude}&lon=${currentLocation.longitude}"
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "CarTabletLauncher/15.0")
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
                        else -> parsed.formattedFullTitle
                    }

                    val targetPos = LatLong(lat, lon)
                    val dist = if (currentLocation != null) calculateDistance(currentLocation, targetPos) else null

                    results.add(
                        SearchResultItem(
                            title = itemTitle,
                            description = if (city.isNotBlank()) "$city • 🌐 Online" else "Dirección exacta • 🌐 Online",
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
    // 📦 BÚSQUEDA OFFLINE MAPSFORGE (poi_data + poi_index + poi_categories)
    // ---------------------------------------------------------------------
    private fun searchMapsforgeOffline(
        parsed: ParsedAddress,
        rawQuery: String,
        currentLocation: LatLong?,
        poiFile: File
    ): List<SearchResultItem> {
        if (!poiFile.exists() || poiFile.length() == 0L) {
            Log.e(TAG, "❌ Archivo POI no existe: ${poiFile.absolutePath}")
            return emptyList()
        }

        val results = mutableListOf<SearchResultItem>()
        var db: SQLiteDatabase? = null

        try {
            db = SQLiteDatabase.openDatabase(poiFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

            // Obtener los términos de búsqueda
            val tokens = if (parsed.searchTokens.isNotEmpty()) {
                parsed.searchTokens
            } else {
                AddressSmartParser.stripAccents(rawQuery).split(" ").filter { it.length >= 2 }
            }

            if (tokens.isEmpty()) return emptyList()

            // Armar WHERE dinámico sobre la columna "data" de poi_data
            val whereClauses = mutableListOf<String>()
            val args = mutableListOf<String>()

            for (token in tokens) {
                whereClauses.add("d.data LIKE ?")
                args.add("%$token%")
            }

            val whereSql = whereClauses.joinToString(" AND ")

            // JOIN exacto con el esquema de Mapsforge POI
            val sql = """
                SELECT 
                    d.data AS raw_data,
                    i.lat AS lat,
                    i.lon AS lon,
                    c.name AS category_name
                FROM poi_data d
                JOIN poi_index i ON d.id = i.id
                LEFT JOIN poi_category_map cm ON d.id = cm.id
                LEFT JOIN poi_categories c ON cm.category = c.id
                WHERE $whereSql
                LIMIT 40
            """.trimIndent()

            val cursor = db.rawQuery(sql, args.toTypedArray())
            cursor.use { c ->
                val dataIdx = c.getColumnIndexOrThrow("raw_data")
                val latIdx = c.getColumnIndexOrThrow("lat")
                val lonIdx = c.getColumnIndexOrThrow("lon")
                val catIdx = c.getColumnIndexOrThrow("category_name")

                while (c.moveToNext()) {
                    val rawData = c.getString(dataIdx) ?: continue
                    val latRaw = c.getDouble(latIdx)
                    val lonRaw = c.getDouble(lonIdx)

                    if (latRaw.isNaN() || lonRaw.isNaN() || (latRaw == 0.0 && lonRaw == 0.0)) continue

                    // Soporte para grados decimales o microgrados (lat/lon * 1E6)
                    val lat = if (abs(latRaw) > 90.0) latRaw / 1_000_000.0 else latRaw
                    val lon = if (abs(lonRaw) > 180.0) lonRaw / 1_000_000.0 else lonRaw

                    if (abs(lat) > 90.0 || abs(lon) > 180.0) continue

                    val cleanTitle = extractReadablePoiName(rawData, parsed)
                    val category = c.getString(catIdx) ?: "Punto de Interés"

                    val targetPos = LatLong(lat, lon)
                    val dist = if (currentLocation != null) calculateDistance(currentLocation, targetPos) else null

                    results.add(
                        SearchResultItem(
                            title = cleanTitle,
                            description = "$category • 📦 Offline",
                            latLong = targetPos,
                            distanceMeters = dist,
                            isOnline = false
                        )
                    )
                }
            }

            // Ordenar por distancia (si hay GPS)
            if (currentLocation != null) {
                results.sortBy { it.distanceMeters ?: Double.MAX_VALUE }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error leyendo Mapsforge SQLite: ${e.message}", e)
        } finally {
            db?.close()
        }

        return results.distinctBy { it.title + it.latLong.latitude.toString() }.take(15)
    }

    // ---------------------------------------------------------------------
    // 🛠️ DESEMPAQUETADOR DE ETIQUETAS DE MAPSFORGE POI (name=..., addr:street=...)
    // ---------------------------------------------------------------------
    private fun extractReadablePoiName(rawData: String, parsed: ParsedAddress): String {
        // En Mapsforge, "data" contiene pares de tags separados por saltos de línea o caracteres nulos
        val lines = rawData.split("\n", "\r", "\u0000", "\u0001", "\t")
        var extractedName = ""
        var extractedStreet = ""
        var extractedHousenumber = ""

        for (line in lines) {
            val trim = line.trim()
            when {
                trim.startsWith("name=", ignoreCase = true) -> extractedName = trim.substring(5).trim()
                trim.startsWith("name:es=", ignoreCase = true) -> extractedName = trim.substring(8).trim()
                trim.startsWith("addr:street=", ignoreCase = true) -> extractedStreet = trim.substring(12).trim()
                trim.startsWith("addr:housenumber=", ignoreCase = true) -> extractedHousenumber = trim.substring(17).trim()
            }
        }

        if (extractedName.isNotBlank()) {
            return if (extractedStreet.isNotBlank() && extractedHousenumber.isNotBlank()) {
                "$extractedName ($extractedStreet # $extractedHousenumber)"
            } else {
                extractedName
            }
        }

        if (extractedStreet.isNotBlank()) {
            return if (extractedHousenumber.isNotBlank()) "$extractedStreet # $extractedHousenumber" else extractedStreet
        }

        // Si no tiene prefijos "name=", tomamos el primer renglón limpio que no sea una clave técnica
        val fallback = lines.firstOrNull { it.isNotBlank() && !it.contains("=") }
            ?: lines.firstOrNull { it.isNotBlank() }
            ?: rawData

        val cleaned = fallback.replace(Regex("^[a-zA-Z0-9_:-]+="), "").trim()
        return if (cleaned.isNotBlank()) cleaned else parsed.formattedFullTitle
    }

    private fun calculateDistance(p1: LatLong, p2: LatLong): Double {
        val r = 6371000.0 // Radio tierra en metros
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}