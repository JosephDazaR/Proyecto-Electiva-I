package com.example.proyectoelectivai.data.network

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Módulo de configuración de WorkManager
 * Maneja la sincronización automática de datos
 */
object WorkManagerModule {
    
    private const val SYNC_WORK_NAME = "sync_places_data"
    private const val SYNC_INTERVAL_HOURS = 6L
    
    /**
     * Configura la sincronización automática
     */
    fun setupSyncWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            SYNC_INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
    
    /**
     * Cancela la sincronización automática
     */
    fun cancelSyncWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
    }
    
    /**
     * Ejecuta una sincronización inmediata
     */
    fun runImmediateSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val immediateSyncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        
        WorkManager.getInstance(context).enqueue(immediateSyncRequest)
    }
    
    /**
     * Obtiene el estado de la sincronización
     */
    fun getSyncStatus(context: Context, callback: (WorkInfo.State?) -> Unit) {
        try {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(SYNC_WORK_NAME)
                .get()
            
            val workInfo = workInfos.firstOrNull()
            callback(workInfo?.state)
        } catch (e: Exception) {
            callback(null)
        }
    }
}
