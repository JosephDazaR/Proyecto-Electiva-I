package com.example.proyectoelectivai.data.repository

import android.content.Context
import android.util.Log
import com.example.proyectoelectivai.data.cache.BoundingBox
import com.example.proyectoelectivai.data.cache.OfflineAreaManager
import com.example.proyectoelectivai.data.cache.ViewportCache
import com.example.proyectoelectivai.data.local.AppDatabase
import com.example.proyectoelectivai.data.local.PlaceDao
import com.example.proyectoelectivai.data.model.*
import com.example.proyectoelectivai.data.network.NetworkModule
import com.example.proyectoelectivai.data.network.OverpassApiService
import com.example.proyectoelectivai.data.network.OverpassQueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repositorio simplificado que solo usa Overpass API
 * Implementa caching inteligente basado en viewport
 */
class PlacesRepository(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val placeDao: PlaceDao = database.placeDao()
    
    // Servicio API
    private val overpassService: OverpassApiService = NetworkModule.createOverpassService(context)
    
    // Gestores de caché
    private val viewportCache = ViewportCache(context)
    private val offlineAreaManager = OfflineAreaManager(context)
    
    private val isNetworkAvailable: Boolean
        get() = NetworkModule.isNetworkAvailable(context)
    
    /**
     * Verifica si hay conexión a internet
     */
    fun isOnline(): Boolean = isNetworkAvailable
    
    /**
     * Configura el centro del área offline (ciudad del usuario)
     */
    fun setOfflineCenter(lat: Double, lon: Double, radiusMeters: Double = OfflineAreaManager.RADIUS_MEDIUM_CITY) {
        offlineAreaManager.setOfflineCenter(lat, lon)
        offlineAreaManager.setMaxRadius(radiusMeters)
        Log.d(TAG, "Área offline configurada: $lat, $lon con radio ${radiusMeters}m")
    }
    
    /**
     * Obtiene lugares turísticos en un viewport específico
     * - Si el área ya está en caché, devuelve datos de Room
     * - Si no está en caché y hay red, descarga y guarda en Room
     * - Si no hay red, devuelve lo que haya en Room para esa área
     */
    suspend fun getPlacesInViewport(boundingBox: BoundingBox): Flow<List<Place>> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Obteniendo lugares en viewport: $boundingBox")
        
        // Verificar si el área ya está en caché
        val isCached = viewportCache.isAreaCached(
            boundingBox.minLat, boundingBox.maxLat,
            boundingBox.minLon, boundingBox.maxLon
        )
        
        if (!isCached && isNetworkAvailable) {
            // Área no cacheada y hay red: descargar datos
            Log.d(TAG, "Área no cacheada, descargando desde Overpass...")
            
            // Verificar si el área está dentro del límite offline
            val clippedBox = if (offlineAreaManager.isConfigured()) {
                offlineAreaManager.clipToOfflineArea(boundingBox)
            } else {
                boundingBox
            }
            
            if (clippedBox != null) {
                downloadAndCachePlaces(clippedBox)
            } else {
                Log.d(TAG, "Área fuera del límite offline, usando solo datos locales")
            }
        } else if (isCached) {
            Log.d(TAG, "Área ya en caché, usando datos locales")
        } else {
            Log.d(TAG, "Sin red, usando datos locales disponibles")
        }
        
        // Siempre devolver datos de Room
        placeDao.getPlacesInBounds(
            boundingBox.minLat, boundingBox.maxLat,
            boundingBox.minLon, boundingBox.maxLon
        )
    }
    
    /**
     * Descarga y cachea lugares turísticos para un área
     */
    private suspend fun downloadAndCachePlaces(boundingBox: BoundingBox) {
        try {
            // Construir query optimizada
            val query = OverpassQueryBuilder.buildOptimalQuery(boundingBox)
            Log.d(TAG, "Query Overpass: $query")
            
            // Llamar a la API
            val response = overpassService.getTouristPlaces(query)
                
                if (response.isSuccessful) {
                val elements = response.body()?.elements ?: emptyList()
                Log.d(TAG, "Overpass devolvió ${elements.size} elementos")
                
                // Convertir elementos a Places
                val places = elements.mapNotNull { element ->
                    convertOverpassElementToPlace(element)
                }
                
                Log.d(TAG, "Convertidos a ${places.size} lugares")
                
                // Guardar en Room
                if (places.isNotEmpty()) {
                    placeDao.insertPlaces(places)
                    Log.d(TAG, "Guardados ${places.size} lugares en Room")
                }
                
                // Marcar área como cacheada
                viewportCache.markAreaAsCached(
                    boundingBox.minLat, boundingBox.maxLat,
                    boundingBox.minLon, boundingBox.maxLon
                )
                
                Log.d(TAG, "Área marcada como cacheada")
            } else {
                Log.e(TAG, "Error en Overpass: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando lugares", e)
        }
    }
    
    /**
     * Convierte un elemento de Overpass a Place
     */
    private fun convertOverpassElementToPlace(element: OverpassElement): Place? {
        val tags = element.tags ?: return null
        
        // Obtener nombre (requerido)
        val name = tags["name"] ?: tags["name:es"] ?: tags["name:en"] ?: return null
        
        // Obtener coordenadas
        val lat = element.lat
        val lon = element.lon
        
        // Determinar tipo de lugar (ahora incluye ciudades)
        val placeType = determinePlaceTypeExtended(tags)
        val description = buildDescriptionExtended(tags, placeType)
        
        return Place(
            id = "osm_${element.type}_${element.id}",
            name = name,
            type = placeType,
            lat = lat,
            lon = lon,
            description = description,
            address = buildAddress(tags),
            phone = tags["phone"] ?: tags["contact:phone"],
            website = tags["website"] ?: tags["contact:website"],
            openingHours = tags["opening_hours"],
            source = "overpass",
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    /**
     * Determina el tipo de lugar extendido (incluye ciudades)
     */
    private fun determinePlaceTypeExtended(tags: Map<String, String>): String {
        // Primero intentar tipos turísticos conocidos
        val touristType = TouristPlaceType.fromOsmTag(tags)
        if (touristType != null) {
            return touristType.value
        }
        
        // Lugares (ciudades, pueblos, etc.)
        tags["place"]?.let { place ->
            return when (place) {
                "city" -> "city"
                "town" -> "town"
                "village" -> "village"
                "hamlet" -> "hamlet"
                else -> place
            }
        }
        
        // Amenities
        tags["amenity"]?.let { return it }
        
        // Shops
        tags["shop"]?.let { return "shop" }
        
        // Por defecto
        return "place"
    }
    
    /**
     * Construye descripción extendida
     */
    private fun buildDescriptionExtended(tags: Map<String, String>, placeType: String): String {
        val parts = mutableListOf<String>()
        
        // Tipo de lugar traducido
        val typeDisplay = when (placeType) {
            "city" -> "Ciudad"
            "town" -> "Pueblo"
            "village" -> "Villa"
            "hamlet" -> "Aldea"
            "museum" -> "Museo"
            "monument" -> "Monumento"
            "attraction" -> "Atracción"
            "park" -> "Parque"
            "viewpoint" -> "Mirador"
            "gallery" -> "Galería"
            "zoo" -> "Zoológico"
            "theme_park" -> "Parque Temático"
            "statue" -> "Estatua"
            "castle" -> "Castillo"
            "ruins" -> "Ruinas"
            "artwork" -> "Obra de Arte"
            else -> placeType.replaceFirstChar { it.uppercase() }
        }
        parts.add(typeDisplay)
        
        // Descripción específica
        tags["description"]?.let { parts.add(it) }
        tags["description:es"]?.let { parts.add(it) }
        
        // Población para ciudades
        tags["population"]?.let { parts.add("Población: $it") }
        
        // Información adicional
        tags["heritage"]?.let { parts.add("Patrimonio: $it") }
        tags["artist"]?.let { parts.add("Artista: $it") }
        tags["architect"]?.let { parts.add("Arquitecto: $it") }
        tags["start_date"]?.let { parts.add("Año: $it") }
        tags["ele"]?.let { parts.add("Altitud: $it m") }
        
        return parts.joinToString(" • ")
    }
    
    /**
     * Construye dirección completa
     */
    private fun buildAddress(tags: Map<String, String>): String? {
        val addressParts = mutableListOf<String>()
        
        tags["addr:street"]?.let { addressParts.add(it) }
        tags["addr:housenumber"]?.let { 
            if (addressParts.isNotEmpty()) {
                addressParts[0] = "${addressParts[0]} $it"
            } else {
                addressParts.add(it)
            }
        }
        tags["addr:city"]?.let { addressParts.add(it) }
        tags["addr:state"]?.let { addressParts.add(it) }
        tags["addr:country"]?.let { addressParts.add(it) }
        
        return if (addressParts.isNotEmpty()) {
            addressParts.joinToString(", ")
        } else {
            tags["addr:full"]
        }
    }
    
    
    /**
     * Obtiene lugares por tipo
     */
    fun getPlacesByType(type: String): Flow<List<Place>> {
        return placeDao.getPlacesByType(type)
    }
    
    /**
     * Busca lugares en la base de datos local
     */
    fun searchPlaces(query: String): Flow<List<Place>> {
        return placeDao.searchPlaces(query)
    }
    
    /**
     * Búsqueda global incremental (estilo YouTube)
     * Primero busca cerca, luego amplía el radio progresivamente
     */
    suspend fun searchPlacesGlobal(
        query: String,
        centerLat: Double,
        centerLon: Double
    ): List<Place> = withContext(Dispatchers.IO) {
        if (query.length < 3) {
            return@withContext emptyList()
        }
        
        val allResults = mutableListOf<Place>()
        
        // Si no hay red, solo retornar resultados ya cacheados en DB local
        if (!isNetworkAvailable) {
            Log.d(TAG, "Modo offline: buscando '$query' solo en base de datos local")
            // Nota: los resultados ya están en la búsqueda local del ViewModel
            return@withContext emptyList()
        }
        
        Log.d(TAG, "Búsqueda global: '$query' desde ($centerLat, $centerLon)")
        
        // Radios incrementales: 5km, 20km, 50km, 100km, 500km, global
        val radii = listOf(5000, 20000, 50000, 100000, 500000)
        
        for ((index, radius) in radii.withIndex()) {
            try {
                Log.d(TAG, "Buscando con radio ${radius}m...")
                
                val queryStr = OverpassQueryBuilder.buildIncrementalSearchQuery(
                    searchName = query,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    radiusMeters = radius,
                limit = 20
            )
                
                val response = overpassService.getTouristPlaces(queryStr)
            
            if (response.isSuccessful) {
                    val elements = response.body()?.elements ?: emptyList()
                    Log.d(TAG, "Radio ${radius}m: ${elements.size} elementos encontrados")
                    
                    val places = elements.mapNotNull { convertOverpassElementToPlace(it) }
                    
                    // Añadir solo lugares únicos (evitar duplicados)
                    places.forEach { place ->
                        if (allResults.none { it.id == place.id }) {
                            allResults.add(place)
                        }
                    }
                    
                    // Si encontramos suficientes resultados, detener
                    if (allResults.size >= 20) {
                        Log.d(TAG, "Suficientes resultados encontrados (${allResults.size})")
                        break
                    }
                }
                
                // Si no encontramos nada en los primeros 2 intentos, intentar búsqueda global
                if (allResults.isEmpty() && index == 1) {
                    Log.d(TAG, "Sin resultados locales, intentando búsqueda global...")
                    val globalResults = searchGlobalByName(query)
                    allResults.addAll(globalResults)
                    break
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error en búsqueda con radio $radius: ${e.message}")
            }
        }
        
        // Guardar en cache si hay resultados
        if (allResults.isNotEmpty()) {
            placeDao.insertPlaces(allResults)
            Log.d(TAG, "Guardados ${allResults.size} lugares en cache")
        }
        
        allResults
    }
    
    /**
     * Búsqueda global sin restricción de ubicación
     */
    private suspend fun searchGlobalByName(query: String): List<Place> {
        return try {
            val queryStr = OverpassQueryBuilder.buildGlobalSearchQuery(query, limit = 50)
            val response = overpassService.getTouristPlaces(queryStr)
            
            if (response.isSuccessful) {
                val elements = response.body()?.elements ?: emptyList()
                Log.d(TAG, "Búsqueda global: ${elements.size} elementos")
                elements.mapNotNull { convertOverpassElementToPlace(it) }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en búsqueda global: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Obtiene todos los lugares guardados
     */
    fun getAllPlaces(): Flow<List<Place>> {
        return placeDao.getAllPlaces()
    }
    
    /**
     * Actualiza el estado de favorito
     */
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean) {
        placeDao.updateFavoriteStatus(id, isFavorite)
    }
    
    /**
     * Obtiene lugares favoritos
     */
    fun getFavoritePlaces(): Flow<List<Place>> {
        return placeDao.getFavoritePlaces()
    }
    
    /**
     * Obtiene información del caché
     */
    fun getCacheInfo(): CacheInfo {
        val cacheStats = viewportCache.getCacheStats()
        val offlineInfo = offlineAreaManager.getOfflineAreaInfo()
        
        return CacheInfo(
            cachedCells = cacheStats.cachedCells,
            cachedAreaKm2 = cacheStats.approximateAreaKm2,
            offlineConfigured = offlineInfo.isConfigured,
            offlineCenterLat = offlineInfo.centerLat,
            offlineCenterLon = offlineInfo.centerLon,
            offlineRadiusKm = offlineInfo.radiusMeters / 1000.0
        )
    }
    
    /**
     * Limpia el caché
     */
    suspend fun clearCache() {
        viewportCache.clearCache()
        Log.d(TAG, "Caché de viewport limpiado")
    }
    
    /**
     * Limpia todos los datos
     */
    suspend fun clearAllData() {
        placeDao.deletePlacesBySource("overpass")
        viewportCache.clearCache()
        Log.d(TAG, "Todos los datos limpiados")
    }
    
    /**
     * Precarga un área específica (útil para descargar offline)
     */
    suspend fun preloadArea(boundingBox: BoundingBox) {
        Log.d(TAG, "Precargando área: $boundingBox")
        
        if (!isNetworkAvailable) {
            Log.d(TAG, "Sin red, no se puede precargar")
            return
        }
        
        // Verificar límite offline
        val clippedBox = if (offlineAreaManager.isConfigured()) {
            offlineAreaManager.clipToOfflineArea(boundingBox)
        } else {
            boundingBox
        }
        
        if (clippedBox != null) {
            downloadAndCachePlaces(clippedBox)
        }
    }
    
    companion object {
        private const val TAG = "PlacesRepository"
    }
}

/**
 * Información del caché
 */
data class CacheInfo(
    val cachedCells: Int,
    val cachedAreaKm2: Double,
    val offlineConfigured: Boolean,
    val offlineCenterLat: Double,
    val offlineCenterLon: Double,
    val offlineRadiusKm: Double
)
