package com.example.proyectoelectivai

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.proyectoelectivai.databinding.ItemSuggestionBinding
import java.util.Locale

data class Suggestion(val title: String, val subtitle: String)

class SuggestionAdapter(
    private val original: List<Suggestion>,
    private val onClick: (Suggestion) -> Unit
) : RecyclerView.Adapter<SuggestionAdapter.VH>() {

    private val items = original.toMutableList()

    inner class VH(val b: ItemSuggestionBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSuggestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.b.tvTitle.text = item.title
        holder.b.tvSubtitle.text = item.subtitle
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun filter(text: String) {
        val q = text.lowercase(Locale.getDefault())
        items.clear()
        if (q.isBlank()) {
            items.addAll(original)
        } else {
            items.addAll(
                original.filter {
                    it.title.lowercase(Locale.getDefault()).contains(q) ||
                            it.subtitle.lowercase(Locale.getDefault()).contains(q)
                }
            )
        }
        notifyDataSetChanged()
    }
}
