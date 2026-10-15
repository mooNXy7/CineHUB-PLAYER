package com.cinehub.player

import android.app.Activity
import android.app.ActivityManager
import android.net.Uri
import android.graphics.Color
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import org.json.JSONArray

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
    private var seriesId = ""
    private var subtitleUrl: String? = null
    private var startupCheck: Runnable? = null
    private val handler = android.os.Handler(mainLooper)
    private val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var speedIndex = 1
    private lateinit var speedButton: Button
    private var subtitleLarge = false
    private var subtitleBackground = true
    private data class QueuedEpisode(val title: String, val contentId: String, val sources: List<String>)
    private var episodeQueue: List<QueuedEpisode> = emptyList()
    private var queueIndex = 0
    private var nextEpisodeDialog: android.app.AlertDialog? = null
    private var nextEpisodeRunnable: Runnable? = null
    private var nextPromptShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        isLive = intent.getBooleanExtra("live", false)
        contentId = intent.getStringExtra("content_id") ?: "unknown"
        seriesId = intent.getStringExtra("series_id").orEmpty()
        subtitleUrl = intent.getStringExtra("subtitle_url")
        episodeQueue = parseEpisodeQueue(intent.getStringExtra("episode_queue").orEmpty())
        val urls = intent.getStringArrayListExtra("sources")?.toList().orEmpty()
        val singleUrl = intent.getStringExtra("url") ?: intent.dataString
        sourceUrls = (urls + listOfNotNull(singleUrl))
            .map(String::trim).filter(String::isNotEmpty).distinct()

        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            keepScreenOn = true
        }
        val root = FrameLayout(this)
        root.addView(playerView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.argb(190, 20, 20, 20))
        }
        controls.addView(Button(this).apply {
            text = "Áudio / Legendas"
            contentDescription = "Selecionar faixas de áudio e legendas"
            setOnClickListener { showTrackMenu() }
        })
        controls.addView(Button(this).apply {
            text = "Legenda externa"
            contentDescription = "Adicionar legenda por URL"
            setOnClickListener { promptExternalSubtitle() }
        })
        controls.addView(Button(this).apply {
            text = "Estilo legenda"
            contentDescription = "Alterar tamanho e fundo da legenda"
            setOnClickListener { cycleSubtitleStyle() }
        })
        speedButton = Button(this).apply {
            text = "Velocidade 1.0×"
            contentDescription = "Alterar velocidade de reprodução"
            setOnClickListener { cycleSpeed() }
        }
        controls.addView(speedButton)
        root.addView(controls, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ))
        setContentView(root)
        TvUi.style(root)

        if (sourceUrls.isEmpty()) {
            Toast.makeText(this, "Nenhuma fonte de reprodução recebida.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val profile = getSharedPreferences("player-settings", MODE_PRIVATE)
            .getString("memory_profile", "auto") ?: "auto"
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        // Use device memory, not the app heap cap: a 1 GB TV box can have a larger heap.
        val lowMemory = when (profile) {
            "low" -> true
            "normal" -> false
            else -> activityManager.isLowRamDevice ||
                memoryInfo.totalMem <= 1_536L * 1024L * 1024L
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (lowMemory) 2_500 else 5_000,
                if (lowMemory) 12_000 else 30_000,
                if (lowMemory) 750 else 1_000,
                if (lowMemory) 1_500 else 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

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
                            if (seriesId.isNotBlank()) {
                                val activeUrl = sourceUrls.getOrNull(currentIndex).orEmpty()
                                val host = runCatching { Uri.parse(activeUrl).host.orEmpty() }.getOrDefault("")
                                if (host.isNotBlank()) {
                                    getSharedPreferences("player-settings", MODE_PRIVATE).edit()
                                        .putString("preferred_source_host_$seriesId", host)
                                        .apply()
                                }
                            }
                            if (!isLive) {
                                val savedPosition = getSharedPreferences("playback-progress", MODE_PRIVATE)
                                    .getLong(contentId, 0L)
                                if (savedPosition > 0L && (this@PlayerActivity.player?.currentPosition ?: 0L) < 5_000L) {
                                    this@PlayerActivity.player?.seekTo(savedPosition)
                                }
                            }
                        } else if (playbackState == Player.STATE_ENDED && !isLive) {
                            showNextEpisodePrompt()
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

    private fun parseEpisodeQueue(raw: String): List<QueuedEpisode> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val urls = item.optJSONArray("sources") ?: continue
                    val sources = buildList {
                        for (i in 0 until urls.length()) {
                            val url = urls.optString(i).trim()
                            if (url.startsWith("https://") || url.startsWith("http://")) add(url)
                        }
                    }.distinct()
                    if (sources.isNotEmpty()) {
                        add(QueuedEpisode(
                            item.optString("title", "Próximo episódio"),
                            item.optString("content_id", "episode-$index"),
                            sources
                        ))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun showNextEpisodePrompt() {
        if (nextPromptShown || queueIndex >= episodeQueue.size) return
        nextPromptShown = true
        val next = episodeQueue[queueIndex]
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Próximo episódio")
            .setMessage(next.title + "\n\nReprodução automática em 10 segundos.")
            .setPositiveButton("Reproduzir agora") { _, _ -> playQueuedEpisode() }
            .setNegativeButton("Parar") { _, _ -> cancelNextEpisodeCountdown() }
            .create()
        nextEpisodeDialog = dialog
        dialog.setOnCancelListener { cancelNextEpisodeCountdown() }
        dialog.show()
        nextEpisodeRunnable = Runnable {
            if (!isFinishing && nextEpisodeDialog?.isShowing == true) {
                nextEpisodeDialog?.dismiss()
                playQueuedEpisode()
            }
        }.also { handler.postDelayed(it, 10_000L) }
    }

    private fun cancelNextEpisodeCountdown() {
        nextEpisodeRunnable?.let(handler::removeCallbacks)
        nextEpisodeRunnable = null
        nextEpisodeDialog = null
    }

    private fun playQueuedEpisode() {
        cancelNextEpisodeCountdown()
        if (queueIndex !in episodeQueue.indices) return
        val next = episodeQueue[queueIndex++]
        contentId = next.contentId
        val preferredHost = if (seriesId.isNotBlank()) {
            getSharedPreferences("player-settings", MODE_PRIVATE)
                .getString("preferred_source_host_$seriesId", null)
        } else null
        sourceUrls = if (preferredHost.isNullOrBlank()) next.sources else {
            next.sources.sortedBy { url ->
                if (runCatching { Uri.parse(url).host }.getOrNull().equals(preferredHost, ignoreCase = true)) 0 else 1
            }
        }
        attempted.clear()
        currentIndex = 0
        nextPromptShown = false
        playSource(0)
    }

    private fun showTrackMenu() {
        val exo = player ?: return
        val options = listOf(
            Triple("Áudio", C.TRACK_TYPE_AUDIO, false),
            Triple("Legendas", C.TRACK_TYPE_TEXT, false)
        ).filter { (_, type, _) -> exo.currentTracks.groups.any { it.type == type && it.length > 0 } }
        if (options.isEmpty()) {
            Toast.makeText(this, "Não há faixas alternativas disponíveis nesta fonte.", Toast.LENGTH_SHORT).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Faixas de reprodução")
            .setItems(options.map { it.first }.toTypedArray()) { _, index ->
                val (title, type, adaptive) = options[index]
                TrackSelectionDialogBuilder(this, title, exo, type)
                    .setAllowAdaptiveSelections(adaptive)
                    .setShowDisableOption(type == C.TRACK_TYPE_TEXT)
                    .build()
                    .show()
            }
            .show()
    }

    private fun cycleSpeed() {
        if (isLive) {
            Toast.makeText(this, "Velocidade indisponível para transmissão ao vivo.", Toast.LENGTH_SHORT).show()
            return
        }
        speedIndex = (speedIndex + 1) % speeds.size
        val speed = speeds[speedIndex]
        player?.setPlaybackParameters(PlaybackParameters(speed))
        speedButton.text = "Velocidade " + speed + "×"
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

        player?.apply {
            stop()
            clearMediaItems()
            setMediaItem(buildMediaItem(url))
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

    private fun buildMediaItem(url: String): MediaItem {
        val itemBuilder = MediaItem.Builder().setUri(Uri.parse(url))
        subtitleUrl?.let { subtitle ->
            val lower = subtitle.substringBefore('?').lowercase()
            val mime = when {
                lower.endsWith(".srt") -> "application/x-subrip"
                lower.endsWith(".vtt") -> "text/vtt"
                lower.endsWith(".ssa") || lower.endsWith(".ass") -> "text/x-ssa"
                else -> "text/vtt"
            }
            itemBuilder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle))
                        .setMimeType(mime)
                        .setLanguage("pt")
                        .setSelectionFlags(0)
                        .build()
                )
            )
        }
        return itemBuilder.build()
    }

    private fun cycleSubtitleStyle() {
        subtitleLarge = !subtitleLarge
        subtitleBackground = !subtitleBackground
        playerView.subtitleView?.apply {
            setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, if (subtitleLarge) 26f else 20f)
            setStyle(
                CaptionStyleCompat(
                    Color.WHITE,
                    if (subtitleBackground) Color.argb(190, 0, 0, 0) else Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    Color.BLACK,
                    null
                )
            )
        }
        Toast.makeText(
            this,
            if (subtitleLarge) "Legenda grande · fundo ligado" else "Legenda padrão · fundo desligado",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun promptExternalSubtitle() {
        val input = EditText(this).apply {
            hint = "https://.../legenda.vtt ou .srt"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(subtitleUrl.orEmpty())
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Adicionar legenda externa")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aplicar") { _, _ ->
                val candidate = input.text?.toString()?.trim().orEmpty()
                if (!candidate.startsWith("https://") && !candidate.startsWith("http://")) {
                    Toast.makeText(this, "Informe uma URL HTTP(S) válida.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                subtitleUrl = candidate
                val position = player?.currentPosition ?: 0L
                val url = sourceUrls.getOrNull(currentIndex) ?: return@setPositiveButton
                player?.setMediaItem(buildMediaItem(url), position)
                player?.prepare()
                player?.playWhenReady = true
            }
            .show()
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
            val exo = player
            val position = exo?.currentPosition ?: 0L
            val duration = exo?.duration ?: C.TIME_UNSET
            // Do not resume at the final frame after a completed movie/episode.
            val savedPosition = if (duration > 0L && position >= duration - 60_000L) 0L else position
            getSharedPreferences("playback-progress", MODE_PRIVATE).edit()
                .putLong(contentId, savedPosition).apply()
        }
    }

    override fun onDestroy() {
        startupCheck?.let(handler::removeCallbacks)
        cancelNextEpisodeCountdown()
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
