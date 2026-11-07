package com.gareca.tv

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SubcategoryAdapter(
    initialItems: List<NavItem>,
    private var selectedSubcategory: String,
    private val onSubcategoryClick: (String) -> Unit,
    private val onDownPressed: (() -> Boolean)? = null,
    private val onLeftFromFirst: (() -> Boolean)? = null,
    private val onUpPressed: (() -> Boolean)? = null
) : RecyclerView.Adapter<SubcategoryAdapter.SubcategoryViewHolder>() {

    private val items = initialItems.toMutableList()

    fun setItems(newItems: List<NavItem>, newSelected: String, rv: RecyclerView? = null) {
        val sameItems = items.size == newItems.size && items.indices.all { i ->
            items[i].title == newItems[i].title && items[i].count == newItems[i].count
        }
        if (sameItems) {
            setSelected(newSelected, rv)
            return
        }
        items.clear()
        items.addAll(newItems)
        selectedSubcategory = newSelected
        notifyDataSetChanged()
    }

    fun setSelected(subcategory: String, rv: RecyclerView? = null) {
        if (selectedSubcategory != subcategory) {
            val oldIndex = items.indexOfFirst { it.title == selectedSubcategory }
            val newIndex = items.indexOfFirst { it.title == subcategory }
            selectedSubcategory = subcategory
            if (rv != null) {
                if (oldIndex != -1) rv.findViewHolderForAdapterPosition(oldIndex)?.itemView?.isSelected = false
                if (newIndex != -1) rv.findViewHolderForAdapterPosition(newIndex)?.itemView?.isSelected = true
            } else {
                if (oldIndex != -1) notifyItemChanged(oldIndex)
                if (newIndex != -1) notifyItemChanged(newIndex)
            }
        }
    }

    fun getSelected(): String = selectedSubcategory

    fun getSelectedPosition(): Int {
        val idx = items.indexOfFirst { it.title == selectedSubcategory }
        return if (idx >= 0) idx else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubcategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_subcategory_tab, parent, false)
        return SubcategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubcategoryViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class SubcategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvSubcategoryTitle)
        private val tvCount: TextView = itemView.findViewById(R.id.tvSubcategoryCount)

        fun bind(item: NavItem, position: Int) {
            tvTitle.text = item.title
            if (item.count > 0) {
                tvCount.visibility = View.VISIBLE
                tvCount.text = item.count.toString()
            } else {
                tvCount.visibility = View.GONE
            }

            val isSelected = item.title == selectedSubcategory
            itemView.isSelected = isSelected

            itemView.setOnClickListener {
                val rv = itemView.parent as? RecyclerView
                setSelected(item.title, rv)
                onSubcategoryClick(item.title)
            }

            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            return@setOnKeyListener onDownPressed?.invoke() ?: false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (position == 0 && onLeftFromFirst != null) {
                                return@setOnKeyListener onLeftFromFirst.invoke()
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (onUpPressed != null) {
                                return@setOnKeyListener onUpPressed.invoke()
                            }
                        }
                    }
                }
                false
            }
        }
    }
}
