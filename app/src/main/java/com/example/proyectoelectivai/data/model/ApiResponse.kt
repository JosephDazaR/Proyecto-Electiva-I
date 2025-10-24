package com.example.proyectoelectivai.data.model

/**
 * Modelos de respuesta para Overpass API (OpenStreetMap)
 * Estructura fija para lugares turísticos
 */

// Overpass API Response
data class OverpassResponse(
    val elements: List<OverpassElement>
)

data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double,
    val lon: Double,
    val tags: Map<String, String>?
)

/**
 * Tipos de lugares turísticos soportados
 */
enum class TouristPlaceType(val value: String, val displayName: String) {
    MUSEUM("museum", "Museo"),
    MONUMENT("monument", "Monumento"),
    ATTRACTION("attraction", "Atracción"),
    ARTWORK("artwork", "Obra de Arte"),
    VIEWPOINT("viewpoint", "Mirador"),
    PARK("park", "Parque"),
    GALLERY("gallery", "Galería"),
    ZOO("zoo", "Zoológico"),
    THEME_PARK("theme_park", "Parque Temático"),
    STATUE("statue", "Estatua"),
    CASTLE("castle", "Castillo"),
    RUINS("ruins", "Ruinas");
    
    companion object {
        fun fromOsmTag(tags: Map<String, String>): TouristPlaceType? {
            return when {
                tags["tourism"] == "museum" -> MUSEUM
                tags["tourism"] == "monument" -> MONUMENT
                tags["tourism"] == "attraction" -> ATTRACTION
                tags["tourism"] == "artwork" -> ARTWORK
                tags["tourism"] == "viewpoint" -> VIEWPOINT
                tags["tourism"] == "gallery" -> GALLERY
                tags["tourism"] == "zoo" -> ZOO
                tags["tourism"] == "theme_park" -> THEME_PARK
                tags["leisure"] == "park" -> PARK
                tags["historic"] == "monument" -> MONUMENT
                tags["historic"] == "statue" -> STATUE
                tags["historic"] == "castle" -> CASTLE
                tags["historic"] == "ruins" -> RUINS
                else -> null
            }
        }
    }
}
