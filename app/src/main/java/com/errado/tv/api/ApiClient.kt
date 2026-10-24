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
                            val (eventStatus, localTime, countdown) = EventItem.calculateTimesAndStatus(extractedTime, eventDate)
                            val subtitleText = if (localTime != null && localTime != extractedTime) {
                                "${streams.size} opciones • Tu hora: $localTime (Arg: $extractedTime)"
                            } else {
                                "${streams.size} opciones disponibles"
                            }
                            list.add(
                                EventItem(
                                    title = cleanTitle,
                                    time = extractedTime,
                                    subtitle = subtitleText,
                                    imageUrl = null,
                                    streams = streams,
                                    status = eventStatus,
                                    localTime = localTime,
                                    countdownText = countdown
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

                            var eventTimestamp = 0L
                            var rawDateStr: String? = null

                            // Comprobar si el día es anterior al día actual
                            val isPreviousDay = match?.let {
                                val (y, m, d) = it.destructured
                                rawDateStr = "$y-$m-$d"
                                val eventCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Argentina/Buenos_Aires")).apply {
                                    set(java.util.Calendar.YEAR, y.toInt())
                                    set(java.util.Calendar.MONTH, m.toInt() - 1)
                                    set(java.util.Calendar.DAY_OF_MONTH, d.toInt())
                                    set(java.util.Calendar.HOUR_OF_DAY, 12)
                                    set(java.util.Calendar.MINUTE, 0)
                                    set(java.util.Calendar.SECOND, 0)
                                }
                                eventTimestamp = eventCal.timeInMillis

                                val todayCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Argentina/Buenos_Aires")).apply {
                                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    set(java.util.Calendar.MINUTE, 0)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                }
                                eventCal.before(todayCal)
                            } ?: false

                            val itemStatus = if (isPreviousDay) EventStatus.REPLAY else EventStatus.CHANNEL_24_7

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
                                    status = itemStatus,
                                    category = "Repeticiones",
                                    timestamp = eventTimestamp,
                                    rawDate = rawDateStr
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
                    val drmLic = json.optString("drmlicenceurl", json.optString("license", "")).trim().ifEmpty { null }
                    val ua = json.optString("user_agent", "").trim().ifEmpty { null }
                    return@withContext StreamOption(
                        title = stream.title,
                        url = realUrl,
                        type = realType,
                        drmLicenseUrl = drmLic,
                        userAgent = ua
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        stream
    }

    data class StreamHealth(
        val stream: StreamOption,
        val isOnline: Boolean,
        val resolution: String? = null
    )

    suspend fun checkStreamHealth(stream: StreamOption): StreamHealth = withContext(Dispatchers.IO) {
        val resolved = resolveStreamUrl(stream)
        val url = resolved.url
        if (!url.startsWith("http")) {
            return@withContext StreamHealth(resolved, isOnline = false)
        }

        val cachedRes = com.errado.tv.StreamCache.getResolution(url)
        if (cachedRes != null) {
            return@withContext StreamHealth(resolved, isOnline = true, resolution = cachedRes)
        }

        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", resolved.userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

            if (url.contains("teveplay.net")) {
                val cid = url.substringAfter("/api/stream/").substringBefore("/index.m3u8")
                val ref = if (cid.isNotEmpty() && cid != url) "https://teveplay.net/watch/$cid" else "https://teveplay.net/"
                reqBuilder.header("Referer", ref)
                reqBuilder.header("Origin", "https://teveplay.net")
            } else if (url.contains("fawanews") || url.contains("193.47.62")) {
                reqBuilder.header("Referer", "http://www.fawanews.sc/")
                reqBuilder.header("Origin", "http://www.fawanews.sc")
            }

            val call = httpClient.newCall(reqBuilder.build())
            val res = call.execute()
            if (res.isSuccessful) {
                var detectedRes: String? = null
                val body = res.body?.string().orEmpty()
                val resRegex = Regex("""RESOLUTION=(\d+x\d+)""")
                val match = resRegex.find(body)
                if (match != null) {
                    val rawDims = match.groupValues[1]
                    val h = rawDims.substringAfter("x").toIntOrNull() ?: 0
                    detectedRes = when {
                        h >= 1080 -> "1080p FHD"
                        h >= 720 -> "720p HD"
                        h >= 540 -> "${h}p"
                        h >= 480 -> "480p"
                        h > 0 -> "${h}p"
                        else -> rawDims
                    }
                } else if (url.contains(".mpd")) {
                    val hMatch = Regex("""height="(\d+)"""").find(body)
                    if (hMatch != null) {
                        val h = hMatch.groupValues[1].toIntOrNull() ?: 0
                        detectedRes = when {
                            h >= 1080 -> "1080p FHD"
                            h >= 720 -> "720p HD"
                            h >= 480 -> "480p"
                            else -> "${h}p"
                        }
                    }
                }
                detectedRes?.let { com.errado.tv.StreamCache.setResolution(url, it) }
                return@withContext StreamHealth(resolved, isOnline = true, resolution = detectedRes)
            } else {
                return@withContext StreamHealth(resolved, isOnline = false)
            }
        } catch (e: Exception) {
            return@withContext StreamHealth(resolved, isOnline = false)
        }
    }

    suspend fun checkReplayUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        if (!url.startsWith("http")) return@withContext false
        if (com.errado.tv.StreamCache.isUnavailable(url)) return@withContext false

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val res = httpClient.newCall(req).execute()
            if (!res.isSuccessful) {
                com.errado.tv.StreamCache.markUnavailable(null, url)
                return@withContext false
            }

            val body = res.body?.string().orEmpty()
            val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            if (lines.isEmpty()) {
                com.errado.tv.StreamCache.markUnavailable(null, url)
                return@withContext false
            }

            var segUrl = lines.first()
            if (!segUrl.startsWith("http")) {
                val base = url.substringBeforeLast("/") + "/"
                segUrl = base + segUrl
            }

            val segReq = Request.Builder()
                .url(segUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Range", "bytes=0-15")
                .build()
            val segRes = httpClient.newCall(segReq).execute()
            if (!segRes.isSuccessful) {
                com.errado.tv.StreamCache.markUnavailable(null, url)
                return@withContext false
            }

            val stream = segRes.body?.byteStream() ?: return@withContext false
            val buffer = ByteArray(16)
            val bytesRead = stream.read(buffer, 0, 16)
            if (bytesRead < 4) {
                com.errado.tv.StreamCache.markUnavailable(null, url)
                return@withContext false
            }

            val b0 = buffer[0].toInt() and 0xFF
            val isMpegTs = b0 == 0x47
            val isMp4 = bytesRead >= 8 && (
                String(buffer, 4, 4, Charsets.US_ASCII) == "ftyp" ||
                String(buffer, 4, 4, Charsets.US_ASCII) == "moof" ||
                String(buffer, 4, 4, Charsets.US_ASCII) == "styp"
            )
            val isFakeImage = b0 == 0x89 || (bytesRead >= 3 && buffer[0].toInt().toChar() == 'G' && buffer[1].toInt().toChar() == 'I' && buffer[2].toInt().toChar() == 'F')

            val isValidVideo = (isMpegTs || isMp4) && !isFakeImage
            if (!isValidVideo) {
                com.errado.tv.StreamCache.markUnavailable(null, url)
            }
            return@withContext isValidVideo
        } catch (e: Exception) {
            com.errado.tv.StreamCache.markUnavailable(null, url)
            return@withContext false
        }
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

                        val isMatch = "vs" in name.lowercase() || " v " in name.lowercase()
                        val timeRegex = Regex("""\b([01]?[0-9]|2[0-3]):([0-5][0-9])\b""")
                        val timeMatch = timeRegex.find(name)
                        val extractedTime = timeMatch?.value

                        var itemStatus = if (isMatch) EventStatus.SCHEDULED else EventStatus.CHANNEL_24_7
                        var localTime: String? = null
                        var countdownText: String? = null

                        if (extractedTime != null) {
                            val (cStatus, lTime, cText) = EventItem.calculateTimesAndStatus(extractedTime)
                            itemStatus = cStatus
                            localTime = lTime
                            countdownText = cText
                        }

                        val subText = if (localTime != null) {
                            "$group • Hora: $localTime • Señal Directa"
                        } else {
                            "$group • Señal Directa (ExoPlayer)"
                        }

                        list.add(
                            EventItem(
                                title = name,
                                time = extractedTime,
                                subtitle = subText,
                                imageUrl = validLogo,
                                streams = listOf(
                                    StreamOption(
                                        title = "🟢 [HD DIRECTO] $name",
                                        url = streamUrl,
                                        type = "exo"
                                    )
                                ),
                                status = itemStatus,
                                category = cat,
                                localTime = localTime,
                                countdownText = countdownText
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
            "regionales" in g -> "📺 Regionales"
            "deportes" in g -> "🏆 Deportes"
            "argentina" in g || "liga profesional" in g || "argentina" in n -> "🇦🇷 Argentina"
            "espana" in g || "laliga" in g || "espana" in n -> "🇪🇸 España"
            "mexico" in g || "liga mx" in g || "expansion" in g || "mexico" in n -> "🇲🇽 México"
            "colombia" in g || "liga colombia" in g || "colombia" in n -> "🇨🇴 Colombia"
            "peru" in g || "liga 1" in g || "peru" in n -> "🇵🇪 Perú"
            "chile" in g || "liga chile" in g || "chile" in n -> "🇨🇱 Chile"
            "ecuador" in g || "liga ecuador" in g || "ecuador" in n -> "🇪🇨 Ecuador"
            "paraguay" in g || "paraguay" in n -> "🇵🇾 Paraguay"
            "uruguay" in g || "uruguay" in n -> "🇺🇾 Uruguay"
            "brasil" in g || "brasileirao" in g || "brasil" in n -> "🇧🇷 Brasil"
            "usa" in g || "mls" in g || "league cup" in n || "leagues cup" in n -> "🇺🇸 USA / MLS"
            "premier" in g || "inglaterra" in g || "championship" in g -> "🇬🇧 Premier League"
            "serie a" in g -> "🇮🇹 Serie A"
            "bundesliga" in g -> "🇩🇪 Bundesliga"
            "ligue 1" in g -> "🇫🇷 Ligue 1"
            "portugal" in g || "primeira" in g -> "🇵🇹 Portugal"
            "latinoamerica" in g || "costa rica" in g || "concacaf" in n || "centroamericana" in n -> "🌎 Latinoamérica"
            "f1" in g || "formula 1" in g || "moto" in g -> "🏎️ F1 / Motor"
            "nba" in g || "basketball" in g -> "🏀 NBA"
            "ufc" in g || "box" in g || "knockout" in g || "mma" in g -> "🥊 Boxeo / UFC"
            "mlb" in g || "baseball" in g -> "⚾ Béisbol MLB"
            "mundo" in g -> "🌍 Mundo"
            else -> "⚽ Otras Copas"
        }
    }
}
