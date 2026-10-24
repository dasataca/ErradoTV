package com.errado.tv

import java.util.concurrent.ConcurrentHashMap

object StreamCache {
    private val resolutionMap = ConcurrentHashMap<String, String>()

    fun setResolution(url: String, resolution: String) {
        if (url.isNotEmpty() && resolution.isNotEmpty()) {
            resolutionMap[url] = resolution
        }
    }

    fun getResolution(url: String): String? {
        return resolutionMap[url]
    }
}
