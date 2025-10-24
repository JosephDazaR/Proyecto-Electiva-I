package com.example.proyectoelectivai.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.proyectoelectivai.data.model.Place
import com.example.proyectoelectivai.data.repository.PlacesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para el mapa principal
 * Maneja el estado de los lugares, filtros y búsquedas
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
    
    init {
        loadAllPlaces()
        // Cargar datos de ejemplo
        loadSampleData()
    }
    
    /**
     * Carga todos los lugares
     */
    fun loadAllPlaces() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                repository.getAllPlaces().collect { placesList ->
                    _places.value = placesList
                    applyFilters()
                }
            } catch (e: Exception) {
                _error.value = "Error cargando lugares: ${e.message}"
            } finally {
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
                }
            } catch (e: Exception) {
                _error.value = "Error cargando lugares por tipo: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Busca lugares
     */
    fun searchPlaces(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            try {
                if (query.isBlank()) {
                    loadAllPlaces()
                } else {
                    // Primero buscar en lugares existentes
                    repository.searchPlaces(query).collect { placesList ->
                        _places.value = placesList
                        applyFilters()
                    }
                    
                    // Siempre intentar geocoding para direcciones
                    if (isAddressQuery(query)) {
                        println("DEBUG: Detectada búsqueda de dirección: '$query'")
                        val addressResults = repository.searchAddress(query)
                        if (addressResults.isNotEmpty()) {
                            println("DEBUG: Direcciones encontradas: ${addressResults.size}")
                            val currentPlaces = _places.value ?: emptyList()
                            _places.value = currentPlaces + addressResults
                            applyFilters()
                        } else {
                            println("DEBUG: No se encontraron direcciones para: '$query'")
                        }
                    } else {
                        // También intentar geocoding para búsquedas generales que podrían ser direcciones
                        if (query.length > 5 && query.contains(Regex("\\d+"))) {
                            println("DEBUG: Intentando geocoding para búsqueda general: '$query'")
                            val addressResults = repository.searchAddress(query)
                            if (addressResults.isNotEmpty()) {
                                println("DEBUG: Direcciones encontradas en búsqueda general: ${addressResults.size}")
                                val currentPlaces = _places.value ?: emptyList()
                                _places.value = currentPlaces + addressResults
                                applyFilters()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = "Error buscando lugares: ${e.message}"
            }
        }
    }
    
    /**
     * Determina si la consulta parece ser una dirección
     */
    private fun isAddressQuery(query: String): Boolean {
        val addressKeywords = listOf(
            "calle", "carrera", "avenida", "avenue", "street", "road", 
            "casa", "apartamento", "edificio", "centro", "norte", "sur", "este", "oeste",
            "barrio", "sector", "zona", "localidad", "chapinero", "usaquen", "suba",
            "kennedy", "bosa", "ciudad bolivar", "san cristobal", "santa fe",
            "teusaquillo", "mártires", "antonio nariño", "puente aranda",
            "rafael uribe uribe", "sumapaz", "fontibon", "engativa"
        )
        
        val hasAddressKeyword = addressKeywords.any { keyword -> 
            query.lowercase().contains(keyword.lowercase()) 
        }
        
        val hasNumbers = query.contains(Regex("\\d+")) // Contiene números
        val hasCardinalDirection = query.contains(Regex("(norte|sur|este|oeste)", RegexOption.IGNORE_CASE))
        
        // Es dirección si tiene palabras clave de dirección, números, o direcciones cardinales
        return hasAddressKeyword || (hasNumbers && hasCardinalDirection) || 
               query.contains(Regex("(calle|carrera)\\s*\\d+", RegexOption.IGNORE_CASE))
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
     * Obtiene lugares en un área específica
     */
    fun getPlacesInBounds(
        minLat: Double, maxLat: Double, 
        minLon: Double, maxLon: Double
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                repository.getPlacesInBounds(minLat, maxLat, minLon, maxLon).collect { placesList ->
                    _places.value = placesList
                    applyFilters()
                }
            } catch (e: Exception) {
                _error.value = "Error cargando lugares en área: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
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
     * Refresca los datos
     */
    fun refresh() {
        loadAllPlaces()
    }
    
    /**
     * Carga datos de ejemplo
     */
    private fun loadSampleData() {
        viewModelScope.launch {
            try {
                repository.loadSampleData()
            } catch (e: Exception) {
                _error.value = "Error cargando datos de ejemplo: ${e.message}"
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
