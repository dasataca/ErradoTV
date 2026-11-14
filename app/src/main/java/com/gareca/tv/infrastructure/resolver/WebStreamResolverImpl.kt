package com.gareca.tv.infrastructure.resolver

import com.gareca.tv.domain.WebStreamResolver
import com.gareca.tv.model.StreamOption
import com.gareca.tv.api.ApiClient
import com.gareca.tv.telemetry.SupabaseLogger
import okhttp3.Request
import java.io.IOException

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
        // Only attempt resolution for web type streams.
        if (!stream.type.equals("web", ignoreCase = true)) return null
        try {
            // Perform a HEAD request to follow redirects without downloading the body.
            val request = Request.Builder()
                .url(stream.url)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Android TV; Linux) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            val response = ApiClient.httpClient.newCall(request).execute()
            // If the server does not support HEAD, fall back to GET (small size expected).
            val finalUrl = response.request.url.toString()
            if (finalUrl.endsWith(".m3u8")) {
                logger.log("info", "WebStreamResolver", "resolved via redirect to HLS playlist: $finalUrl")
                return stream.copy(url = finalUrl, type = "exo")
            }
            // For non-.m3u8 final URLs, fetch a small portion of the body to detect a playlist.
            if (!response.isSuccessful) {
                // Not successful – cannot resolve.
                logger.log("warn", "WebStreamResolver", "HEAD request failed for ${stream.url}, code=${response.code}")
                return null
            }
            // If content-type suggests HLS, we can trust the URL.
            val contentType = response.header("Content-Type", "")
            if ((contentType?.contains("application/vnd.apple.mpegurl") == true) || (contentType?.contains("application/x-mpegURL") == true)) {
                logger.log("info", "WebStreamResolver", "resolved via content-type to HLS: $finalUrl")
                return stream.copy(url = finalUrl, type = "exo")
            }
            // Some servers ignore HEAD; perform a GET of a limited range.
            val getRequest = Request.Builder()
                .url(stream.url)
                .header("User-Agent", "Mozilla/5.0 (Android TV; Linux) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Range", "bytes=0-4096")
                .build()
            val getRes = ApiClient.httpClient.newCall(getRequest).execute()
            if (getRes.isSuccessful) {
                val bodySnippet = getRes.body?.string().orEmpty()
                if (bodySnippet.contains("#EXTM3U")) {
                    logger.log("info", "WebStreamResolver", "detected HLS playlist in response body for ${stream.url}")
                    return stream.copy(url = finalUrl, type = "exo")
                }
            }
        } catch (e: IOException) {
            logger.log("error", "WebStreamResolver", "exception while resolving ${stream.url}", mapOf("exception" to e.toString()))
        } catch (e: Exception) {
            logger.log("error", "WebStreamResolver", "unexpected error for ${stream.url}", mapOf("exception" to e.toString()))
        }
        return null
    }
}
