package com.example.ffxivmtoiletroll
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ffxivmtoiletroll.databinding.ItemRuleBinding

class RuleAdapter<T>(
    private var items: List<T>,
    private val nameExtractor: (T) -> String,
    private val onItemClick: (T) -> Unit,
    private val onItemLongClick: (T) -> Unit
) : RecyclerView.Adapter<RuleAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemRuleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.textViewRuleName.text = nameExtractor(item)
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { onItemLongClick(item); true }
    }

    override fun getItemCount() = items.size
    fun updateData(newItems: List<T>) { items = newItems; notifyDataSetChanged() }
}
