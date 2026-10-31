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
    private val onItemClick: (EventItem) -> Unit,
    private val onLeftPressed: (() -> Boolean)? = null,
    private val onItemFocus: ((position: Int, total: Int) -> Unit)? = null
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    private val items = mutableListOf<EventItem>()

    val currentList: List<EventItem>
        get() = items

    fun submitList(newItems: List<EventItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event_card, parent, false)
        return EventViewHolder(view, onItemClick, onLeftPressed, onItemFocus)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(items[position], position, items.size)
    }

    override fun getItemCount(): Int = items.size

    class EventViewHolder(
        itemView: View,
        private val onItemClick: (EventItem) -> Unit,
        private val onLeftPressed: (() -> Boolean)? = null,
        private val onItemFocus: ((position: Int, total: Int) -> Unit)? = null
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val tvStreamBadge: TextView = itemView.findViewById(R.id.tvStreamBadge)
        private val tvCountdown: TextView = itemView.findViewById(R.id.tvCountdown)

        private var boundPosition: Int = -1
        private var boundTotal: Int = -1

        init {
            // TV focus animation
            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).start()
                    if (boundPosition >= 0 && boundTotal > 0) {
                        onItemFocus?.invoke(boundPosition, boundTotal)
                    }
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
            }

            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (onLeftPressed != null) {
                            return@setOnKeyListener onLeftPressed.invoke()
                        }
                    }
                }
                false
            }
        }

        fun bind(item: EventItem, position: Int, total: Int) {
            boundPosition = position
            boundTotal = total
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
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .override(160, 90)
                    .centerCrop()
                    .into(ivThumbnail)
            } else {
                ivThumbnail.visibility = View.GONE
            }

            val is247 = item.status == EventStatus.CHANNEL_24_7 || item.originalStatus == EventStatus.CHANNEL_24_7 || item.category != "Partidos"
            val effectiveStatus = if (is247 && item.status == EventStatus.REPLAY_UNAVAILABLE) {
                if (item.originalStatus != EventStatus.REPLAY_UNAVAILABLE) item.originalStatus else EventStatus.CHANNEL_24_7
            } else {
                item.status
            }

            // Distintivos explícitos para canales y eventos
            val isChannel = item.country != null || item.status == EventStatus.CHANNEL_24_7 || item.category != "Partidos"
            val badgeText: String
            val badgeColor: String

            if (item.status == EventStatus.REPLAY_UNAVAILABLE) {
                badgeText = "⚪ NO DISPONIBLE"
                badgeColor = "#6B7280"
            } else if (isChannel) {
                when (item.streamType.lowercase()) {
                    "exo" -> {
                        badgeText = "⚡ EXOPLAYER HLS"
                        badgeColor = "#059669"
                    }
                    "youtube" -> {
                        badgeText = "🔴 YOUTUBE LIVE"
                        badgeColor = "#DC2626"
                    }
                    "web" -> {
                        badgeText = "🔵 WEB HTML5"
                        badgeColor = "#2563EB"
                    }
                    else -> {
                        badgeText = "🌐 WEB EXTERNA"
                        badgeColor = "#7C3AED"
                    }
                }
            } else {
                badgeText = effectiveStatus.label
                badgeColor = effectiveStatus.colorHex
            }

            tvStreamBadge.text = badgeText
            val badgeDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f
                setColor(Color.parseColor(badgeColor))
            }
            tvStreamBadge.background = badgeDrawable

            // Si el canal/partido no está disponible o finalizó, atenuar visualmente
            val isUnavailable = item.status == EventStatus.REPLAY_UNAVAILABLE || effectiveStatus == EventStatus.REPLAY_UNAVAILABLE
            itemView.alpha = if (isUnavailable || (!is247 && effectiveStatus == EventStatus.FINISHED)) 0.5f else 1.0f

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
