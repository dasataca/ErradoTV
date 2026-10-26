package com.gareca.tv

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SuggestionAdapter(
    private val onItemClick: (String) -> Unit,
    private val onUpFromFirst: (() -> Unit)? = null
) : RecyclerView.Adapter<SuggestionAdapter.SuggestionViewHolder>() {

    private var items = listOf<String>()

    fun submitList(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getItem(position: Int): String? = items.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_suggestion, parent, false)
        return SuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val text = items[position]
        holder.tvText.text = text
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = true
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.02f).scaleY(1.02f).setDuration(100).start()
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }
        }
        holder.itemView.setOnClickListener {
            onItemClick(text)
        }
        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && position == 0) {
                    onUpFromFirst?.invoke()
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    override fun getItemCount(): Int = items.size
    fun getItems(): List<String> = items

    class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvText: TextView = view.findViewById(R.id.tvSuggestionText)
    }
}
