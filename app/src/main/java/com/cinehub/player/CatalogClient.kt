package com.cinehub.player

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * Low-memory client for public CineHUB-ACERVO JSON indexes.
 * Large catalog arrays are read incrementally; previews stop after a small page.
 */
object CatalogClient {
    private const val BASE =
        "https://raw.githubusercontent.com/mooNXy7/CineHUB-ACERVO/main/"
    private const val SAIMO_BASE =
        "https://raw.githubusercontent.com/gabrielsaimo/SaimoPlayer/main/"
    private val executor = Executors.newSingleThreadExecutor()
    private data class CachedValue(val storedAtMs: Long, val value: Any)
    private val catalogCache = LinkedHashMap<String, CachedValue>(16, 0.75f, true)
    private const val CATALOG_CACHE_TTL_MS = 5 * 60 * 1000L
    private const val CATALOG_CACHE_MAX_ENTRIES = 32
    @Volatile private var cachedProviderBases: Map<Int, String> = emptyMap()
    @Volatile private var providerBasesLoadedAt = 0L
    private const val PROVIDER_BASES_TTL_MS = 10 * 60 * 1000L
    private val categoryPaths = mapOf(
        "movies" to "catalog/vod/index/movies.json",
        "series" to "catalog/vod/index/series.json",
        "anime" to "catalog/vod/index/anime.json",
        "doramas" to "catalog/vod/index/doramas.json"
    )

    data class CatalogIndex(
        val schemaVersion: Int,
        val categories: Map<String, Pair<Int, String>>
    )

    data class CatalogEntry(
        val id: String,
        val title: String,
        val type: String,
        val detailPath: String,
        val posterPath: String,
        val year: String
    )

    data class RadioEntry(val id: String, val name: String, val streamUrl: String, val genre: String)
    data class ChannelEntry(
        val id: String,
        val name: String,
        val category: String,
        val logoUrl: String,
        val sources: List<String>
    )
    data class PlaybackSource(val url: String, val audio: String, val priority: Int, val sourceId: String, val sourceType: String)
    data class ProviderReference(val provider: String, val ref: String, val audio: String, val sourceId: String)
    data class EpisodeEntry(
        val season: Int,
        val episode: Int,
        val audio: String,
        val sources: List<PlaybackSource>,
        val providerRefs: List<ProviderReference> = emptyList()
    )
    data class ContentDetails(
        val id: String,
        val title: String,
        val type: String,
        val sources: List<PlaybackSource>,
        val episodes: List<EpisodeEntry>,
        val providerRefs: List<ProviderReference> = emptyList(),
        val tmdbId: Int = 0
    )

