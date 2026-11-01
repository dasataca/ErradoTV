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
    private val onItemFocus: ((position: Int, total: Int) -> Unit)? = null,
    private val onUpPressed: (() -> Boolean)? = null,
    private val onDownAtEnd: (() -> Boolean)? = null,
    private val isFavorite: ((EventItem) -> Boolean)? = null,
    private val onItemLongClick: ((EventItem) -> Unit)? = null
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
        return EventViewHolder(view, onItemClick, onLeftPressed, onItemFocus, onUpPressed, onDownAtEnd, isFavorite, onItemLongClick)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(items[position], position, items.size)
    }

    override fun getItemCount(): Int = items.size

    class EventViewHolder(
        itemView: View,
        private val onItemClick: (EventItem) -> Unit,
        private val onLeftPressed: (() -> Boolean)? = null,
        private val onItemFocus: ((position: Int, total: Int) -> Unit)? = null,
        private val onUpPressed: (() -> Boolean)? = null,
        private val onDownAtEnd: (() -> Boolean)? = null,
        private val isFavorite: ((EventItem) -> Boolean)? = null,
        private val onItemLongClick: ((EventItem) -> Unit)? = null
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val llChannelActions: View? = itemView.findViewById(R.id.llChannelActions)
        private val llMatchStatus: View? = itemView.findViewById(R.id.llMatchStatus)
        private val tvChannelStreamBadge: TextView? = itemView.findViewById(R.id.tvChannelStreamBadge)
        private val tvStreamBadge: TextView = itemView.findViewById(R.id.tvStreamBadge)
        private val tvCountdown: TextView = itemView.findViewById(R.id.tvCountdown)
        private val btnCardFavorite: TextView? = itemView.findViewById(R.id.btnCardFavorite)
        private val btnCardPlay: TextView? = itemView.findViewById(R.id.btnCardPlay)

        private var boundPosition: Int = -1
        private var boundTotal: Int = -1
        private var boundItem: EventItem? = null

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

            itemView.setOnLongClickListener {
                if (llChannelActions?.visibility == View.VISIBLE) {
                    boundItem?.let {
                        onItemLongClick?.invoke(it)
                        true
                    } ?: false
                } else {
                    false
                }
            }

            btnCardFavorite?.setOnClickListener {
                boundItem?.let { item ->
                    onItemLongClick?.invoke(item)
                }
            }

            btnCardPlay?.setOnClickListener {
                boundItem?.let { item ->
                    onItemClick(item)
                }
            }

            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (onLeftPressed != null) {
                            return@setOnKeyListener onLeftPressed.invoke()
                        }
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (llChannelActions?.visibility == View.VISIBLE) {
                            btnCardFavorite?.let {
                                it.requestFocus()
                                return@setOnKeyListener true
                            }
                        }
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP && boundPosition == 0) {
                        if (onUpPressed != null) {
                            return@setOnKeyListener onUpPressed.invoke()
                        }
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN && boundPosition == boundTotal - 1 && boundTotal > 1) {
                        if (onDownAtEnd != null) {
                            return@setOnKeyListener onDownAtEnd.invoke()
                        }
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_PROG_YELLOW || keyCode == android.view.KeyEvent.KEYCODE_BUTTON_Y) {
                        if (llChannelActions?.visibility == View.VISIBLE) {
                            boundItem?.let {
                                onItemLongClick?.invoke(it)
                                return@setOnKeyListener true
                            }
                        }
                    }
                }
                false
            }

            btnCardFavorite?.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                        itemView.requestFocus()
                        return@setOnKeyListener true
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                        btnCardPlay?.let {
                            it.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                }
                false
            }

            btnCardPlay?.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                        btnCardFavorite?.let {
                            it.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                }
                false
            }
        }

        fun bind(item: EventItem, position: Int, total: Int) {
            boundItem = item
            boundPosition = position
            boundTotal = total
            tvTitle.text = item.title
            tvSubtitle.text = item.subtitle

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

            val isMatch = item.category == "Partidos" || item.category == "Repeticiones" ||
                           item.category == "⚽ Partidos del Día" || item.category == "🔁 Repeticiones" ||
                           item.status == EventStatus.LIVE || item.status == EventStatus.STARTING_SOON ||
                           item.status == EventStatus.SCHEDULED || item.status == EventStatus.FINISHED ||
                           item.status == EventStatus.REPLAY

            if (isMatch) {
                // Bloque de Partidos: Interfaz original sin favoritos ni botones Play
                llChannelActions?.visibility = View.GONE
                llMatchStatus?.visibility = View.VISIBLE

                val displayTime = item.localTime ?: item.time
                if (displayTime != null) {
                    tvTime.visibility = View.VISIBLE
                    tvTime.text = displayTime
                } else {
                    tvTime.visibility = View.GONE
                }

                if (!item.countdownText.isNullOrEmpty() &&
                    item.countdownText != item.status.label &&
                    item.countdownText != "PROGRAMADO" &&
                    item.status != EventStatus.FINISHED &&
                    item.status != EventStatus.REPLAY_UNAVAILABLE) {
                    tvCountdown.visibility = View.VISIBLE
                    tvCountdown.text = item.countdownText
                } else {
                    tvCountdown.visibility = View.GONE
                }

                val effectiveStatus = if (item.status == EventStatus.REPLAY_UNAVAILABLE && item.originalStatus != EventStatus.REPLAY_UNAVAILABLE) {
                    item.originalStatus
                } else {
                    item.status
                }

                tvStreamBadge.text = effectiveStatus.label
                val badgeDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8f
                    setColor(Color.parseColor(effectiveStatus.colorHex))
                }
                tvStreamBadge.background = badgeDrawable

                val isUnavailable = item.status == EventStatus.REPLAY_UNAVAILABLE || effectiveStatus == EventStatus.REPLAY_UNAVAILABLE
                itemView.alpha = if (isUnavailable || effectiveStatus == EventStatus.FINISHED) 0.5f else 1.0f
            } else {
                // Bloque de Canales (TV Mundial, Regionales, Deportes 24/7): Distintivo compacto, Fav y Play
                llMatchStatus?.visibility = View.GONE
                llChannelActions?.visibility = View.VISIBLE
                tvTime.visibility = View.GONE
                tvCountdown.visibility = View.GONE

                val isFav = isFavorite?.invoke(item) == true
                btnCardFavorite?.text = "★"
                btnCardFavorite?.setTextColor(if (isFav) Color.parseColor("#FFD700") else Color.parseColor("#64748B"))

                val badgeText: String
                val badgeColor: String
                if (item.status == EventStatus.REPLAY_UNAVAILABLE) {
                    badgeText = "⚪ NO DISP."
                    badgeColor = "#6B7280"
                } else {
                    when (item.streamType.lowercase()) {
                        "exo" -> {
                            badgeText = "⚡ HLS"
                            badgeColor = "#059669"
                        }
                        "youtube" -> {
                            badgeText = "🔴 YouTube"
                            badgeColor = "#DC2626"
                        }
                        "web" -> {
                            badgeText = "🔵 Web"
                            badgeColor = "#2563EB"
                        }
                        else -> {
                            badgeText = "🌐 Web"
                            badgeColor = "#7C3AED"
                        }
                    }
                }

                tvChannelStreamBadge?.text = badgeText
                val channelBadgeDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8f
                    setColor(Color.parseColor(badgeColor))
                }
                tvChannelStreamBadge?.background = channelBadgeDrawable

                val isUnavailable = item.status == EventStatus.REPLAY_UNAVAILABLE
                itemView.alpha = if (isUnavailable) 0.5f else 1.0f
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
