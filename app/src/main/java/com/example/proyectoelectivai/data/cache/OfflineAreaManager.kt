package com.example.proyectoelectivai.data.cache

import android.content.Context
import android.content.SharedPreferences
import android.location.Location

/**
 * Gestor de áreas offline
 * Limita el radio máximo de descarga a la ciudad del usuario
 */
class OfflineAreaManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "offline_area", Context.MODE_PRIVATE
    )
    
    // Radio máximo para área offline (en metros)
    // Por defecto: 15 km de radio (cubre la mayoría de ciudades principales)
    private var maxOfflineRadius = prefs.getFloat("max_radius", DEFAULT_MAX_RADIUS.toFloat()).toDouble()
    
    // Centro del área offline (ubicación de la ciudad del usuario)
    private var centerLat = prefs.getFloat("center_lat", 0f).toDouble()
    private var centerLon = prefs.getFloat("center_lon", 0f).toDouble()
    
    /**
     * Establece el centro del área offline (ubicación actual del usuario)
     * Esto define la ciudad base para el modo offline
     */
    fun setOfflineCenter(lat: Double, lon: Double) {
        centerLat = lat
        centerLon = lon
        
        prefs.edit()
            .putFloat("center_lat", lat.toFloat())
            .putFloat("center_lon", lon.toFloat())
            .apply()
    }
    
    /**
     * Establece el radio máximo de descarga offline
     */
    fun setMaxRadius(radiusMeters: Double) {
        maxOfflineRadius = radiusMeters
        prefs.edit()
            .putFloat("max_radius", radiusMeters.toFloat())
            .apply()
    }
    
    /**
     * Verifica si un área está dentro del límite offline
     */
    fun isWithinOfflineArea(lat: Double, lon: Double): Boolean {
        if (centerLat == 0.0 && centerLon == 0.0) {
            // Si no hay centro definido, permitir todo
            return true
        }
        
        val distance = calculateDistance(centerLat, centerLon, lat, lon)
        return distance <= maxOfflineRadius
    }
    
    /**
     * Verifica si un bounding box está completamente dentro del área offline
     */
    fun isWithinOfflineArea(boundingBox: BoundingBox): Boolean {
        if (centerLat == 0.0 && centerLon == 0.0) {
            return true
        }
        
        // Verificar las 4 esquinas del bounding box
        val corners = listOf(
            Pair(boundingBox.minLat, boundingBox.minLon),
            Pair(boundingBox.minLat, boundingBox.maxLon),
            Pair(boundingBox.maxLat, boundingBox.minLon),
            Pair(boundingBox.maxLat, boundingBox.maxLon)
        )
        
        return corners.all { (lat, lon) ->
            isWithinOfflineArea(lat, lon)
        }
    }
    
    /**
     * Obtiene el bounding box que limita el área offline
     */
    fun getOfflineAreaBounds(): BoundingBox? {
        if (centerLat == 0.0 && centerLon == 0.0) {
            return null
        }
        
        // Convertir radio a grados aproximados
        val latOffset = maxOfflineRadius / 111000.0 // 1 grado lat ≈ 111 km
        val lonOffset = maxOfflineRadius / (111000.0 * kotlin.math.cos(Math.toRadians(centerLat)))
        
        return BoundingBox(
            minLat = centerLat - latOffset,
            maxLat = centerLat + latOffset,
            minLon = centerLon - lonOffset,
            maxLon = centerLon + lonOffset
        )
    }
    
    /**
     * Recorta un bounding box para que esté dentro del área offline
     */
    fun clipToOfflineArea(boundingBox: BoundingBox): BoundingBox? {
        val offlineArea = getOfflineAreaBounds() ?: return boundingBox
        
        // Calcular intersección
        val minLat = kotlin.math.max(boundingBox.minLat, offlineArea.minLat)
        val maxLat = kotlin.math.min(boundingBox.maxLat, offlineArea.maxLat)
        val minLon = kotlin.math.max(boundingBox.minLon, offlineArea.minLon)
        val maxLon = kotlin.math.min(boundingBox.maxLon, offlineArea.maxLon)
        
        // Verificar si hay intersección válida
        if (minLat >= maxLat || minLon >= maxLon) {
            return null
        }
        
        return BoundingBox(minLat, maxLat, minLon, maxLon)
    }
    
    /**
     * Obtiene la distancia desde el centro del área offline
     */
    fun getDistanceFromCenter(lat: Double, lon: Double): Double {
        if (centerLat == 0.0 && centerLon == 0.0) {
            return 0.0
        }
        return calculateDistance(centerLat, centerLon, lat, lon)
    }
    
    /**
     * Verifica si el área offline está configurada
     */
    fun isConfigured(): Boolean {
        return centerLat != 0.0 || centerLon != 0.0
    }
    
    /**
     * Obtiene información del área offline
     */
    fun getOfflineAreaInfo(): OfflineAreaInfo {
        return OfflineAreaInfo(
            centerLat = centerLat,
            centerLon = centerLon,
            radiusMeters = maxOfflineRadius,
            isConfigured = isConfigured(),
            approximateAreaKm2 = kotlin.math.PI * (maxOfflineRadius / 1000.0).pow(2)
        )
    }
    
    /**
     * Limpia la configuración del área offline
     */
    fun clearOfflineArea() {
        centerLat = 0.0
        centerLon = 0.0
        maxOfflineRadius = DEFAULT_MAX_RADIUS
        
        prefs.edit().clear().apply()
    }
    
    /**
     * Calcula la distancia entre dos puntos usando la fórmula de Haversine
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }
    
    companion object {
        // Radio por defecto: 15 km (cubre ciudades medianas)
        private const val DEFAULT_MAX_RADIUS = 15000.0
        
        // Radios predefinidos para diferentes tamaños de ciudad
        const val RADIUS_SMALL_CITY = 5000.0    // 5 km
        const val RADIUS_MEDIUM_CITY = 15000.0  // 15 km
        const val RADIUS_LARGE_CITY = 30000.0   // 30 km
        const val RADIUS_METROPOLIS = 50000.0   // 50 km
    }
}

/**
 * Información del área offline
 */
data class OfflineAreaInfo(
    val centerLat: Double,
    val centerLon: Double,
    val radiusMeters: Double,
    val isConfigured: Boolean,
    val approximateAreaKm2: Double
)

// Extension para Math.pow
private fun Double.pow(exponent: Int): Double {
    return Math.pow(this, exponent.toDouble())
}

