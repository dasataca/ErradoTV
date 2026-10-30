package com.gareca.tv

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class NavItem(
    val title: String,
    val count: Int = 0
)

class SidebarCategoryAdapter(
    initialItems: List<NavItem>,
    private var selectedCategory: String,
    private val onCategorySelected: (String) -> Unit,
    private val onRightPressed: (() -> Boolean)? = null,
    private val onLeftPressed: (() -> Boolean)? = null,
    private val onUpFromFirst: (() -> Boolean)? = null
) : RecyclerView.Adapter<SidebarCategoryAdapter.SidebarViewHolder>() {

    private val items = initialItems.toMutableList()

    fun setItems(newItems: List<NavItem>, newSelected: String) {
        items.clear()
        items.addAll(newItems)
        selectedCategory = newSelected
        notifyDataSetChanged()
    }

    fun setSelectedCategory(category: String) {
        if (selectedCategory != category) {
            val oldIndex = items.indexOfFirst { it.title == selectedCategory }
            val newIndex = items.indexOfFirst { it.title == category }
            selectedCategory = category
            if (oldIndex != -1) notifyItemChanged(oldIndex)
            if (newIndex != -1) notifyItemChanged(newIndex)
        }
    }

    fun getSelectedPosition(): Int = items.indexOfFirst { it.title == selectedCategory }

    fun getSelectedCategory(): String = selectedCategory

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SidebarViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sidebar_category, parent, false)
        return SidebarViewHolder(view)
    }

    override fun onBindViewHolder(holder: SidebarViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class SidebarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvSidebarCategoryTitle)
        private val tvCount: TextView = itemView.findViewById(R.id.tvSidebarCategoryCount)
        private val viewAccentBar: View = itemView.findViewById(R.id.viewAccentBar)

        fun bind(item: NavItem, position: Int) {
            tvTitle.text = item.title
            if (item.count > 0) {
                tvCount.visibility = View.VISIBLE
                tvCount.text = item.count.toString()
            } else {
                tvCount.visibility = View.GONE
            }

            val isSelected = item.title == selectedCategory
            itemView.isSelected = isSelected
            viewAccentBar.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE

            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).start()
                    viewAccentBar.visibility = View.VISIBLE
                    if (selectedCategory != item.title) {
                        setSelectedCategory(item.title)
                        onCategorySelected(item.title)
                    }
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    viewAccentBar.visibility = if (item.title == selectedCategory) View.VISIBLE else View.INVISIBLE
                }
            }

            itemView.setOnClickListener {
                setSelectedCategory(item.title)
                onCategorySelected(item.title)
                itemView.post {
                    onRightPressed?.invoke()
                }
            }

            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (onRightPressed != null) {
                                return@setOnKeyListener onRightPressed.invoke()
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (onLeftPressed != null) {
                                return@setOnKeyListener onLeftPressed.invoke()
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            setSelectedCategory(item.title)
                            onCategorySelected(item.title)
                            itemView.post {
                                onRightPressed?.invoke()
                            }
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (position == 0 && onUpFromFirst != null) {
                                return@setOnKeyListener onUpFromFirst.invoke()
                            }
                        }
                    }
                }
                false
            }
        }
    }
}
