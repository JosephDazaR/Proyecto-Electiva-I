package com.example.proyectoelectivai.data.network

import com.example.proyectoelectivai.data.model.OpenAQResponse
import com.example.proyectoelectivai.data.model.OpenTripMapResponse
import com.example.proyectoelectivai.data.model.OverpassResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Servicios API para obtener datos de diferentes fuentes
 */
interface ApiService {
    
    /**
     * OpenTripMap API - Lugares turísticos
     * @param apikey API key de OpenTripMap
     * @param lon Longitud
     * @param lat Latitud
     * @param radius Radio en metros (por defecto 1000)
     * @param limit Límite de resultados (por defecto 20)
     */
    @GET("0.1/en/places/radius")
    suspend fun getTouristPlaces(
        @Query("apikey") apikey: String,
        @Query("lon") lon: Double,
        @Query("lat") lat: Double,
        @Query("radius") radius: Int = 1000,
        @Query("limit") limit: Int = 20
    ): Response<OpenTripMapResponse>
    
    /**
     * OpenAQ API - Calidad del aire
     * @param lat Latitud
     * @param lon Longitud
     * @param radius Radio en metros (por defecto 1000)
     * @param limit Límite de resultados (por defecto 20)
     */
    @GET("v2/locations")
    suspend fun getAirQuality(
        @Query("coordinates") coordinates: String, // formato: "lat,lon"
        @Query("radius") radius: Int = 1000,
        @Query("limit") limit: Int = 20
    ): Response<OpenAQResponse>
    
    /**
     * Overpass API - Datos de OpenStreetMap
     * @param bbox Bounding box en formato "minLat,minLon,maxLat,maxLon"
     */
    @GET("interpreter")
    suspend fun getOSMData(
        @Query("data") query: String
    ): Response<OverpassResponse>
}
