package com.gareca.tv.model

import java.util.Calendar
import java.util.TimeZone

enum class EventStatus(val label: String, val colorHex: String) {
    LIVE("● EN VIVO", "#EF4444"),
    STARTING_SOON("POR EMPEZAR", "#F59E0B"),
    SCHEDULED("PROGRAMADO", "#3B82F6"),
    FINISHED("FINALIZADO", "#6B7280"),
    REPLAY("REPETICIÓN", "#8B5CF6"),
    REPLAY_UNAVAILABLE("NO DISPONIBLE", "#6B7280"),
    CHANNEL_24_7("CANAL 24/7", "#10B981")
}

data class StreamOption(
    val title: String,
    val url: String,
    val type: String,
    val drmLicenseUrl: String? = null,
    val userAgent: String? = null
)

data class EventTimingInfo(
    val status: EventStatus,
    val localTime: String?,
    val countdown: String?,
    val startEpochMillis: Long = 0L
)

data class EventItem(
    val title: String,
    var time: String? = null,
    var subtitle: String,
    val imageUrl: String? = null,
    val streams: List<StreamOption>,
    var status: EventStatus = EventStatus.CHANNEL_24_7,
    val category: String = "Partidos",
    val timestamp: Long = 0L,
    val rawDate: String? = null,
    var localTime: String? = null,
    var startEpochMillis: Long = 0L,
    var countdownText: String? = null,
    var originalStatus: EventStatus = status,
    val country: String? = null,
    val streamType: String = streams.firstOrNull()?.type ?: "exo"
) {
    companion object {
        private val SERVER_TZ: TimeZone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")

        fun calculateTimesAndStatus(
            timeStr: String?,
            dateStr: String? = null
        ): EventTimingInfo {
            if (timeStr == null || !timeStr.contains(":")) {
                return EventTimingInfo(EventStatus.CHANNEL_24_7, null, null, 0L)
            }

            return try {
                val parts = timeStr.trim().split(":")
                val matchH = parts[0].toInt()
                val matchM = parts[1].toInt()

                val nowServerCal = Calendar.getInstance(SERVER_TZ)
                val currH = nowServerCal.get(Calendar.HOUR_OF_DAY)

                val matchCal = Calendar.getInstance(SERVER_TZ)
                if (dateStr != null && dateStr.contains("-")) {
                    val dParts = dateStr.split("-")
                    if (dParts.size == 3) {
                        matchCal.set(Calendar.YEAR, dParts[0].toInt())
                        matchCal.set(Calendar.MONTH, dParts[1].toInt() - 1)
                        matchCal.set(Calendar.DAY_OF_MONTH, dParts[2].toInt())
                    }
                }

                matchCal.set(Calendar.HOUR_OF_DAY, matchH)
                matchCal.set(Calendar.MINUTE, matchM)
                matchCal.set(Calendar.SECOND, 0)
                matchCal.set(Calendar.MILLISECOND, 0)

                // Si no había fecha fija y es madrugada tras noche previa
                if (dateStr == null && matchH < 4 && currH > 18) {
                    matchCal.add(Calendar.DAY_OF_MONTH, 1)
                }

                val startMillis = matchCal.timeInMillis
                val nowMillis = System.currentTimeMillis()

                // Formatear en huso horario local del usuario
                val localCal = Calendar.getInstance(TimeZone.getDefault()).apply {
                    timeInMillis = startMillis
                }
                val localH = String.format("%02d", localCal.get(Calendar.HOUR_OF_DAY))
                val localM = String.format("%02d", localCal.get(Calendar.MINUTE))
                val localFormattedTime = "$localH:$localM"

                val diffMillis = startMillis - nowMillis

                val (calculatedStatus, countdown) = when {
                    diffMillis > 24 * 3600 * 1000L -> {
                        val days = diffMillis / (24 * 3600 * 1000L)
                        val hours = (diffMillis % (24 * 3600 * 1000L)) / (3600 * 1000L)
                        EventStatus.SCHEDULED to "⏳ En ${days}d ${hours}h"
                    }
                    diffMillis in 30 * 60 * 1000L .. 24 * 3600 * 1000L -> {
                        val hours = diffMillis / (3600 * 1000L)
                        val mins = (diffMillis % (3600 * 1000L)) / (60 * 1000L)
                        EventStatus.SCHEDULED to if (hours > 0) "⏳ En ${hours}h ${mins}m" else "⏳ En ${mins}m"
                    }
                    diffMillis in 0L until 30 * 60 * 1000L -> {
                        val mins = Math.max(1L, diffMillis / (60 * 1000L))
                        EventStatus.STARTING_SOON to "⏳ ¡En $mins min!"
                    }
                    diffMillis < 0 && -diffMillis <= 135 * 60 * 1000L -> {
                        EventStatus.LIVE to "● EN JUEGO"
                    }
                    else -> {
                        EventStatus.FINISHED to "FINALIZADO"
                    }
                }

                EventTimingInfo(calculatedStatus, localFormattedTime, countdown, startMillis)
            } catch (_: Exception) {
                EventTimingInfo(EventStatus.SCHEDULED, timeStr, null, 0L)
            }
        }

        fun calculateStatus(timeStr: String?): EventStatus {
            return calculateTimesAndStatus(timeStr).status
        }
    }
}
