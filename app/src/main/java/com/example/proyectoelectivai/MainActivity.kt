package com.example.proyectoelectivai

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.navigation.NavigationView
import com.example.proyectoelectivai.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var googleMap: GoogleMap? = null
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var suggestionAdapter: SuggestionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar + Drawer
        setSupportActionBar(binding.toolbar)
        toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupNavigation(binding.navigationView)
        setupMap()
        setupSearchAndSuggestions()
    }

    private fun setupNavigation(navigationView: NavigationView) {
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.item_lugares -> { /* TODO */ }
                R.id.item_zonas_contaminadas -> { /* TODO */ }
                R.id.item_capas_mapa -> { /* TODO */ }
                R.id.item_config -> { /* TODO */ }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun setupMap() {
        val mapFragment = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(binding.mapContainer.id, mapFragment)
            .commitNow()

        mapFragment.getMapAsync { map ->
            googleMap = map
            // Ejemplo: centrar en Bogotá
            val bogota = LatLng(4.7110, -74.0721)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(bogota, 11f))
        }
    }

    private fun setupSearchAndSuggestions() {
        val searchView = binding.searchView
        searchView.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { performSearch(it) }
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

    private fun performSearch(query: String) {
        if (query.contains("monserrate", ignoreCase = true)) {
            val pos = LatLng(4.6056, -74.0565)
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 14.5f))
        }
    }

    private fun filterSuggestions(text: String) {
        suggestionAdapter.filter(text)
    }
}
