package com.example.proyectoelectivai.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.proyectoelectivai.R
import com.example.proyectoelectivai.data.model.Place
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Actividad principal del mapa
 * Integra MapLibre con capas de datos turísticos, ambientales y gastronómicos
 */
class MapActivity : AppCompatActivity() {
    
    private lateinit var mapView: MapView
    private lateinit var mapLibreMap: MapLibreMap
    private lateinit var viewModel: MapViewModel
    private lateinit var locationManager: LocationManager
    
    private var currentLocation: Location? = null
    
    // Permisos de ubicación
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Ubicación obtenida
                Toast.makeText(this, "Permisos de ubicación concedidos", Toast.LENGTH_SHORT).show()
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Ubicación obtenida
                Toast.makeText(this, "Permisos de ubicación concedidos", Toast.LENGTH_SHORT).show()
                getCurrentLocation()
            }
            else -> {
                // Usar ubicación por defecto (Bogotá)
                moveToDefaultLocation()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar MapLibre
        MapLibre.getInstance(this)
        
        setContentView(R.layout.activity_map)
        
        // Inicializar componentes
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        
        // Inicializar ViewModel
        viewModel = ViewModelProvider(this)[MapViewModel::class.java]
        
        // Inicializar LocationManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        
        // Configurar mapa
        setupMap()
        
        // Configurar observadores
        setupObservers()
        
        // NO solicitar permisos aquí, se hará cuando el mapa esté listo
    }
    
    private fun setupMap() {
        mapView.getMapAsync { map ->
            mapLibreMap = map
            
            // Configurar estilo del mapa con OpenStreetMap
            val styleBuilder = org.maplibre.android.maps.Style.Builder()
                .fromJson(
                    """
                    {
                      "version": 8,
                      "sources": {
                        "osm": {
                          "type": "raster",
                          "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                          "tileSize": 256
                        }
                      },
                      "layers": [
                        {
                          "id": "osm-layer",
                          "type": "raster",
                          "source": "osm"
                        }
                      ]
                    }
                    """.trimIndent()
                )

            map.setStyle(styleBuilder) { style ->
                // El mapa está listo
                setupMapLayers(style)
                
                // AHORA que el mapa está listo, solicitar permisos
                requestLocationPermissions()
            }
            
            // Configurar listeners
            setupMapListeners()
        }
    }
    
    private fun setupMapLayers(style: Style) {
        // Crear fuente de datos para lugares
        val placesSource = GeoJsonSource("places-source")
        style.addSource(placesSource)
        
        // Crear capa de puntos
        val placesLayer = CircleLayer("places-layer", "places-source")
            .withProperties(
                org.maplibre.android.style.layers.PropertyFactory.circleColor("#FF0000"),
                org.maplibre.android.style.layers.PropertyFactory.circleRadius(12f),
                org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth(3f),
                org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor("#FFFFFF")
            )
        
        style.addLayer(placesLayer)
        
        // Crear capa de símbolos para etiquetas
        val labelsLayer = SymbolLayer("places-labels", "places-source")
            .withProperties(
                org.maplibre.android.style.layers.PropertyFactory.textField(org.maplibre.android.style.expressions.Expression.get("name")),
                org.maplibre.android.style.layers.PropertyFactory.textSize(14f),
                org.maplibre.android.style.layers.PropertyFactory.textColor("#000000"),
                org.maplibre.android.style.layers.PropertyFactory.textHaloColor("#FFFFFF"),
                org.maplibre.android.style.layers.PropertyFactory.textHaloWidth(2f),
                org.maplibre.android.style.layers.PropertyFactory.textOffset(arrayOf(0f, -2f))
            )
        
        style.addLayer(labelsLayer)
    }
    
    private fun setupMapListeners() {
        mapLibreMap.addOnMapClickListener { point ->
            // Manejar clic en el mapa
            handleMapClick(point)
            true
        }
    }
    
    private fun setupObservers() {
        // Observar lugares filtrados
        viewModel.filteredPlaces.observe(this) { places ->
            println("DEBUG: Lugares filtrados recibidos: ${places.size}")
            places.forEach { place ->
                println("DEBUG: Lugar: ${place.name} - ${place.type} - Lat: ${place.lat}, Lon: ${place.lon}")
            }
            updateMapWithPlaces(places)
        }
        
        // Observar lugar seleccionado
        viewModel.selectedPlace.observe(this) { place ->
            if (place != null) {
                showPlaceDetails(place)
            }
        }
        
        // Observar errores
        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
        
        // Configurar botones
        setupButtons()
    }
    
    private fun setupButtons() {
        // Botón de búsqueda con texto en tiempo real
        val searchEditText = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchEditText)
        searchEditText.setOnEditorActionListener { _, _, _ ->
            val query = searchEditText.text.toString()
            if (query.isNotEmpty()) {
                println("DEBUG: Búsqueda manual: '$query'")
                Toast.makeText(this, "Buscando: $query", Toast.LENGTH_SHORT).show()
                viewModel.searchPlaces(query)
            }
            true
        }
        
        // Agregar placeholder más claro
        searchEditText.hint = "Buscar lugares, direcciones... (ej: Museo, Calle 82)"
        
        // Búsqueda mientras escribes con debounce
        val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var searchRunnable: Runnable? = null
        
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString()
                
                // Cancelar búsqueda anterior
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                
                if (query.length > 2) { // Buscar después de 3 caracteres
                    searchRunnable = Runnable {
                        println("DEBUG: Buscando: '$query'")
                        Toast.makeText(this@MapActivity, "Buscando: $query", Toast.LENGTH_SHORT).show()
                        viewModel.searchPlaces(query)
                    }
                    searchHandler.postDelayed(searchRunnable!!, 500) // Debounce de 500ms
                } else if (query.isEmpty()) {
                    println("DEBUG: Cargando todos los lugares")
                    viewModel.loadAllPlaces() // Cargar todos si está vacío
                }
            }
        })
        
        // FAB de filtros
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabFilters)
            .setOnClickListener {
                // TODO: Abrir filtros
                Toast.makeText(this, "Filtros", Toast.LENGTH_SHORT).show()
            }
        
        // FAB de modo de vista
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabViewMode)
            .setOnClickListener {
                // TODO: Cambiar modo de vista
                Toast.makeText(this, "Modo de vista", Toast.LENGTH_SHORT).show()
            }
        
        // FAB de ubicación
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabLocation)
            .setOnClickListener {
                moveToDefaultLocation()
            }
    }
    
    private fun updateMapWithPlaces(places: List<Place>) {
        println("DEBUG: Actualizando mapa con ${places.size} lugares")
        
        mapLibreMap.getStyle { style ->
            val features = places.map { place ->
                println("DEBUG: Creando feature para: ${place.name}")
                Feature.fromGeometry(
                    Point.fromLngLat(place.lon, place.lat)
                ).apply {
                    addStringProperty("id", place.id)
                    addStringProperty("name", place.name)
                    addStringProperty("type", place.type)
                    addStringProperty("description", place.description ?: "")
                }
            }
            
            val source = style.getSourceAs<GeoJsonSource>("places-source")
            if (source != null) {
                source.setGeoJson(FeatureCollection.fromFeatures(features))
                println("DEBUG: GeoJSON actualizado con ${features.size} features")
                
                // Si hay lugares, mover la cámara al primer lugar
                if (places.isNotEmpty()) {
                    val firstPlace = places.first()
                    moveToLocation(firstPlace.lat, firstPlace.lon)
                    Toast.makeText(this, "Encontrados ${places.size} lugares", Toast.LENGTH_SHORT).show()
                }
            } else {
                println("DEBUG: Error: No se encontró la fuente 'places-source'")
            }
        }
    }
    
    private fun handleMapClick(point: LatLng) {
        // Buscar lugar más cercano al punto clicado
        val places = viewModel.filteredPlaces.value ?: emptyList()
        val closestPlace = findClosestPlace(point, places)
        
        if (closestPlace != null) {
            viewModel.selectPlace(closestPlace)
        }
    }
    
    private fun findClosestPlace(point: LatLng, places: List<Place>): Place? {
        if (places.isEmpty()) return null
        
        return places.minByOrNull { place ->
            val distance = calculateDistance(
                point.latitude, point.longitude,
                place.lat, place.lon
            )
            distance
        }
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // Radio de la Tierra en metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earthRadius * c
    }
    
    private fun showPlaceDetails(place: Place) {
        // TODO: Implementar bottom sheet con detalles del lugar
        Toast.makeText(this, "Lugar: ${place.name}", Toast.LENGTH_SHORT).show()
    }
    
    private fun requestLocationPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Ya tiene permisos, obtener ubicación real
                getCurrentLocation()
            }
            else -> {
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }
    
    private fun getCurrentLocation() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (location != null) {
                    currentLocation = location
                    moveToLocation(location.latitude, location.longitude)
                    Toast.makeText(this, "Ubicación actual encontrada", Toast.LENGTH_SHORT).show()
                } else {
                    // Si no hay ubicación reciente, usar ubicación por defecto
                    Toast.makeText(this, "No se encontró ubicación reciente, usando Bogotá", Toast.LENGTH_SHORT).show()
                    moveToDefaultLocation()
                }
            } else {
                moveToDefaultLocation()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            moveToDefaultLocation()
        }
    }
    
    private fun moveToLocation(lat: Double, lon: Double) {
        val cameraPosition = CameraPosition.Builder()
            .target(LatLng(lat, lon))
            .zoom(15.0)
            .build()
        
        mapLibreMap.cameraPosition = cameraPosition
    }
    
    private fun moveToDefaultLocation() {
        // Bogotá por defecto
        moveToLocation(4.7110, -74.0721)
    }
    
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }
    
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
    
    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}