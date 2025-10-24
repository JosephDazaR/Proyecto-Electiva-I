package com.example.proyectoelectivai.data.network

import com.example.proyectoelectivai.data.model.OpenAQResponse
import com.example.proyectoelectivai.data.model.OverpassResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Servicios API para obtener datos de diferentes fuentes
 */
interface ApiService {
    
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
     * Overpass API - Datos de OpenStreetMap (lugares turísticos, restaurantes, parques)
     * @param query Consulta Overpass QL
     */
    @GET("interpreter")
    suspend fun getOSMData(
        @Query("data") query: String
    ): Response<OverpassResponse>
}
