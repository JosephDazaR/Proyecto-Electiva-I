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
        @Query("limit") limit: Int = 10,
        @Query("countrycodes") countryCodes: String = "co",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("extratags") extraTags: Int = 1,
        @Query("namedetails") nameDetails: Int = 1
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
    val address: AddressDetails?,
    val extratags: ExtraTags?,
    val namedetails: NameDetails?
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

/**
 * Tags adicionales del lugar
 */
data class ExtraTags(
    val amenity: String?,
    val shop: String?,
    val tourism: String?,
    val leisure: String?,
    val historic: String?,
    val cuisine: String?,
    val opening_hours: String?,
    val phone: String?,
    val website: String?
)

/**
 * Detalles de nombres
 */
data class NameDetails(
    val name: String?,
    val name_en: String?,
    val name_es: String?,
    val alt_name: String?
)
