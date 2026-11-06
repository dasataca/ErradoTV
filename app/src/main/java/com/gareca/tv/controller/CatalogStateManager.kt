package com.gareca.tv.controller

import android.content.Context
import com.gareca.tv.db.AppDatabase
import com.gareca.tv.model.EventItem
import com.gareca.tv.util.EventSorter
import java.util.Collections
import java.util.HashSet

/**
 * Gestor centralizado del estado del catálogo y favoritos en memoria RAM.
 * Proporciona acceso O(1) a favoritos para garantizar navegación D-Pad a 60 FPS
 * e indexación en memoria de los canales de TV Mundial y Deportes.
 */
class CatalogStateManager {

    // Cachés de favoritos en RAM O(1) con sincronización de hilos
    val cachedSportsFavorites: MutableSet<String> = Collections.synchronizedSet(HashSet())
    val cachedCxtvFavorites: MutableSet<String> = Collections.synchronizedSet(HashSet())
    val cachedFreeTvFavorites: MutableSet<String> = Collections.synchronizedSet(HashSet())

    // Catálogos principales en memoria
    var cachedDailyEvents: List<EventItem> = emptyList()
    var cachedLiveChannels: List<EventItem> = emptyList()
    var cachedReplayEvents: List<EventItem> = emptyList()
    var cachedCxtvChannels: List<EventItem> = emptyList()
    var preSortedAllChannels: List<EventItem> = emptyList()

    // Categorías y países autodescubiertos dinámicamente
    var dynamicCxtvCategories: List<String> = emptyList()
    var dynamicCxtvCountries: List<String> = emptyList()

    // Índices mapeados por categoría y país para acceso instantáneo
    val cxtvCategoryMap: MutableMap<String, MutableList<EventItem>> = mutableMapOf()
    val cxtvCountryMap: MutableMap<String, MutableList<EventItem>> = mutableMapOf()

    /**
     * Carga inicial de favoritos desde la base de datos SQLite hacia memoria RAM.
     */
    fun loadFavoritesFromDb(context: Context) {
        try {
            val db = AppDatabase.getInstance(context)
            cachedSportsFavorites.clear()
            cachedSportsFavorites.addAll(db.getAllFavorites("sports"))

            cachedCxtvFavorites.clear()
            cachedCxtvFavorites.addAll(db.getAllFavorites("cxtv"))

            cachedFreeTvFavorites.clear()
            cachedFreeTvFavorites.addAll(db.getAllFavorites("freetv"))
        } catch (_: Exception) {}
    }

    /**
     * Comprobación ultra-rápida O(1) en RAM para determinar si un canal es favorito.
     */
    fun isFavorite(channelKey: String, source: String? = null): Boolean {
        val cleanKey = channelKey.trim().lowercase()
        return when (source) {
            "sports" -> cachedSportsFavorites.contains(cleanKey)
            "cxtv" -> cachedCxtvFavorites.contains(cleanKey)
            "freetv" -> cachedFreeTvFavorites.contains(cleanKey)
            else -> cachedSportsFavorites.contains(cleanKey) ||
                    cachedCxtvFavorites.contains(cleanKey) ||
                    cachedFreeTvFavorites.contains(cleanKey)
        }
    }

    /**
     * Conmuta el estado de favorito tanto en memoria RAM O(1) como en SQLite.
     * Retorna el nuevo estado (true = agregado, false = removido).
     */
    fun toggleFavorite(context: Context, channelKey: String, source: String): Boolean {
        val cleanKey = channelKey.trim().lowercase()
        val current = isFavorite(cleanKey, source)
        val newStatus = !current

        // Actualización inmediata en RAM
        when (source) {
            "sports" -> if (newStatus) cachedSportsFavorites.add(cleanKey) else cachedSportsFavorites.remove(cleanKey)
            "freetv" -> if (newStatus) cachedFreeTvFavorites.add(cleanKey) else cachedFreeTvFavorites.remove(cleanKey)
            else -> if (newStatus) cachedCxtvFavorites.add(cleanKey) else cachedCxtvFavorites.remove(cleanKey)
        }

        // Persistencia asíncrona / directa en SQLite
        try {
            val db = AppDatabase.getInstance(context)
            if (newStatus) {
                db.addFavorite(cleanKey, source)
            } else {
                db.removeFavorite(cleanKey, source)
            }
        } catch (_: Exception) {}

        return newStatus
    }

    /**
     * Obtiene el conteo total de favoritos para una fuente dada.
     */
    fun getFavoriteCount(source: String): Int {
        return when (source) {
            "sports" -> cachedSportsFavorites.size
            "freetv" -> cachedFreeTvFavorites.size
            else -> cachedCxtvFavorites.size
        }
    }

    /**
     * Indexa el catálogo de canales en memoria por categoría y país para acelerar filtrados.
     */
    fun indexCatalog(channels: List<EventItem>) {
        cxtvCategoryMap.clear()
        cxtvCountryMap.clear()

        for (item in channels) {
            val cat = item.category.trim()
            if (cat.isNotEmpty()) {
                cxtvCategoryMap.getOrPut(cat) { mutableListOf() }.add(item)
            }
            val country = item.country?.trim().orEmpty()
            if (country.isNotEmpty()) {
                cxtvCountryMap.getOrPut(country) { mutableListOf() }.add(item)
            }
        }
    }

    /**
     * Limpia todas las colecciones en caso de cierre de sesión o cambio de usuario.
     */
    fun clear() {
        cachedSportsFavorites.clear()
        cachedCxtvFavorites.clear()
        cachedFreeTvFavorites.clear()
        cachedDailyEvents = emptyList()
        cachedLiveChannels = emptyList()
        cachedReplayEvents = emptyList()
        cachedCxtvChannels = emptyList()
        preSortedAllChannels = emptyList()
        dynamicCxtvCategories = emptyList()
        dynamicCxtvCountries = emptyList()
        cxtvCategoryMap.clear()
        cxtvCountryMap.clear()
    }
}
