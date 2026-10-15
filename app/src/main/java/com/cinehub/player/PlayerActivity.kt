package com.cinehub.player

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Minimal native playback shell. Launch with:
 * ACTION_VIEW data=<stream URL>, or extras "url", "sources" (StringArrayList),
 * "live" (Boolean), "content_id" and optional "subtitle_url".
 */
@UnstableApi
class PlayerActivity : Activity() {
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var sourceUrls: List<String> = emptyList()
    private var attempted = mutableSetOf<String>()
    private var currentIndex = 0
    private var isLive = false
    private var contentId = "unknown"
    private var subtitleUrl: String? = null
    private var startupCheck: Runnable? = null
    private val handler = android.os.Handler(mainLooper)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        isLive = intent.getBooleanExtra("live", false)
        contentId = intent.getStringExtra("content_id") ?: "unknown"
        subtitleUrl = intent.getStringExtra("subtitle_url")
        val urls = intent.getStringArrayListExtra("sources")?.toList().orEmpty()
        val singleUrl = intent.getStringExtra("url") ?: intent.dataString
        sourceUrls = (urls + listOfNotNull(singleUrl))
            .map(String::trim).filter(String::isNotEmpty).distinct()

        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            keepScreenOn = true
        }
        setContentView(FrameLayout(this).apply { addView(playerView) })

        if (sourceUrls.isEmpty()) {
            Toast.makeText(this, "Nenhuma fonte de reprodução recebida.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val lowMemory = Runtime.getRuntime().maxMemory() < 256L * 1024L * 1024L
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (lowMemory) 5_000 else 10_000,
                if (lowMemory) 20_000 else 45_000,
                1_000,
                2_000
            ).build()

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build().also { exo ->
                playerView.player = exo
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        tryNextSource()
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            startupCheck?.let(handler::removeCallbacks)
                            startupCheck = null
                            if (!isLive) {
                                val savedPosition = getSharedPreferences("playback-progress", MODE_PRIVATE)
                                    .getLong(contentId, 0L)
                                if (savedPosition > 0L && currentPosition < 5_000L) {
                                    seekTo(savedPosition)
                                }
                            }
                        }
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        // LIVE must remain playing even if the controller or remote sends pause.
                        if (isLive && !playWhenReady) this@PlayerActivity.player?.play()
                    }
                })
            }
        playSource(0)
    }

    private fun playSource(index: Int) {
        if (index !in sourceUrls.indices) {
            Toast.makeText(this, "Todas as fontes disponíveis falharam.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        currentIndex = index
        val url = sourceUrls[index]
        if (!attempted.add(url)) {
            playSource(index + 1)
            return
        }

        val itemBuilder = MediaItem.Builder().setUri(Uri.parse(url))
        subtitleUrl?.let { subtitle ->
            itemBuilder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle))
                        .setMimeType(
                            when {
                                subtitle.endsWith(".vtt", true) -> "text/vtt"
                                subtitle.endsWith(".srt", true) -> "application/x-subrip"
                                else -> "text/vtt"
                            }
                        )
                        .setLanguage("pt")
                        .setSelectionFlags(0)
                        .build()
                )
            )
        }
        player?.apply {
            stop()
            clearMediaItems()
            setMediaItem(itemBuilder.build())
            prepare()
            playWhenReady = true
        }

        startupCheck?.let(handler::removeCallbacks)
        startupCheck = Runnable {
            if (player?.playbackState != Player.STATE_READY &&
                player?.playbackState != Player.STATE_ENDED) {
                tryNextSource()
            }
        }.also { handler.postDelayed(it, 15_000L) }
    }

    private fun tryNextSource() {
        startupCheck?.let(handler::removeCallbacks)
        startupCheck = null
        val next = sourceUrls.indices.firstOrNull { it > currentIndex && sourceUrls[it] !in attempted }
        if (next != null) playSource(next)
        else {
            Toast.makeText(this, "Não foi possível reproduzir este conteúdo.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isLive && (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE)) {
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && player != null) {
            player!!.playWhenReady = !player!!.playWhenReady
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        if (!isLive) {
            val position = player?.currentPosition ?: 0L
            getSharedPreferences("playback-progress", MODE_PRIVATE).edit()
                .putLong(contentId, position).apply()
        }
    }

    override fun onDestroy() {
        startupCheck?.let(handler::removeCallbacks)
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
