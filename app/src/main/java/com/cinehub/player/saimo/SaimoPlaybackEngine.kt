package com.cinehub.player.saimo

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Playback session ported from Saimo TV's playback path.
 *
 * It deliberately does not own the Activity or close the screen on a source
 * failure. The host decides how to present an exhausted-source state.
 */
@UnstableApi
class SaimoPlaybackEngine(
    context: Context,
    private val live: Boolean = false,
    private val onState: (State) -> Unit = {},
) : AutoCloseable {

    data class State(
        val sourceIndex: Int,
        val sourceCount: Int,
        val buffering: Boolean,
        val playing: Boolean,
        val error: PlaybackException? = null,
        val exhausted: Boolean = false,
    )

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val lowMemory = runCatching {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        manager?.isLowRamDevice == true || manager?.let {
            val info = android.app.ActivityManager.MemoryInfo()
            it.getMemoryInfo(info)
            info.totalMem in 1..(1536L * 1024L * 1024L)
        } == true
    }.getOrDefault(false)

    private val trackSelector = DefaultTrackSelector(appContext)
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            if (lowMemory) 2_500 else 5_000,
            if (lowMemory) 12_000 else 30_000,
            if (lowMemory) 750 else 1_000,
            if (lowMemory) 1_500 else 2_000,
        )
        .setTargetBufferBytes(if (lowMemory) 32 * 1024 * 1024 else 96 * 1024 * 1024)
        .setPrioritizeTimeOverSizeThresholds(!lowMemory)
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .setRenderersFactory(DefaultRenderersFactory(appContext).setEnableDecoderFallback(true))
        .build()

    private var sources: List<Source> = emptyList()
    private var currentIndex = -1
    private val attempted = linkedSetOf<Int>()
    private var generation = 0
    private var startedAtPosition = 0L
    private var closed = false

    private val startupDeadline = Runnable {
        if (!closed && player.playbackState != Player.STATE_READY &&
            player.playbackState != Player.STATE_ENDED) {
            advance(lastError = null)
        }
    }

    private var lastError: PlaybackException? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                    handler.removeCallbacks(startupDeadline)
                }
                emit()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = emit()

            override fun onPlayerError(error: PlaybackException) {
                lastError = error
                advance(error)
            }
        })
    }

    fun setSources(input: List<Source>, startPositionMs: Long = 0L, preferredIndex: Int = 0) {
        check(!closed) { "Playback engine is closed" }
        sources = input.filter { isHttpUrl(it.url) }.distinctBy { it.url }
        attempted.clear()
        currentIndex = -1
        startedAtPosition = startPositionMs.coerceAtLeast(0L)
        lastError = null
        generation++
        if (sources.isEmpty()) {
            onState(State(-1, 0, buffering = false, playing = false, exhausted = true))
            return
        }
        val first = preferredIndex.takeIf { it in sources.indices } ?: 0
        playSource(first)
    }

    fun retry() {
        if (closed || sources.isEmpty()) return
        attempted.clear()
        playSource((currentIndex + 1).takeIf { it in sources.indices } ?: 0)
    }

    fun switchTo(index: Int) {
        if (closed || index !in sources.indices) return
        playSource(index)
    }

    private fun playSource(index: Int) {
        if (closed || index !in sources.indices || !attempted.add(index)) {
            advance(lastError)
            return
        }
        generation++
        val myGeneration = generation
        handler.removeCallbacks(startupDeadline)
        currentIndex = index
        val resumeAt = if (player.playbackState == Player.STATE_READY) player.currentPosition else startedAtPosition
        try {
            player.stop()
            player.clearMediaItems()
            player.setMediaSource(Playback.mediaSource(appContext, sources[index]))
            player.prepare()
            player.seekTo(resumeAt.coerceAtLeast(0L))
            player.playWhenReady = true
            handler.postDelayed({
                if (!closed && generation == myGeneration &&
                    player.playbackState != Player.STATE_READY &&
                    player.playbackState != Player.STATE_ENDED) {
                    advance(lastError)
                }
            }, SOURCE_TIMEOUT_MS)
            emit()
        } catch (failure: Throwable) {
            lastError = PlaybackException("Source initialization failed: ${failure.message}", failure, C.ERROR_CODE_IO_UNSPECIFIED)
            advance(lastError)
        }
    }

    private fun advance(error: PlaybackException?) {
        if (closed) return
        handler.removeCallbacks(startupDeadline)
        lastError = error ?: lastError
        val next = sources.indices.firstOrNull { it !in attempted }
        if (next == null) {
            player.playWhenReady = false
            onState(State(currentIndex, sources.size, buffering = false, playing = false, error = lastError, exhausted = true))
            return
        }
        playSource(next)
    }

    private fun emit() {
        if (closed) return
        onState(
            State(
                sourceIndex = currentIndex,
                sourceCount = sources.size,
                buffering = player.playbackState == Player.STATE_BUFFERING,
                playing = player.isPlaying,
                error = lastError,
            )
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        generation++
        handler.removeCallbacksAndMessages(null)
        player.release()
    }

    private fun isHttpUrl(value: String): Boolean = runCatching {
        val uri = java.net.URL(value)
        (uri.protocol == "http" || uri.protocol == "https") && uri.host.isNotBlank() &&
            uri.userInfo == null
    }.getOrDefault(false)

    companion object {
        const val SOURCE_TIMEOUT_MS = 15_000L
    }
}
