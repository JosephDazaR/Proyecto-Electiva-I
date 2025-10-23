package com.example.proyectoelectivai.data.network

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.proyectoelectivai.data.repository.PlacesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker para sincronización automática de datos
 * Se ejecuta cada 6 horas para actualizar los datos locales
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val repository = PlacesRepository(context)
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Sincronizar todos los tipos de datos
            repository.getAllPlaces().collect { places ->
                // Los datos se actualizan automáticamente en el repositorio
                // si hay conexión a internet
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
