package com.cinehub.player

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import android.widget.FrameLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var streamInput: EditText
    private lateinit var memoryProfileButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val root = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(15, 15, 15))
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 32, 48, 32)
            isFocusableInTouchMode = true
        }
        root.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        content.addView(TextView(this).apply {
            text = "CineHUB PLAYER"
            textSize = 30f
            setTextColor(Color.WHITE)
        })
        content.addView(TextView(this).apply {
            text = "Player nativo de teste · Android TV / Google TV / TV Box"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, 8, 0, 24)
        })
        status = TextView(this).apply {
            text = "Consultando CineHUB-ACERVO…"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, 8, 0, 20)
        }
        content.addView(status)
        val categories = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf(
            "movies" to "Filmes",
            "series" to "Séries",
            "anime" to "Animes",
            "doramas" to "Doramas"
        ).forEach { (key, label) ->
            categories.addView(Button(this).apply {
                text = label
                setOnClickListener { showCategoryPreview(key, label) }
            })
        }
        val categoriesScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(categories, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        content.addView(categoriesScroll)
        content.addView(Button(this).apply {
            text = "CARREGAR RÁDIOS DO CINEHUB-ACERVO"
            setOnClickListener { showRadioPicker() }
        })
        memoryProfileButton = Button(this).apply {
            setOnClickListener {
                val prefs = getSharedPreferences("player-settings", MODE_PRIVATE)
                val current = prefs.getString("memory_profile", "auto") ?: "auto"
                val next = when (current) {
                    "auto" -> "low"
                    "low" -> "normal"
                    else -> "auto"
                }
                prefs.edit().putString("memory_profile", next).apply()
                updateMemoryProfileLabel()
            }
        }
        content.addView(memoryProfileButton)
        updateMemoryProfileLabel()
        streamInput = EditText(this).apply {
            hint = "URL HTTP(S) de stream autorizado (HLS, DASH ou MP4)"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            textSize = 16f
            contentDescription = "URL do stream para teste"
        }
        content.addView(streamInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(Button(this).apply {
            text = "REPRODUZIR VÍDEO"
            setOnClickListener { launchPlayer(live = false) }
        })
        content.addView(Button(this).apply {
            text = "TESTAR TRANSMISSÃO AO VIVO"
            setOnClickListener { launchPlayer(live = true) }
        })
        content.addView(Button(this).apply {
            text = "ATUALIZAR STATUS DO CATÁLOGO"
            setOnClickListener { loadCatalogStatus() }
        })
        setContentView(root)
        TvUi.style(root)
        loadCatalogStatus()
    }

    private fun updateMemoryProfileLabel() {
        val profile = getSharedPreferences("player-settings", MODE_PRIVATE)
            .getString("memory_profile", "auto") ?: "auto"
        memoryProfileButton.text = when (profile) {
            "low" -> "Buffer: ECONOMIA DE MEMÓRIA (toque para alterar)"
            "normal" -> "Buffer: DESEMPENHO (toque para alterar)"
            else -> "Buffer: AUTOMÁTICO (toque para alterar)"
        }
        memoryProfileButton.contentDescription = "Perfil de buffer: $profile"
    }

    private fun launchPlayer(live: Boolean) {
        val url = streamInput.text?.toString()?.trim().orEmpty()
        if (!PlaybackUrlPolicy.isSafeHttpUrl(url)) {
            streamInput.error = "Informe uma URL HTTP(S) válida, com host e sem credenciais"
            return
        }
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("url", url)
            putExtra("live", live)
            putExtra("content_id", url.hashCode().toString())
        })
    }

    private fun showRadioPicker() {
        status.text = "Carregando rádios em modo econômico…"
        CatalogClient.loadRadioPreview(60) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { radios ->
                        if (radios.isEmpty()) {
                            status.text = "Nenhuma rádio com URL válida foi encontrada."
                            return@fold
                        }
                        val labels = radios.map { radio ->
                            if (radio.genre.isNotBlank()) radio.name + " · " + radio.genre else radio.name
                        }.toTypedArray()
                        status.text = "Rádios carregadas: ${radios.size}. Selecione uma para ouvir."
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Rádios CineHUB")
                            .setItems(labels) { _, index ->
                                val radio = radios[index]
                                startActivity(Intent(this, PlayerActivity::class.java).apply {
                                    putExtra("url", radio.streamUrl)
                                    putExtra("live", true)
                                    putExtra("content_id", radio.id)
                                })
                            }
                            .setNegativeButton("Fechar", null)
                            .show()
                    },
                    onFailure = { error ->
                        status.text = "Falha ao carregar rádios: " + (error.message ?: "erro desconhecido")
                    }
                )
            }
        }
    }

    private fun showCategoryPreview(category: String, label: String) {
        status.text = "Carregando prévia de $label sem baixar o índice inteiro…"
        CatalogClient.loadCategoryPreview(category, 30) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { entries ->
                        if (entries.isEmpty()) {
                            status.text = "Nenhum item encontrado em $label."
                            return@fold
                        }
                        val labels = entries.map { entry ->
                            if (entry.year.isNotBlank()) entry.title + " (" + entry.year + ")" else entry.title
                        }.toTypedArray()
                        status.text = "$label · prévia de ${entries.size} itens"
                        android.app.AlertDialog.Builder(this)
                            .setTitle(label)
                            .setItems(labels) { _, index -> showContentDetails(entries[index]) }
                            .setNegativeButton("Fechar", null)
                            .show()
                    },
                    onFailure = { error ->
                        status.text = "Não foi possível carregar $label: " + (error.message ?: "erro desconhecido")
                        status.setTextColor(Color.rgb(255, 190, 150))
                    }
                )
            }
        }
    }

    private fun showContentDetails(entry: CatalogClient.CatalogEntry) {
        status.text = "Consultando fontes e episódios de ${entry.title}…"
        CatalogClient.loadContentDetails(entry) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { details ->
                        if (details.episodes.isNotEmpty()) {
                            showSeasonPicker(details)
                        } else {
                            choosePlaybackSources(
                                details.title,
                                details.sources.filter { CatalogClient.isDirectPlaybackSource(it) }
                                    .sortedBy { it.priority }.map { it.url },
                                details.id,
                                tmdbId = details.tmdbId
                            )
                        }
                    },
                    onFailure = { error ->
                        status.text = "Não foi possível abrir os detalhes: " + (error.message ?: "erro desconhecido")
                    }
                )
            }
        }
    }

    /** TV-friendly drill-down: season -> episode -> audio/version -> source failover. */
    private fun showSeasonPicker(details: CatalogClient.ContentDetails) {
        val seasons = details.episodes.map { it.season }.distinct().sorted()
        if (seasons.isEmpty()) {
            status.text = "Este título não possui episódios publicados."
            return
        }
        val labels = seasons.map { "Temporada $it · " + details.episodes.count { ep -> ep.season == it }.let { count -> "$count faixas de áudio/episódio" } }
        android.app.AlertDialog.Builder(this)
            .setTitle(details.title + " · Temporadas")
            .setItems(labels.toTypedArray()) { _, index ->
                showEpisodePicker(details, seasons[index])
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showEpisodePicker(details: CatalogClient.ContentDetails, season: Int) {
        val episodes = details.episodes.filter { it.season == season }
            .map { it.episode }.filter { it > 0 }.distinct().sorted()
        if (episodes.isEmpty()) {
            android.app.AlertDialog.Builder(this).setTitle("Temporada $season")
                .setMessage("Nenhum episódio listado nesta temporada.")
                .setPositiveButton("OK", null).show()
            return
        }
        val labels = episodes.map { number ->
            val variants = details.episodes.filter { it.season == season && it.episode == number }
                .map { audioLabel(it.audio) }.distinct()
            "Episódio $number" + if (variants.isNotEmpty()) " · " + variants.joinToString(" / ") else ""
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(details.title + " · Temporada $season")
            .setItems(labels.toTypedArray()) { _, index ->
                showEpisodeVersionPicker(details, season, episodes[index])
            }
            .setNegativeButton("Voltar", null)
            .show()
    }

    private fun showEpisodeVersionPicker(
        details: CatalogClient.ContentDetails,
        season: Int,
        episodeNumber: Int
    ) {
        val variants = details.episodes.filter {
            it.season == season && it.episode == episodeNumber
        }.sortedBy { it.sources.minOfOrNull { source -> source.priority } ?: Int.MAX_VALUE }

        if (variants.isEmpty()) return
        val labels = variants.map { variant ->
            val direct = variant.sources.filter { CatalogClient.isDirectPlaybackSource(it) }
            audioLabel(variant.audio) + " · " + direct.size + " fontes diretas"
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("T$season · E$episodeNumber · Áudio")
            .setItems(labels.toTypedArray()) { _, index ->
                val variant = variants[index]
                val direct = variant.sources.filter { CatalogClient.isDirectPlaybackSource(it) }
                    .sortedBy { it.priority }.map { it.url }.distinct()
                val id = details.id + "-" + season + "-" + episodeNumber + "-" + variant.audio
                if (direct.isEmpty()) {
                    val references = variant.providerRefs.size
                    android.app.AlertDialog.Builder(this)
                        .setTitle(details.title + " · T$season E$episodeNumber")
                        .setMessage(
                            "Esta versão (${audioLabel(variant.audio)}) ainda não tem URL direta compatível. " +
                            if (references > 0) "O acervo preservou $references referências de provedor. Falta ligar o resolvedor correspondente; não vou tratá-las como vídeo direto."
                            else if (variant.sources.isNotEmpty()) "As URLs publicadas são páginas de provedor, não fluxos de vídeo diretos."
                            else "O acervo ainda não publicou fontes para esta versão."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    choosePlaybackSources(
                        details.title + " · T$season E$episodeNumber · " + audioLabel(variant.audio),
                        direct,
                        id,
                        buildEpisodeQueue(details, season, episodeNumber, variant.audio),
                        details.id,
                        details.tmdbId,
                        season,
                        episodeNumber
                    )
                }
            }
            .setNegativeButton("Voltar", null)
            .show()
    }

    private fun audioLabel(audio: String): String = when (audio.lowercase()) {
        "dub", "dublado", "pt", "pt-br" -> "Dublado"
        "leg", "legendado", "sub" -> "Legendado"
        "", "null" -> "Áudio não informado"
        else -> audio.uppercase()
    }

    private fun choosePlaybackSources(
        title: String,
        urls: List<String>,
        contentId: String,
        episodeQueue: String? = null,
        seriesId: String? = null,
        tmdbId: Int = 0,
        season: Int = 0,
        episode: Int = 0
    ) {
        val uniqueUrls = urls.distinct()
        if (uniqueUrls.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("O acervo tem metadados para este item, mas ainda não publicou uma URL direta compatível com o player. Fontes do tipo embed precisam de um resolvedor específico.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putStringArrayListExtra("sources", ArrayList(uniqueUrls))
            putExtra("content_id", contentId)
            putExtra("live", false)
            episodeQueue?.let { putExtra("episode_queue", it) }
            seriesId?.let { putExtra("series_id", it) }
            if (tmdbId > 0) putExtra("tmdb_id", tmdbId)
            if (season > 0) putExtra("season_number", season)
            if (episode > 0) putExtra("episode_number", episode)
        })
    }

    private fun buildEpisodeQueue(
        details: CatalogClient.ContentDetails,
        season: Int,
        episodeNumber: Int,
        preferredAudio: String
    ): String {
        val allEpisodes = details.episodes
            .filter { it.episode > 0 }
            .distinctBy { it.season to it.episode }
            .sortedWith(compareBy<CatalogClient.EpisodeEntry> { it.season }.thenBy { it.episode })
        val currentIndex = allEpisodes.indexOfFirst { it.season == season && it.episode == episodeNumber }
        if (currentIndex < 0 || currentIndex >= allEpisodes.lastIndex) return "[]"
        val queue = JSONArray()
        allEpisodes.drop(currentIndex + 1).forEach { episodeKey ->
            val variants = details.episodes.filter {
                it.season == episodeKey.season && it.episode == episodeKey.episode
            }
            val selected = variants.firstOrNull { it.audio.equals(preferredAudio, ignoreCase = true) }
                ?.takeIf { it.sources.any(CatalogClient::isDirectPlaybackSource) }
                ?: variants.firstOrNull { variant -> variant.sources.any(CatalogClient::isDirectPlaybackSource) }
            val urls = selected?.sources.orEmpty()
                .filter(CatalogClient::isDirectPlaybackSource)
                .sortedBy { it.priority }.map { it.url }.distinct()
            if (urls.isNotEmpty()) {
                queue.put(JSONObject().apply {
                    put("title", details.title + " · T" + episodeKey.season + " E" + episodeKey.episode)
                    put("content_id", details.id + "-" + episodeKey.season + "-" + episodeKey.episode + "-" + (selected?.audio ?: ""))
                    put("sources", JSONArray(urls))
                    put("tmdb_id", details.tmdbId)
                    put("season", episodeKey.season)
                    put("episode", episodeKey.episode)
                })
            }
        }
        return queue.toString()
    }

    private fun loadCatalogStatus() {
        status.text = "Consultando CineHUB-ACERVO…"
        CatalogClient.loadVodIndex { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { index ->
                        val lines = index.categories.entries.joinToString("\n") { (key, value) ->
                            key + ": " + value.first + " itens"
                        }
                        status.text = "CineHUB-ACERVO conectado · schema " + index.schemaVersion + "\n" + lines
                        status.setTextColor(Color.rgb(180, 235, 180))
                    },
                    onFailure = { error ->
                        status.text = "Catálogo indisponível; o player aceita links diretos.\n" + (error.message ?: "Falha de rede")
                        status.setTextColor(Color.rgb(255, 190, 150))
                    }
                )
            }
        }
    }
}
