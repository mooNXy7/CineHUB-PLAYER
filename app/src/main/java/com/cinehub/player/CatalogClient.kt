package com.cinehub.player

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Small asynchronous client for the public CineHUB-ACERVO JSON indices.
 * Uses bounded HTTP timeouts and never downloads individual VOD detail pages here.
 */
object CatalogClient {
    private const val BASE =
        "https://raw.githubusercontent.com/mooNXy7/CineHUB-ACERVO/main/"
    private val executor = Executors.newSingleThreadExecutor()

    data class CatalogIndex(
        val schemaVersion: Int,
        val categories: Map<String, Pair<Int, String>>
    )

    fun loadVodIndex(onResult: (Result<CatalogIndex>) -> Unit) {
        executor.execute {
            onResult(runCatching {
                val json = JSONObject(fetch(BASE + "catalog/vod/index.json"))
                val categoriesJson = json.getJSONObject("categories")
                val categories = mutableMapOf<String, Pair<Int, String>>()
                val keys = categoriesJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val item = categoriesJson.getJSONObject(key)
                    categories[key] = item.optInt("count") to item.optString("index_path")
                }
                CatalogIndex(json.optInt("schema_version", 1), categories)
            })
        }
    }

    fun loadRadios(onResult: (Result<String>) -> Unit) {
        executor.execute {
            onResult(runCatching { fetch(BASE + "catalog/radios/radios.json") })
        }
    }

    private fun fetch(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CineHUB-PLAYER/0.1")
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) error("Catálogo respondeu HTTP $status")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
