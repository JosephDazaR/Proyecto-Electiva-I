package com.example.proyectoelectivai

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.navigation.NavigationView
import com.example.proyectoelectivai.databinding.ActivityMainBinding
import com.example.proyectoelectivai.ui.map.MapActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var suggestionAdapter: SuggestionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
        setupSearchAndSuggestions()
        
        // Redirigir a MapActivity
        startActivity(Intent(this, MapActivity::class.java))
        finish()
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

}
