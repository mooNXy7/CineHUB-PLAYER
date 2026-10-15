package com.cinehub.player

/**
 * Bounds and normalizes an episode queue received from catalog metadata or an Intent.
 * Prevents duplicate episode launches and oversized queues on low-memory TV boxes.
 */
internal data class QueuedEpisode(
    val title: String,
    val contentId: String,
    val sources: List<String>,
    val tmdbId: Int = 0,
    val season: Int = 0,
    val episode: Int = 0,
    val posterUrl: String = ""
)

internal object EpisodeQueuePolicy {
    const val MAX_EPISODES = 100
    const val MAX_SOURCES_PER_EPISODE = 12
    private const val MAX_TITLE_LENGTH = 180
    private const val MAX_ID_LENGTH = 240

    /** Returns a bounded window start that always contains the selected episode. */
    fun windowStart(
        totalEpisodes: Int,
        currentIndex: Int,
        maxEpisodes: Int = MAX_EPISODES,
        historyEpisodes: Int = 20
    ): Int? {
        if (totalEpisodes <= 0 || currentIndex !in 0 until totalEpisodes ||
            maxEpisodes <= 0 || historyEpisodes < 0
        ) return null
        return (currentIndex - historyEpisodes).coerceAtLeast(0)
            .coerceAtMost((totalEpisodes - maxEpisodes).coerceAtLeast(0))
    }

    fun normalize(items: List<QueuedEpisode>): List<QueuedEpisode> {
        val seenIds = mutableSetOf<String>()
        return items.asSequence()
            .mapNotNull { item ->
                val id = item.contentId.trim().take(MAX_ID_LENGTH)
                if (id.isBlank() || id in seenIds) return@mapNotNull null
                val sources = item.sources.asSequence()
                    .map(String::trim)
                    .filter(PlaybackUrlPolicy::isSafeHttpUrl)
                    .distinct()
                    .take(MAX_SOURCES_PER_EPISODE)
                    .toList()
                if (sources.isEmpty()) return@mapNotNull null
                seenIds.add(id)
                QueuedEpisode(
                    title = item.title.trim().ifBlank { "Próximo episódio" }.take(MAX_TITLE_LENGTH),
                    contentId = id,
                    sources = sources,
                    tmdbId = item.tmdbId.coerceAtLeast(0),
                    season = item.season.coerceAtLeast(0),
                    episode = item.episode.coerceAtLeast(0),
                    posterUrl = item.posterUrl.takeIf(PlaybackUrlPolicy::isSafeHttpUrl).orEmpty()
                )
            }
            .take(MAX_EPISODES)
            .toList()
    }
}
