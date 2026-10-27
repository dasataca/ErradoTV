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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.gareca.tv.api.ApiClient
import com.gareca.tv.model.StreamOption
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
    private var hideControlsRunnable: Runnable? = null
    private var watchdogRunnable: Runnable? = null

    private var videoFrameReceived = false
    private var currentResolutionText = ""
    private var currentStreamUrl = ""
    private var currentStreamTitle = ""
    private var currentSubtitle: String? = null
    private var currentImageUrl: String? = null
    private var currentDrmLicenseUrl: String? = null
    private var currentCustomUserAgent: String? = null
    private var currentOriginalStream: StreamOption? = null
    private var zapJob: Job? = null

    // Resiliencia y Auto-Recuperación HLS ante micro-cortes transitorios
    private var retryCount = 0
    private val retryDelaysMs = longArrayOf(500L, 2000L, 4000L)
    private var retryRunnable: Runnable? = null
    private var recoveryJob: Job? = null
    private var isRevalidating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        playerBuffering = findViewById(R.id.playerBuffering)
        playerError = findViewById(R.id.playerError)
        tvResolutionBadge = findViewById(R.id.tvResolutionBadge)

        // Configurar PlayerView para que no mantenga los controles fijos en pantalla
        playerView.controllerAutoShow = false
        playerView.controllerHideOnTouch = true
        playerView.controllerShowTimeoutMs = 3500
        playerView.hideController()

        llChannelBanner = findViewById(R.id.llChannelBanner)
        ivChannelLogo = findViewById(R.id.ivChannelLogo)
        tvChannelBannerTitle = findViewById(R.id.tvChannelBannerTitle)
        tvChannelBannerSub = findViewById(R.id.tvChannelBannerSub)
        tvChannelBannerIndex = findViewById(R.id.tvChannelBannerIndex)

        val streamUrl = intent.getStringExtra("EXTRA_URL") ?: ""
        val streamTitle = intent.getStringExtra("EXTRA_TITLE") ?: ""
        val drmLicenseUrl = intent.getStringExtra("EXTRA_DRM_LICENSE")
        val customUserAgent = intent.getStringExtra("EXTRA_USER_AGENT")
        val originalUrl = intent.getStringExtra("EXTRA_ORIGINAL_URL") ?: streamUrl
        val originalType = intent.getStringExtra("EXTRA_ORIGINAL_TYPE") ?: "exo"
        currentImageUrl = intent.getStringExtra("EXTRA_IMAGE_URL")
        currentSubtitle = intent.getStringExtra("EXTRA_SUBTITLE")

        if (streamUrl.isEmpty()) {
            finish()
            return
        }

        currentStreamUrl = streamUrl
        currentStreamTitle = streamTitle
        currentDrmLicenseUrl = drmLicenseUrl
        currentCustomUserAgent = customUserAgent
        currentOriginalStream = ChannelNavigationManager.current()?.stream ?: StreamOption(
            title = streamTitle,
            url = originalUrl,
            type = originalType,
            drmLicenseUrl = drmLicenseUrl,
            userAgent = customUserAgent
        )

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

            // Capa 1 (Búfer de absorción): DefaultLoadControl para tolerar micro-cortes HLS en RAM (25s a 40s)
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    25_000, // minBufferMs: 25s
                    40_000, // maxBufferMs: 40s
                    1_000,  // bufferForPlaybackMs: 1s para inicio veloz
                    1_500   // bufferForPlaybackAfterRebufferMs: 1500ms
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val exoPlayer = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build()

            player = exoPlayer
            playerView.player = exoPlayer

            exoPlayer.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoFrameReceived = true
                        resetRecoveryState()
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
                            resetRecoveryState()
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
                    handlePlayerError(error)
                }
            })

            loadAndPlayMedia(url, drmLicenseUrl, customUserAgent, title)
        } catch (t: Throwable) {
            t.printStackTrace()
            handleDefinitiveFailure(PlaybackException("Error inicializando reproductor: ${t.message}", t, PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
        }
    }

    private fun handlePlayerError(error: PlaybackException) {
        if (isFinishing || isDestroyed) return

        SupabaseLogger.warn(
            "player_error_transient",
            "Error en reproducción (${error.errorCodeName}, código ${error.errorCode}). Evaluando auto-recuperación (intento $retryCount de 3).",
            mapOf("url" to currentStreamUrl, "errorCode" to error.errorCode, "errorName" to error.errorCodeName, "title" to currentStreamTitle)
        )

        // Capa 2 (Reconexión silenciosa progresiva): hasta 3 reintentos (500ms, 2s, 4s)
        if (retryCount < retryDelaysMs.size) {
            val delay = retryDelaysMs[retryCount]
            retryCount++

            playerBuffering.visibility = View.VISIBLE
            playerError.visibility = View.GONE

            retryRunnable?.let { handler.removeCallbacks(it) }
            watchdogRunnable?.let { handler.removeCallbacks(it) }

            val r = Runnable {
                if (!isFinishing && !isDestroyed) {
                    SupabaseLogger.info(
                        "player_retry_execute",
                        "Ejecutando auto-reintento #$retryCount tras ${delay}ms para $currentStreamTitle",
                        mapOf("url" to currentStreamUrl)
                    )
                    retryPlayback()
                }
            }
            retryRunnable = r
            handler.postDelayed(r, delay)
            return
        }

        // Capa 3 (Revalidación puntual): Si tras 3 reintentos no reengancha, consultar resolver puntual
        if (!isRevalidating) {
            isRevalidating = true
            playerBuffering.visibility = View.VISIBLE
            playerError.visibility = View.GONE
            watchdogRunnable?.let { handler.removeCallbacks(it) }

            recoveryJob?.cancel()
            recoveryJob = lifecycleScope.launch {
                try {
                    SupabaseLogger.info(
                        "player_revalidate_stream",
                        "Iniciando revalidación puntual de enlace para: $currentStreamTitle",
                        mapOf("url" to currentStreamUrl)
                    )
                    val targetStream = currentOriginalStream ?: ChannelNavigationManager.current()?.stream ?: StreamOption(
                        title = currentStreamTitle,
                        url = currentStreamUrl,
                        type = "exo",
                        drmLicenseUrl = currentDrmLicenseUrl,
                        userAgent = currentCustomUserAgent
                    )

                    val reResolved = ApiClient.resolveStreamUrl(targetStream)
                    if (isFinishing || isDestroyed) return@launch

                    currentStreamUrl = reResolved.url
                    currentDrmLicenseUrl = reResolved.drmLicenseUrl
                    currentCustomUserAgent = reResolved.userAgent

                    retryPlayback()
                } catch (e: Exception) {
                    e.printStackTrace()
                    handleDefinitiveFailure(error)
                }
            }
            return
        }

        // Capa 4 (Fallo definitivo): Solo si tras reintentos y revalidación sigue sin responder
        handleDefinitiveFailure(error)
    }

    private fun resetRecoveryState() {
        retryCount = 0
        isRevalidating = false
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
        recoveryJob?.cancel()
        recoveryJob = null
    }

    private fun buildMediaSource(url: String, drmLicenseUrl: String? = null, customUserAgent: String? = null): MediaSource {
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

        // Colchón de Latencia Objetivo para Canales en Vivo (12s de margen de protección constante)
        val isReplay = url.contains("refooty") || url.contains("_replay") || (currentSubtitle?.contains("Repetición", ignoreCase = true) == true)
        if (!isReplay) {
            val liveConfig = MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(12_000L) // 12 segundos detrás del live edge para absorber micro-cortes del CDN
                .setMinOffsetMs(6_000L)     // Mínimo 6s para evitar caer en el borde del CDN
                .setMaxOffsetMs(20_000L)    // Máximo 20s
                .setMinPlaybackSpeed(0.97f) // Micro-ajuste imperceptible de velocidad para preservar el colchón
                .setMaxPlaybackSpeed(1.03f)
                .build()
            mediaItemBuilder.setLiveConfiguration(liveConfig)
        }

        val mediaItem = mediaItemBuilder.build()
        return mediaSourceFactory.createMediaSource(mediaItem)
    }

    private fun loadAndPlayMedia(url: String, drmLicenseUrl: String? = null, customUserAgent: String? = null, title: String = "") {
        val exoPlayer = player ?: return
        try {
            val mediaSource = buildMediaSource(url, drmLicenseUrl, customUserAgent)
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true

            armWatchdog(url, title)
        } catch (t: Throwable) {
            t.printStackTrace()
            handleDefinitiveFailure(PlaybackException("Error cargando media: ${t.message}", t, PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
        }
    }

    private fun retryPlayback() {
        val exoPlayer = player ?: return
        try {
            val mediaSource = buildMediaSource(currentStreamUrl, currentDrmLicenseUrl, currentCustomUserAgent)
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            armWatchdog(currentStreamUrl, currentStreamTitle)
        } catch (t: Throwable) {
            t.printStackTrace()
            exoPlayer.playerError?.let { handlePlayerError(it) } ?: handleDefinitiveFailure()
        }
    }

    private fun handleDefinitiveFailure(error: PlaybackException? = null) {
        resetRecoveryState()
        watchdogRunnable?.let { handler.removeCallbacks(it) }

        StreamCache.markUnavailable(
            this@PlayerActivity,
            currentStreamUrl,
            reason = "player_error_definitive: ${error?.errorCodeName ?: "reconnect_failed"}",
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
            "player_error_definitive",
            "Agotados reintentos y revalidación. Señal no disponible: ${error?.message ?: "sin respuesta"}",
            mapOf(
                "url" to currentStreamUrl,
                "errorCode" to (error?.errorCode ?: -1),
                "errorName" to (error?.errorCodeName ?: "UNKNOWN"),
                "title" to currentStreamTitle
            )
        )
    }

    private fun armWatchdog(url: String, title: String) {
        videoFrameReceived = false
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            if (!videoFrameReceived && !isFinishing && !isDestroyed && currentStreamUrl == url) {
                if (retryCount < retryDelaysMs.size || !isRevalidating) {
                    SupabaseLogger.warn("watchdog_timeout_retry", "Watchdog sin fotogramas tras 10s: activando auto-reintento", mapOf("url" to url, "title" to title))
                    player?.let { p ->
                        p.playerError?.let { handlePlayerError(it) } ?: handlePlayerError(PlaybackException("Watchdog sin fotogramas tras 10s", null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
                    } ?: handleDefinitiveFailure()
                } else {
                    handleDefinitiveFailure()
                }
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
        currentOriginalStream = channel.stream
        currentResolutionText = ""
        tvResolutionBadge.visibility = View.GONE

        resetRecoveryState()

        // Feedback visual inmediato en la TV con el nuevo canal
        showChannelBanner()

        // Ocultar controles multimedia inmediatamente al hacer zapping para que no queden fijos
        playerView.hideController()
        hideControlsRunnable?.let { handler.removeCallbacks(it) }

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
                currentDrmLicenseUrl = resolved.drmLicenseUrl
                currentCustomUserAgent = resolved.userAgent

                loadAndPlayMedia(resolved.url, resolved.drmLicenseUrl, resolved.userAgent, channel.title)

                SupabaseLogger.info(
                    "channel_zap",
                    "Zapping a canal: ${channel.title}",
                    mapOf("url" to resolved.url, "index" to ChannelNavigationManager.currentIndex)
                )
            } catch (e: Exception) {
                if (currentStreamTitle != channel.title) return@launch
                handleDefinitiveFailure()
            }
        }
    }

    private fun showControlsTemporarily() {
        playerView.showController()
        hideControlsRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            if (!isFinishing && !isDestroyed) {
                playerView.hideController()
            }
        }
        hideControlsRunnable = r
        handler.postDelayed(r, 3500)
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
                    showControlsTemporarily()
                    if (currentResolutionText.isNotEmpty()) {
                        showResolutionBadge(currentResolutionText)
                    }
                    return true
                }
                KeyEvent.KEYCODE_INFO -> {
                    showChannelBanner()
                    showControlsTemporarily()
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
        resetRecoveryState()
        hideBadgeRunnable?.let { handler.removeCallbacks(it) }
        hideBannerRunnable?.let { handler.removeCallbacks(it) }
        hideControlsRunnable?.let { handler.removeCallbacks(it) }
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        zapJob?.cancel()
        player?.release()
        player = null
    }
}
