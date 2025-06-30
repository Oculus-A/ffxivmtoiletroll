package com.example.ffxivmtoiletroll
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ffxivmtoiletroll.databinding.ItemRuleBinding

class ConditionAdapter(
    private var items: List<Condition>,
    private val getCaptureRuleName: (String) -> String,
    private val onItemClick: (Condition) -> Unit,
    private val onItemLongClick: (Condition) -> Unit
) : RecyclerView.Adapter<ConditionAdapter.ViewHolder>() {
    class ViewHolder(val binding: ItemRuleBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val ruleName = getCaptureRuleName(item.captureRuleId)
        holder.binding.textViewRuleName.text = if (item.isMet) "当 [${ruleName}] 成功" else "当 [${ruleName}] 失败"
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { onItemLongClick(item); true }
    }
    override fun getItemCount() = items.size
    fun updateData(newItems: List<Condition>) { items = newItems; notifyDataSetChanged() }
}
