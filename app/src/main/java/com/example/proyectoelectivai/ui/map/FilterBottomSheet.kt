package com.example.proyectoelectivai.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.example.proyectoelectivai.R
import com.example.proyectoelectivai.databinding.FragmentFilterBottomSheetBinding

/**
 * Bottom Sheet para filtros de lugares
 * Permite filtrar por tipo de lugar y otros criterios
 */
class FilterBottomSheet : BottomSheetDialogFragment() {
    
    private var _binding: FragmentFilterBottomSheetBinding? = null
    private val binding get() = _binding!!
    
    private var onFiltersChanged: ((Set<String>) -> Unit)? = null
    private var onViewModeChanged: ((ViewMode) -> Unit)? = null
    
    private var selectedTypes = mutableSetOf<String>()
    private var currentViewMode = ViewMode.POINTS
    
    companion object {
        fun newInstance(
            selectedTypes: Set<String> = emptySet(),
            viewMode: ViewMode = ViewMode.POINTS
        ): FilterBottomSheet {
            val fragment = FilterBottomSheet()
            val args = Bundle()
            args.putStringArray("selectedTypes", selectedTypes.toTypedArray())
            args.putString("viewMode", viewMode.name)
            fragment.arguments = args
            return fragment
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            selectedTypes = it.getStringArray("selectedTypes")?.toSet()?.toMutableSet() ?: mutableSetOf()
            currentViewMode = ViewMode.valueOf(it.getString("viewMode") ?: ViewMode.POINTS.name)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilterBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }
    
    private fun setupUI() {
        setupTypeFilters()
        setupViewModeFilters()
        setupButtons()
    }
    
    private fun setupTypeFilters() {
        val typeChips = mapOf(
            "museum" to "Museos",
            "monument" to "Monumentos",
            "attraction" to "Atracciones",
            "park" to "Parques",
            "viewpoint" to "Miradores",
            "gallery" to "Galerías",
            "statue" to "Estatuas",
            "castle" to "Castillos",
            "ruins" to "Ruinas",
            "zoo" to "Zoológicos",
            "theme_park" to "Parques Temáticos",
            "artwork" to "Arte"
        )
        
        typeChips.forEach { (type, label) ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = selectedTypes.contains(type)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedTypes.add(type)
                    } else {
                        selectedTypes.remove(type)
                    }
                }
            }
            binding.chipGroupTypes.addView(chip)
        }
    }
    
    private fun setupViewModeFilters() {
        val viewModeChips = mapOf(
            ViewMode.POINTS to "Puntos",
            ViewMode.CLUSTERS to "Clústeres",
            ViewMode.HEATMAP to "Mapa de Calor"
        )
        
        viewModeChips.forEach { (mode, label) ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = currentViewMode == mode
                setOnClickListener {
                    currentViewMode = mode
                    onViewModeChanged?.invoke(mode)
                    // Actualizar estado de chips
                    for (i in 0 until binding.chipGroupViewMode.childCount) {
                        val childChip = binding.chipGroupViewMode.getChildAt(i) as Chip
                        childChip.isChecked = childChip == this
                    }
                }
            }
            binding.chipGroupViewMode.addView(chip)
        }
    }
    
    private fun setupButtons() {
        binding.btnApplyFilters.setOnClickListener {
            onFiltersChanged?.invoke(selectedTypes)
            dismiss()
        }
        
        binding.btnClearFilters.setOnClickListener {
            selectedTypes.clear()
            currentViewMode = ViewMode.POINTS
            
            // Limpiar chips de tipos
            for (i in 0 until binding.chipGroupTypes.childCount) {
                val chip = binding.chipGroupTypes.getChildAt(i) as Chip
                chip.isChecked = false
            }
            
            // Limpiar chips de modo de vista
            for (i in 0 until binding.chipGroupViewMode.childCount) {
                val chip = binding.chipGroupViewMode.getChildAt(i) as Chip
                chip.isChecked = false
            }
            
            // Seleccionar modo de puntos por defecto
            if (binding.chipGroupViewMode.childCount > 0) {
                val firstChip = binding.chipGroupViewMode.getChildAt(0) as Chip
                firstChip.isChecked = true
            }
            
            onFiltersChanged?.invoke(emptySet())
            onViewModeChanged?.invoke(ViewMode.POINTS)
            dismiss()
        }
    }
    
    fun setOnFiltersChangedListener(listener: (Set<String>) -> Unit) {
        onFiltersChanged = listener
    }
    
    fun setOnViewModeChangedListener(listener: (ViewMode) -> Unit) {
        onViewModeChanged = listener
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
