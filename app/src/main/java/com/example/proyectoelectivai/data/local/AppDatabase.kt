package com.example.proyectoelectivai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.proyectoelectivai.data.model.Place

/**
 * Base de datos principal de la aplicación
 * Maneja el almacenamiento local de lugares usando Room
 */
@Database(
    entities = [Place::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun placeDao(): PlaceDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "places_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Converters para tipos complejos en Room
 */
class Converters {
    // Aquí se pueden agregar convertidores si es necesario
    // Por ejemplo, para listas, fechas personalizadas, etc.
}
