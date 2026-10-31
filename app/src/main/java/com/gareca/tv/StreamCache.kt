package com.gareca.tv

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

object StreamCache {
    private val resolutionMap = ConcurrentHashMap<String, String>()
    private val unavailableUrls = ConcurrentHashMap.newKeySet<String>()
    private var appContext: Context? = null

    fun init(context: Context) {
        val appCtx = context.applicationContext ?: context
        appContext = appCtx

        val prefs = appCtx.getSharedPreferences("stream_cache_prefs", Context.MODE_PRIVATE)
        val savedDead = prefs.getStringSet("dead_urls", emptySet()) ?: emptySet()
        unavailableUrls.addAll(savedDead)

        val resPrefs = appCtx.getSharedPreferences("stream_resolutions_prefs", Context.MODE_PRIVATE)
        resPrefs.all.forEach { (url, res) ->
            if (res is String && url.isNotEmpty() && res.isNotEmpty()) {
                resolutionMap[url] = res
            }
        }
    }

    fun setResolution(url: String, resolution: String, context: Context? = null) {
        if (url.isNotEmpty() && resolution.isNotEmpty()) {
            resolutionMap[url] = resolution
            val targetCtx = context?.applicationContext ?: appContext
            targetCtx?.let {
                try {
                    val resPrefs = it.getSharedPreferences("stream_resolutions_prefs", Context.MODE_PRIVATE)
                    resPrefs.edit().putString(url, resolution).apply()
                } catch (_: Exception) {}
            }
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

    fun markAvailable(context: Context?, url: String) {
        if (url.isEmpty()) return
        unavailableUrls.remove(url)
        context?.let {
            try {
                val prefs = it.getSharedPreferences("stream_cache_prefs", Context.MODE_PRIVATE)
                val set = prefs.getStringSet("dead_urls", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                if (set.remove(url)) {
                    prefs.edit().putStringSet("dead_urls", set).apply()
                }
            } catch (_: Exception) {}
        }
    }

    fun isUnavailable(url: String): Boolean {
        return unavailableUrls.contains(url)
    }
}
