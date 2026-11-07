package com.gareca.tv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.bumptech.glide.Glide
import com.gareca.tv.model.EventItem
import com.gareca.tv.model.EventStatus

object EventDiffCallback : DiffUtil.ItemCallback<EventItem>() {
    override fun areItemsTheSame(oldItem: EventItem, newItem: EventItem): Boolean {
        val oldStreamUrl = oldItem.streams.firstOrNull()?.url
        val newStreamUrl = newItem.streams.firstOrNull()?.url
        return if (!oldStreamUrl.isNullOrEmpty() && !newStreamUrl.isNullOrEmpty()) {
            oldStreamUrl == newStreamUrl && oldItem.title == newItem.title
        } else {
            oldItem.title == newItem.title && oldItem.category == newItem.category && oldItem.time == newItem.time
        }
    }

    override fun areContentsTheSame(oldItem: EventItem, newItem: EventItem): Boolean {
        return oldItem == newItem
    }
}

class EventAdapter(
    private val onItemClick: (EventItem) -> Unit,
    private val onLeftPressed: (() -> Boolean)? = null,
    private val onItemFocus: ((position: Int, total: Int) -> Unit)? = null,
    private val onUpPressed: (() -> Boolean)? = null,
    private val onDownAtEnd: (() -> Boolean)? = null,
    private val isFavorite: ((EventItem) -> Boolean)? = null,
    private val onItemLongClick: ((EventItem) -> Unit)? = null
) : ListAdapter<EventItem, EventAdapter.EventViewHolder>(EventDiffCallback) {

    override fun submitList(newItems: List<EventItem>?) {
        super.submitList(if (newItems != null) ArrayList(newItems) else null)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event_card, parent, false)
        return EventViewHolder(view, onItemClick, onLeftPressed, onItemFocus, onUpPressed, onDownAtEnd, isFavorite, onItemLongClick)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position), position, itemCount)
    }

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

        companion object {
            private val COLOR_GOLD = Color.parseColor("#FFD700")
            private val COLOR_MUTED = Color.parseColor("#64748B")

            private val badgeColorMap = mapOf(
                EventStatus.LIVE to Color.parseColor(EventStatus.LIVE.colorHex),
                EventStatus.STARTING_SOON to Color.parseColor(EventStatus.STARTING_SOON.colorHex),
                EventStatus.SCHEDULED to Color.parseColor(EventStatus.SCHEDULED.colorHex),
                EventStatus.FINISHED to Color.parseColor(EventStatus.FINISHED.colorHex),
                EventStatus.REPLAY to Color.parseColor(EventStatus.REPLAY.colorHex),
                EventStatus.REPLAY_UNAVAILABLE to Color.parseColor(EventStatus.REPLAY_UNAVAILABLE.colorHex),
                EventStatus.CHANNEL_24_7 to Color.parseColor(EventStatus.CHANNEL_24_7.colorHex)
            )

            private val streamTypeColorMap = mapOf(
                "exo" to Color.parseColor("#059669"),
                "youtube" to Color.parseColor("#DC2626"),
                "web" to Color.parseColor("#2563EB"),
                "other" to Color.parseColor("#7C3AED")
            )

            private val drawableCache = HashMap<Int, GradientDrawable>()

            fun getBadgeDrawable(color: Int): GradientDrawable {
                val cached = drawableCache[color]
                if (cached != null) {
                    return (cached.constantState?.newDrawable()?.mutate() as? GradientDrawable) ?: cached
                }
                val gd = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8f
                    setColor(color)
                }
                drawableCache[color] = gd
                return (gd.constantState?.newDrawable()?.mutate() as? GradientDrawable) ?: gd
            }
        }

        private var boundPosition: Int = -1
        private var boundTotal: Int = -1
        private var boundItem: EventItem? = null

        init {
            // Foco 100% acelerado por hardware GPU mediante card_selector.xml (cero animadores en CPU)
            itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && boundPosition >= 0 && boundTotal > 0) {
                    onItemFocus?.invoke(boundPosition, boundTotal)
                }
            }

            btnCardPlay?.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && boundPosition >= 0 && boundTotal > 0) {
                    onItemFocus?.invoke(boundPosition, boundTotal)
                }
            }

            btnCardFavorite?.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && boundPosition >= 0 && boundTotal > 0) {
                    onItemFocus?.invoke(boundPosition, boundTotal)
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
                            btnCardPlay?.let {
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

            btnCardPlay?.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                        itemView.requestFocus()
                        return@setOnKeyListener true
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                        btnCardFavorite?.let {
                            it.requestFocus()
                            return@setOnKeyListener true
                        }
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN && boundPosition == boundTotal - 1 && boundTotal > 1) {
                        if (onDownAtEnd != null) {
                            return@setOnKeyListener onDownAtEnd.invoke()
                        }
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP && boundPosition == 0) {
                        if (onUpPressed != null) {
                            return@setOnKeyListener onUpPressed.invoke()
                        }
                    }
                }
                false
            }

            btnCardFavorite?.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                        btnCardPlay?.let {
                            it.requestFocus()
                            return@setOnKeyListener true
                        }
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                        return@setOnKeyListener true
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN && boundPosition == boundTotal - 1 && boundTotal > 1) {
                        if (onDownAtEnd != null) {
                            return@setOnKeyListener onDownAtEnd.invoke()
                        }
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP && boundPosition == 0) {
                        if (onUpPressed != null) {
                            return@setOnKeyListener onUpPressed.invoke()
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
            tvSubtitle.text = com.gareca.tv.epg.EpgManager.getFormattedSubtitle(item, item.subtitle)

            if (!item.imageUrl.isNullOrEmpty()) {
                ivThumbnail.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(item.imageUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565)
                    .dontAnimate()
                    .override(120, 68)
                    .centerCrop()
                    .into(ivThumbnail)
            } else {
                Glide.with(itemView.context).clear(ivThumbnail)
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
                val badgeColor = badgeColorMap[effectiveStatus] ?: Color.GRAY
                tvStreamBadge.background = getBadgeDrawable(badgeColor)

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
                btnCardFavorite?.setTextColor(if (isFav) COLOR_GOLD else COLOR_MUTED)

                val badgeText: String
                val streamColor: Int
                if (item.status == EventStatus.REPLAY_UNAVAILABLE) {
                    badgeText = "⚪ NO DISP."
                    streamColor = badgeColorMap[EventStatus.REPLAY_UNAVAILABLE] ?: Color.GRAY
                } else {
                    when (item.streamType.lowercase()) {
                        "exo" -> {
                            badgeText = "⚡ HLS"
                            streamColor = streamTypeColorMap["exo"] ?: Color.GREEN
                        }
                        "youtube" -> {
                            badgeText = "🔴 YouTube"
                            streamColor = streamTypeColorMap["youtube"] ?: Color.RED
                        }
                        "web" -> {
                            badgeText = "🔵 Web"
                            streamColor = streamTypeColorMap["web"] ?: Color.BLUE
                        }
                        else -> {
                            badgeText = "🌐 Web"
                            streamColor = streamTypeColorMap["other"] ?: Color.MAGENTA
                        }
                    }
                }

                tvChannelStreamBadge?.text = badgeText
                tvChannelStreamBadge?.background = getBadgeDrawable(streamColor)

                val isUnavailable = item.status == EventStatus.REPLAY_UNAVAILABLE
                itemView.alpha = if (isUnavailable) 0.5f else 1.0f
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
