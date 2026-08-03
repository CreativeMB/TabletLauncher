package com.creativem.toblauncher

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// DTO de la API Radio Browser
data class ApiRadioStation(
    @SerializedName("stationuuid") val stationUuid: String,
    @SerializedName("name") val name: String,
    @SerializedName("url_resolved") val urlResolved: String,
    @SerializedName("url") val url: String,
    @SerializedName("country") val country: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("tags") val tags: String?
) {
    fun toRadioStation(): RadioStation {
        val location = listOfNotNull(state, country)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifEmpty { "En Español" }

        val mainTag = tags?.split(",")?.firstOrNull()?.trim()?.capitalize() ?: "General"

        return RadioStation(
            id = stationUuid,
            name = name.trim().ifEmpty { "Emisora sin nombre" },
            freqLabel = "ONLINE",
            city = location,
            streamUrl = if (urlResolved.isNotBlank()) urlResolved else url,
            genre = mainTag
        )
    }
}

// Interfaz Retrofit
interface RadioBrowserApi {
    // 🛑 Recibe el país como parámetro dinámico (Colombia por defecto)
    @GET("json/stations/bycountry/{country}")
    suspend fun getStationsByCountry(
        @Path("country") country: String,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true
    ): List<ApiRadioStation>
}
// Cliente Retrofit Singleton
object RetrofitClient {
    private const val BASE_URL = "https://de1.api.radio-browser.info/"

    val api: RadioBrowserApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "SmartRadioLauncher/1.0") // Requerido por la API
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
            .create(RadioBrowserApi::class.java)
    }
}