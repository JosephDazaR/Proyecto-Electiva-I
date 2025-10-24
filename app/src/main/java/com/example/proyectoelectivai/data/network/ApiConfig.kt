package com.example.proyectoelectivai.data.network

/**
 * Configuración de APIs
 * Aquí se definen las API keys y URLs base
 */
object ApiConfig {
    
    // OpenAQ API (Calidad del aire)
    const val OPEN_AQ_BASE_URL = "https://api.openaq.org/"
    
    // Overpass API (OpenStreetMap) - Gratuito, sin API key
    const val OVERPASS_BASE_URL = "https://overpass-api.de/"
    
    // MapLibre/Mapbox - Token gratuito
    const val MAPLIBRE_ACCESS_TOKEN = "pk.eyJ1Ijoiam9zZXBoeWxuIiwiYSI6ImNtaDQxMWtnbDFncGZhbXB1YnlkZ2Zud3cifQ.wIZPfb_V3yIe8gu1ELVvDw"
    
    // Configuración por defecto
    const val DEFAULT_LAT = 4.7110
    const val DEFAULT_LON = -74.0721
    const val DEFAULT_RADIUS = 1000
    const val DEFAULT_LIMIT = 20
    
    // Configuración de cache
    const val CACHE_SIZE_MB = 10L
    const val CACHE_MAX_AGE_HOURS = 1L
    const val CACHE_MAX_STALE_DAYS = 1L
    
    // Configuración de sincronización
    const val SYNC_INTERVAL_HOURS = 6L
    const val SYNC_TIMEOUT_SECONDS = 30L
}
