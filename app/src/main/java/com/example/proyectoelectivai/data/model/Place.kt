package com.example.proyectoelectivai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Entidad que representa un lugar en la base de datos local
 * Puede ser turístico, de contaminación, parque, restaurante, etc.
 */
@Entity(
    tableName = "places",
    indices = [Index(value = ["type"]), Index(value = ["name"])]
)
data class Place(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String, // "tourist", "air_quality", "park", "restaurant", "osm"
    val lat: Double,
    val lon: Double,
    val description: String? = null,
    val rating: Double? = null,
    val address: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val openingHours: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val source: String, // "opentripmap", "openaq", "osm"
    val isFavorite: Boolean = false,
    val airQualityIndex: Int? = null, // Para datos de contaminación
    val airQualityLevel: String? = null, // "good", "moderate", "unhealthy", etc.
    val temperature: Double? = null,
    val humidity: Double? = null
)
