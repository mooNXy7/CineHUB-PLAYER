package com.cinehub.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSourcePolicyTest {
    private val sources = listOf(
        "https://one.example/live.m3u8",
        "https://two.example/live.m3u8",
        "https://three.example/live.m3u8"
    )

    @Test
    fun selectsNextSourceInPriorityOrder() {
        assertEquals(1, PlaybackSourcePolicy.nextUnattemptedIndex(sources, 0, setOf(sources[0])))
    }

    @Test
    fun skipsAlreadyAttemptedUrls() {
        assertEquals(
            2,
            PlaybackSourcePolicy.nextUnattemptedIndex(sources, 0, setOf(sources[0], sources[1]))
        )
    }

    @Test
    fun neverRetriesEarlierOrCurrentSource() {
        assertNull(
            PlaybackSourcePolicy.nextUnattemptedIndex(sources, 2, setOf(sources[0], sources[1]))
        )
    }

    @Test
    fun returnsNullWhenAllRemainingSourcesWereAttempted() {
        assertNull(
            PlaybackSourcePolicy.nextUnattemptedIndex(sources, 0, sources.toSet())
        )
    }
}
