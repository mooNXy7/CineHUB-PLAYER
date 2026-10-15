package com.cinehub.player.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackProgressTest {
    @Test
    fun nearlyFinishedItemIsMarkedComplete() {
        val progress = PlaybackProgress("movie-1", 3_550_000, 3_600_000, 0)
        assertTrue(progress.isComplete)
        assertEquals(0L, progress.resumePositionMs())
    }

    @Test
    fun unfinishedItemResumesAtSavedPosition() {
        val progress = PlaybackProgress("movie-2", 420_000, 3_600_000, 0)
        assertEquals(420_000L, progress.resumePositionMs())
    }
}
