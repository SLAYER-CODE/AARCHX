package org.aarchdroid.dragonterminal.ui.pm.adapter.holder

import android.view.View
import android.widget.TextView

import org.aarchdroid.dragonterminal.util.SortedListAdapter

import org.aarchdroid.R
import org.aarchdroid.dragonterminal.ui.pm.adapter.PackageAdapter
import org.aarchdroid.dragonterminal.ui.pm.model.PackageModel

class PackageViewHolder(private val rootView: View, private val listener: PackageAdapter.Listener) : SortedListAdapter.ViewHolder<PackageModel>(rootView) {
    private val packageNameView: TextView = rootView.findViewById(R.id.package_item_name)
    private val packageDescView: TextView = rootView.findViewById(R.id.package_item_desc)

    override fun performBind(item: PackageModel) {
        rootView.setOnClickListener { listener.onModelClicked(item) }
        packageNameView.text = item.packageInfo.packageName
        packageDescView.text = item.packageInfo.description
    }
}
