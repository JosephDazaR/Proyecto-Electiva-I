package com.example.proyectoelectivai.ui.map

import android.content.Context
import android.util.Log
import com.mapbox.mapboxsdk.offline.OfflineManager
import com.mapbox.mapboxsdk.offline.OfflineRegion
import com.mapbox.mapboxsdk.offline.OfflineRegionDefinition
import com.mapbox.mapboxsdk.offline.OfflineRegionError
import com.mapbox.mapboxsdk.offline.OfflineRegionStatus
import com.mapbox.mapboxsdk.offline.OfflineTilePyramidRegionDefinition
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.geometry.LatLng
import org.maplibre.android.offline.OfflineManager as MapLibreOfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionDefinition
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.geometry.LatLng

/**
 * Gestor de cache offline para MapLibre
 * Maneja la descarga y almacenamiento de tiles para uso offline
 */
class OfflineManager(private val context: Context) {
    
    private var offlineManager: MapLibreOfflineManager? = null
    private var downloadProgress: ((Int) -> Unit)? = null
    private var downloadComplete: (() -> Unit)? = null
    private var downloadError: ((String) -> Unit)? = null
    
    companion object {
        private const val TAG = "OfflineManager"
        private const val REGION_NAME = "BogotaRegion"
    }
    
    /**
     * Inicializa el gestor offline
     */
    fun initialize() {
        offlineManager = MapLibreOfflineManager.getInstance(context)
    }
    
    /**
     * Descarga tiles para una región específica
     * @param bounds Área geográfica a descargar
     * @param minZoom Zoom mínimo
     * @param maxZoom Zoom máximo
     */
    fun downloadRegion(
        bounds: LatLngBounds,
        minZoom: Double = 10.0,
        maxZoom: Double = 16.0
    ) {
        offlineManager?.let { manager ->
            val definition = OfflineTilePyramidRegionDefinition(
                "mapbox://styles/mapbox/streets-v11", // Estilo por defecto
                bounds,
                minZoom,
                maxZoom,
                context.resources.displayMetrics.density
            )
            
            val metadata = REGION_NAME.toByteArray()
            
            manager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    Log.d(TAG, "Región offline creada: ${offlineRegion.id}")
                    startDownload(offlineRegion)
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "Error creando región offline: $error")
                    downloadError?.invoke(error)
                }
            })
        }
    }
    
    /**
     * Descarga tiles para Bogotá por defecto
     */
    fun downloadBogotaRegion() {
        val bogotaBounds = LatLngBounds.Builder()
            .include(LatLng(4.5, -74.2))  // Suroeste
            .include(LatLng(4.9, -73.9))  // Noreste
            .build()
        
        downloadRegion(bogotaBounds, 10.0, 16.0)
    }
    
    /**
     * Inicia la descarga de una región
     */
    private fun startDownload(offlineRegion: OfflineRegion) {
        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
        
        offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                val progress = if (status.requiredResourceCount > 0) {
                    ((status.completedResourceCount.toDouble() / status.requiredResourceCount.toDouble()) * 100).toInt()
                } else {
                    0
                }
                
                Log.d(TAG, "Progreso descarga: $progress% (${status.completedResourceCount}/${status.requiredResourceCount})")
                downloadProgress?.invoke(progress)
                
                if (status.isComplete) {
                    Log.d(TAG, "Descarga completada")
                    downloadComplete?.invoke()
                }
            }
            
            override fun onError(error: OfflineRegionError) {
                Log.e(TAG, "Error en descarga: ${error.message}")
                downloadError?.invoke(error.message ?: "Error desconocido")
            }
            
            override fun mapboxTileCountLimitExceeded(limit: Long) {
                Log.w(TAG, "Límite de tiles excedido: $limit")
                downloadError?.invoke("Límite de tiles excedido")
            }
        })
    }
    
    /**
     * Obtiene las regiones offline existentes
     */
    fun getOfflineRegions(callback: (List<OfflineRegion>) -> Unit) {
        offlineManager?.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>) {
                callback(offlineRegions.toList())
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "Error listando regiones: $error")
                callback(emptyList())
            }
        })
    }
    
    /**
     * Elimina una región offline
     */
    fun deleteOfflineRegion(offlineRegion: OfflineRegion, callback: (Boolean) -> Unit) {
        offlineRegion.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                Log.d(TAG, "Región offline eliminada")
                callback(true)
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "Error eliminando región: $error")
                callback(false)
            }
        })
    }
    
    /**
     * Obtiene el estado de una región offline
     */
    fun getRegionStatus(offlineRegion: OfflineRegion, callback: (OfflineRegionStatus?) -> Unit) {
        offlineRegion.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus) {
                callback(status)
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "Error obteniendo estado: $error")
                callback(null)
            }
        })
    }
    
    /**
     * Pausa la descarga de una región
     */
    fun pauseDownload(offlineRegion: OfflineRegion) {
        offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
        Log.d(TAG, "Descarga pausada")
    }
    
    /**
     * Reanuda la descarga de una región
     */
    fun resumeDownload(offlineRegion: OfflineRegion) {
        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
        Log.d(TAG, "Descarga reanudada")
    }
    
    /**
     * Configura callbacks para el progreso de descarga
     */
    fun setDownloadCallbacks(
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        downloadProgress = onProgress
        downloadComplete = onComplete
        downloadError = onError
    }
    
    /**
     * Verifica si hay regiones offline disponibles
     */
    fun hasOfflineRegions(callback: (Boolean) -> Unit) {
        getOfflineRegions { regions ->
            callback(regions.isNotEmpty())
        }
    }
    
    /**
     * Obtiene el tamaño total de las regiones offline
     */
    fun getOfflineStorageSize(callback: (Long) -> Unit) {
        getOfflineRegions { regions ->
            var totalSize = 0L
            var completedRegions = 0
            
            if (regions.isEmpty()) {
                callback(0L)
                return@getOfflineRegions
            }
            
            regions.forEach { region ->
                getRegionStatus(region) { status ->
                    status?.let {
                        totalSize += it.requiredResourceSize
                        completedRegions++
                        
                        if (completedRegions == regions.size) {
                            callback(totalSize)
                        }
                    }
                }
            }
        }
    }
}
