package com.example.proyectoelectivai.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.proyectoelectivai.R
import com.example.proyectoelectivai.data.model.Place
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.CircleLayer
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.style.sources.Source
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.maps.MapView as MapLibreMapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.maps.MapView as MapLibreMapView

/**
 * Actividad principal del mapa
 * Integra MapLibre con capas de datos turísticos, ambientales y gastronómicos
 */
class MapActivity : AppCompatActivity() {
    
    private lateinit var mapView: MapLibreMapView
    private lateinit var mapLibreMap: MapLibreMap
    private lateinit var viewModel: MapViewModel
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private var currentLocation: Location? = null
    
    // Permisos de ubicación
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
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
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
        
        setContentView(R.layout.activity_map)
        
        // Inicializar componentes
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        
        // Inicializar ViewModel
        viewModel = ViewModelProvider(this)[MapViewModel::class.java]
        
        // Inicializar cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Configurar mapa
        setupMap()
        
        // Configurar observadores
        setupObservers()
        
        // Solicitar permisos
        requestLocationPermissions()
    }
    
    private fun setupMap() {
        mapView.getMapAsync { map ->
            mapLibreMap = map
            
            // Configurar estilo del mapa
            map.setStyle(Style.MAPBOX_STREETS) { style ->
                // El mapa está listo
                setupMapLayers(style)
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
                com.mapbox.mapboxsdk.style.expressions.Expression.get("circle-color"),
                com.mapbox.mapboxsdk.style.expressions.Expression.get("circle-radius"),
                com.mapbox.mapboxsdk.style.expressions.Expression.get("circle-stroke-width"),
                com.mapbox.mapboxsdk.style.expressions.Expression.get("circle-stroke-color")
            )
        
        style.addLayer(placesLayer)
        
        // Crear capa de símbolos para etiquetas
        val labelsLayer = SymbolLayer("places-labels", "places-source")
            .withProperties(
                com.mapbox.mapboxsdk.style.expressions.Expression.get("text-field"),
                com.mapbox.mapboxsdk.style.expressions.Expression.get("text-size"),
                com.mapbox.mapboxsdk.style.expressions.Expression.get("text-color")
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
    }
    
    private fun updateMapWithPlaces(places: List<Place>) {
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
            source?.setGeoJson(FeatureCollection.fromFeatures(features))
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
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        currentLocation = location
                        moveToLocation(location.latitude, location.longitude)
                    } else {
                        moveToDefaultLocation()
                    }
                }
                .addOnFailureListener {
                    moveToDefaultLocation()
                }
        }
    }
    
    private fun moveToLocation(lat: Double, lon: Double) {
        mapLibreMap.animateCamera(
            CameraPosition.Builder()
                .target(LatLng(lat, lon))
                .zoom(15.0)
                .build()
        )
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
