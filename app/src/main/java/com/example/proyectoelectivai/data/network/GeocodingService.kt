package com.example.proyectoelectivai.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Servicio de geocoding usando Nominatim (OpenStreetMap)
 * Permite buscar direcciones y obtener coordenadas
 */
interface GeocodingService {
    
    @GET("search")
    suspend fun searchAddress(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 5,
        @Query("countrycodes") countryCodes: String = "co",
        @Query("addressdetails") addressDetails: Int = 1
    ): Response<List<GeocodingResult>>
    
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1
    ): Response<GeocodingResult>
}

/**
 * Resultado de geocoding
 */
data class GeocodingResult(
    val place_id: Long,
    val display_name: String,
    val lat: String,
    val lon: String,
    val type: String?,
    val importance: Double?,
    val address: AddressDetails?
)

/**
 * Detalles de dirección
 */
data class AddressDetails(
    val house_number: String?,
    val road: String?,
    val neighbourhood: String?,
    val suburb: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val postcode: String?
)