    /**
     * Bounded in-memory TTL cache for small catalog pages and individual details.
     * If refresh fails, return the last valid value rather than breaking the UI.
     */
    private fun <T : Any> cachedResult(key: String, loader: () -> T): Result<T> {
        readCached<T>(key, allowStale = false)?.let { return Result.success(it) }
        val result = runCatching(loader)
        result.getOrNull()?.let { value ->
            synchronized(catalogCache) {
                catalogCache[key] = CachedValue(System.currentTimeMillis(), value)
                while (catalogCache.size > CATALOG_CACHE_MAX_ENTRIES) {
                    val firstKey = catalogCache.entries.iterator().next().key
                    catalogCache.remove(firstKey)
                }
            }
        }
        if (result.isFailure) {
            readCached<T>(key, allowStale = true)?.let { return Result.success(it) }
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> readCached(key: String, allowStale: Boolean): T? =
        synchronized(catalogCache) {
            val entry = catalogCache[key] ?: return@synchronized null
            if (!allowStale && System.currentTimeMillis() - entry.storedAtMs > CATALOG_CACHE_TTL_MS) {
                return@synchronized null
            }
            entry.value as? T
        }


    /** Reject catalog entries that are HTML embed pages mislabeled as direct streams. */
    fun isDirectPlaybackSource(source: PlaybackSource): Boolean {
        if (source.sourceType.equals("embed", ignoreCase = true)) return false
        if (!PlaybackUrlPolicy.isSafeHttpUrl(source.url)) return false
        val uri = runCatching { URL(source.url) }.getOrNull() ?: return false
        val host = uri.host.lowercase()
        val path = uri.path.lowercase()
        if (host == "mgeb.top" || host.endsWith(".mgeb.top") || host == "nhdapi.com" || host.endsWith(".nhdapi.com")) return false
        if (path.contains("/embed/") || path.endsWith("/embed")) return false
        return true
    }

    fun loadVodIndex(onResult: (Result<CatalogIndex>) -> Unit) {
        executor.execute {
            onResult(cachedResult("vod-index") {
                val json = JSONObject(fetchText(BASE + "catalog/vod/index.json", 2 * 1024 * 1024))
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

    fun loadCategoryPreview(category: String, limit: Int = 40, onResult: (Result<List<CatalogEntry>>) -> Unit) {
        executor.execute {
            onResult(cachedResult("category:${category.lowercase()}:${limit}") {
                require(limit in 1..100) { "Preview limit must be between 1 and 100" }
                val path = categoryPaths[category.lowercase()]
                    ?: error("Unknown catalog category: $category")
                readArrayPreview(BASE + path, limit)
            })
        }
    }

    fun loadContentDetails(entry: CatalogEntry, onResult: (Result<ContentDetails>) -> Unit) {
        executor.execute {
            onResult(cachedResult("details:${entry.id}") {
                require(entry.detailPath.startsWith("catalog/vod/details/")) { "Invalid catalog detail path" }
                // Some shard names begin with "#"; encode it so URL parsing does not treat it as a fragment.
                val safePath = entry.detailPath.replace("#", "%23")
                val details = readContentDetails(BASE + safePath, entry.id)
                // Provider refs use the same indexed bases and relative file refs as SaimoPlayer's VodFormato.
                val bases = providerBases()
                val directSources = (details.sources + resolveProviderReferences(details.providerRefs, bases))
                    .distinctBy { it.url }.sortedBy { it.priority }
                val episodes = details.episodes.map { episode ->
                    episode.copy(
                        sources = (episode.sources + resolveProviderReferences(episode.providerRefs, bases))
                            .distinctBy { it.url }.sortedBy { it.priority }
                    )
                }
                details.copy(sources = directSources, episodes = episodes)
            })
        }
    }

    private fun readContentDetails(address: String, wantedId: String): ContentDetails {
        val connection = open(address)
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("Detalhes do catálogo responderam HTTP $status")
            val reader = JsonReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            reader.beginArray()
            while (reader.hasNext()) {
                var id = ""
                var title = ""
                var type = ""
                var sources = emptyList<PlaybackSource>()
                var episodes = emptyList<EpisodeEntry>()
                var providerRefs = emptyList<ProviderReference>()
                var tmdbId = 0
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "id" -> id = readNullableString(reader)
                        "title" -> title = readNullableString(reader)
                        "type" -> type = readNullableString(reader)
                        "sources" -> sources = readSources(reader)
                        "seasons" -> episodes = readSeasons(reader)
                        "provider_refs" -> providerRefs = readProviderReferences(reader)
                        "tmdb_id" -> tmdbId = readNullableString(reader).toIntOrNull() ?: 0
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (id == wantedId) {
                    reader.close()
                    return ContentDetails(id, title, type, sources, episodes, providerRefs, tmdbId)
                }
            }
            reader.close()
            error("Título não encontrado no arquivo de detalhes")
        } finally {
            connection.disconnect()
        }
    }

    private fun providerBases(): Map<Int, String> {
        val now = System.currentTimeMillis()
        if (cachedProviderBases.isNotEmpty() && now - providerBasesLoadedAt < PROVIDER_BASES_TTL_MS) {
            return cachedProviderBases
        }
        return runCatching {
            val text = fetchText(SAIMO_BASE + "vod/indice.txt", 2 * 1024 * 1024)
            val parsed = text.lineSequence().mapNotNull { line ->
                if (!line.startsWith("base:")) return@mapNotNull null
                val value = line.removePrefix("base:").trim()
                val split = value.split(Regex("\\s+"), limit = 2)
                if (split.size != 2) return@mapNotNull null
                val index = split[0].toIntOrNull() ?: return@mapNotNull null
                val base = split[1].trim()
                val uri = runCatching { URL(base) }.getOrNull() ?: return@mapNotNull null
                if (!PlaybackUrlPolicy.isSafeHttpUrl(base) ||
                    uri.host.endsWith(".invalid", ignoreCase = true)) return@mapNotNull null
                index to if (base.endsWith("/")) base else "$base/"
            }.toMap()
            cachedProviderBases = parsed
            providerBasesLoadedAt = now
            parsed
        }.getOrElse { cachedProviderBases }
    }

    private fun resolveProviderReferences(
        references: List<ProviderReference>,
        bases: Map<Int, String>
    ): List<PlaybackSource> = references.mapIndexedNotNull { index, reference ->
        val providerIndex = reference.provider.toIntOrNull() ?: return@mapIndexedNotNull null
        val base = bases[providerIndex] ?: return@mapIndexedNotNull null
        val url = ProviderUrlResolver.resolve(base, reference.ref)
            ?: return@mapIndexedNotNull null
        PlaybackSource(
            url = url,
            audio = reference.audio,
            priority = 200 + index,
            sourceId = reference.sourceId.ifBlank { "provider-" + reference.provider },
            sourceType = "provider-resolved"
        )
    }

    private fun readSources(reader: JsonReader): List<PlaybackSource> {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return emptyList()
        }
        val result = mutableListOf<PlaybackSource>()
        reader.beginArray()
        while (reader.hasNext()) {
            var url = ""
            var audio = ""
            var priority = 100
            var sourceId = ""
            var sourceType = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "url" -> url = readNullableString(reader)
                    "audio" -> audio = readNullableString(reader)
                    "priority" -> priority = readNullableString(reader).toIntOrNull() ?: 100
                    "source_id" -> sourceId = readNullableString(reader)
                    "source_type" -> sourceType = readNullableString(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (PlaybackUrlPolicy.isSafeHttpUrl(url)) {
                result.add(PlaybackSource(url, audio, priority, sourceId, sourceType))
            }
        }
        reader.endArray()
        return result.sortedBy { it.priority }
    }

    private fun readProviderReferences(reader: JsonReader): List<ProviderReference> {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return emptyList()
        }
        val result = mutableListOf<ProviderReference>()
        reader.beginArray()
        while (reader.hasNext()) {
            var provider = ""
            var ref = ""
            var audio = ""
            var sourceId = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "provider" -> provider = readNullableString(reader)
                    "ref" -> ref = readNullableString(reader)
                    "audio" -> audio = readNullableString(reader)
                    "source_id" -> sourceId = readNullableString(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (provider.isNotBlank() && ref.isNotBlank()) {
                result.add(ProviderReference(provider, ref, audio, sourceId))
            }
        }
        reader.endArray()
        return result
    }

    private fun readSeasons(reader: JsonReader): List<EpisodeEntry> {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return emptyList()
        }
        val result = mutableListOf<EpisodeEntry>()
        reader.beginArray()
        while (reader.hasNext()) {
            var seasonNumber = 1
            var seasonEpisodes = emptyList<EpisodeEntry>()
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "season" -> seasonNumber = readNullableString(reader).toIntOrNull() ?: 1
                    "episodes" -> {
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull()
                        } else {
                            reader.beginArray()
                            val parsed = mutableListOf<EpisodeEntry>()
                            while (reader.hasNext()) {
                                var episodeNumber = 0
                                var audio = ""
                                var sources = emptyList<PlaybackSource>()
                                var providerRefs = emptyList<ProviderReference>()
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "episode" -> episodeNumber = readNullableString(reader).toIntOrNull() ?: 0
                                        "audio" -> audio = readNullableString(reader)
                                        "sources" -> sources = readSources(reader)
                                        "provider_refs" -> providerRefs = readProviderReferences(reader)
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                parsed.add(EpisodeEntry(seasonNumber, episodeNumber, audio, sources, providerRefs))
                            }
                            reader.endArray()
                            seasonEpisodes = parsed
                        }
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            result.addAll(seasonEpisodes)
        }
        reader.endArray()
        return result.sortedWith(compareBy<EpisodeEntry> { it.season }.thenBy { it.episode }.thenBy { it.audio })
    }

    /**
     * Loads a small, playable-only channel preview. Records with no direct source are
     * deliberately omitted: pending candidates are metadata, not playable streams.
     */
    fun loadChannelPreview(limit: Int = 60, onResult: (Result<List<ChannelEntry>>) -> Unit) {
        executor.execute {
            onResult(cachedResult("channels:${limit}") {
                require(limit in 1..100) { "Channel preview limit must be between 1 and 100" }
                val connection = open(BASE + "catalog/channels/channels.json")
                try {
                    val status = connection.responseCode
                    if (status !in 200..299) error("Catálogo de canais respondeu HTTP $status")
                    val reader = JsonReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                    val entries = ArrayList<ChannelEntry>(limit)
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "channels" -> {
                                reader.beginArray()
                                while (reader.hasNext() && entries.size < limit) {
                                    var id = ""
                                    var name = ""
                                    var category = ""
                                    var logo = ""
                                    var streamUrl = ""
                                    var sources = emptyList<String>()
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "id" -> id = readNullableString(reader)
                                            "name", "title" -> name = readNullableString(reader)
                                            "category", "group" -> category = readNullableString(reader)
                                            "logo_url", "logo" -> logo = readNullableString(reader)
                                            "stream_url", "url" -> streamUrl = readNullableString(reader)
                                            "sources" -> sources = readChannelSources(reader)
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                    val validSources = ChannelSourcePolicy.normalize(streamUrl, sources)
                                    if (name.isNotBlank() && validSources.isNotEmpty()) {
                                        entries.add(ChannelEntry(id.ifBlank { name.lowercase() }, name, category, logo, validSources))
                                    }
                                }
                                reader.close()
                                return@runCatching entries
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.close()
                    entries
                } finally {
                    connection.disconnect()
                }
            })
        }
    }

    private fun readChannelSources(reader: JsonReader): List<String> {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return emptyList()
        }
        val result = mutableListOf<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> {
                    val candidate = readNullableString(reader)
                    if (PlaybackUrlPolicy.isSafeHttpUrl(candidate)) result.add(candidate)
                }
                JsonToken.BEGIN_OBJECT -> {
                    var url = ""
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "url", "stream_url", "uri" -> url = readNullableString(reader)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    if (PlaybackUrlPolicy.isSafeHttpUrl(url)) result.add(url)
                }
                else -> reader.skipValue()
            }
        }
        reader.endArray()
        return result.distinct()
    }

    fun loadRadioPreview(limit: Int = 50, onResult: (Result<List<RadioEntry>>) -> Unit) {
        executor.execute {
            onResult(cachedResult("radios:${limit}") {
                require(limit in 1..100) { "Radio preview limit must be between 1 and 100" }
                val connection = open(BASE + "catalog/radios/radios.json")
                try {
                    val status = connection.responseCode
                    if (status !in 200..299) error("Catálogo respondeu HTTP $status")
                    val reader = JsonReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                    val entries = ArrayList<RadioEntry>(limit)
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "radios" -> {
                                reader.beginArray()
                                while (reader.hasNext() && entries.size < limit) {
                                    var id = ""
                                    var name = ""
                                    var stream = ""
                                    var genre = ""
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "id" -> id = readNullableString(reader)
                                            "name" -> name = readNullableString(reader)
                                            "stream_url" -> stream = readNullableString(reader)
                                            "genre" -> genre = readNullableString(reader)
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                    if (name.isNotBlank() && PlaybackUrlPolicy.isSafeHttpUrl(stream)) {
                                        entries.add(RadioEntry(id, name, stream, genre))
                                    }
                                }
                                // We intentionally stop after a bounded preview for low-memory devices.
                                reader.close()
                                return@runCatching entries
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.close()
                    entries
                } finally {
                    connection.disconnect()
                }
            })
        }
    }

    private fun readArrayPreview(address: String, limit: Int): List<CatalogEntry> {
        val connection = open(address)
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("Catálogo respondeu HTTP $status")
            val reader = JsonReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            reader.isLenient = false
            val entries = ArrayList<CatalogEntry>(limit)
            reader.beginArray()
            while (reader.hasNext() && entries.size < limit) {
                var id = ""
                var title = ""
                var type = ""
                var detailPath = ""
                var posterPath = ""
                var year = ""
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "id" -> id = readNullableString(reader)
                        "title" -> title = readNullableString(reader)
                        "type" -> type = readNullableString(reader)
                        "detail_path" -> detailPath = readNullableString(reader)
                        "poster_url" -> posterPath = readNullableString(reader)
                        "year" -> year = readNullableString(reader)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (title.isNotBlank()) entries.add(CatalogEntry(id, title, type, detailPath, posterPath, year))
            }
            reader.close()
            return entries
        } finally {
            connection.disconnect()
        }
    }

    private fun readNullableString(reader: JsonReader): String =
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            ""
        } else {
            reader.nextString()
        }

    private fun fetchText(address: String, maxBytes: Int): String {
        val connection = open(address)
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("Catálogo respondeu HTTP $status")
            val output = StringBuilder()
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { input ->
                val buffer = CharArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) error("Resposta do catálogo excede o limite permitido")
                    output.append(buffer, 0, count)
                }
            }
            return output.toString()
        } finally {
            connection.disconnect()
        }
    }

    private fun open(address: String): HttpURLConnection =
        (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CineHUB-PLAYER/0.1")
        }
}
