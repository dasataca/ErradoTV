package com.gareca.tv.util

import com.gareca.tv.model.EventItem
import com.gareca.tv.model.EventStatus

enum class ReplaySortMode {
    DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC
}

enum class GlobalSortMode {
    DEFAULT,
    NAME_ASC,
    NAME_DESC
}

/**
 * Motor de ordenación centralizado para GarecaTV.
 * Implementa las reglas de negocio de AGENTS.md:
 * 1. Cronología estricta por hora en Partidos del Día.
 * 2. Canales no disponibles / finalizados siempre al final.
 * 3. En vivo > Por empezar > Programado > 24/7 > Replay > Unavailable > Finalizado.
 * 4. Agrupamiento temático A-Z y alfabético por nombre A-Z.
 */
object EventSorter {

    /**
     * Ordena los partidos del día en orden cronológico estricto según su hora (startEpochMillis).
     * Los eventos con epoch no nulo van primero en orden ascendente; los que no tienen hora van al final.
     */
    fun sortDailyMatchesChronologically(list: List<EventItem>): List<EventItem> {
        return list.sortedWith(
            compareBy<EventItem> { item ->
                if (item.startEpochMillis > 0L) item.startEpochMillis else Long.MAX_VALUE
            }.thenBy { it.time.orEmpty() }
            .thenBy { it.title.lowercase().trim() }
        )
    }

    /**
     * Ordena canales de TV Mundial / CXTv:
     * - Canales no disponibles o finalizados siempre al final (prioridad 100).
     * - Agrupados alfabéticamente por categoría (A-Z).
     * - Dentro de categoría, alfabéticamente por país (A-Z).
     * - Dentro de país, alfabéticamente por nombre (A-Z).
     */
    fun sortCxtvChannelList(list: List<EventItem>): List<EventItem> {
        return list.sortedWith(
            compareBy<EventItem> { item ->
                when {
                    item.status == EventStatus.REPLAY_UNAVAILABLE || item.status == EventStatus.FINISHED -> 100
                    else -> 0
                }
            }
            .thenBy { it.category.lowercase().trim() }
            .thenBy { it.country.orEmpty().lowercase().trim() }
            .thenBy { it.title.lowercase().trim() }
        )
    }

    /**
     * Ordena listas deportivas generales (Deportes, Canales 24/7, Búsqueda global de deportes):
     * 1. En vivo (LIVE = 10)
     * 2. Por empezar (STARTING_SOON = 20)
     * 3. Programados (SCHEDULED = 30)
     * 4. Canales 24/7 (CHANNEL_24_7 = 40)
     * 5. Repeticiones (REPLAY = 50)
     * 6. No disponibles (REPLAY_UNAVAILABLE = 90)
     * 7. Finalizados (FINISHED = 100)
     */
    fun sortEventList(list: List<EventItem>): List<EventItem> {
        return list.sortedWith(
            compareBy<EventItem> { item ->
                when {
                    item.status == EventStatus.FINISHED || item.originalStatus == EventStatus.FINISHED -> 100
                    item.status == EventStatus.LIVE -> 10
                    item.status == EventStatus.STARTING_SOON -> 20
                    item.status == EventStatus.SCHEDULED -> 30
                    item.status == EventStatus.CHANNEL_24_7 || item.originalStatus == EventStatus.CHANNEL_24_7 || item.category != "Partidos" -> 40
                    item.status == EventStatus.REPLAY -> 50
                    item.status == EventStatus.REPLAY_UNAVAILABLE -> 90
                    else -> 60
                }
            }.thenBy { item ->
                if (item.startEpochMillis > 0L) item.startEpochMillis else Long.MAX_VALUE
            }.thenBy { it.title.lowercase().trim() }
        )
    }

    /**
     * Ordena la lista de repeticiones según el modo seleccionado.
     */
    fun sortReplayEvents(list: List<EventItem>, sortMode: ReplaySortMode): List<EventItem> {
        return when (sortMode) {
            ReplaySortMode.DATE_DESC -> list.sortedWith(
                compareBy<EventItem> { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }
                    .thenByDescending { it.timestamp }
                    .thenBy { it.title.lowercase().trim() }
            )
            ReplaySortMode.DATE_ASC -> list.sortedWith(
                compareBy<EventItem> { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }
                    .thenBy { it.timestamp }
                    .thenBy { it.title.lowercase().trim() }
            )
            ReplaySortMode.NAME_ASC -> list.sortedWith(
                compareBy<EventItem> { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }
                    .thenBy { it.title.lowercase().trim() }
            )
            ReplaySortMode.NAME_DESC -> list.sortedWith(
                compareBy<EventItem> { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }
                    .thenByDescending { it.title.lowercase().trim() }
            )
        }
    }

    /**
     * Ordena resultados de búsqueda en TV Mundial.
     */
    fun sortGlobalSearchEvents(list: List<EventItem>, sortMode: GlobalSortMode): List<EventItem> {
        return when (sortMode) {
            GlobalSortMode.NAME_ASC -> list.sortedWith(
                compareBy<EventItem> { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }
                    .thenBy { it.title.lowercase().trim() }
            )
            GlobalSortMode.NAME_DESC -> list.sortedWith(
                compareBy<EventItem> { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }
                    .thenByDescending { it.title.lowercase().trim() }
            )
            GlobalSortMode.DEFAULT -> sortCxtvChannelList(list)
        }
    }
}
