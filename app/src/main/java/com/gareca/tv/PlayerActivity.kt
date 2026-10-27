package com.gareca.tv

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.gareca.tv.api.ApiClient
import com.gareca.tv.telemetry.SupabaseLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var playerBuffering: ProgressBar
    private lateinit var playerError: TextView
    private lateinit var tvResolutionBadge: TextView

    // Controles de Banner de Canal para TV
    private lateinit var llChannelBanner: View
    private lateinit var ivChannelLogo: ImageView
    private lateinit var tvChannelBannerTitle: TextView
    private lateinit var tvChannelBannerSub: TextView
    private lateinit var tvChannelBannerIndex: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var hideBadgeRunnable: Runnable? = null
    private var hideBannerRunnable: Runnable? = null
    private var watchdogRunnable: Runnable? = null

    private var videoFrameReceived = false
    private var currentResolutionText = ""
    private var currentStreamUrl = ""
    private var currentStreamTitle = ""
    private var currentSubtitle: String? = null
    private var currentImageUrl: String? = null
    private var zapJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        playerBuffering = findViewById(R.id.playerBuffering)
        playerError = findViewById(R.id.playerError)
        tvResolutionBadge = findViewById(R.id.tvResolutionBadge)

        llChannelBanner = findViewById(R.id.llChannelBanner)
        ivChannelLogo = findViewById(R.id.ivChannelLogo)
        tvChannelBannerTitle = findViewById(R.id.tvChannelBannerTitle)
        tvChannelBannerSub = findViewById(R.id.tvChannelBannerSub)
        tvChannelBannerIndex = findViewById(R.id.tvChannelBannerIndex)

        val streamUrl = intent.getStringExtra("EXTRA_URL") ?: ""
        val streamTitle = intent.getStringExtra("EXTRA_TITLE") ?: ""
        val drmLicenseUrl = intent.getStringExtra("EXTRA_DRM_LICENSE")
        val customUserAgent = intent.getStringExtra("EXTRA_USER_AGENT")
        currentImageUrl = intent.getStringExtra("EXTRA_IMAGE_URL")
        currentSubtitle = intent.getStringExtra("EXTRA_SUBTITLE")

        if (streamUrl.isEmpty()) {
            finish()
            return
        }

        currentStreamUrl = streamUrl
        currentStreamTitle = streamTitle

        initializePlayer(streamUrl, drmLicenseUrl, customUserAgent, streamTitle)
        showChannelBanner()
    }

    private fun initializePlayer(url: String, drmLicenseUrl: String? = null, customUserAgent: String? = null, title: String = "") {
        try {
            // Configuramos el selector de pistas para forzar SIEMPRE la máxima calidad y bitrate disponible
            val trackSelector = DefaultTrackSelector(this).apply {
                setParameters(
                    buildUponParameters()
                        .setForceHighestSupportedBitrate(true)
                        .clearVideoSizeConstraints()
                        .setMaxVideoSize(3840, 2160) // Apunta a la mayor resolución
                        .setAllowVideoMixedMimeTypeAdaptiveness(true)
                        .setAllowVideoNonSeamlessAdaptiveness(true)
                        .setExceedVideoConstraintsIfNecessary(true)
                        .setExceedRendererCapabilitiesIfNecessary(true)
                )
            }

            val exoPlayer = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .build()

            player = exoPlayer
            playerView.player = exoPlayer

            exoPlayer.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoFrameReceived = true
                        StreamCache.markAvailable(this@PlayerActivity, currentStreamUrl)
                        val label = "${videoSize.width}x${videoSize.height} (${getQualityLabel(videoSize.height)}) [MÁX]"
                        currentResolutionText = label
                        showResolutionBadge(label)
                        StreamCache.setResolution(currentStreamUrl, getQualityLabel(videoSize.height))

                        SupabaseLogger.info(
                            "stream_resolution",
                            "Resolución máxima aplicada: $label",
                            mapOf("width" to videoSize.width, "height" to videoSize.height, "url" to currentStreamUrl)
                        )
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            playerBuffering.visibility = View.VISIBLE
                            playerError.visibility = View.GONE
                        }
                        Player.STATE_READY -> {
                            playerBuffering.visibility = View.GONE
                            playerError.visibility = View.GONE
                            StreamCache.markAvailable(this@PlayerActivity, currentStreamUrl)
                            SupabaseLogger.info("player_ready", "Stream reproduciendo exitosamente", mapOf("url" to currentStreamUrl))
                        }
                        Player.STATE_ENDED -> {
                            playerBuffering.visibility = View.GONE
                        }
                        Player.STATE_IDLE -> {}
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    StreamCache.markUnavailable(
                        this@PlayerActivity,
                        currentStreamUrl,
                        reason = "player_error: ${error.errorCodeName}",
                        title = currentStreamTitle,
                        broadcast = true
                    )
                    playerBuffering.visibility = View.GONE
                    playerError.visibility = View.VISIBLE
                    playerError.text = if (ChannelNavigationManager.totalCount > 1) {
                        "⚠️ Señal temporalmente fuera del aire o finalizada.\nPresiona ◀ o ▶ para sintonizar otro canal."
                    } else {
                        "⚠️ Señal temporalmente fuera del aire o finalizada.\nPresiona la tecla ATRÁS en tu control remoto para elegir otro canal."
                    }
                    SupabaseLogger.error(
                        "player_error",
                        "Error de reproducción en ExoPlayer: ${error.message}",
                        mapOf("url" to currentStreamUrl, "errorCode" to error.errorCode, "errorName" to error.errorCodeName, "title" to currentStreamTitle)
                    )
                }
            })

            loadAndPlayMedia(url, drmLicenseUrl, customUserAgent, title)
        } catch (t: Throwable) {
            t.printStackTrace()
            StreamCache.markUnavailable(
                this@PlayerActivity,
                url,
                reason = "player_init_exception: ${t.message}",
                title = title,
                broadcast = true
            )
            playerBuffering.visibility = View.GONE
            playerError.visibility = View.VISIBLE
            playerError.text = "⚠️ Error al iniciar la reproducción.\nEsta señal no está disponible en este momento.\nPresione VOLVER en su control para elegir otra opción."
            SupabaseLogger.warn(
                "player_init_error",
                "Error inicializando reproductor: ${t.message}",
                mapOf("url" to url, "error" to (t.message ?: ""), "title" to title)
            )
        }
    }

    private fun loadAndPlayMedia(url: String, drmLicenseUrl: String? = null, customUserAgent: String? = null, title: String = "") {
        val exoPlayer = player ?: return
        try {
            val defaultUa = customUserAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            val httpDataSourceFactory = OkHttpDataSource.Factory(ApiClient.httpClient)
                .setUserAgent(defaultUa)

            if (url.contains("teveplay.net")) {
                val channelId = url.substringAfter("/api/stream/").substringBefore("/index.m3u8")
                val referer = if (channelId.isNotEmpty() && channelId != url) {
                    "https://teveplay.net/watch/$channelId"
                } else {
                    "https://teveplay.net/"
                }
                httpDataSourceFactory.setDefaultRequestProperties(
                    mapOf(
                        "Referer" to referer,
                        "Origin" to "https://teveplay.net"
                    )
                )
            } else if (url.contains("fawanews") || url.contains("193.47.62")) {
                httpDataSourceFactory.setDefaultRequestProperties(
                    mapOf(
                        "Referer" to "http://www.fawanews.sc/",
                        "Origin" to "http://www.fawanews.sc"
                    )
                )
            }

            val mediaSourceFactory = DefaultMediaSourceFactory(this)
                .setDataSourceFactory(httpDataSourceFactory)

            val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(url))
            if (url.contains(".mpd")) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            } else if (url.contains(".m3u8")) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }

            if (!drmLicenseUrl.isNullOrEmpty()) {
                mediaItemBuilder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                        .setLicenseUri(Uri.parse(drmLicenseUrl))
                        .build()
                )
            }

            val mediaItem = mediaItemBuilder.build()
            val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true

            armWatchdog(url, title)
        } catch (t: Throwable) {
            t.printStackTrace()
            StreamCache.markUnavailable(
                this@PlayerActivity,
                url,
                reason = "player_load_exception: ${t.message}",
                title = title,
                broadcast = true
            )
            playerBuffering.visibility = View.GONE
            playerError.visibility = View.VISIBLE
            playerError.text = if (ChannelNavigationManager.totalCount > 1) {
                "⚠️ Error al sintonizar esta señal.\nPresiona ◀ o ▶ para sintonizar otro canal."
            } else {
                "⚠️ Error al iniciar la reproducción.\nPresione VOLVER en su control para elegir otra opción."
            }
        }
    }

    private fun armWatchdog(url: String, title: String) {
        videoFrameReceived = false
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            if (!videoFrameReceived && !isFinishing && !isDestroyed && currentStreamUrl == url) {
                StreamCache.markUnavailable(
                    this@PlayerActivity,
                    url,
                    reason = "watchdog_timeout_no_video",
                    title = title,
                    broadcast = true
                )
                playerBuffering.visibility = View.GONE
                playerError.visibility = View.VISIBLE
                playerError.text = if (ChannelNavigationManager.totalCount > 1) {
                    "⚠️ Esta transmisión no devolvió respuesta de video o la señal caducó.\nPresiona ◀ o ▶ para sintonizar otro canal."
                } else {
                    "⚠️ Esta transmisión no devolvió respuesta de video o los archivos han caducado.\nPresione ATRÁS en su control remoto para elegir otro partido."
                }
                SupabaseLogger.warn("video_playback_timeout", "Video no produjo fotogramas válidos: $url", mapOf("title" to title))
            }
        }
        watchdogRunnable = runnable
        handler.postDelayed(runnable, 10000)
    }

    private fun showChannelBanner() {
        val navItem = ChannelNavigationManager.current()
        val title = navItem?.title ?: currentStreamTitle
        val sub = navItem?.subtitle ?: currentSubtitle
        val img = navItem?.imageUrl ?: currentImageUrl

        tvChannelBannerTitle.text = title
        if (!sub.isNullOrEmpty()) {
            tvChannelBannerSub.text = sub
            tvChannelBannerSub.visibility = View.VISIBLE
        } else {
            tvChannelBannerSub.visibility = View.GONE
        }

        if (ChannelNavigationManager.hasChannels && ChannelNavigationManager.totalCount > 1) {
            tvChannelBannerIndex.text = "Canal ${ChannelNavigationManager.currentPositionText}  [◀ ▶ Zapping]"
            tvChannelBannerIndex.visibility = View.VISIBLE
        } else {
            tvChannelBannerIndex.visibility = View.GONE
        }

        if (!img.isNullOrEmpty()) {
            Glide.with(this)
                .load(img)
                .placeholder(R.mipmap.ic_launcher)
                .error(R.mipmap.ic_launcher)
                .into(ivChannelLogo)
        } else {
            ivChannelLogo.setImageResource(R.mipmap.ic_launcher)
        }

        llChannelBanner.animate().cancel()
        llChannelBanner.alpha = 1f
        llChannelBanner.visibility = View.VISIBLE

        hideBannerRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            llChannelBanner.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction {
                    llChannelBanner.visibility = View.GONE
                }
                .start()
        }
        hideBannerRunnable = runnable
        handler.postDelayed(runnable, 3500)
    }

    private fun zapToNextChannel() {
        val next = ChannelNavigationManager.next() ?: return
        playChannelItem(next)
    }

    private fun zapToPreviousChannel() {
        val prev = ChannelNavigationManager.previous() ?: return
        playChannelItem(prev)
    }

    private fun playChannelItem(channel: ChannelItem) {
        currentStreamTitle = channel.title
        currentSubtitle = channel.subtitle
        currentImageUrl = channel.imageUrl
        currentResolutionText = ""
        tvResolutionBadge.visibility = View.GONE

        // Feedback visual inmediato en la TV con el nuevo canal
        showChannelBanner()

        // Detener stream anterior y mostrar buffering
        player?.stop()
        player?.clearMediaItems()
        playerBuffering.visibility = View.VISIBLE
        playerError.visibility = View.GONE

        watchdogRunnable?.let { handler.removeCallbacks(it) }
        zapJob?.cancel()

        zapJob = lifecycleScope.launch {
            try {
                val resolved = ApiClient.resolveStreamUrl(channel.stream)
                if (currentStreamTitle != channel.title) return@launch

                currentStreamUrl = resolved.url
                loadAndPlayMedia(resolved.url, resolved.drmLicenseUrl, resolved.userAgent, channel.title)

                SupabaseLogger.info(
                    "channel_zap",
                    "Zapping a canal: ${channel.title}",
                    mapOf("url" to resolved.url, "index" to ChannelNavigationManager.currentIndex)
                )
            } catch (e: Exception) {
                if (currentStreamTitle != channel.title) return@launch
                playerBuffering.visibility = View.GONE
                playerError.visibility = View.VISIBLE
                playerError.text = "⚠️ No se pudo conectar a este canal.\nPresiona ◀ o ▶ para sintonizar otra señal."
            }
        }
    }

    private fun getQualityLabel(height: Int): String {
        return when {
            height >= 2160 -> "4K UHD"
            height >= 1080 -> "1080p FHD"
            height >= 720 -> "720p HD"
            height >= 576 -> "576p"
            height >= 480 -> "480p"
            else -> "${height}p"
        }
    }

    private fun showResolutionBadge(text: String) {
        tvResolutionBadge.text = text
        tvResolutionBadge.visibility = View.VISIBLE

        hideBadgeRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            tvResolutionBadge.visibility = View.GONE
        }
        hideBadgeRunnable = runnable
        handler.postDelayed(runnable, 4500)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (ChannelNavigationManager.totalCount > 1) {
                        zapToNextChannel()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (ChannelNavigationManager.totalCount > 1) {
                        zapToPreviousChannel()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    showChannelBanner()
                    playerView.showController()
                    if (currentResolutionText.isNotEmpty()) {
                        showResolutionBadge(currentResolutionText)
                    }
                    return true
                }
                KeyEvent.KEYCODE_INFO -> {
                    showChannelBanner()
                    if (currentResolutionText.isNotEmpty()) {
                        showResolutionBadge(currentResolutionText)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    player?.let {
                        if (it.isPlaying) it.pause() else it.play()
                        return true
                    }
                }
                KeyEvent.KEYCODE_BACK -> {
                    finish()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideBadgeRunnable?.let { handler.removeCallbacks(it) }
        hideBannerRunnable?.let { handler.removeCallbacks(it) }
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        zapJob?.cancel()
        player?.release()
        player = null
    }
}
