package com.cinehub.player.core

/**
 * Pure policy for selecting source attempts. The Android adapter is responsible
 * for actually preparing Media3 and reporting failures/timeouts.
 */
data class SourceAttempt(
    val url: String,
    val index: Int,
    val startPositionMs: Long
)

class SourceFailoverPolicy(
    val startupTimeoutMs: Long = 15_000L
) {
    init {
        require(startupTimeoutMs > 0) { "startupTimeoutMs must be positive" }
    }

    fun attempts(
        urls: List<String>,
        failedUrls: Set<String> = emptySet(),
        startPositionMs: Long = 0L
    ): List<SourceAttempt> {
        require(startPositionMs >= 0L) { "startPositionMs cannot be negative" }
        val unique = urls.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .filterNot(failedUrls::contains)
            .toList()

        return unique.mapIndexed { index, url ->
            SourceAttempt(url = url, index = index, startPositionMs = startPositionMs)
        }
    }
}
