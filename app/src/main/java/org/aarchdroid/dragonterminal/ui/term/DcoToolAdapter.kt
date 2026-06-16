package org.aarchdroid.dragonterminal.ui.term

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.aarchdroid.R

data class DcoToolItem(
    val key: String,
    val displayName: String,
    val description: String,
    val drawableName: String,
    val cmd: String
)

class DcoToolAdapter(
    private var items: List<DcoToolItem>,
    private val onLaunch: (DcoToolItem) -> Unit
) : RecyclerView.Adapter<DcoToolAdapter.Holder>() {

    fun update(newItems: List<DcoToolItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tool_card, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val item = items[position]
        h.title.text = item.displayName
        h.desc.text = item.description
        val resId = h.itemView.context.resources.getIdentifier(
            item.drawableName, "drawable", h.itemView.context.packageName
        )
        if (resId != 0) h.icon.setImageResource(resId)
        h.launchBtn.setOnClickListener { onLaunch(item) }
        h.itemView.setOnClickListener { onLaunch(item) }
    }

    override fun getItemCount(): Int = items.size

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val title: TextView = v.findViewById(R.id.title)
        val desc: TextView = v.findViewById(R.id.description)
        val launchBtn: ImageView = v.findViewById(R.id.install_btn)
    }
}
