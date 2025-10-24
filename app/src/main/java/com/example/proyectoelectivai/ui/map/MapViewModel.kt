package com.example.proyectoelectivai.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.proyectoelectivai.data.cache.BoundingBox
import com.example.proyectoelectivai.data.model.Place
import com.example.proyectoelectivai.data.repository.PlacesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para el mapa principal
 * Maneja carga de datos basada en viewport
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = PlacesRepository(application)
    
    // Estado de los lugares
    private val _places = MutableLiveData<List<Place>>()
    val places: LiveData<List<Place>> = _places
    
    // Estado de filtros
    private val _selectedTypes = MutableStateFlow(setOf<String>())
    val selectedTypes: StateFlow<Set<String>> = _selectedTypes.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _viewMode = MutableStateFlow(ViewMode.POINTS)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()
    
    // Estado de carga
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Estado de error
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    // Lugares filtrados
    private val _filteredPlaces = MutableLiveData<List<Place>>()
    val filteredPlaces: LiveData<List<Place>> = _filteredPlaces
    
    // Lugar seleccionado
    private val _selectedPlace = MutableLiveData<Place?>()
    val selectedPlace: LiveData<Place?> = _selectedPlace
    
    // Viewport actual
    private var currentViewport: BoundingBox? = null
    
    /**
     * Configura el centro del área offline
     */
    fun setOfflineCenter(lat: Double, lon: Double) {
        repository.setOfflineCenter(lat, lon)
    }
    
    /**
     * Carga lugares para un viewport específico
     * Este es el método principal que se llama cuando el mapa se mueve
     */
    fun loadPlacesForViewport(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        val boundingBox = BoundingBox(minLat, maxLat, minLon, maxLon)
        currentViewport = boundingBox
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                // Verificar modo offline
                if (!repository.isOnline()) {
                    _error.value = "Modo offline: mostrando solo lugares guardados"
                }
                
                repository.getPlacesInViewport(boundingBox).collect { placesList ->
                    _places.value = placesList
                    applyFilters()
                    _isLoading.value = false  // Ocultar loading después de recibir datos
                }
            } catch (e: Exception) {
                _error.value = "Error cargando lugares: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Carga todos los lugares guardados (para vista inicial)
     */
    fun loadAllPlaces() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                repository.getAllPlaces().collect { placesList ->
                    _places.value = placesList
                    applyFilters()
                    _isLoading.value = false  // Ocultar loading después de recibir datos
                }
            } catch (e: Exception) {
                _error.value = "Error cargando lugares: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Carga lugares por tipo
     */
    fun loadPlacesByType(type: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                repository.getPlacesByType(type).collect { placesList ->
                    _places.value = placesList
                    applyFilters()
                    _isLoading.value = false  // Ocultar loading después de recibir datos
                }
            } catch (e: Exception) {
                _error.value = "Error cargando lugares por tipo: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Precarga un área para uso offline
     */
    fun preloadArea(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        val boundingBox = BoundingBox(minLat, maxLat, minLon, maxLon)
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                repository.preloadArea(boundingBox)
                _error.value = "Área descargada para uso offline"
            } catch (e: Exception) {
                _error.value = "Error descargando área: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Busca lugares globalmente (estilo YouTube)
     * Primero busca local, luego amplía progresivamente hasta todo el mundo
     */
    fun searchPlaces(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            try {
                if (query.isBlank()) {
                    // Si está vacío, cargar viewport actual o todos
                    currentViewport?.let {
                        loadPlacesForViewport(it.minLat, it.maxLat, it.minLon, it.maxLon)
                    } ?: loadAllPlaces()
                } else if (query.length >= 3) {
                    _isLoading.value = true
                    _error.value = null
                    
                    // Verificar modo offline
                    val isOffline = !repository.isOnline()
                    if (isOffline) {
                        _error.value = "Modo offline: buscando solo en lugares guardados"
                    }
                    
                    // Primero buscar en DB local (tomar solo el primer valor)
                    val localPlaces = repository.searchPlaces(query)
                    var firstEmissionReceived = false
                    
                    localPlaces.collect { places ->
                        if (!firstEmissionReceived) {
                            _places.value = places
                            applyFilters()
                            firstEmissionReceived = true
                            
                            // Luego buscar globalmente si tenemos ubicación Y hay internet
                            if (!isOffline) {
                                currentViewport?.let { viewport ->
                                    val centerLat = (viewport.minLat + viewport.maxLat) / 2
                                    val centerLon = (viewport.minLon + viewport.maxLon) / 2
                                    
                                    val globalPlaces = repository.searchPlacesGlobal(query, centerLat, centerLon)
                                    
                                    if (globalPlaces.isNotEmpty()) {
                                        // Combinar resultados locales y globales, sin duplicados
                                        val currentPlaces = _places.value ?: emptyList()
                                        val combined = (currentPlaces + globalPlaces).distinctBy { it.id }
                                        _places.value = combined
                                        applyFilters()
                                    }
                                }
                            }
                            
                            _isLoading.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = "Error buscando lugares: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Aplica filtros a los lugares
     */
    private fun applyFilters() {
        val currentPlaces = _places.value ?: emptyList()
        val currentTypes = _selectedTypes.value
        val currentQuery = _searchQuery.value
        
        val filtered = currentPlaces.filter { place ->
            // Filtro por tipo
            val typeMatch = currentTypes.isEmpty() || currentTypes.contains(place.type)
            
            // Filtro por búsqueda
            val searchMatch = currentQuery.isBlank() || 
                place.name.contains(currentQuery, ignoreCase = true) ||
                place.description?.contains(currentQuery, ignoreCase = true) == true ||
                place.address?.contains(currentQuery, ignoreCase = true) == true
            
            typeMatch && searchMatch
        }
        
        _filteredPlaces.value = filtered
    }
    
    /**
     * Actualiza los tipos seleccionados
     */
    fun updateSelectedTypes(types: Set<String>) {
        _selectedTypes.value = types
        applyFilters()
    }
    
    /**
     * Alterna un tipo en la selección
     */
    fun toggleType(type: String) {
        val currentTypes = _selectedTypes.value.toMutableSet()
        if (currentTypes.contains(type)) {
            currentTypes.remove(type)
        } else {
            currentTypes.add(type)
        }
        _selectedTypes.value = currentTypes
        applyFilters()
    }
    
    /**
     * Cambia el modo de visualización
     */
    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }
    
    /**
     * Selecciona un lugar
     */
    fun selectPlace(place: Place?) {
        _selectedPlace.value = place
    }
    
    /**
     * Actualiza el estado de favorito de un lugar
     */
    fun toggleFavorite(place: Place) {
        viewModelScope.launch {
            try {
                repository.updateFavoriteStatus(place.id, !place.isFavorite)
                // Recargar lugares para reflejar el cambio
                loadAllPlaces()
            } catch (e: Exception) {
                _error.value = "Error actualizando favorito: ${e.message}"
            }
        }
    }
    
    /**
     * Obtiene lugares favoritos
     */
    fun getFavoritePlaces(): Flow<List<Place>> {
        return repository.getFavoritePlaces()
    }
    
    
    /**
     * Obtiene estadísticas de lugares
     */
    fun getPlacesStats(): Map<String, Int> {
        val places = _places.value ?: emptyList()
        return places.groupBy { it.type }.mapValues { it.value.size }
    }
    
    /**
     * Limpia el error
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Refresca los datos del viewport actual
     */
    fun refresh() {
        currentViewport?.let {
            loadPlacesForViewport(it.minLat, it.maxLat, it.minLon, it.maxLon)
        } ?: loadAllPlaces()
    }
    
    /**
     * Obtiene información del caché
     */
    fun getCacheInfo() = repository.getCacheInfo()
    
    /**
     * Limpia el caché
     */
    fun clearCache() {
        viewModelScope.launch {
            try {
                repository.clearCache()
                _error.value = "Caché limpiado"
            } catch (e: Exception) {
                _error.value = "Error limpiando caché: ${e.message}"
            }
        }
    }
}

/**
 * Modos de visualización del mapa
 */
enum class ViewMode {
    POINTS,      // Puntos individuales
    CLUSTERS,    // Agrupados en clústeres
    HEATMAP      // Mapa de calor
}
