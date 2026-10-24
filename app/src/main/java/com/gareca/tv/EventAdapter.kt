package com.gareca.tv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.bumptech.glide.Glide
import com.gareca.tv.model.EventItem
import com.gareca.tv.model.EventStatus

class EventAdapter(
    private val onItemClick: (EventItem) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    private val items = mutableListOf<EventItem>()

    fun submitList(newItems: List<EventItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event_card, parent, false)
        return EventViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class EventViewHolder(
        itemView: View,
        private val onItemClick: (EventItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val tvStreamBadge: TextView = itemView.findViewById(R.id.tvStreamBadge)
        private val tvCountdown: TextView = itemView.findViewById(R.id.tvCountdown)

        init {
            // TV focus animation
            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).start()
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
            }
        }

        fun bind(item: EventItem) {
            tvTitle.text = item.title
            tvSubtitle.text = item.subtitle

            val displayTime = item.localTime ?: item.time
            if (displayTime != null) {
                tvTime.visibility = View.VISIBLE
                tvTime.text = displayTime
            } else {
                tvTime.visibility = View.GONE
            }

            val hasTimeInfo = displayTime != null
            if (hasTimeInfo && !item.countdownText.isNullOrEmpty() &&
                item.countdownText != item.status.label &&
                item.countdownText != "PROGRAMADO" &&
                item.status != EventStatus.CHANNEL_24_7 &&
                item.status != EventStatus.REPLAY &&
                item.status != EventStatus.REPLAY_UNAVAILABLE) {
                tvCountdown.visibility = View.VISIBLE
                tvCountdown.text = item.countdownText
            } else {
                tvCountdown.visibility = View.GONE
            }

            if (!item.imageUrl.isNullOrEmpty()) {
                ivThumbnail.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(item.imageUrl)
                    .centerCrop()
                    .into(ivThumbnail)
            } else {
                ivThumbnail.visibility = View.GONE
            }

            // Configurar insignia de estado dinámico (EN VIVO, POR EMPEZAR, PROGRAMADO, FINALIZADO)
            tvStreamBadge.text = item.status.label
            val badgeDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f
                setColor(Color.parseColor(item.status.colorHex))
            }
            tvStreamBadge.background = badgeDrawable

            // Si el partido ya finalizó o no está disponible, atenuar la tarjeta
            itemView.alpha = if (item.status == EventStatus.FINISHED || item.status == EventStatus.REPLAY_UNAVAILABLE) 0.5f else 1.0f

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
