package com.errado.tv.api

import com.errado.tv.model.EventItem
import com.errado.tv.model.EventStatus
import com.errado.tv.model.StreamOption
import com.errado.tv.telemetry.SupabaseLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {

    val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val EVENTS_URL = "https://errado-app.pages.dev/data/event.json"
    private const val DESTACADO_URL = "https://errado-app.pages.dev/data/destacado.json"

    data class ApiResponse(
        val date: String,
        val events: List<EventItem>
    )

    suspend fun fetchDailyEvents(): ApiResponse = withContext(Dispatchers.IO) {
        val list = mutableListOf<EventItem>()
        var eventDate = ""

        try {
            val req = Request.Builder()
                .url(EVENTS_URL)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = httpClient.newCall(req).execute()
            val rawEncrypted = response.body?.string().orEmpty()
            val decryptedJson = CryptoUtils.decrypt(rawEncrypted)

            if (decryptedJson.isNotEmpty()) {
                val root = JSONObject(decryptedJson)
                eventDate = root.optString("event_date", "")

                val eventsArray = root.optJSONArray("events")
                if (eventsArray != null) {
                    for (i in 0 until eventsArray.length()) {
                        val item = eventsArray.getJSONObject(i)
                        val rawTitle = item.optString("event_name_time", "Partido").trim()
                        val timeRegex = Regex("""^(\d{1,2}:\d{2})\s*(.*)$""")
                        val match = timeRegex.find(rawTitle)
                        val extractedTime = match?.groupValues?.get(1)
                        val cleanTitle = if (match != null && match.groupValues[2].isNotEmpty()) {
                            match.groupValues[2]
                        } else {
                            rawTitle
                        }

                        val streams = mutableListOf<StreamOption>()

                        for (k in 1..16) {
                            val urlKey = "event_url_$k"
                            val typeKey = "event_type_url_$k"
                            val streamUrl = item.optString(urlKey, "").trim()
                            val streamType = item.optString(typeKey, "exo").trim()

                            if (streamUrl.isNotEmpty() && !streamUrl.equals("null", ignoreCase = true)) {
                                streams.add(
                                    StreamOption(
                                        title = "Opción $k (${streamType.uppercase()})",
                                        url = streamUrl,
                                        type = streamType
                                    )
                                )
                            }
                        }

                        if (streams.isNotEmpty()) {
                            val eventStatus = EventItem.calculateStatus(extractedTime)
                            list.add(
                                EventItem(
                                    title = cleanTitle,
                                    time = extractedTime,
                                    subtitle = "${streams.size} opciones disponibles",
                                    imageUrl = null,
                                    streams = streams,
                                    status = eventStatus
                                )
                            )
                        }
                    }
                }
                SupabaseLogger.info(
                    "events_loaded",
                    "Eventos diarios cargados exitosamente: ${list.size} partidos",
                    mapOf("date" to eventDate, "count" to list.size)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            SupabaseLogger.error(
                "events_load_error",
                "Error descargando eventos diarios: ${e.message}",
                mapOf("error" to e.toString())
            )
        }

        ApiResponse(eventDate, list)
    }

    suspend fun fetchFeaturedEvents(): List<EventItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<EventItem>()
        try {
            val req = Request.Builder()
                .url(DESTACADO_URL)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = httpClient.newCall(req).execute()
            val rawEncrypted = response.body?.string().orEmpty()
            val decryptedJson = CryptoUtils.decrypt(rawEncrypted)

            if (decryptedJson.isNotEmpty()) {
                val root = JSONObject(decryptedJson)
                val channels = root.optJSONArray("event_tv_channel")
                if (channels != null) {
                    for (i in 0 until channels.length()) {
                        val item = channels.getJSONObject(i)
                        val name = item.optString("event_name_channel", "Canal").trim()
                        val streamUrl = item.optString("event_url", "").trim()
                        val streamType = item.optString("event_type_url", "exo").trim()
                        val img = item.optString("event_image_url", "").trim()

                        if (streamUrl.isNotEmpty()) {
                            list.add(
                                EventItem(
                                    title = name,
                                    subtitle = "Destacado (ExoPlayer directo)",
                                    imageUrl = if (img.isNotEmpty()) img else null,
                                    streams = listOf(
                                        StreamOption(
                                            title = "Stream Principal",
                                            url = streamUrl,
                                            type = streamType
                                        )
                                    ),
                                    status = EventStatus.CHANNEL_24_7
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun resolveStreamUrl(stream: StreamOption): StreamOption = withContext(Dispatchers.IO) {
        if (stream.type.equals("config", ignoreCase = true)) {
            try {
                val req = Request.Builder()
                    .url(stream.url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val res = httpClient.newCall(req).execute()
                val body = res.body?.string().orEmpty()
                if (body.isNotEmpty()) {
                    val json = JSONObject(body)
                    val realUrl = json.optString("url", stream.url)
                    val realType = json.optString("type", "exo")
                    return@withContext StreamOption(
                        title = stream.title,
                        url = realUrl,
                        type = realType
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        stream
    }
}
