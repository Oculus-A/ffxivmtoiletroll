package com.example.ffxivmtoiletroll
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ffxivmtoiletroll.databinding.ItemRuleBinding

class ActionAdapter(
    private var items: List<Action>,
    // (新增) 传入 ResourceManager 用于检查资源是否存在
    private val resourceManager: ResourceManager,
    private val onItemClick: (Action) -> Unit,
    private val onItemLongClick: (Action) -> Unit
) : RecyclerView.Adapter<ActionAdapter.ViewHolder>() {
    class ViewHolder(val binding: ItemRuleBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        // (已修改) 根据新的数据结构和资源存在性来生成显示文本
        holder.binding.textViewRuleName.text = formatActionText(item)
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { onItemLongClick(item); true }
    }
    override fun getItemCount() = items.size
    fun updateData(newItems: List<Action>) { items = newItems; notifyDataSetChanged() }

    // (新增) 格式化动作文本的辅助方法
    private fun formatActionText(action: Action): String {
        return when (action) {
            is Action.ShowImage -> {
                val details = action.details
                val identifier = details.image
                // 检查资源是否存在，如果不存在则添加警告
                val warning = if (resourceManager.loadBitmap(identifier) == null) " ⚠️资源丢失" else ""
                val name = if (identifier.type == ResourceType.BUILT_IN) identifier.path.removePrefix("display_") else identifier.path
                "显示图片: ${name} at (${details.positionX}, ${details.positionY})$warning"
            }
            is Action.PlaySound -> {
                val details = action.details
                val identifier = details.sound
                // 检查资源是否存在
                val fileExists = when (identifier.type) {
                    ResourceType.BUILT_IN -> true // 假设内置资源总是存在
                    ResourceType.USER -> resourceManager.getSoundFile(identifier)?.exists() ?: false
                }
                val warning = if (!fileExists) " ⚠️资源丢失" else ""
                "播放声音: ${identifier.path}$warning"
            }
        }
    }
}
