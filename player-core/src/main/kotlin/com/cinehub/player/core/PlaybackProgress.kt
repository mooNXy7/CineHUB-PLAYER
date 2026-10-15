package com.cinehub.player.core

data class PlaybackProgress(
    val contentId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long
) {
    val isComplete: Boolean
        get() = durationMs > 0 && positionMs >= durationMs - COMPLETION_TAIL_MS

    fun resumePositionMs(): Long =
        if (isComplete) 0L else positionMs.coerceAtLeast(0L)

    companion object {
        private const val COMPLETION_TAIL_MS = 90_000L
    }
}
