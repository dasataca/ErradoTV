package com.errado.tv.model

import java.util.Calendar
import java.util.TimeZone

enum class EventStatus(val label: String, val colorHex: String) {
    LIVE("● EN VIVO", "#EF4444"),
    STARTING_SOON("POR EMPEZAR", "#F59E0B"),
    SCHEDULED("PROGRAMADO", "#3B82F6"),
    FINISHED("FINALIZADO", "#6B7280"),
    REPLAY("REPETICIÓN", "#8B5CF6"),
    CHANNEL_24_7("CANAL 24/7", "#10B981")
}

data class StreamOption(
    val title: String,
    val url: String,
    val type: String
)

data class EventItem(
    val title: String,
    val time: String? = null,
    val subtitle: String,
    val imageUrl: String? = null,
    val streams: List<StreamOption>,
    val status: EventStatus = EventStatus.CHANNEL_24_7
) {
    companion object {
        fun calculateStatus(timeStr: String?): EventStatus {
            if (timeStr == null || !timeStr.contains(":")) {
                return EventStatus.CHANNEL_24_7
            }

            return try {
                val parts = timeStr.trim().split(":")
                val matchH = parts[0].toInt()
                val matchM = parts[1].toInt()

                // Huso horario de los eventos del servidor: GMT-3 (Argentina / Sudamérica)
                val cal = Calendar.getInstance(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"))
                val currH = cal.get(Calendar.HOUR_OF_DAY)
                val currM = cal.get(Calendar.MINUTE)

                val currTotalMinutes = currH * 60 + currM
                var matchTotalMinutes = matchH * 60 + matchM

                // Ajuste para partidos tras la medianoche (ej: 00:00 o 01:00 listados en la cartelera nocturna)
                if (matchTotalMinutes < 4 * 60 && currTotalMinutes > 18 * 60) {
                    matchTotalMinutes += 24 * 60
                }

                val diffMinutes = currTotalMinutes - matchTotalMinutes

                when {
                    diffMinutes < 0 -> {
                        // Aún no empezó
                        if (-diffMinutes <= 30) {
                            EventStatus.STARTING_SOON
                        } else {
                            EventStatus.SCHEDULED
                        }
                    }
                    diffMinutes in 0..135 -> {
                        // En juego (partido de fútbol: 90 min + entretiempo + descuentos)
                        EventStatus.LIVE
                    }
                    else -> {
                        // Finalizado (más de 2 horas y 15 min desde el pitido inicial)
                        EventStatus.FINISHED
                    }
                }
            } catch (_: Exception) {
                EventStatus.SCHEDULED
            }
        }
    }
}
