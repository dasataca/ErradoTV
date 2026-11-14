package com.gareca.tv.infrastructure.resolver

import com.gareca.tv.domain.WebStreamResolver
import com.gareca.tv.model.StreamOption
import com.gareca.tv.api.ApiClient
import com.gareca.tv.telemetry.SupabaseLogger
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
/**
 * Hexagonal adapter that resolves generic web streams to direct HLS URLs.
 *
 * The implementation is deliberately lightweight: it performs a HEAD request (or a GET if needed)
 * following all redirects. If the final URL ends with ".m3u8" or the response body contains an
 * HLS playlist marker "#EXTM3U", the stream is considered resolvable and a new [StreamOption]
 * with type "exo" is returned. Otherwise, null is returned and the caller will fall back to the
 * existing WebView based handling.
 *
 * No advertising SDKs are imported and all networking uses the shared OkHttp client from
 * [ApiClient] to respect connection pooling and timeout configuration.
 */
class WebStreamResolverImpl : WebStreamResolver {
    private val logger = SupabaseLogger

    override fun resolve(stream: StreamOption): StreamOption? {
        // Only handle web streams
        if (!stream.type.equals("web", ignoreCase = true)) return null
        var attempt = 0
        while (attempt < 2) {
            attempt++
            try {
                // HEAD request – skip for known 403 domain la18hd.su
                if (!stream.url.contains("la18hd.su")) {
                    val headRequest = Request.Builder()
                        .url(stream.url)
                        .head()
                        .header("User-Agent", "Mozilla/5.0 (Android TV; Linux) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()
                    ApiClient.httpClient.newCall(headRequest).execute().use { response ->
                        val finalUrl = response.request.url.toString()
                        if (finalUrl.endsWith(".m3u8")) {
                            logger.log("info", "WebStreamResolver", "resolved via redirect to HLS playlist: $finalUrl")
                            return stream.copy(url = finalUrl, type = "exo")
                        }
                        if (!response.isSuccessful) {
                            logger.log("warn", "WebStreamResolver", "HEAD request failed (attempt $attempt) for ${stream.url}, code=${response.code}")
                        } else {
                            val contentType = response.header("Content-Type") ?: ""
                            if (contentType.contains("application/vnd.apple.mpegurl") || contentType.contains("application/x-mpegURL")) {
                                logger.log("info", "WebStreamResolver", "resolved via content-type to HLS: $finalUrl")
                                return stream.copy(url = finalUrl, type = "exo")
                            }
                        }
                    }
                }
                // GET fallback – try to detect an HLS playlist in the response body
                val getRequest = Request.Builder()
                    .url(stream.url)
                    .header("User-Agent", "Mozilla/5.0 (Android TV; Linux) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Range", "bytes=0-4096")
                    .build()
                ApiClient.httpClient.newCall(getRequest).execute().use { getRes ->
                    if (getRes.isSuccessful) {
                        val bodySnippet = getRes.body?.string().orEmpty()
                        if (bodySnippet.contains("#EXTM3U")) {
                            val finalUrl = getRes.request.url.toString()
                            logger.log("info", "WebStreamResolver", "detected HLS playlist in response body for ${stream.url}")
                            return stream.copy(url = finalUrl, type = "exo")
                        }
                    } else {
                        logger.log("warn", "WebStreamResolver", "GET request failed (attempt $attempt) for ${stream.url}, code=${getRes.code}")
                    }
                }
                // retry loop will continue if not resolved
            } catch (e: IOException) {
                logger.log("error", "WebStreamResolver", "IOException on attempt $attempt for ${stream.url}", mapOf("exception" to e.toString()))
            } catch (e: Exception) {
                logger.log("error", "WebStreamResolver", "Unexpected error on attempt $attempt for ${stream.url}", mapOf("exception" to e.toString()))
                break
            }
        }
        return null
    }
}
