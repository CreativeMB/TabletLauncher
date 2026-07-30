package com.creativem.toblauncher

data class CountryMap(
    val name: String,
    val code: String,
    val downloadUrl: String,
    val fileSize: String
) {
    val fileName: String
        get() = "${code.lowercase()}_map.mbtiles"
}

object MapRepository {
    val availableCountries = listOf(
        CountryMap(
            name = "Colombia",
            code = "CO",
            downloadUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/mapsforge/maps/v5/south-america/colombia.map",
            fileSize = "350 MB"
        ),
        CountryMap(
            name = "México",
            code = "MX",
            downloadUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/mapsforge/maps/v5/north-america/mexico.map",
            fileSize = "450 MB"
        ),
        CountryMap(
            name = "Argentina",
            code = "AR",
            downloadUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/mapsforge/maps/v5/south-america/argentina.map",
            fileSize = "500 MB"
        ),
        CountryMap(
            name = "Chile",
            code = "CL",
            downloadUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/mapsforge/maps/v5/south-america/chile.map",
            fileSize = "300 MB"
        ),
        CountryMap(
            name = "Perú",
            code = "PE",
            downloadUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/mapsforge/maps/v5/south-america/peru.map",
            fileSize = "320 MB"
        ),
        CountryMap(
            name = "Ecuador",
            code = "EC",
            downloadUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/mapsforge/maps/v5/south-america/ecuador.map",
            fileSize = "180 MB"
        ),
        CountryMap(
            name = "España",
            code = "ES",
            downloadUrl = "https://ftp.gwdg.de/pub/misc/openstreetmap/mapsforge/maps/v5/europe/spain.map",
            fileSize = "750 MB"
        )
    )

    fun searchCountries(query: String): List<CountryMap> {
        if (query.isBlank()) return emptyList()
        val normalizedQuery = query.trim().lowercase()
        return availableCountries.filter {
            it.name.lowercase().contains(normalizedQuery) ||
                    it.code.lowercase().contains(normalizedQuery)
        }
    }
}