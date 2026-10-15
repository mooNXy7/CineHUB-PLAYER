package com.cinehub.player

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var streamInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 32, 48, 32)
            setBackgroundColor(Color.rgb(15, 15, 15))
            isFocusableInTouchMode = true
        }
        root.addView(TextView(this).apply {
            text = "CineHUB PLAYER"
            textSize = 30f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
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
        root.addView(status)
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
        root.addView(categories)
        streamInput = EditText(this).apply {
            hint = "URL HTTP(S) de stream autorizado (HLS, DASH ou MP4)"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            textSize = 16f
            contentDescription = "URL do stream para teste"
        }
        root.addView(streamInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(Button(this).apply {
            text = "REPRODUZIR VÍDEO"
            setOnClickListener { launchPlayer(live = false) }
        })
        root.addView(Button(this).apply {
            text = "TESTAR TRANSMISSÃO AO VIVO"
            setOnClickListener { launchPlayer(live = true) }
        })
        root.addView(Button(this).apply {
            text = "ATUALIZAR STATUS DO CATÁLOGO"
            setOnClickListener { loadCatalogStatus() }
        })
        setContentView(root)
        loadCatalogStatus()
    }

    private fun launchPlayer(live: Boolean) {
        val url = streamInput.text?.toString()?.trim().orEmpty()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            streamInput.error = "Informe uma URL HTTP(S) válida"
            return
        }
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("url", url)
            putExtra("live", live)
            putExtra("content_id", url.hashCode().toString())
        })
    }

    private fun showCategoryPreview(category: String, label: String) {
        status.text = "Carregando prévia de $label sem baixar o índice inteiro…"
        CatalogClient.loadCategoryPreview(category, 30) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { entries ->
                        val titles = entries.joinToString("\n") { entry ->
                            if (entry.year.isNotBlank()) entry.title + " (" + entry.year + ")" else entry.title
                        }
                        status.text = "$label · prévia de ${entries.size} itens\n" + titles
                        status.setTextColor(Color.WHITE)
                    },
                    onFailure = { error ->
                        status.text = "Não foi possível carregar $label: " + (error.message ?: "erro desconhecido")
                        status.setTextColor(Color.rgb(255, 190, 150))
                    }
                )
            }
        }
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
