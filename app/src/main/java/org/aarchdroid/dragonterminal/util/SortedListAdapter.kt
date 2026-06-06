package org.aarchdroid.dragonterminal.util

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import androidx.recyclerview.widget.SortedListAdapterCallback

abstract class SortedListAdapter<T : SortedListAdapter.ViewModel>(
    context: Context,
    modelClass: Class<T>,
    comparator: Comparator<T>
) : RecyclerView.Adapter<SortedListAdapter.ViewHolder<T>>() {

    private val sortedList = SortedList<T>(modelClass, object : SortedListAdapterCallback<T>(this) {
        override fun compare(o1: T, o2: T): Int = comparator.compare(o1, o2)

        override fun areItemsTheSame(item1: T, item2: T): Boolean = item1.isSameModelAs(item2)

        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = oldItem.isContentTheSameAs(newItem)
    })

    private val callbacks = mutableListOf<Callback>()

    fun addCallback(callback: Callback) {
        callbacks.add(callback)
    }

    fun removeCallback(callback: Callback) {
        callbacks.remove(callback)
    }

    override fun getItemCount(): Int = sortedList.size()

    fun getItem(position: Int): T = sortedList[position]

    fun edit(): EditableList {
        return EditableList(this)
    }

    override fun onBindViewHolder(holder: ViewHolder<T>, position: Int) {
        holder.performBind(getItem(position))
    }

    abstract class ViewHolder<T>(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        abstract fun performBind(item: T)
    }

    interface ViewModel {
        fun <T> isSameModelAs(t: T): Boolean
        fun <T> isContentTheSameAs(t: T): Boolean
    }

    interface Callback {
        fun onEditStarted() {}
        fun onEditFinished() {}
    }

    class EditableList(private val adapter: SortedListAdapter<*>) {
        fun <T : ViewModel> replaceAll(items: List<T>): EditableList {
            @Suppress("UNCHECKED_CAST")
            val sortedList = adapter.sortedList as SortedList<T>
            sortedList.beginBatchedUpdates()
            sortedList.clear()
            sortedList.addAll(items)
            sortedList.endBatchedUpdates()
            return this
        }

        fun <T : ViewModel> add(items: List<T>): EditableList {
            @Suppress("UNCHECKED_CAST")
            val sortedList = adapter.sortedList as SortedList<T>
            sortedList.beginBatchedUpdates()
            sortedList.addAll(items)
            sortedList.endBatchedUpdates()
            return this
        }

        fun commit() {
            adapter.callbacks.forEach { it.onEditFinished() }
        }
    }

    class ComparatorBuilder<T> {
        private val orders = mutableListOf<Order<*, *>>()

        fun <M : T> setOrderForModel(modelClass: Class<M>, comparator: Comparator<in M>): ComparatorBuilder<T> {
            @Suppress("UNCHECKED_CAST")
            orders.add(Order<M, Any>(modelClass, comparator as Comparator<Any>))
            return this
        }

        fun build(): Comparator<T> {
            return Comparator { a, b ->
                for (order in orders) {
                    if (order.modelClass.isInstance(a) && order.modelClass.isInstance(b)) {
                        @Suppress("UNCHECKED_CAST")
                        val cmp = (order.comparator as Comparator<Any>).compare(a, b)
                        if (cmp != 0) return@Comparator cmp
                    }
                }
                0
            }
        }

        private class Order<M, T>(val modelClass: Class<M>, val comparator: Comparator<in T>)
    }
}
