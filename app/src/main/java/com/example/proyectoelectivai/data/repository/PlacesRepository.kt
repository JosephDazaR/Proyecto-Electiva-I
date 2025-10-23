package com.example.proyectoelectivai.data.repository

import android.content.Context
import com.example.proyectoelectivai.data.local.AppDatabase
import com.example.proyectoelectivai.data.local.PlaceDao
import com.example.proyectoelectivai.data.model.*
import com.example.proyectoelectivai.data.network.ApiService
import com.example.proyectoelectivai.data.network.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.*

/**
 * Repositorio principal que maneja el cacheo inteligente
 * Si hay red: solicita datos a la API, guarda en Room, devuelve resultado
 * Si no hay red: devuelve datos almacenados en Room
 */
class PlacesRepository(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val placeDao: PlaceDao = database.placeDao()
    
    // Servicios API
    private val openTripMapService = NetworkModule.createApiService(context)
    private val openAQService = NetworkModule.createOpenAQService(context)
    private val overpassService = NetworkModule.createOverpassService(context)
    
    private val isNetworkAvailable: Boolean
        get() = NetworkModule.isNetworkAvailable(context)
    
    /**
     * Obtiene todos los lugares con cacheo inteligente
     */
    suspend fun getAllPlaces(): Flow<List<Place>> {
        return if (isNetworkAvailable) {
            // Si hay red, actualizar datos y devolver
            refreshAllData()
            placeDao.getAllPlaces()
        } else {
            // Si no hay red, devolver datos cacheados
            placeDao.getAllPlaces()
        }
    }
    
    /**
     * Obtiene lugares por tipo con cacheo inteligente
     */
    suspend fun getPlacesByType(type: String): Flow<List<Place>> {
        return if (isNetworkAvailable) {
            refreshDataByType(type)
            placeDao.getPlacesByType(type)
        } else {
            placeDao.getPlacesByType(type)
        }
    }
    
    /**
     * Busca lugares con cacheo inteligente
     */
    suspend fun searchPlaces(query: String): Flow<List<Place>> {
        return placeDao.searchPlaces(query)
    }
    
    /**
     * Obtiene lugares favoritos
     */
    fun getFavoritePlaces(): Flow<List<Place>> {
        return placeDao.getFavoritePlaces()
    }
    
    /**
     * Obtiene lugares en un área específica
     */
    suspend fun getPlacesInBounds(
        minLat: Double, maxLat: Double, 
        minLon: Double, maxLon: Double
    ): Flow<List<Place>> {
        return if (isNetworkAvailable) {
            refreshDataInBounds(minLat, maxLat, minLon, maxLon)
            placeDao.getPlacesInBounds(minLat, maxLat, minLon, maxLon)
        } else {
            placeDao.getPlacesInBounds(minLat, maxLat, minLon, maxLon)
        }
    }
    
    /**
     * Actualiza el estado de favorito de un lugar
     */
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean) {
        placeDao.updateFavoriteStatus(id, isFavorite)
    }
    
    /**
     * Refresca todos los datos desde las APIs
     */
    private suspend fun refreshAllData() {
        try {
            val currentTime = System.currentTimeMillis()
            val places = mutableListOf<Place>()
            
            // Obtener datos de OpenTripMap (lugares turísticos)
            places.addAll(fetchTouristPlaces())
            
            // Obtener datos de OpenAQ (calidad del aire)
            places.addAll(fetchAirQualityData())
            
            // Obtener datos de OSM (parques, restaurantes, etc.)
            places.addAll(fetchOSMData())
            
            // Guardar en base de datos
            if (places.isNotEmpty()) {
                placeDao.insertPlaces(places)
            }
            
        } catch (e: Exception) {
            // En caso de error, usar datos cacheados
            println("Error refreshing data: ${e.message}")
        }
    }
    
    /**
     * Refresca datos por tipo específico
     */
    private suspend fun refreshDataByType(type: String) {
        try {
            val places = when (type) {
                "tourist" -> fetchTouristPlaces()
                "air_quality" -> fetchAirQualityData()
                "park", "restaurant" -> fetchOSMData()
                else -> emptyList()
            }
            
            if (places.isNotEmpty()) {
                placeDao.insertPlaces(places)
            }
        } catch (e: Exception) {
            println("Error refreshing data for type $type: ${e.message}")
        }
    }
    
    /**
     * Refresca datos en un área específica
     */
    private suspend fun refreshDataInBounds(
        minLat: Double, maxLat: Double, 
        minLon: Double, maxLon: Double
    ) {
        try {
            val centerLat = (minLat + maxLat) / 2
            val centerLon = (minLon + maxLon) / 2
            val radius = calculateRadius(minLat, maxLat, minLon, maxLon)
            
            val places = mutableListOf<Place>()
            places.addAll(fetchTouristPlaces(centerLat, centerLon, radius))
            places.addAll(fetchAirQualityData(centerLat, centerLon, radius))
            places.addAll(fetchOSMDataInBounds(minLat, maxLat, minLon, maxLon))
            
            if (places.isNotEmpty()) {
                placeDao.insertPlaces(places)
            }
        } catch (e: Exception) {
            println("Error refreshing data in bounds: ${e.message}")
        }
    }
    
    /**
     * Obtiene lugares turísticos de OpenTripMap
     */
    private suspend fun fetchTouristPlaces(
        lat: Double = 4.7110, 
        lon: Double = -74.0721, 
        radius: Int = 1000
    ): List<Place> {
        return try {
            val response = openTripMapService.getTouristPlaces(
                apikey = "TU_API_KEY_AQUI", // TODO: Configurar desde variables de entorno
                lon = lon,
                lat = lat,
                radius = radius,
                limit = 20
            )
            
            if (response.isSuccessful) {
                response.body()?.features?.mapNotNull { feature ->
                    val coords = feature.geometry.coordinates
                    if (coords.size >= 2) {
                        Place(
                            id = "tourist_${feature.properties.xid}",
                            name = feature.properties.name,
                            type = "tourist",
                            lat = coords[1], // lat
                            lon = coords[0], // lon
                            description = feature.properties.kinds,
                            rating = feature.properties.rate?.toDoubleOrNull(),
                            source = "opentripmap"
                        )
                    } else null
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("Error fetching tourist places: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Obtiene datos de calidad del aire de OpenAQ
     */
    private suspend fun fetchAirQualityData(
        lat: Double = 4.7110, 
        lon: Double = -74.0721, 
        radius: Int = 1000
    ): List<Place> {
        return try {
            val response = openAQService.getAirQuality(
                coordinates = "$lat,$lon",
                radius = radius,
                limit = 20
            )
            
            if (response.isSuccessful) {
                response.body()?.results?.mapNotNull { result ->
                    val pm25Measurement = result.measurements.find { it.parameter == "pm25" }
                    val pm10Measurement = result.measurements.find { it.parameter == "pm10" }
                    
                    if (pm25Measurement != null || pm10Measurement != null) {
                        val aqi = pm25Measurement?.value?.toInt() ?: pm10Measurement?.value?.toInt() ?: 0
                        Place(
                            id = "air_${result.location}",
                            name = result.location,
                            type = "air_quality",
                            lat = result.coordinates.latitude,
                            lon = result.coordinates.longitude,
                            description = "Air quality monitoring station",
                            address = "${result.city}, ${result.country}",
                            airQualityIndex = aqi,
                            airQualityLevel = getAirQualityLevel(aqi),
                            source = "openaq"
                        )
                    } else null
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("Error fetching air quality data: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Obtiene datos de OpenStreetMap
     */
    private suspend fun fetchOSMData(
        lat: Double = 4.7110, 
        lon: Double = -74.0721, 
        radius: Int = 1000
    ): List<Place> {
        return try {
            val bbox = calculateBoundingBox(lat, lon, radius)
            fetchOSMDataInBounds(bbox.minLat, bbox.maxLat, bbox.minLon, bbox.maxLon)
        } catch (e: Exception) {
            println("Error fetching OSM data: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Obtiene datos de OSM en un área específica
     */
    private suspend fun fetchOSMDataInBounds(
        minLat: Double, maxLat: Double, 
        minLon: Double, maxLon: Double
    ): List<Place> {
        return try {
            val query = """
                [out:json][timeout:25];
                (
                  node["leisure"="park"]($minLat,$minLon,$maxLat,$maxLon);
                  node["amenity"="restaurant"]($minLat,$minLon,$maxLat,$maxLon);
                  node["amenity"="cafe"]($minLat,$minLon,$maxLat,$maxLon);
                );
                out geom;
            """.trimIndent()
            
            val response = overpassService.getOSMData(query)
            
            if (response.isSuccessful) {
                response.body()?.elements?.mapNotNull { element ->
                    val tags = element.tags ?: return@mapNotNull null
                    val type = when {
                        tags["leisure"] == "park" -> "park"
                        tags["amenity"] == "restaurant" -> "restaurant"
                        tags["amenity"] == "cafe" -> "cafe"
                        else -> "other"
                    }
                    
                    Place(
                        id = "osm_${element.type}_${element.id}",
                        name = tags["name"] ?: "Unnamed $type",
                        type = type,
                        lat = element.lat,
                        lon = element.lon,
                        description = tags["description"] ?: tags["cuisine"],
                        address = tags["addr:street"],
                        phone = tags["phone"],
                        website = tags["website"],
                        openingHours = tags["opening_hours"],
                        source = "osm"
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("Error fetching OSM data in bounds: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Calcula el nivel de calidad del aire basado en el índice
     */
    private fun getAirQualityLevel(aqi: Int): String {
        return when {
            aqi <= 50 -> "good"
            aqi <= 100 -> "moderate"
            aqi <= 150 -> "unhealthy_sensitive"
            aqi <= 200 -> "unhealthy"
            aqi <= 300 -> "very_unhealthy"
            else -> "hazardous"
        }
    }
    
    /**
     * Calcula el radio basado en las coordenadas
     */
    private fun calculateRadius(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): Int {
        val latDiff = maxLat - minLat
        val lonDiff = maxLon - minLon
        val avgLat = (minLat + maxLat) / 2
        
        // Conversión aproximada de grados a metros
        val latMeters = latDiff * 111000
        val lonMeters = lonDiff * 111000 * kotlin.math.cos(Math.toRadians(avgLat))
        
        return kotlin.math.max(latMeters, lonMeters).toInt()
    }
    
    /**
     * Calcula el bounding box para las coordenadas
     */
    private fun calculateBoundingBox(lat: Double, lon: Double, radius: Int): BoundingBox {
        val latOffset = radius / 111000.0
        val lonOffset = radius / (111000.0 * kotlin.math.cos(Math.toRadians(lat)))
        
        return BoundingBox(
            minLat = lat - latOffset,
            maxLat = lat + latOffset,
            minLon = lon - lonOffset,
            maxLon = lon + lonOffset
        )
    }
    
    private data class BoundingBox(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )
}
