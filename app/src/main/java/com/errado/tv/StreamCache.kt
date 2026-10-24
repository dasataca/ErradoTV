package com.errado.tv

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

object StreamCache {
    private val resolutionMap = ConcurrentHashMap<String, String>()
    private val unavailableUrls = ConcurrentHashMap.newKeySet<String>()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("stream_cache_prefs", Context.MODE_PRIVATE)
        val savedDead = prefs.getStringSet("dead_urls", emptySet()) ?: emptySet()
        unavailableUrls.addAll(savedDead)
    }

    fun setResolution(url: String, resolution: String) {
        if (url.isNotEmpty() && resolution.isNotEmpty()) {
            resolutionMap[url] = resolution
        }
    }

    fun getResolution(url: String): String? {
        return resolutionMap[url]
    }

    fun markUnavailable(
        context: Context?,
        url: String,
        reason: String = "error",
        title: String? = null,
        broadcast: Boolean = true
    ) {
        if (url.isEmpty()) return
        val isNew = unavailableUrls.add(url)
        context?.let {
            try {
                val prefs = it.getSharedPreferences("stream_cache_prefs", Context.MODE_PRIVATE)
                val set = prefs.getStringSet("dead_urls", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                set.add(url)
                prefs.edit().putStringSet("dead_urls", set).apply()
            } catch (_: Exception) {}
        }
        if (isNew && broadcast) {
            CommunityStreamManager.reportUnavailable(url, reason, title)
        }
    }

    fun isUnavailable(url: String): Boolean {
        return unavailableUrls.contains(url)
    }
}
