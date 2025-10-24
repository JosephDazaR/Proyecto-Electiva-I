package com.example.proyectoelectivai

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.proyectoelectivai.ui.map.MapActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Redirigir directamente a MapActivity
        startActivity(Intent(this, MapActivity::class.java))
        finish()
    }
}