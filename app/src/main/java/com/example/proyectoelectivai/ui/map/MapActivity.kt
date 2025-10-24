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
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.proyectoelectivai.R
import com.example.proyectoelectivai.data.model.Place
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
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
    
    // UI Components
    private lateinit var searchEditText: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var topResultsCarousel: CardView
    private lateinit var topResultsViewPager: ViewPager2
    private lateinit var resultsCard: CardView
    private lateinit var rvPlaces: RecyclerView
    private lateinit var tvResultsCount: TextView
    private lateinit var emptyState: View
    private lateinit var progressBar: ProgressBar
    
    // Adapters
    private lateinit var placesAdapter: PlacesAdapter
    private lateinit var top3Adapter: PlacesAdapter
    
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
        
        searchEditText = findViewById(R.id.searchEditText)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        topResultsCarousel = findViewById(R.id.topResultsCarousel)
        topResultsViewPager = findViewById(R.id.topResultsViewPager)
        resultsCard = findViewById(R.id.resultsCard)
        rvPlaces = findViewById(R.id.rvPlaces)
        tvResultsCount = findViewById(R.id.tvResultsCount)
        emptyState = findViewById(R.id.emptyState)
        progressBar = findViewById(R.id.progressBar)
        
        // Inicializar ViewModel
        viewModel = ViewModelProvider(this)[MapViewModel::class.java]
        
        // Inicializar LocationManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        
        // Configurar RecyclerView
        setupRecyclerView()
        
        // Configurar mapa
        setupMap()
        
        // Configurar observadores
        setupObservers()
        
        // Configurar búsqueda
        setupSearch()
        
        // NO solicitar permisos aquí, se hará cuando el mapa esté listo
    }
    
    private fun setupRecyclerView() {
        // Adapter principal para lista completa
        placesAdapter = PlacesAdapter(
            onPlaceClick = { place ->
                // Al hacer clic en un lugar, centrarlo en el mapa y cerrar lista
                moveToLocation(place.lat, place.lon)
                viewModel.selectPlace(place)
                hideResultsList()
            },
            onShowOnMapClick = { place ->
                // Al hacer clic en "mostrar en mapa", centrar y resaltar
                moveToLocation(place.lat, place.lon)
                viewModel.selectPlace(place)
            }
        )
        
        rvPlaces.apply {
            layoutManager = LinearLayoutManager(this@MapActivity)
            adapter = placesAdapter
        }
        
        // Adapter para carousel de Top 3
        top3Adapter = PlacesAdapter(
            onPlaceClick = { place ->
                moveToLocation(place.lat, place.lon)
                viewModel.selectPlace(place)
                hideResultsList()
            },
            onShowOnMapClick = { place ->
                moveToLocation(place.lat, place.lon)
                viewModel.selectPlace(place)
            }
        )
        
        topResultsViewPager.adapter = top3Adapter
        topResultsViewPager.offscreenPageLimit = 1
        topResultsViewPager.setPageTransformer { page, position ->
            page.scaleY = 0.85f + (1 - kotlin.math.abs(position)) * 0.15f
        }
        
        // Botón cerrar lista
        findViewById<ImageButton>(R.id.btnCloseResults).setOnClickListener {
            hideResultsList()
        }
    }
    
    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                
                // Mostrar/ocultar botón limpiar
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                
                // Buscar con debounce
                searchHandler.removeCallbacks(searchRunnable)
                
                if (query.length > 2) {
                    searchHandler.postDelayed(searchRunnable, 300)
                } else if (query.isEmpty()) {
                    viewModel.searchPlaces("")
                    hideResultsList()
                }
            }
        })
        
        btnClearSearch.setOnClickListener {
            searchEditText.text.clear()
            hideResultsList()
        }
    }
    
    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val searchRunnable = Runnable {
        val query = searchEditText.text.toString()
        if (query.isNotEmpty()) {
            viewModel.searchPlaces(query)
            showResultsList()
        }
    }
    
    private fun showResultsList() {
        resultsCard.visibility = View.VISIBLE
    }
    
    private fun hideResultsList() {
        topResultsCarousel.visibility = View.GONE
        resultsCard.visibility = View.GONE
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
                
                // Cargar datos iniciales para el viewport
                loadDataForCurrentViewport()
            }
            
            // Configurar listeners
            setupMapListeners()
        }
    }
    
    private fun setupMapLayers(style: Style) {
        // Crear fuente de datos para lugares
        val placesSource = GeoJsonSource("places-source")
        style.addSource(placesSource)
        
        // Crear capa de puntos con colores según tipo
        val placesLayer = CircleLayer("places-layer", "places-source")
            .withProperties(
                // Color según tipo de lugar
                PropertyFactory.circleColor(
                    Expression.match(Expression.get("type"),
                        Expression.literal("#E91E63"), // Color por defecto (rosa)
                        Expression.stop("museum", "#9C27B0"),      // Morado para museos
                        Expression.stop("monument", "#FF5722"),     // Naranja para monumentos
                        Expression.stop("attraction", "#FF9800"),   // Naranja claro para atracciones
                        Expression.stop("artwork", "#F44336"),      // Rojo para arte
                        Expression.stop("statue", "#FF5722"),       // Naranja para estatuas
                        Expression.stop("park", "#4CAF50"),         // Verde para parques
                        Expression.stop("viewpoint", "#03A9F4"),    // Azul para miradores
                        Expression.stop("gallery", "#9C27B0"),      // Morado para galerías
                        Expression.stop("zoo", "#8BC34A"),          // Verde claro para zoológicos
                        Expression.stop("theme_park", "#FF9800"),   // Naranja claro para parques temáticos
                        Expression.stop("castle", "#795548"),       // Marrón para castillos
                        Expression.stop("ruins", "#9E9E9E"),        // Gris para ruinas
                        Expression.stop("city", "#2196F3"),         // Azul para ciudades
                        Expression.stop("town", "#03A9F4"),         // Azul claro para pueblos
                        Expression.stop("village", "#81D4FA"),      // Azul muy claro para villas
                        Expression.stop("hamlet", "#B3E5FC")        // Azul pastel para aldeas
                    )
                ),
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleOpacity(0.9f)
            )
        
        style.addLayer(placesLayer)
    }
    
    private fun setupMapListeners() {
        mapLibreMap.addOnMapClickListener { point ->
            // Manejar clic en el mapa
            handleMapClick(point)
            true
        }
        
        // Listener para detectar cuando el mapa deja de moverse
        mapLibreMap.addOnCameraIdleListener {
            // Cargar datos para el viewport actual
            loadDataForCurrentViewport()
        }
        
        // Listener para detectar el inicio del movimiento
        mapLibreMap.addOnCameraMoveStartedListener { reason ->
            // Opcional: puedes mostrar un indicador de que se está moviendo
            when (reason) {
                MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE -> {
                    // Movimiento por gestos del usuario
                }
                MapLibreMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION -> {
                    // Movimiento por código
                }
                MapLibreMap.OnCameraMoveStartedListener.REASON_API_ANIMATION -> {
                    // Animación de la API
                }
            }
        }
    }
    
    private fun setupObservers() {
        // Observar lugares filtrados
        viewModel.filteredPlaces.observe(this) { places ->
            println("DEBUG: Lugares filtrados recibidos: ${places.size}")
            
            // Actualizar mapa
            updateMapWithPlaces(places)
            
            // Actualizar lista si está visible
            if (resultsCard.visibility == View.VISIBLE) {
                updatePlacesList(places)
            }
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
        
        // Observar estado de carga
        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        // Configurar botones
        setupButtons()
    }
    
    private fun updatePlacesList(places: List<Place>) {
        if (places.isEmpty()) {
            // Sin resultados
            topResultsCarousel.visibility = View.GONE
            resultsCard.visibility = View.GONE
            return
        }
        
        // Top 3 en carousel (si hay suficientes)
        if (places.size >= 3) {
            val top3 = places.take(3)
            top3Adapter.submitList(top3)
            topResultsCarousel.visibility = View.VISIBLE
        } else {
            topResultsCarousel.visibility = View.GONE
        }
        
        // Lista completa (todos los resultados)
        placesAdapter.submitList(places)
        
        // Actualizar contador
        tvResultsCount.text = if (places.size == 1) {
            "1 lugar"
        } else {
            "${places.size} lugares"
        }
        
        // Mostrar/ocultar estado vacío
        if (places.isEmpty()) {
            rvPlaces.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            rvPlaces.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        }
    }
    
    private fun setupButtons() {
        // FAB de filtros
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabFilters)
            .setOnClickListener {
                openFilters()
            }
        
        // FAB de ubicación
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabLocation)
            .setOnClickListener {
                // Verificar permisos antes de obtener ubicación
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    getCurrentLocation()
                } else {
                    // Solicitar permisos si no los tiene
                    locationPermissionRequest.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }
    }
    
    private fun openFilters() {
        val filterBottomSheet = FilterBottomSheet.newInstance(
            selectedTypes = viewModel.selectedTypes.value,
            viewMode = viewModel.viewMode.value
        )
        
        filterBottomSheet.setOnFiltersChangedListener { types ->
            viewModel.updateSelectedTypes(types)
        }
        
        filterBottomSheet.setOnViewModeChangedListener { mode ->
            viewModel.setViewMode(mode)
        }
        
        filterBottomSheet.show(supportFragmentManager, "FilterBottomSheet")
    }
    
    private fun updateMapWithPlaces(places: List<Place>) {
        println("DEBUG: Actualizando mapa con ${places.size} lugares")
        
        mapLibreMap.getStyle { style ->
            val features = places.map { place ->
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
                println("DEBUG: Iniciando obtención de ubicación...")
                
                // Verificar qué proveedores están disponibles
                val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                val passiveEnabled = locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)
                
                println("DEBUG: GPS habilitado: $gpsEnabled")
                println("DEBUG: Network habilitado: $networkEnabled")
                println("DEBUG: Passive habilitado: $passiveEnabled")
                
                // Intentar con la última ubicación conocida de diferentes proveedores
                var bestLocation: Location? = null
                
                // Probar GPS primero
                val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (gpsLocation != null && isLocationRecent(gpsLocation)) {
                    bestLocation = gpsLocation
                    println("DEBUG: Ubicación GPS encontrada: ${gpsLocation.latitude}, ${gpsLocation.longitude}")
                }
                
                // Si no hay GPS, probar Network
                if (bestLocation == null) {
                    val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (networkLocation != null && isLocationRecent(networkLocation)) {
                        bestLocation = networkLocation
                        println("DEBUG: Ubicación Network encontrada: ${networkLocation.latitude}, ${networkLocation.longitude}")
                    }
                }
                
                // Si no hay Network, probar Passive
                if (bestLocation == null) {
                    val passiveLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                    if (passiveLocation != null && isLocationRecent(passiveLocation)) {
                        bestLocation = passiveLocation
                        println("DEBUG: Ubicación Passive encontrada: ${passiveLocation.latitude}, ${passiveLocation.longitude}")
                    }
                }
                
                // Si encontramos una ubicación reciente, usarla
                if (bestLocation != null) {
                    currentLocation = bestLocation
                    moveToLocation(bestLocation.latitude, bestLocation.longitude)
                    
                    // Configurar área offline en el ViewModel
                    viewModel.setOfflineCenter(bestLocation.latitude, bestLocation.longitude)
                    
                    Toast.makeText(this, "Ubicación actual encontrada", Toast.LENGTH_SHORT).show()
                    return
                }
                
                // Si no hay ubicación reciente, solicitar nueva ubicación
                Toast.makeText(this, "Obteniendo ubicación actual...", Toast.LENGTH_SHORT).show()
                println("DEBUG: No hay ubicación reciente, solicitando nueva ubicación...")
                
                val locationListener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        println("DEBUG: Nueva ubicación recibida: ${location.latitude}, ${location.longitude}")
                        currentLocation = location
                        moveToLocation(location.latitude, location.longitude)
                        
                        // Configurar área offline en el ViewModel
                        viewModel.setOfflineCenter(location.latitude, location.longitude)
                        
                        Toast.makeText(this@MapActivity, "Ubicación actualizada", Toast.LENGTH_SHORT).show()
                        locationManager.removeUpdates(this)
                    }
                    
                    override fun onProviderEnabled(provider: String) {
                        println("DEBUG: Proveedor habilitado: $provider")
                    }
                    
                    override fun onProviderDisabled(provider: String) {
                        println("DEBUG: Proveedor deshabilitado: $provider")
                    }
                }
                
                // Solicitar actualización de ubicación de múltiples proveedores
                if (gpsEnabled) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L, // 1 segundo
                        1f, // 1 metro
                        locationListener
                    )
                }
                
                if (networkEnabled) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        1000L,
                        10f, // 10 metros
                        locationListener
                    )
                }
                
                // Timeout después de 15 segundos
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    locationManager.removeUpdates(locationListener)
                    if (currentLocation == null) {
                        println("DEBUG: Timeout alcanzado, no se pudo obtener ubicación")
                        Toast.makeText(this, "No se pudo obtener ubicación, usando Bogotá", Toast.LENGTH_SHORT).show()
                        moveToDefaultLocation()
                    }
                }, 15000)
                
            } else {
                println("DEBUG: Permisos de ubicación no concedidos")
                Toast.makeText(this, "Permisos de ubicación no concedidos, usando Bogotá", Toast.LENGTH_SHORT).show()
                moveToDefaultLocation()
            }
        } catch (e: Exception) {
            println("DEBUG: Error obteniendo ubicación: ${e.message}")
            Toast.makeText(this, "Error obteniendo ubicación: ${e.message}", Toast.LENGTH_SHORT).show()
            moveToDefaultLocation()
        }
    }
    
    private fun isLocationRecent(location: Location): Boolean {
        val currentTime = System.currentTimeMillis()
        val locationTime = location.time
        val timeDifference = currentTime - locationTime
        // Considerar reciente si tiene menos de 30 minutos
        val isRecent = timeDifference < 30 * 60 * 1000
        println("DEBUG: Ubicación de hace ${timeDifference / 1000 / 60} minutos, es reciente: $isRecent")
        return isRecent
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
        val defaultLat = 4.7110
        val defaultLon = -74.0721
        moveToLocation(defaultLat, defaultLon)
        
        // Configurar área offline en Bogotá por defecto
        viewModel.setOfflineCenter(defaultLat, defaultLon)
    }
    
    /**
     * Carga datos para el viewport actual del mapa
     */
    private fun loadDataForCurrentViewport() {
        try {
            val visibleRegion = mapLibreMap.projection.visibleRegion
            val bounds = visibleRegion.latLngBounds
            
            val minLat = bounds.latitudeSouth
            val maxLat = bounds.latitudeNorth
            val minLon = bounds.longitudeWest
            val maxLon = bounds.longitudeEast
            
            println("DEBUG: Cargando datos para viewport: [$minLat, $maxLat] x [$minLon, $maxLon]")
            
            // Cargar datos usando el ViewModel
            viewModel.loadPlacesForViewport(minLat, maxLat, minLon, maxLon)
        } catch (e: Exception) {
            println("DEBUG: Error obteniendo viewport: ${e.message}")
        }
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