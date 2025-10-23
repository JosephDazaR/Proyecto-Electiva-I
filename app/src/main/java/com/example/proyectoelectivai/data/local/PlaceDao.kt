package com.example.proyectoelectivai.data.local

import androidx.room.*
import androidx.lifecycle.LiveData
import com.example.proyectoelectivai.data.model.Place
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones de base de datos con lugares
 */
@Dao
interface PlaceDao {
    
    @Query("SELECT * FROM places ORDER BY lastUpdated DESC")
    fun getAllPlaces(): Flow<List<Place>>
    
    @Query("SELECT * FROM places WHERE type = :type ORDER BY lastUpdated DESC")
    fun getPlacesByType(type: String): Flow<List<Place>>
    
    @Query("SELECT * FROM places WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchPlaces(query: String): Flow<List<Place>>
    
    @Query("SELECT * FROM places WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoritePlaces(): Flow<List<Place>>
    
    @Query("SELECT * FROM places WHERE lat BETWEEN :minLat AND :maxLat AND lon BETWEEN :minLon AND :maxLon")
    fun getPlacesInBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): Flow<List<Place>>
    
    @Query("SELECT * FROM places WHERE type = :type AND lat BETWEEN :minLat AND :maxLat AND lon BETWEEN :minLon AND :maxLon")
    fun getPlacesByTypeInBounds(type: String, minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): Flow<List<Place>>
    
    @Query("SELECT * FROM places WHERE airQualityIndex IS NOT NULL ORDER BY airQualityIndex ASC")
    fun getAirQualityPlaces(): Flow<List<Place>>
    
    @Query("SELECT * FROM places WHERE source = :source ORDER BY lastUpdated DESC")
    fun getPlacesBySource(source: String): Flow<List<Place>>
    
    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getPlaceById(id: String): Place?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: Place)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaces(places: List<Place>)
    
    @Update
    suspend fun updatePlace(place: Place)
    
    @Query("UPDATE places SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean)
    
    @Delete
    suspend fun deletePlace(place: Place)
    
    @Query("DELETE FROM places WHERE source = :source")
    suspend fun deletePlacesBySource(source: String)
    
    @Query("DELETE FROM places WHERE lastUpdated < :timestamp")
    suspend fun deleteOldPlaces(timestamp: Long)
    
    @Query("SELECT COUNT(*) FROM places")
    suspend fun getPlacesCount(): Int
    
    @Query("SELECT COUNT(*) FROM places WHERE type = :type")
    suspend fun getPlacesCountByType(type: String): Int
}
