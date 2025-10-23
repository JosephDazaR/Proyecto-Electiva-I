package com.example.proyectoelectivai

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.navigation.NavigationView
import com.example.proyectoelectivai.databinding.ActivityMainBinding
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var suggestionAdapter: SuggestionAdapter
    private lateinit var mapView: MapView
    private lateinit var maplibreMap: MapLibreMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        org.maplibre.android.MapLibre.getInstance(applicationContext)


        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupNavigation(binding.navigationView)

        mapView = binding.mapView
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { map ->
            maplibreMap = map
            setupMap()
        }

        setupSearchAndSuggestions()
    }


    private fun setupNavigation(navigationView: NavigationView) {
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.item_lugares -> {}
                R.id.item_zonas_contaminadas -> {}
                R.id.item_capas_mapa -> {}
                R.id.item_config -> {}
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    //NO TOCAR!!!
private fun setupMap() {
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

    maplibreMap.setStyle(styleBuilder) { style ->
        val position = org.maplibre.android.camera.CameraPosition.Builder()
            .target(org.maplibre.android.geometry.LatLng(4.7110, -74.0721))
            .zoom(11.0)
            .build()
        maplibreMap.cameraPosition = position
    }
}




    private fun setupSearchAndSuggestions() {
        val searchView = binding.searchView
        searchView.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { /* performSearch(it) */ }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterSuggestions(newText.orEmpty())
                return true
            }
        })

        val initialSuggestions = listOf(
            Suggestion("Parques destacados", "Zonas verdes para visitar"),
            Suggestion("Miradores cercanos", "Panorámicas y fotos"),
            Suggestion("Zonas contaminadas", "Áreas a evitar por calidad del aire"),
            Suggestion("Rutas ecológicas", "Caminatas y ciclovías"),
        )

        suggestionAdapter = SuggestionAdapter(initialSuggestions) { suggestion ->
            binding.searchView.setQuery(suggestion.title, true)
        }

        binding.rvSuggestions.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = suggestionAdapter
        }
    }

    private fun filterSuggestions(text: String) {
        suggestionAdapter.filter(text)
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }
}
