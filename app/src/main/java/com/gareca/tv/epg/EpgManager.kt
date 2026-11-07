package com.gareca.tv.epg

import android.content.ComponentCallbacks2
import android.util.LruCache
import com.gareca.tv.model.EventItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Representa un programa individual dentro de la Guía Electrónica de Programas (EPG).
 */
data class EpgProgram(
    val title: String,
    val description: String = "",
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val category: String? = null
) {
    val durationMillis: Long get() = (endEpochMillis - startEpochMillis).coerceAtLeast(1L)

    @Volatile
    private var cachedTimeRange: String? = null

    fun isCurrentlyPlaying(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return nowMillis in startEpochMillis until endEpochMillis
    }

    fun getProgressPercent(nowMillis: Long = System.currentTimeMillis()): Int {
        if (nowMillis <= startEpochMillis) return 0
        if (nowMillis >= endEpochMillis) return 100
        val elapsed = nowMillis - startEpochMillis
        return ((elapsed * 100) / durationMillis).toInt().coerceIn(0, 100)
    }

    fun getFormattedTimeRange(timeZone: TimeZone = TimeZone.getDefault()): String {
        cachedTimeRange?.let { return it }
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        val startStr = sdf.format(Date(startEpochMillis))
        val endStr = sdf.format(Date(endEpochMillis))
        val res = "$startStr - $endStr"
        cachedTimeRange = res
        return res
    }
}

/**
 * Gestor de Guía Electrónica de Programas (EPG) ultra-ligero y optimizado para TV Boxes con 1GB de RAM.
 * Utiliza LruCache acotado en memoria, purgado agresivo en trimMemory y generación resiliente
 * de programación sintética cuando la señal no cuenta con XMLTV remoto.
 */
object EpgManager {

    // Límite estricto de canales en memoria RAM para evitar OutOfMemory en dispositivos de 1GB
    private const val MAX_CACHED_CHANNELS = 60

