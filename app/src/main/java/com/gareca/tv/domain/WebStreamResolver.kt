package com.gareca.tv.domain

import com.gareca.tv.model.StreamOption

/**
 * Port (interface) for resolving web streams to direct HLS URLs.
 *
 * The implementation lives in the infrastructure layer and uses OkHttp to
 * perform a lightweight HEAD/GET request, following redirects and inspecting
 * the final response for an HLS playlist URL.
 */
interface WebStreamResolver {
    /**
     * Attempts to resolve a [StreamOption] of type "web" into a direct HLS URL.
     *
     * @return a new [StreamOption] with type "exo" and the resolved URL, or null
     *         if the stream cannot be resolved (e.g., requires JavaScript, DRM, etc.).
     */
    fun resolve(stream: StreamOption): StreamOption?
}
