package com.example.proyectoelectivai.data.model

/**
 * Modelos de respuesta para las diferentes APIs
 */

// OpenTripMap API Response
data class OpenTripMapResponse(
    val features: List<OpenTripMapFeature>
)

data class OpenTripMapFeature(
    val type: String,
    val properties: OpenTripMapProperties,
    val geometry: OpenTripMapGeometry
)

data class OpenTripMapProperties(
    val xid: String,
    val name: String,
    val rate: String?,
    val osm: String?,
    val wikidata: String?,
    val kinds: String?
)

data class OpenTripMapGeometry(
    val type: String,
    val coordinates: List<Double> // [lon, lat]
)

// OpenAQ API Response
data class OpenAQResponse(
    val results: List<OpenAQResult>
)

data class OpenAQResult(
    val location: String,
    val city: String?,
    val country: String,
    val coordinates: OpenAQCoordinates,
    val measurements: List<OpenAQMeasurement>
)

data class OpenAQCoordinates(
    val latitude: Double,
    val longitude: Double
)

data class OpenAQMeasurement(
    val parameter: String,
    val value: Double,
    val lastUpdated: String,
    val unit: String
)

// Overpass API (OSM) Response
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

// Respuesta unificada para el repositorio
data class PlacesApiResponse(
    val touristPlaces: List<Place> = emptyList(),
    val airQualityPlaces: List<Place> = emptyList(),
    val osmPlaces: List<Place> = emptyList(),
    val success: Boolean = true,
    val error: String? = null
)
