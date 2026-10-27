package com.gareca.tv

import com.gareca.tv.model.StreamOption

data class ChannelItem(
    val title: String,
    val subtitle: String? = null,
    val stream: StreamOption,
    val imageUrl: String? = null
)

object ChannelNavigationManager {
    private var playlist: List<ChannelItem> = emptyList()
    var currentIndex: Int = -1
        private set

    val hasChannels: Boolean
        get() = playlist.isNotEmpty()

    val totalCount: Int
        get() = playlist.size

    val currentPositionText: String
        get() = if (playlist.isNotEmpty() && currentIndex in playlist.indices) {
            "${currentIndex + 1} de ${playlist.size}"
        } else ""

    fun setPlaylist(items: List<ChannelItem>, initialIndex: Int) {
        playlist = items
        currentIndex = if (initialIndex in items.indices) initialIndex else 0
    }

    fun current(): ChannelItem? {
        return playlist.getOrNull(currentIndex)
    }

    fun next(): ChannelItem? {
        if (playlist.isEmpty()) return null
        currentIndex = (currentIndex + 1) % playlist.size
        return playlist[currentIndex]
    }

    fun previous(): ChannelItem? {
        if (playlist.isEmpty()) return null
        currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
        return playlist[currentIndex]
    }

    fun clear() {
        playlist = emptyList()
        currentIndex = -1
    }
}