    // Caché LRU estándar Java de programas por clave de canal (100% compatible Android TV y JVM)
    private val channelProgramsCache = object : java.util.LinkedHashMap<String, List<EpgProgram>>(MAX_CACHED_CHANNELS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<EpgProgram>>?): Boolean {
            return size > MAX_CACHED_CHANNELS
        }
    }

    // Caché O(1) de subtítulos preformateados por minuto para navegación a 60 FPS sin asignaciones de CPU en D-Pad
    @Volatile
    private var lastMinuteBucket: Long = -1L
    private val subtitleCache = object : java.util.LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 256
    }

    // Ventana sintética compartida de 2 horas (evita crear Calendar/Date repetidamente en miles de canales)
    @Volatile private var cachedSyntheticStart: Long = 0L
    @Volatile private var cachedSyntheticEnd: Long = 0L
    @Volatile private var cachedSyntheticTitle: String = ""
    @Volatile private var cachedSyntheticRange: String = ""

    /**
     * Obtiene el programa que se está transmitiendo actualmente en el canal.
     */
    fun getCurrentProgram(channelKey: String, nowMillis: Long = System.currentTimeMillis()): EpgProgram? {
        val cleanKey = channelKey.trim().lowercase()
        val programs = synchronized(channelProgramsCache) {
            channelProgramsCache[cleanKey]
        }
        if (!programs.isNullOrEmpty()) {
            val active = programs.firstOrNull { it.isCurrentlyPlaying(nowMillis) }
            if (active != null) return active
        }
        return generateSyntheticCurrentProgram(cleanKey, nowMillis)
    }

    /**
     * Obtiene la lista completa de programas del día para un canal.
     */
    fun getProgramsForChannel(channelKey: String): List<EpgProgram> {
        val cleanKey = channelKey.trim().lowercase()
        val existing = synchronized(channelProgramsCache) {
            channelProgramsCache[cleanKey]
        }
        if (!existing.isNullOrEmpty()) return existing

        val generated = generateSyntheticDailySchedule(cleanKey)
        synchronized(channelProgramsCache) {
            channelProgramsCache[cleanKey] = generated
        }
        return generated
    }

    /**
     * Actualiza o inserta datos EPG para un canal.
     */
    fun putPrograms(channelKey: String, programs: List<EpgProgram>) {
        if (programs.isEmpty()) return
        val cleanKey = channelKey.trim().lowercase()
        synchronized(channelProgramsCache) {
            channelProgramsCache[cleanKey] = programs
        }
    }

    /**
     * Formatea el subtítulo de la tarjeta con el programa en vivo y el horario EPG:
     * Ej: "[20:00 - 21:30] Noticiero Central (45%)" o mantiene el subtítulo original si no hay EPG.
     * Cero asignaciones de memoria durante el scroll mediante memoización por minuto.
     */
    fun getFormattedSubtitle(item: EventItem, defaultSubtitle: String): String {
        // En eventos deportivos con cuenta regresiva u horario programado, se preserva el formato de partido
        if (item.category == "Partidos" || item.time != null) {
            return defaultSubtitle
        }

        val now = System.currentTimeMillis()
        val currentMinuteBucket = now / 60_000L
        synchronized(subtitleCache) {
            if (currentMinuteBucket != lastMinuteBucket) {
                lastMinuteBucket = currentMinuteBucket
                subtitleCache.clear()
            } else {
                val cached = subtitleCache[item.title]
                if (cached != null) return cached
            }
        }

        val program = getCurrentProgram(item.title, now)
        val formatted = if (program != null && program.title.isNotBlank()) {
            val range = program.getFormattedTimeRange()
            val progress = program.getProgressPercent(now)
            "[$range] ${program.title} • ${progress}%"
        } else {
            defaultSubtitle
        }

        synchronized(subtitleCache) {
            subtitleCache[item.title] = formatted
        }
        return formatted
    }

    /**
     * Genera dinámicamente un programa sintético en intervalos de 1 a 2 horas para canales 24/7 sin guía remota,
     * garantizando que el usuario siempre disponga de información temporal en Leanback TV.
     * Reutiliza una única ventana calculada en memoria para todos los canales, reduciendo el coste de CPU a cero.
     */
    private fun generateSyntheticCurrentProgram(channelKey: String, nowMillis: Long): EpgProgram {
        if (nowMillis in cachedSyntheticStart until cachedSyntheticEnd && cachedSyntheticTitle.isNotBlank()) {
            return EpgProgram(
                title = cachedSyntheticTitle,
                description = "Transmisión en directo ($channelKey)",
                startEpochMillis = cachedSyntheticStart,
                endEpochMillis = cachedSyntheticEnd
            )
        }

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startMillis = cal.timeInMillis

        cal.add(Calendar.HOUR_OF_DAY, 2)
        val endMillis = cal.timeInMillis

        val title = when (hour) {
            in 6..8 -> "Transmisión Matutina en Vivo"
            in 9..12 -> "Programación Matinal"
            in 13..15 -> "Emisión Meridiana / Noticias"
            in 16..19 -> "Tarde de Entretenimiento"
            in 20..22 -> "Horario Central / Emisión Principal"
            in 23..24 -> "Noche en Vivo"
            else -> "Transmisión de Madrugada"
        }

        cachedSyntheticStart = startMillis
        cachedSyntheticEnd = endMillis
        cachedSyntheticTitle = title

        return EpgProgram(
            title = title,
            description = "Transmisión en directo ($channelKey)",
            startEpochMillis = startMillis,
            endEpochMillis = endMillis
        )
    }

    /**
     * Genera una grilla diaria sintética para canales continuos.
     */
    private fun generateSyntheticDailySchedule(channelKey: String): List<EpgProgram> {
        val list = mutableListOf<EpgProgram>()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val slots = listOf(
            0 to "Transmisión Nocturna",
            6 to "Informativo Matinal",
            9 to "Variedades y Magacín",
            12 to "Emisión Meridiana",
            15 to "Especial de la Tarde",
            18 to "Resumen del Día",
            20 to "Emisión Central en Directo",
            22 to "Cierre de Jornada"
        )

        for (i in 0 until slots.size) {
            val (slotHour, slotTitle) = slots[i]
            cal.set(Calendar.HOUR_OF_DAY, slotHour)
            val start = cal.timeInMillis
            val nextHour = if (i + 1 < slots.size) slots[i + 1].first else 24
            cal.set(Calendar.HOUR_OF_DAY, nextHour)
            val end = cal.timeInMillis

            list.add(
                EpgProgram(
                    title = slotTitle,
                    description = "Transmisión programada ($channelKey)",
                    startEpochMillis = start,
                    endEpochMillis = end
                )
            )
        }
        return list
    }

    /**
     * Responde a avisos del sistema Android para liberar memoria RAM de inmediato en TV Boxes de 1GB.
     */
    fun trimMemory(level: Int) {
        synchronized(channelProgramsCache) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                channelProgramsCache.clear()
            } else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) {
                while (channelProgramsCache.size > MAX_CACHED_CHANNELS / 2) {
                    val it = channelProgramsCache.iterator()
                    if (it.hasNext()) {
                        it.next()
                        it.remove()
                    } else break
                }
            }
        }
        synchronized(subtitleCache) {
            subtitleCache.clear()
        }
    }

    fun clear() {
        synchronized(channelProgramsCache) {
            channelProgramsCache.clear()
        }
        synchronized(subtitleCache) {
            subtitleCache.clear()
        }
    }
}
