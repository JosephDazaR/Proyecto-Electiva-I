package com.example.proyectoelectivai.data.network

import com.example.proyectoelectivai.data.cache.BoundingBox

/**
 * Constructor de queries para Overpass API
 * Crea queries optimizadas para obtener lugares turísticos
 */
object OverpassQueryBuilder {
    
    /**
     * Construye una query para obtener lugares turísticos en un área
     * La estructura del JSON será siempre fija
     */
    fun buildTouristPlacesQuery(boundingBox: BoundingBox): String {
        val bbox = "${boundingBox.minLat},${boundingBox.minLon},${boundingBox.maxLat},${boundingBox.maxLon}"
        
        return """
            [out:json][timeout:60];
            (
              // Museos
              node["tourism"="museum"]($bbox);
              way["tourism"="museum"]($bbox);
              relation["tourism"="museum"]($bbox);
              
              // Monumentos
              node["tourism"="monument"]($bbox);
              way["tourism"="monument"]($bbox);
              node["historic"="monument"]($bbox);
              way["historic"="monument"]($bbox);
              
              // Atracciones turísticas
              node["tourism"="attraction"]($bbox);
              way["tourism"="attraction"]($bbox);
              
              // Arte y obras de arte
              node["tourism"="artwork"]($bbox);
              way["tourism"="artwork"]($bbox);
              node["historic"="statue"]($bbox);
              way["historic"="statue"]($bbox);
              
              // Miradores
              node["tourism"="viewpoint"]($bbox);
              
              // Parques
              node["leisure"="park"]($bbox);
              way["leisure"="park"]($bbox);
              relation["leisure"="park"]($bbox);
              
              // Galerías
              node["tourism"="gallery"]($bbox);
              way["tourism"="gallery"]($bbox);
              
              // Zoológicos y parques temáticos
              node["tourism"="zoo"]($bbox);
              way["tourism"="zoo"]($bbox);
              node["tourism"="theme_park"]($bbox);
              way["tourism"="theme_park"]($bbox);
              
              // Castillos y ruinas
              node["historic"="castle"]($bbox);
              way["historic"="castle"]($bbox);
              node["historic"="ruins"]($bbox);
              way["historic"="ruins"]($bbox);
            );
            out center;
        """.trimIndent()
    }
    
    /**
     * Construye una query más ligera para áreas grandes
     * Solo obtiene los lugares más relevantes
     */
    fun buildLightTouristPlacesQuery(boundingBox: BoundingBox): String {
        val bbox = "${boundingBox.minLat},${boundingBox.minLon},${boundingBox.maxLat},${boundingBox.maxLon}"
        
        return """
            [out:json][timeout:30];
            (
              node["tourism"="museum"]($bbox);
              node["tourism"="monument"]($bbox);
              node["tourism"="attraction"]($bbox);
              node["historic"="monument"]($bbox);
              node["leisure"="park"]($bbox);
            );
            out center;
        """.trimIndent()
    }
    
    /**
     * Calcula el área del bounding box en km²
     */
    private fun calculateArea(boundingBox: BoundingBox): Double {
        val latDiff = boundingBox.maxLat - boundingBox.minLat
        val lonDiff = boundingBox.maxLon - boundingBox.minLon
        
        // Conversión aproximada a km²
        val latKm = latDiff * 111.0
        val lonKm = lonDiff * 111.0 * kotlin.math.cos(Math.toRadians((boundingBox.minLat + boundingBox.maxLat) / 2))
        
        return latKm * lonKm
    }
    
    /**
     * Selecciona la query apropiada según el tamaño del área
     */
    fun buildOptimalQuery(boundingBox: BoundingBox): String {
        val area = calculateArea(boundingBox)
        
        return if (area > 25.0) { // Más de 25 km²
            buildLightTouristPlacesQuery(boundingBox)
        } else {
            buildTouristPlacesQuery(boundingBox)
        }
    }
    
    /**
     * Construye una query para un punto con radio
     */
    fun buildTouristPlacesQueryAroundPoint(lat: Double, lon: Double, radiusMeters: Int = 1000): String {
        return """
            [out:json][timeout:30];
            (
              node["tourism"~"^(museum|monument|attraction|artwork|viewpoint|gallery|zoo|theme_park)$"](around:$radiusMeters,$lat,$lon);
              node["historic"~"^(monument|statue|castle|ruins)$"](around:$radiusMeters,$lat,$lon);
              node["leisure"="park"](around:$radiusMeters,$lat,$lon);
            );
            out center;
        """.trimIndent()
    }
    
    /**
     * Búsqueda global por nombre (estilo YouTube)
     * Busca en todo el mundo cualquier lugar que coincida con el nombre
     */
    fun buildGlobalSearchQuery(searchName: String, limit: Int = 50): String {
        val escapedName = searchName.replace("\"", "\\\"")
        
        return """
            [out:json][timeout:30];
            (
              // Lugares turísticos
              node["tourism"]["name"~"$escapedName",i];
              way["tourism"]["name"~"$escapedName",i];
              relation["tourism"]["name"~"$escapedName",i];
              
              // Lugares históricos
              node["historic"]["name"~"$escapedName",i];
              way["historic"]["name"~"$escapedName",i];
              
              // Parques
              node["leisure"="park"]["name"~"$escapedName",i];
              way["leisure"="park"]["name"~"$escapedName",i];
              
              // Ciudades y pueblos
              node["place"~"^(city|town|village|hamlet)$"]["name"~"$escapedName",i];
              
              // Lugares nombrados generales
              node["name"~"$escapedName",i]["amenity"];
              way["name"~"$escapedName",i]["amenity"];
            );
            out center $limit;
        """.trimIndent()
    }
    
    /**
     * Búsqueda incremental por radio (primero cerca, luego lejos)
     * Ideal para búsqueda progresiva
     */
    fun buildIncrementalSearchQuery(
        searchName: String, 
        centerLat: Double, 
        centerLon: Double, 
        radiusMeters: Int,
        limit: Int = 20
    ): String {
        val escapedName = searchName.replace("\"", "\\\"")
        
        return """
            [out:json][timeout:30];
            (
              node["name"~"$escapedName",i](around:$radiusMeters,$centerLat,$centerLon);
              way["name"~"$escapedName",i](around:$radiusMeters,$centerLat,$centerLon);
              node["place"]["name"~"$escapedName",i](around:$radiusMeters,$centerLat,$centerLon);
            );
            out center $limit;
        """.trimIndent()
    }
}

