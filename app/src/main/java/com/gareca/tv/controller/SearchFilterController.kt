package com.gareca.tv.controller

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.gareca.tv.db.AppDatabase
import com.gareca.tv.model.EventItem

/**
 * Controlador de búsqueda tokenizada multidisjuntiva y debounce de consultas para GarecaTV.
 * Permite buscar términos compuestos como "racing boca" o "boca racing" coincidiendo con
 * el título, subtítulo, categoría y país de los eventos.
 */
class SearchFilterController(
    private val debounceMs: Long = 180L,
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {

    private var pendingDebounceRunnable: Runnable? = null

    /**
     * Ejecuta una acción tras un intervalo de debounce, cancelando cualquier ejecución pendiente.
     */
    fun debounce(action: () -> Unit) {
        pendingDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            action()
        }
        pendingDebounceRunnable = runnable
        mainHandler.postDelayed(runnable, debounceMs)
    }

    /**
     * Cancela cualquier tarea de debounce pendiente.
     */
    fun cancelPending() {
        pendingDebounceRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingDebounceRunnable = null
        }
    }

    /**
     * Divide una consulta en tokens no vacíos en minúsculas.
     */
    fun tokenize(query: String): List<String> {
        return query.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }
    }

    /**
     * Comprueba si un elemento coincide con TODOS los tokens de búsqueda especificados (búsqueda multidisjuntiva).
     */
    fun matchesTokens(item: EventItem, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return true
        val target = "${item.title} ${item.subtitle} ${item.category} ${item.country.orEmpty()}".lowercase()
        return tokens.all { target.contains(it) }
    }

    /**
     * Filtra una lista de EventItem según los tokens de la consulta.
     */
    fun filterByQuery(items: List<EventItem>, query: String): List<EventItem> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return items
        return items.filter { matchesTokens(it, tokens) }
    }

    /**
     * Obtiene sugerencias en tiempo real a partir de la consulta, utilizando SQLite FTS o catálogo en memoria.
     */
    fun getSuggestions(
        context: Context,
        query: String,
        allKeywords: List<String>,
        limit: Int = 6
    ): List<String> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()

        val dbSuggestions = try {
            AppDatabase.getInstance(context).getSuggestions(q, limit = limit)
        } catch (_: Exception) {
            emptyList()
        }

        if (dbSuggestions.isNotEmpty()) {
            return dbSuggestions
        }

        val tokens = tokenize(q)
        return allKeywords.filter { keyword ->
            val lower = keyword.lowercase()
            tokens.all { lower.contains(it) }
        }.sortedBy { item ->
            val lower = item.lowercase()
            if (lower.startsWith(q)) 0 else 1
        }.take(limit)
    }

    /**
     * Busca a través de todas las fuentes deportivas en memoria si FTS no arrojó resultados.
     */
    fun searchSportsAcrossSources(
        query: String,
        dailyEvents: List<EventItem>,
        liveChannels: List<EventItem>,
        replayEvents: List<EventItem>
    ): List<EventItem> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()

        val results = mutableListOf<EventItem>()
        for (item in dailyEvents) {
            if (matchesTokens(item, tokens)) results.add(item)
        }
        for (item in liveChannels) {
            if (matchesTokens(item, tokens)) results.add(item)
        }
        for (item in replayEvents) {
            if (matchesTokens(item, tokens)) results.add(item)
        }
        return results
    }
}
