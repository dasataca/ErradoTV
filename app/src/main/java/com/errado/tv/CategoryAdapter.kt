package com.errado.tv

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CategoryAdapter(
    private val categories: List<String>,
    private var selectedCategory: String,
    private val onCategoryClick: (String) -> Unit,
    private val onDownPressed: ((String) -> Boolean)? = null,
    private val onCategoryFocus: ((String) -> Unit)? = null
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    fun setSelectedCategory(category: String) {
        if (selectedCategory != category) {
            val oldIndex = categories.indexOf(selectedCategory)
            val newIndex = categories.indexOf(category)
            selectedCategory = category
            if (oldIndex != -1) notifyItemChanged(oldIndex)
            if (newIndex != -1) notifyItemChanged(newIndex)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_tab, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val cat = categories[position]
        holder.bind(
            category = cat,
            isSelected = cat == selectedCategory,
            onClick = {
                setSelectedCategory(cat)
                onCategoryClick(cat)
            },
            onFocus = {
                setSelectedCategory(cat)
                (onCategoryFocus ?: onCategoryClick).invoke(cat)
            },
            onDown = onDownPressed
        )
    }

    override fun getItemCount(): Int = categories.size

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTabTitle: TextView = itemView.findViewById(R.id.tvTabTitle)

        fun bind(
            category: String,
            isSelected: Boolean,
            onClick: () -> Unit,
            onFocus: () -> Unit,
            onDown: ((String) -> Boolean)?
        ) {
            tvTabTitle.text = category
            itemView.isSelected = isSelected

            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start()
                    onFocus()
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }

            itemView.setOnClickListener {
                onClick()
            }

            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (onDown != null && onDown(category)) {
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }
    }
}
