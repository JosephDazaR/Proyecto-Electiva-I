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
    private val openAQService = NetworkModule.createOpenAQService(context)
    private val overpassService = NetworkModule.createOverpassService(context)
    private val geocodingService = NetworkModule.createGeocodingService(context)
    
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
     * Busca direcciones usando geocoding
     */
    suspend fun searchAddress(query: String): List<Place> {
        return try {
            if (isNetworkAvailable) {
                val response = geocodingService.searchAddress(query)
                if (response.isSuccessful) {
                    response.body()?.mapNotNull { result ->
                        Place(
                            id = "geocoding_${result.place_id}",
                            name = result.display_name,
                            type = "address",
                            lat = result.lat.toDouble(),
                            lon = result.lon.toDouble(),
                            description = "Dirección encontrada",
                            address = result.display_name,
                            source = "geocoding"
                        )
                    } ?: emptyList()
                } else {
                    emptyList()
                }
            } else {
                // Si no hay red, buscar en lugares existentes
                placeDao.searchPlaces(query).first()
            }
        } catch (e: Exception) {
            println("Error searching address: ${e.message}")
            emptyList()
        }
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
            
            // Obtener datos de OpenAQ (calidad del aire)
            places.addAll(fetchAirQualityData())
            
            // Obtener datos de OSM (lugares turísticos, parques, restaurantes, etc.)
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
                "air_quality" -> fetchAirQualityData()
                "tourist", "park", "restaurant", "cafe" -> fetchOSMData()
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
     * Obtiene datos de OpenStreetMap (lugares turísticos, restaurantes, parques, etc.)
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
                  node["tourism"~"^(museum|attraction|monument|gallery|zoo|theme_park)$"]($minLat,$minLon,$maxLat,$maxLon);
                  node["leisure"="park"]($minLat,$minLon,$maxLat,$maxLon);
                  node["amenity"="restaurant"]($minLat,$minLon,$maxLat,$maxLon);
                  node["amenity"="cafe"]($minLat,$minLon,$maxLat,$maxLon);
                  node["amenity"="bar"]($minLat,$minLon,$maxLat,$maxLon);
                  node["amenity"="fast_food"]($minLat,$minLon,$maxLat,$maxLon);
                );
                out geom;
            """.trimIndent()
            
            val response = overpassService.getOSMData(query)
            
            if (response.isSuccessful) {
                response.body()?.elements?.mapNotNull { element ->
                    val tags = element.tags ?: return@mapNotNull null
                    val type = when {
                        tags["tourism"] in listOf("museum", "attraction", "monument", "gallery", "zoo", "theme_park") -> "tourist"
                        tags["leisure"] == "park" -> "park"
                        tags["amenity"] == "restaurant" -> "restaurant"
                        tags["amenity"] == "cafe" -> "cafe"
                        tags["amenity"] == "bar" -> "bar"
                        tags["amenity"] == "fast_food" -> "fast_food"
                        else -> "other"
                    }
                    
                    Place(
                        id = "osm_${element.type}_${element.id}",
                        name = tags["name"] ?: "Unnamed $type",
                        type = type,
                        lat = element.lat,
                        lon = element.lon,
                        description = tags["description"] ?: tags["cuisine"] ?: tags["tourism"],
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
    
    /**
     * Carga datos de ejemplo para Bogotá
     */
    suspend fun loadSampleData() {
        try {
            val samplePlaces = listOf(
                Place(
                    id = "sample_1",
                    name = "Museo del Oro",
                    type = "tourist",
                    lat = 4.6019,
                    lon = -74.0719,
                    description = "Museo de arte precolombino con colección de oro",
                    address = "Calle 16 #5-41, Bogotá",
                    phone = "+57 1 343 2222",
                    website = "https://www.banrepcultural.org/museo-del-oro",
                    source = "sample"
                ),
                Place(
                    id = "sample_2",
                    name = "Parque Simón Bolívar",
                    type = "park",
                    lat = 4.6500,
                    lon = -74.0833,
                    description = "Parque urbano más grande de Bogotá",
                    address = "Calle 63 #68-95, Bogotá",
                    source = "sample"
                ),
                Place(
                    id = "sample_3",
                    name = "Andrés DC",
                    type = "restaurant",
                    lat = 4.6561,
                    lon = -74.0597,
                    description = "Restaurante tradicional colombiano",
                    address = "Calle 82 #12-21, Bogotá",
                    phone = "+57 1 616 8888",
                    source = "sample"
                ),
                Place(
                    id = "sample_4",
                    name = "Café San Alberto",
                    type = "cafe",
                    lat = 4.6097,
                    lon = -74.0817,
                    description = "Café de especialidad colombiano",
                    address = "Calle 93 #13-49, Bogotá",
                    phone = "+57 1 616 1616",
                    source = "sample"
                ),
                Place(
                    id = "sample_5",
                    name = "Catedral Primada",
                    type = "tourist",
                    lat = 4.5981,
                    lon = -74.0758,
                    description = "Catedral principal de Bogotá",
                    address = "Carrera 7 #10-80, Bogotá",
                    source = "sample"
                ),
                Place(
                    id = "sample_6",
                    name = "Estación de Monitoreo Aire Centro",
                    type = "air_quality",
                    lat = 4.6097,
                    lon = -74.0817,
                    description = "Estación de monitoreo de calidad del aire",
                    address = "Centro de Bogotá",
                    airQualityIndex = 45,
                    airQualityLevel = "good",
                    source = "sample"
                )
            )
            
            // Solo insertar si no hay datos
            val count = placeDao.getPlacesCount()
            if (count == 0) {
                placeDao.insertPlaces(samplePlaces)
                println("Datos de ejemplo cargados: ${samplePlaces.size} lugares")
            }
        } catch (e: Exception) {
            println("Error cargando datos de ejemplo: ${e.message}")
        }
    }
    
    private data class BoundingBox(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )
}
