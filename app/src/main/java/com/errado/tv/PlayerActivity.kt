package com.errado.tv

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.errado.tv.telemetry.SupabaseLogger

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var playerBuffering: ProgressBar
    private lateinit var playerError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        playerBuffering = findViewById(R.id.playerBuffering)
        playerError = findViewById(R.id.playerError)

        val streamUrl = intent.getStringExtra("EXTRA_URL") ?: ""
        if (streamUrl.isEmpty()) {
            finish()
            return
        }

        initializePlayer(streamUrl)
    }

    private fun initializePlayer(url: String) {
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer

        val mediaItem = MediaItem.fromUri(Uri.parse(url))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.playWhenReady = true

        exoPlayer.addListener(object : Player.Listener {
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
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
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
