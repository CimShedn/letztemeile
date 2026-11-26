package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ---- Komoot Photon Geocoder DTOs (OpenStreetMap, free, worldwide / DACH, no API key) ----

data class PhotonResponseDto(
    val features: List<PhotonFeatureDto>? = null
)

data class PhotonFeatureDto(
    val geometry: PhotonGeometryDto? = null,
    val properties: PhotonPropertiesDto? = null
)

data class PhotonGeometryDto(
    val coordinates: List<Double>? = null
)

data class PhotonPropertiesDto(
    val name: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val district: String? = null,
    val locality: String? = null,
    val city: String? = null,
    val county: String? = null,
    val state: String? = null,
    val postcode: String? = null,
    val country: String? = null
)

interface PhotonGeocodingApi {
    @GET("api/")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("lat") biasLat: Double = 48.137154,
        @Query("lon") biasLon: Double = 11.576124,
        @Query("limit") limit: Int = 8,
        @Query("lang") lang: String = "de"
    ): PhotonResponseDto

    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("limit") limit: Int = 1,
        @Query("lang") lang: String = "de"
    ): PhotonResponseDto
}

object NetworkModule {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "LetzteMeile-Android/0.4 (Scooter Transit)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    val photonApi: PhotonGeocodingApi = Retrofit.Builder()
        .baseUrl("https://photon.komoot.io/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(PhotonGeocodingApi::class.java)
}
