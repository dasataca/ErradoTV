package com.errado.tv.api

import com.errado.tv.model.EventItem
import com.errado.tv.model.EventStatus
import com.errado.tv.model.StreamOption
import com.errado.tv.telemetry.SupabaseLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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
                            // Detectar si contiene fecha en formato YYYY-MM-DD o YYYY_MM_DD
                            val dateRegex = Regex("""(\d{4})[_-](\d{2})[_-](\d{2})""")
                            val match = dateRegex.find(streamUrl) ?: dateRegex.find(img)
                            val extractedDate = match?.let {
                                val (_, m, d) = it.destructured
                                "$d/$m"
                            }
                            val subText = match?.let {
                                val (y, m, d) = it.destructured
                                "Repetición del $d/$m/$y (ExoPlayer)"
                            } ?: "Canal 24/7 (ExoPlayer directo)"

                            val itemStatus = if (extractedDate != null) EventStatus.REPLAY else EventStatus.CHANNEL_24_7

                            list.add(
                                EventItem(
                                    title = name,
                                    time = extractedDate,
                                    subtitle = subText,
                                    imageUrl = if (img.isNotEmpty()) img else null,
                                    streams = listOf(
                                        StreamOption(
                                            title = "Stream Principal",
                                            url = streamUrl,
                                            type = streamType
                                        )
                                    ),
                                    status = itemStatus
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

    private const val TEVEPLAY_CHANNELS_URL = "https://teveplay.net/api/channels"

    suspend fun fetchTeveplayChannels(): List<EventItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<EventItem>()
        try {
            val req = Request.Builder()
                .url(TEVEPLAY_CHANNELS_URL)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val response = httpClient.newCall(req).execute()
            val raw = response.body?.string().orEmpty()

            if (raw.isNotEmpty() && raw.startsWith("[")) {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", "").trim()
                    val name = obj.optString("name", "Canal").trim()
                    val group = obj.optString("group", "Deportes").trim()
                    val logo = obj.optString("logo", "").trim()

                    if (id.isNotEmpty()) {
                        val streamUrl = "https://teveplay.net/api/stream/$id/index.m3u8"
                        val cat = mapGroupToCategory(group, name)
                        val validLogo = if (logo.isNotEmpty() && !logo.contains("placehold.co")) logo else null

                        list.add(
                            EventItem(
                                title = name,
                                time = null,
                                subtitle = "$group • Señal Directa (ExoPlayer)",
                                imageUrl = validLogo,
                                streams = listOf(
                                    StreamOption(
                                        title = "🟢 [HD DIRECTO] $name",
                                        url = streamUrl,
                                        type = "exo"
                                    )
                                ),
                                status = EventStatus.LIVE,
                                category = cat
                            )
                        )
                    }
                }
                SupabaseLogger.info(
                    "channels_loaded",
                    "Canales de TevePlay cargados: ${list.size} señales activas",
                    mapOf("count" to list.size)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            SupabaseLogger.warn("channels_load_error", "Error cargando canales de TevePlay: ${e.message}")
        }
        list
    }

    fun mapGroupToCategory(group: String, name: String): String {
        val g = group.lowercase()
        val n = name.lowercase()
        return when {
            "argentina" in g || "liga profesional" in g || "tyc" in n || "tnt sports" in n -> "🇦🇷 Argentina"
            "deportes" in g -> "🏆 Deportes"
            "espana" in g || "laliga" in g -> "🇪🇸 España"
            "mexico" in g || "liga mx" in g -> "🇲🇽 México"
            "colombia" in g || "liga colombia" in g || "win sports" in n -> "🇨🇴 Colombia"
            "peru" in g || "liga 1" in g -> "🇵🇪 Perú"
            "chile" in g -> "🇨🇱 Chile"
            "uruguay" in g -> "🇺🇾 Uruguay"
            "brasil" in g || "brasileirao" in g -> "🇧🇷 Brasil"
            "premier league" in g || "inglaterra" in g -> "🇬🇧 Premier League"
            "f1" in g || "moto" in g || "formula 1" in g -> "🏎️ F1 / Motor"
            "nba" in g || "basketball" in g -> "🏀 NBA"
            "ufc" in g || "mma" in g || "box" in g -> "🥊 Boxeo / UFC"
            else -> "📺 Otros Canales"
        }
    }
}
