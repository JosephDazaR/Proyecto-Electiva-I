package com.example.proyectoelectivai.data.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.pow

/**
 * Sistema de caché que trackea áreas del mapa ya descargadas
 * Evita descargar datos duplicados y optimiza la memoria RAM
 */
class ViewportCache(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "viewport_cache", Context.MODE_PRIVATE
    )
    private val mutex = Mutex()
    private val loadedAreas = mutableSetOf<String>()
    
    // Tamaño de la cuadrícula para dividir el mundo en celdas
    // Cada celda cubre aproximadamente 1km x 1km
    private val GRID_SIZE = 0.01 // ~1.11 km en el ecuador
    
    init {
        // Cargar áreas previamente descargadas
        loadCachedAreas()
    }
    
    /**
     * Verifica si un viewport ya fue descargado
     */
    suspend fun isAreaCached(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): Boolean {
        return try {
            mutex.withLock {
                val cells = getViewportCells(minLat, maxLat, minLon, maxLon)
                if (cells.isEmpty()) {
                    Log.w(TAG, "Viewport demasiado grande o inválido, considerando como no cacheado")
                    false
                } else {
                    cells.all { it in loadedAreas }
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError en isAreaCached, limpiando caché", e)
            clearCache()
            false
        }
    }
    
    /**
     * Marca un viewport como descargado
     */
    suspend fun markAreaAsCached(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        try {
            mutex.withLock {
                val cells = getViewportCells(minLat, maxLat, minLon, maxLon)
                if (cells.isNotEmpty()) {
                    loadedAreas.addAll(cells)
                    saveCachedAreas()
                    
                    // Limpiar caché si hay demasiadas áreas (limitar memoria)
                    if (loadedAreas.size > MAX_CACHED_CELLS) {
                        cleanOldestAreas()
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError en markAreaAsCached, limpiando caché", e)
            clearCache()
        }
    }
    
    /**
     * Obtiene las áreas que necesitan ser descargadas en un viewport
     */
    suspend fun getMissingAreas(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<BoundingBox> {
        return try {
            mutex.withLock {
                val cells = getViewportCells(minLat, maxLat, minLon, maxLon)
                if (cells.isEmpty()) {
                    Log.w(TAG, "Viewport demasiado grande, no se pueden obtener áreas faltantes")
                    emptyList()
                } else {
                    val missingCells = cells.filter { it !in loadedAreas }
                    // Agrupar celdas adyacentes en bounding boxes más grandes
                    groupCellsIntoBoundingBoxes(missingCells)
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError en getMissingAreas, limpiando caché", e)
            clearCache()
            emptyList()
        }
    }
    
    /**
     * Limpia el caché completo
     */
    suspend fun clearCache() {
        mutex.withLock {
            loadedAreas.clear()
            prefs.edit().clear().apply()
        }
    }
    
    /**
     * Limpia el caché de un área específica
     */
    suspend fun clearAreaCache(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        mutex.withLock {
            val cells = getViewportCells(minLat, maxLat, minLon, maxLon)
            loadedAreas.removeAll(cells.toSet())
            saveCachedAreas()
        }
    }
    
    /**
     * Obtiene todas las celdas que cubre un viewport
     * Con protección contra bucles infinitos y límites de memoria
     */
    private fun getViewportCells(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<String> {
        val cells = mutableListOf<String>()
        
        // Validar y limitar el rango para evitar bucles infinitos
        val safeMinLat = minLat.coerceIn(-90.0, 90.0)
        val safeMaxLat = maxLat.coerceIn(-90.0, 90.0)
        val safeMinLon = minLon.coerceIn(-180.0, 180.0)
        val safeMaxLon = maxLon.coerceIn(-180.0, 180.0)
        
        // Calcular el número aproximado de celdas para detectar áreas demasiado grandes
        val latCells = ((safeMaxLat - safeMinLat) / GRID_SIZE).toInt()
        val lonCells = ((safeMaxLon - safeMinLon) / GRID_SIZE).toInt()
        val totalCells = latCells * lonCells
        
        // Limitar el número máximo de celdas para evitar OutOfMemoryError
        if (totalCells > MAX_CELLS_PER_REQUEST) {
            Log.w(TAG, "Viewport demasiado grande: $totalCells celdas. Limitando a ${MAX_CELLS_PER_REQUEST}")
            return emptyList()
        }
        
        var lat = alignToGrid(safeMinLat)
        var cellCount = 0
        
        while (lat < safeMaxLat && cellCount < MAX_CELLS_PER_REQUEST) {
            var lon = alignToGrid(safeMinLon)
            while (lon < safeMaxLon && cellCount < MAX_CELLS_PER_REQUEST) {
                cells.add(getCellKey(lat, lon))
                lon += GRID_SIZE
                cellCount++
            }
            lat += GRID_SIZE
        }
        
        return cells
    }
    
    /**
     * Alinea una coordenada a la cuadrícula
     */
    private fun alignToGrid(coord: Double): Double {
        return (coord / GRID_SIZE).toInt() * GRID_SIZE
    }
    
    /**
     * Genera una clave única para una celda
     * Optimizado para reducir creación de strings
     */
    private fun getCellKey(lat: Double, lon: Double): String {
        val latKey = (lat / GRID_SIZE).toInt()
        val lonKey = (lon / GRID_SIZE).toInt()
        return StringBuilder(16).append(latKey).append(',').append(lonKey).toString()
    }
    
    /**
     * Agrupa celdas adyacentes en bounding boxes más grandes
     * Optimiza el número de requests a la API
     */
    private fun groupCellsIntoBoundingBoxes(cells: List<String>): List<BoundingBox> {
        if (cells.isEmpty()) return emptyList()
        
        // Convertir celdas a coordenadas
        val coordinates = cells.map { cell ->
            val parts = cell.split(",")
            val lat = parts[0].toInt() * GRID_SIZE
            val lon = parts[1].toInt() * GRID_SIZE
            lat to lon
        }
        
        // Crear un bounding box para cada grupo de celdas adyacentes
        val boxes = mutableListOf<BoundingBox>()
        val processed = mutableSetOf<Pair<Double, Double>>()
        
        for (coord in coordinates) {
            if (coord in processed) continue
            
            // Buscar celdas adyacentes
            val adjacent = findAdjacentCells(coord, coordinates, processed)
            processed.addAll(adjacent)
            
            // Crear bounding box
            val minLat = adjacent.minOf { it.first }
            val maxLat = adjacent.maxOf { it.first } + GRID_SIZE
            val minLon = adjacent.minOf { it.second }
            val maxLon = adjacent.maxOf { it.second } + GRID_SIZE
            
            boxes.add(BoundingBox(minLat, maxLat, minLon, maxLon))
        }
        
        return boxes
    }
    
    /**
     * Encuentra celdas adyacentes a una celda dada
     */
    private fun findAdjacentCells(
        start: Pair<Double, Double>,
        allCells: List<Pair<Double, Double>>,
        processed: Set<Pair<Double, Double>>
    ): Set<Pair<Double, Double>> {
        val adjacent = mutableSetOf(start)
        val queue = mutableListOf(start)
        
        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            
            // Buscar vecinos (arriba, abajo, izquierda, derecha)
            val neighbors = listOf(
                current.first + GRID_SIZE to current.second,
                current.first - GRID_SIZE to current.second,
                current.first to current.second + GRID_SIZE,
                current.first to current.second - GRID_SIZE
            )
            
            for (neighbor in neighbors) {
                if (neighbor in allCells && neighbor !in processed && neighbor !in adjacent) {
                    adjacent.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }
        
        return adjacent
    }
    
    /**
     * Carga áreas previamente cacheadas desde SharedPreferences
     */
    private fun loadCachedAreas() {
        val saved = prefs.getStringSet("cached_areas", emptySet()) ?: emptySet()
        loadedAreas.addAll(saved)
    }
    
    /**
     * Guarda áreas cacheadas en SharedPreferences
     */
    private fun saveCachedAreas() {
        prefs.edit().putStringSet("cached_areas", loadedAreas).apply()
    }
    
    /**
     * Limpia las áreas más antiguas para liberar memoria
     * Implementa limpieza agresiva para evitar OutOfMemoryError
     */
    private fun cleanOldestAreas() {
        val currentSize = loadedAreas.size
        if (currentSize > MAX_CACHED_CELLS) {
            val toRemove = currentSize - (MAX_CACHED_CELLS / 2) // Limpiar más agresivamente
            Log.d(TAG, "Limpiando $toRemove celdas de $currentSize (límite: $MAX_CACHED_CELLS)")
            
            // Convertir a lista para poder usar take()
            val areasList = loadedAreas.toList()
            val toRemoveSet = areasList.take(toRemove).toSet()
            
            loadedAreas.removeAll(toRemoveSet)
            saveCachedAreas()
            
            // Forzar garbage collection si es posible
            System.gc()
        }
    }
    
    /**
     * Obtiene estadísticas del caché
     */
    fun getCacheStats(): CacheStats {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsagePercent = (usedMemory.toDouble() / maxMemory.toDouble()) * 100.0
        
        return CacheStats(
            cachedCells = loadedAreas.size,
            approximateAreaKm2 = loadedAreas.size * (GRID_SIZE * 111.0).pow(2.0),
            maxCells = MAX_CACHED_CELLS,
            memoryUsageMB = usedMemory / (1024 * 1024),
            maxMemoryMB = maxMemory / (1024 * 1024),
            memoryUsagePercent = memoryUsagePercent
        )
    }
    
    companion object {
        // Máximo de celdas en caché (reducido para evitar OutOfMemoryError)
        private const val MAX_CACHED_CELLS = 1000
        // Máximo de celdas por request para evitar bucles infinitos
        private const val MAX_CELLS_PER_REQUEST = 100
        private const val TAG = "ViewportCache"
    }
}

/**
 * Representa un área rectangular en coordenadas geográficas
 */
data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
) {
    fun contains(lat: Double, lon: Double): Boolean {
        return lat in minLat..maxLat && lon in minLon..maxLon
    }
    
    fun intersects(other: BoundingBox): Boolean {
        return !(other.maxLat < minLat || other.minLat > maxLat ||
                other.maxLon < minLon || other.minLon > maxLon)
    }
}

/**
 * Estadísticas del caché
 */
data class CacheStats(
    val cachedCells: Int,
    val approximateAreaKm2: Double,
    val maxCells: Int,
    val memoryUsageMB: Long,
    val maxMemoryMB: Long,
    val memoryUsagePercent: Double
)

// Extension para Math.pow
private fun Int.pow(exponent: Double): Double {
    return Math.pow(this.toDouble(), exponent)
}

