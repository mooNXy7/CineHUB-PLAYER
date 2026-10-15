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
import android.widget.HorizontalScrollView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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
    private var isRadio = false
    private var radioName = "Rádio CineHUB"
    private var radioGenre = ""
    private var radioExperience: RadioExperienceView? = null
    private var contentId = "unknown"
    private var seriesId = ""
    private var tmdbId = 0
    private var seasonNumber = 0
    private var episodeNumber = 0
    private var introMarkers: List<IntroMarker> = emptyList()
    private val promptedIntroMarkers = mutableSetOf<String>()
    private var introCheckRunnable: Runnable? = null
    private var introPrompt: android.app.AlertDialog? = null
    private var subtitleUrl: String? = null
    private var subtitleMimeTypeOverride: String? = null
    private var startupCheck: Runnable? = null
    private val handler = android.os.Handler(mainLooper)
    private val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var speedIndex = 1
    private lateinit var speedButton: Button
    private var subtitleLarge = false
    private var subtitleBackground = true
    private var episodeQueue: List<QueuedEpisode> = emptyList()
    private var queueIndex = 0
    private var nextEpisodeDialog: android.app.AlertDialog? = null
    private var nextEpisodeRunnable: Runnable? = null
    private var nextPromptShown = false
    private var failureDialogShown = false
    private var stillWatchingRunnable: Runnable? = null
    private var stillWatchingDialog: android.app.AlertDialog? = null
    private val stillWatchingIntervalMs = 2L * 60L * 60L * 1_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        isRadio = intent.getBooleanExtra("radio", false) ||
            intent.getStringExtra("content_type").equals("radio", ignoreCase = true)
        isLive = intent.getBooleanExtra("live", false) || isRadio
        radioName = intent.getStringExtra("radio_name")?.trim().orEmpty().ifBlank { "Rádio CineHUB" }
        radioGenre = intent.getStringExtra("radio_genre")?.trim().orEmpty()
        contentId = intent.getStringExtra("content_id") ?: "unknown"
        seriesId = intent.getStringExtra("series_id").orEmpty()
        tmdbId = intent.getIntExtra("tmdb_id", 0).coerceAtLeast(0)
        seasonNumber = intent.getIntExtra("season_number", 0).coerceAtLeast(0)
        episodeNumber = intent.getIntExtra("episode_number", 0).coerceAtLeast(0)
        // Never pass an untrusted intent subtitle URI to Media3 (e.g. file:// or content://).
        subtitleUrl = intent.getStringExtra("subtitle_url")
            ?.trim()
            ?.takeIf(PlaybackUrlPolicy::isSafeHttpUrl)
        episodeQueue = parseEpisodeQueue(intent.getStringExtra("episode_queue").orEmpty())
        val suppliedCurrentQueueIndex = intent.getIntExtra("episode_queue_current_index", -1)
        queueIndex = if (suppliedCurrentQueueIndex in episodeQueue.indices) {
            suppliedCurrentQueueIndex + 1
        } else {
            0
        }
        val urls = intent.getStringArrayListExtra("sources")?.toList().orEmpty()
        val singleUrl = intent.getStringExtra("url") ?: intent.dataString
        sourceUrls = (urls + listOfNotNull(singleUrl))
            .map(String::trim)
            .filter(PlaybackUrlPolicy::isSafeHttpUrl)
            .distinct()

        playerView = PlayerView(this).apply {
            useController = !isRadio
            if (isRadio) visibility = View.GONE
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            keepScreenOn = true
        }
        val root = FrameLayout(this)
        root.addView(playerView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        if (!isRadio) {
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
                text = "Legendas online"
                contentDescription = "Buscar legendas online ou inserir uma URL externa"
                setOnClickListener { showSubtitleOptions() }
            })
            controls.addView(Button(this).apply {
                text = "Estilo legenda"
                contentDescription = "Alterar tamanho e fundo da legenda"
                setOnClickListener { cycleSubtitleStyle() }
            })
            if (!isLive && episodeQueue.isNotEmpty()) {
                controls.addView(Button(this).apply {
                    text = "Temporadas / episódios"
                    contentDescription = "Abrir temporadas e episódios disponíveis"
                    setOnClickListener { showSeasonQueuePicker() }
                })
            }
            speedButton = Button(this).apply {
                text = "Velocidade 1.0×"
                contentDescription = "Alterar velocidade de reprodução"
                setOnClickListener { cycleSpeed() }
            }
            controls.addView(speedButton)
            // Keep all player actions reachable on narrow TV screens and 720p TV boxes.
            val controlsScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(controls, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ))
            }
            // Keep the custom action strip away from Media3's own timeline/controller at the bottom.
            root.addView(controlsScroll, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ))
    
        } else {
            val radioScreen = RadioExperienceView(
                context = this,
                stationName = radioName,
                genre = radioGenre,
                onBack = { finish() },
                onPlayPause = {
                    player?.let { exo ->
                        if (exo.isPlaying) exo.pause() else exo.play()
                    }
                }
            )
            radioExperience = radioScreen
            root.addView(radioScreen, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
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
        val trackSelector = DefaultTrackSelector(this)
        // Conservative ceiling for low-memory TV boxes; normal devices keep source-native resolution.
        if (lowMemory) {
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setMaxVideoSize(1280, 720)
                .build()
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
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build().also { exo ->
                if (!isRadio) playerView.player = exo
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        if (isRadio) radioExperience?.setPlaybackState(false, errorMessage = "Falha no sinal. Tentando outra fonte…")
                        tryNextSource()
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (isRadio) {
                            radioExperience?.setPlaybackState(
                                isPlaying = this@PlayerActivity.player?.isPlaying == true,
                                isBuffering = playbackState == Player.STATE_BUFFERING
                            )
                        }
                        if (playbackState == Player.STATE_BUFFERING && startupCheck == null) {
                            // Also protect against a stream that worked initially and then stalls.
                            startupCheck = Runnable {
                                if (player?.playbackState == Player.STATE_BUFFERING) tryNextSource()
                            }.also { handler.postDelayed(it, 15_000L) }
                        }
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
                                if (this@PlayerActivity.player?.isPlaying == true) scheduleStillWatchingPrompt()
                            }
                        } else if (playbackState == Player.STATE_ENDED && !isLive) {
                            showNextEpisodePrompt()
                        } else if (playbackState == Player.STATE_ENDED && isLive) {
                            // LIVE feeds can end cleanly at protocol level while the source is dead.
                            // Treat that as a source failure and try the next known endpoint.
                            handler.post {
                                if (!isFinishing && isLive) tryNextSource()
                            }
                        }
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        // TV channels stay continuous; radio deliberately supports pause/resume.
                        if (isLive && !isRadio && !playWhenReady) {
                            this@PlayerActivity.player?.play()
                        } else if (!isLive) {
                            if (playWhenReady) scheduleStillWatchingPrompt()
                            else cancelStillWatchingPrompt()
                        }
                        if (isRadio) {
                            radioExperience?.setPlaybackState(
                                isPlaying = this@PlayerActivity.player?.isPlaying == true,
                                isBuffering = this@PlayerActivity.player?.playbackState == Player.STATE_BUFFERING
                            )
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isRadio) {
                            radioExperience?.setPlaybackState(
                                isPlaying = isPlaying,
                                isBuffering = this@PlayerActivity.player?.playbackState == Player.STATE_BUFFERING
                            )
                        }
                    }
                })
            }
        playSource(0)
        loadIntroMarkers()
    }

    override fun onStart() {
        super.onStart()
        scheduleIntroCheck()
        scheduleStillWatchingPrompt()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        scheduleStillWatchingPrompt()
    }

    private fun scheduleStillWatchingPrompt() {
        cancelStillWatchingPrompt()
        if (isLive || isFinishing || player?.isPlaying != true) return
        stillWatchingRunnable = Runnable {
            stillWatchingRunnable = null
            if (isFinishing || isLive || player?.isPlaying != true) return@Runnable
            player?.pause()
            val dialog = android.app.AlertDialog.Builder(this)
                .setTitle("Ainda está assistindo?")
                .setMessage("A reprodução está ativa há duas horas. Deseja continuar?")
                .setPositiveButton("Continuar assistindo") { _, _ -> player?.play() }
                .setNegativeButton("Pausar") { _, _ -> player?.pause() }
                .create()
            stillWatchingDialog = dialog
            dialog.setOnCancelListener {
                stillWatchingDialog = null
                player?.play()
            }
            dialog.setOnDismissListener { stillWatchingDialog = null }
            dialog.show()
        }.also { handler.postDelayed(it, stillWatchingIntervalMs) }
    }

    private fun cancelStillWatchingPrompt() {
        stillWatchingRunnable?.let(handler::removeCallbacks)
        stillWatchingRunnable = null
    }

    private fun loadIntroMarkers() {
        introCheckRunnable?.let(handler::removeCallbacks)
        introCheckRunnable = null
        introMarkers = emptyList()
        promptedIntroMarkers.clear()
        introPrompt?.dismiss()
        introPrompt = null
        if (isLive || tmdbId <= 0) return
        IntroSkipClient.load(tmdbId, seasonNumber, episodeNumber) { markers ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                introMarkers = markers
                scheduleIntroCheck()
            }
        }
    }

    private fun scheduleIntroCheck() {
        if (isLive || introMarkers.isEmpty() || isFinishing || introCheckRunnable != null) return
        introCheckRunnable = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed || isLive) {
                    introCheckRunnable = null
                    return
                }
                val exo = player
                if (exo?.playbackState == Player.STATE_READY && introPrompt?.isShowing != true) {
                    val marker = IntroSkipPolicy.activeMarker(
                        positionMs = exo.currentPosition,
                        durationMs = exo.duration.takeIf { it > 0L } ?: 0L,
                        markers = introMarkers,
                        alreadyPrompted = promptedIntroMarkers
                    )
                    if (marker != null) {
                        promptedIntroMarkers.add(marker.key)
                        showIntroSkipPrompt(marker)
                    }
                }
                handler.postDelayed(this, 1_000L)
            }
        }.also { handler.postDelayed(it, 1_000L) }
    }

    private fun showIntroSkipPrompt(marker: IntroMarker) {
        val endMs = marker.endMs ?: return
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Trecho detectado")
            .setMessage("O TheIntroDB marcou uma ${marker.type.label}. Deseja pular este trecho?")
            .setPositiveButton("Pular") { _, _ ->
                player?.seekTo(endMs.coerceAtLeast(0L))
                player?.playWhenReady = true
            }
            .setNegativeButton("Continuar", null)
            .create()
        introPrompt = dialog
        dialog.setOnDismissListener { introPrompt = null }
        dialog.show()
    }

    private fun parseEpisodeQueue(raw: String): List<QueuedEpisode> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val parsed = buildList {
                // A hard input bound avoids iterating a malformed or unexpectedly huge Intent payload.
                for (index in 0 until minOf(array.length(), EpisodeQueuePolicy.MAX_EPISODES * 4)) {
                    val item = array.optJSONObject(index) ?: continue
                    val urls = item.optJSONArray("sources") ?: continue
                    val sources = buildList {
                        for (i in 0 until minOf(urls.length(), EpisodeQueuePolicy.MAX_SOURCES_PER_EPISODE * 4)) {
                            add(urls.optString(i))
                        }
                    }
                    add(QueuedEpisode(
                        item.optString("title", "Próximo episódio"),
                        item.optString("content_id", "episode-$index"),
                        sources,
                        item.optInt("tmdb_id", 0).coerceAtLeast(0),
                        item.optInt("season", 0).coerceAtLeast(0),
                        item.optInt("episode", 0).coerceAtLeast(0)
                    ))
                }
            }
            EpisodeQueuePolicy.normalize(parsed)
        }.getOrDefault(emptyList())
    }

    private fun showSeasonQueuePicker() {
        if (episodeQueue.isEmpty()) {
            Toast.makeText(this, "Não há episódios disponíveis nesta fila.", Toast.LENGTH_SHORT).show()
            return
        }
        val seasons = episodeQueue.map { it.season }.distinct().sorted()
        if (seasons.isEmpty()) {
            showEpisodeQueuePanel(null)
            return
        }
        val labels = seasons.map { season ->
            "Temporada $season · " + episodeQueue.count { it.season == season } + " episódios"
        }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Temporadas disponíveis")
            .setItems(labels) { _, index ->
                seasons.getOrNull(index)?.let { showEpisodeQueuePanel(it) }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showEpisodeQueuePanel(season: Int?) {
        val entries = episodeQueue.mapIndexedNotNull { index, episode ->
            if (season == null || episode.season == season) index to episode else null
        }
        if (entries.isEmpty()) {
            Toast.makeText(this, "Nenhum episódio disponível nesta temporada.", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = entries.map { (_, episode) ->
            "Episódio ${episode.episode}" +
                if (episode.episode <= 0) " · ${episode.title}" else ""
        }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(if (season == null) "Episódios disponíveis" else "Temporada $season · Episódios")
            .setItems(labels) { _, selected ->
                val targetIndex = entries.getOrNull(selected)?.first ?: return@setItems
                queueIndex = targetIndex
                playQueuedEpisode()
            }
            .setNegativeButton("Voltar", if (season == null) null else { _, _ -> showSeasonQueuePicker() })
            .show()
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
        // Subtitle URLs from a previous episode can point to a different timeline.
        subtitleUrl = null
        subtitleMimeTypeOverride = null
        tmdbId = next.tmdbId
        seasonNumber = next.season
        episodeNumber = next.episode
        loadIntroMarkers()
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
        failureDialogShown = false
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
        if (isRadio) radioExperience?.setPlaybackState(false, isBuffering = true)
        val url = sourceUrls[index]
        if (!PlaybackUrlPolicy.isSafeHttpUrl(url)) {
            tryNextSource()
            return
        }
        if (!attempted.add(url)) {
            tryNextSource()
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
        subtitleUrl?.takeIf(PlaybackUrlPolicy::isSafeHttpUrl)?.let { subtitle ->
            val lower = subtitle.substringBefore('?').substringBefore('#').lowercase()
            val mime = subtitleMimeTypeOverride ?: when {
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

    private fun showSubtitleOptions() {
        if (tmdbId <= 0) {
            promptExternalSubtitle()
            return
        }
        Toast.makeText(this, "Buscando legendas online…", Toast.LENGTH_SHORT).show()
        SubtitleSearchClient.search(
            tmdbId = tmdbId,
            isSeries = seasonNumber > 0,
            season = seasonNumber,
            episode = episodeNumber
        ) { options ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (options.isEmpty()) {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Legendas online")
                        .setMessage("Não encontrei legendas para este título agora. Você ainda pode inserir uma URL externa.")
                        .setPositiveButton("Inserir URL") { _, _ -> promptExternalSubtitle() }
                        .setNegativeButton("Fechar", null)
                        .show()
                    return@runOnUiThread
                }
                val labels = options.map { it.label }.toTypedArray()
                android.app.AlertDialog.Builder(this)
                    .setTitle("Legendas online · OpenSubtitles")
                    .setItems(labels) { _, index ->
                        options.getOrNull(index)?.let { applyExternalSubtitle(it.url, "application/x-subrip") }
                    }
                    .setNeutralButton("Inserir URL") { _, _ -> promptExternalSubtitle() }
                    .setNegativeButton("Fechar", null)
                    .show()
            }
        }
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
                applyExternalSubtitle(input.text?.toString()?.trim().orEmpty())
            }
            .show()
    }

    private fun applyExternalSubtitle(candidate: String, mimeTypeOverride: String? = null) {
        if (!PlaybackUrlPolicy.isSafeHttpUrl(candidate)) {
            Toast.makeText(this, "Informe uma URL HTTP(S) válida, com host e sem credenciais.", Toast.LENGTH_SHORT).show()
            return
        }
        val url = sourceUrls.getOrNull(currentIndex) ?: return
        subtitleUrl = candidate
        subtitleMimeTypeOverride = mimeTypeOverride
        val position = player?.currentPosition ?: 0L
        player?.setMediaItem(buildMediaItem(url), position)
        player?.prepare()
        player?.playWhenReady = true
    }

    private fun tryNextSource() {
        startupCheck?.let(handler::removeCallbacks)
        startupCheck = null
        val next = PlaybackSourcePolicy.nextUnattemptedIndex(sourceUrls, currentIndex, attempted)
        if (next != null) {
            playSource(next)
            return
        }

        // A broken episode should not strand the user when a later episode is available.
        if (!isLive && queueIndex < episodeQueue.size && !isFinishing && !failureDialogShown) {
            failureDialogShown = true
            val nextEpisode = episodeQueue[queueIndex]
            android.app.AlertDialog.Builder(this)
                .setTitle("Fontes indisponíveis")
                .setMessage(
                    "Não foi possível iniciar este conteúdo. Deseja pular para " +
                        nextEpisode.title + "?"
                )
                .setPositiveButton("Tentar próximo episódio") { _, _ -> playQueuedEpisode() }
                .setNegativeButton("Fechar player") { _, _ -> finish() }
                .setOnCancelListener { if (!isFinishing) finish() }
                .show()
            return
        }

        Toast.makeText(this, "Não foi possível reproduzir este conteúdo.", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isLive && !isRadio && (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE)) {
            return true
        }
        if ((keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                    (isRadio && (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE))) &&
            player != null) {
            if (isRadio) {
                if (player!!.isPlaying) player!!.pause() else player!!.play()
            } else {
                player!!.playWhenReady = !player!!.playWhenReady
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        introCheckRunnable?.let(handler::removeCallbacks)
        introCheckRunnable = null
        introPrompt?.dismiss()
        introPrompt = null
        cancelStillWatchingPrompt()
        stillWatchingDialog?.dismiss()
        stillWatchingDialog = null
        super.onStop()
        if (!isLive) {
            val exo = player
            val savedPosition = PlaybackProgressPolicy.positionToPersist(
                positionMs = exo?.currentPosition ?: -1L,
                durationMs = exo?.duration ?: C.TIME_UNSET,
                isReady = exo?.playbackState == Player.STATE_READY,
                isEnded = exo?.playbackState == Player.STATE_ENDED
            )
            // If playback was still buffering/failed, preserve the previous resume point.
            if (savedPosition != null) {
                getSharedPreferences("playback-progress", MODE_PRIVATE).edit()
                    .putLong(contentId, savedPosition).apply()
            }
        }
    }

    override fun onDestroy() {
        startupCheck?.let(handler::removeCallbacks)
        introCheckRunnable?.let(handler::removeCallbacks)
        introCheckRunnable = null
        introPrompt?.dismiss()
        introPrompt = null
        cancelStillWatchingPrompt()
        stillWatchingDialog?.dismiss()
        stillWatchingDialog = null
        cancelNextEpisodeCountdown()
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
