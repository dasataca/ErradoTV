package com.errado.tv

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.errado.tv.telemetry.SupabaseLogger

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var playerBuffering: ProgressBar
    private lateinit var playerError: TextView
    private lateinit var tvResolutionBadge: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var hideBadgeRunnable: Runnable? = null
    private var currentResolutionText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        playerBuffering = findViewById(R.id.playerBuffering)
        playerError = findViewById(R.id.playerError)
        tvResolutionBadge = findViewById(R.id.tvResolutionBadge)

        val streamUrl = intent.getStringExtra("EXTRA_URL") ?: ""
        if (streamUrl.isEmpty()) {
            finish()
            return
        }

        initializePlayer(streamUrl)
    }

    private fun initializePlayer(url: String) {
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

        val mediaItem = MediaItem.fromUri(Uri.parse(url))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.playWhenReady = true

        exoPlayer.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val label = "${videoSize.width}x${videoSize.height} (${getQualityLabel(videoSize.height)}) [MÁX]"
                    currentResolutionText = label
                    showResolutionBadge(label)

                    SupabaseLogger.info(
                        "stream_resolution",
                        "Resolución máxima aplicada: $label",
                        mapOf("width" to videoSize.width, "height" to videoSize.height, "url" to url)
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
                        SupabaseLogger.info("player_ready", "Stream reproduciendo exitosamente", mapOf("url" to url))
                    }
                    Player.STATE_ENDED -> {
                        playerBuffering.visibility = View.GONE
                    }
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playerBuffering.visibility = View.GONE
                playerError.visibility = View.VISIBLE
                playerError.text = "Error al reproducir el enlace:\n${error.message}"
                SupabaseLogger.error(
                    "player_error",
                    "Error de reproducción en ExoPlayer: ${error.message}",
                    mapOf("url" to url, "errorCode" to error.errorCode, "errorName" to error.errorCodeName)
                )
            }
        })

        exoPlayer.prepare()
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let {
                    if (it.isPlaying) it.pause() else it.play()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_INFO -> {
                if (currentResolutionText.isNotEmpty()) {
                    showResolutionBadge(currentResolutionText)
                    return true
                }
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideBadgeRunnable?.let { handler.removeCallbacks(it) }
        player?.release()
        player = null
    }
}
