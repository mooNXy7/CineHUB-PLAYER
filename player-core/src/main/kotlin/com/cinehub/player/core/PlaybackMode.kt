package com.cinehub.player.core

/** LIVE must never be paused by player controls. */
enum class PlaybackMode {
    LIVE,
    VOD,
    RADIO
}

data class PlaybackItem(
    val id: String,
    val mode: PlaybackMode,
    val sourceUrls: List<String>,
    val seriesId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
)
