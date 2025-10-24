package com.example.proyectoelectivai.ui.map

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.proyectoelectivai.R
import com.example.proyectoelectivai.data.model.Place
import com.example.proyectoelectivai.data.model.TouristPlaceType

/**
 * Adaptador reactivo para lista de lugares
 * Usa ListAdapter con DiffUtil para actualizaciones eficientes
 */
class PlacesAdapter(
    private val onPlaceClick: (Place) -> Unit,
    private val onShowOnMapClick: (Place) -> Unit
) : ListAdapter<Place, PlacesAdapter.PlaceViewHolder>(PlaceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_place, parent, false)
        return PlaceViewHolder(view, onPlaceClick, onShowOnMapClick)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PlaceViewHolder(
        itemView: View,
        private val onPlaceClick: (Place) -> Unit,
        private val onShowOnMapClick: (Place) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val ivPlaceIcon: ImageView = itemView.findViewById(R.id.ivPlaceIcon)
        private val tvPlaceName: TextView = itemView.findViewById(R.id.tvPlaceName)
        private val tvPlaceType: TextView = itemView.findViewById(R.id.tvPlaceType)
        private val tvPlaceDescription: TextView = itemView.findViewById(R.id.tvPlaceDescription)
        private val tvPlaceAddress: TextView = itemView.findViewById(R.id.tvPlaceAddress)
        private val btnShowOnMap: ImageButton = itemView.findViewById(R.id.btnShowOnMap)

        fun bind(place: Place) {
            tvPlaceName.text = place.name
            tvPlaceType.text = getDisplayTypeName(place.type)
            
            // Descripción
            if (!place.description.isNullOrBlank()) {
                tvPlaceDescription.text = place.description
                tvPlaceDescription.visibility = View.VISIBLE
            } else {
                tvPlaceDescription.visibility = View.GONE
            }
            
            // Dirección
            if (!place.address.isNullOrBlank()) {
                tvPlaceAddress.text = place.address
                tvPlaceAddress.visibility = View.VISIBLE
            } else {
                tvPlaceAddress.visibility = View.GONE
            }
            
            // Icono según tipo
            val iconRes = getIconForType(place.type)
            ivPlaceIcon.setImageResource(iconRes)
            
            // Clicks
            itemView.setOnClickListener { onPlaceClick(place) }
            btnShowOnMap.setOnClickListener { onShowOnMapClick(place) }
        }
        
        private fun getDisplayTypeName(type: String): String {
            return when (type) {
                "museum" -> "MUSEO"
                "monument" -> "MONUMENTO"
                "attraction" -> "ATRACCIÓN"
                "artwork" -> "OBRA DE ARTE"
                "viewpoint" -> "MIRADOR"
                "park" -> "PARQUE"
                "gallery" -> "GALERÍA"
                "zoo" -> "ZOOLÓGICO"
                "theme_park" -> "PARQUE TEMÁTICO"
                "statue" -> "ESTATUA"
                "castle" -> "CASTILLO"
                "ruins" -> "RUINAS"
                "city" -> "CIUDAD"
                "town" -> "PUEBLO"
                "village" -> "VILLA"
                "hamlet" -> "ALDEA"
                else -> type.uppercase()
            }
        }
        
        private fun getIconForType(type: String): Int {
            return when (type) {
                "museum", "gallery" -> R.drawable.ic_museum
                "monument", "statue", "artwork" -> R.drawable.ic_monument
                "park", "viewpoint" -> R.drawable.ic_park
                "attraction", "zoo", "theme_park", "castle", "ruins" -> R.drawable.ic_attraction
                "city", "town", "village", "hamlet" -> R.drawable.ic_location
                else -> R.drawable.ic_location
            }
        }
    }

    /**
     * DiffUtil callback para actualizaciones eficientes
     */
    private class PlaceDiffCallback : DiffUtil.ItemCallback<Place>() {
        override fun areItemsTheSame(oldItem: Place, newItem: Place): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Place, newItem: Place): Boolean {
            return oldItem == newItem
        }
    }
}

