package org.aarchdroid.dragonterminal.ui.pm.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import org.aarchdroid.dragonterminal.util.SortedListAdapter
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.ui.pm.adapter.holder.PackageViewHolder
import org.aarchdroid.dragonterminal.ui.pm.model.PackageModel
import java.util.*

class PackageAdapter(context: Context, comparator: Comparator<PackageModel>, private val listener: PackageAdapter.Listener) : SortedListAdapter<PackageModel>(context, PackageModel::class.java, comparator), FastScrollRecyclerView.SectionedAdapter {

    override fun getSectionName(position: Int): String {
        return getItem(position).packageInfo.packageName?.substring(0, 1) ?: "#"
    }

    interface Listener {
        fun onModelClicked(model: PackageModel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SortedListAdapter.ViewHolder<PackageModel> {
        val rootView = LayoutInflater.from(parent.context).inflate(R.layout.item_package, parent, false)
        return PackageViewHolder(rootView, listener)
    }
}
