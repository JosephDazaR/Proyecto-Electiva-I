package com.example.proyectoelectivai.data.network

import com.example.proyectoelectivai.data.model.OverpassResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Servicio API simplificado para Overpass (OpenStreetMap)
 * Solo obtiene lugares turísticos con estructura fija de JSON
 */
interface OverpassApiService {
    
    /**
     * Overpass API - Obtiene lugares turísticos en un área específica
     * @param query Consulta Overpass QL
     */
    @GET("interpreter")
    suspend fun getTouristPlaces(
        @Query("data") query: String
    ): Response<OverpassResponse>
}
