package com.example.ffxivmtoiletroll
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ffxivmtoiletroll.databinding.ItemRuleBinding

class ActionAdapter(
    private var items: List<Action>,
    private val getResourceNameById: (Int, String) -> String,
    private val onItemClick: (Action) -> Unit,
    private val onItemLongClick: (Action) -> Unit
) : RecyclerView.Adapter<ActionAdapter.ViewHolder>() {
    class ViewHolder(val binding: ItemRuleBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.textViewRuleName.text = when (item) {
            is Action.ShowImage -> {
                val imageName = getResourceNameById(item.details.imageResId, "drawable").removePrefix("display_")
                "显示图片: ${imageName} at (${item.details.positionX}, ${item.details.positionY})"
            }
            is Action.PlaySound -> {
                val soundName = getResourceNameById(item.details.soundResId, "raw")
                "播放声音: ${soundName}"
            }
        }
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { onItemLongClick(item); true }
    }
    override fun getItemCount() = items.size
    fun updateData(newItems: List<Action>) { items = newItems; notifyDataSetChanged() }
}
