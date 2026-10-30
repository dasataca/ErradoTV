package com.gareca.tv

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SidebarCategoryAdapter(
    initialCategories: List<String>,
    private var selectedCategory: String,
    private val onCategorySelected: (String) -> Unit,
    private val onRightPressed: (() -> Boolean)? = null,
    private val onUpFromFirst: (() -> Boolean)? = null
) : RecyclerView.Adapter<SidebarCategoryAdapter.SidebarViewHolder>() {

    private val categories = initialCategories.toMutableList()

    fun setCategories(newCategories: List<String>, newSelected: String) {
        categories.clear()
        categories.addAll(newCategories)
        selectedCategory = newSelected
        notifyDataSetChanged()
    }

    fun setSelectedCategory(category: String) {
        if (selectedCategory != category) {
            val oldIndex = categories.indexOf(selectedCategory)
            val newIndex = categories.indexOf(category)
            selectedCategory = category
            if (oldIndex != -1) notifyItemChanged(oldIndex)
            if (newIndex != -1) notifyItemChanged(newIndex)
        }
    }

    fun getSelectedPosition(): Int = categories.indexOf(selectedCategory)

    fun getSelectedCategory(): String = selectedCategory

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SidebarViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sidebar_category, parent, false)
        return SidebarViewHolder(view)
    }

    override fun onBindViewHolder(holder: SidebarViewHolder, position: Int) {
        holder.bind(categories[position], position)
    }

    override fun getItemCount(): Int = categories.size

    inner class SidebarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvSidebarCategoryTitle)

        fun bind(category: String, position: Int) {
            tvTitle.text = category
            val isSelected = category == selectedCategory
            itemView.isSelected = isSelected

            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
                    if (selectedCategory != category) {
                        setSelectedCategory(category)
                        onCategorySelected(category)
                    }
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }

            itemView.setOnClickListener {
                setSelectedCategory(category)
                onCategorySelected(category)
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
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            setSelectedCategory(category)
                            onCategorySelected(category)
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
