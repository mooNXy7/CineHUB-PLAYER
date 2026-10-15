package com.cinehub.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackProgressPolicyTest {
    @Test
    fun preservesPreviousProgressWhenPlaybackNeverBecomesReady() {
        assertNull(
            PlaybackProgressPolicy.positionToPersist(
                positionMs = 0L,
                durationMs = 900_000L,
                isReady = false,
                isEnded = false
            )
        )
    }

    @Test
    fun persistsCurrentPositionWhenReady() {
        assertEquals(
            245_000L,
            PlaybackProgressPolicy.positionToPersist(
                positionMs = 245_000L,
                durationMs = 900_000L,
                isReady = true,
                isEnded = false
            )
        )
    }

    @Test
    fun clearsProgressNearTheEndToAvoidResumeAtFinalFrame() {
        assertEquals(
            0L,
            PlaybackProgressPolicy.positionToPersist(
                positionMs = 870_000L,
                durationMs = 900_000L,
                isReady = true,
                isEnded = false
            )
        )
    }

    @Test
    fun clearsProgressWhenPlaybackEnded() {
        assertEquals(
            0L,
            PlaybackProgressPolicy.positionToPersist(
                positionMs = 900_000L,
                durationMs = 900_000L,
                isReady = false,
                isEnded = true
            )
        )
    }

    @Test
    fun rejectsInvalidNegativePosition() {
        assertNull(
            PlaybackProgressPolicy.positionToPersist(
                positionMs = -1L,
                durationMs = 900_000L,
                isReady = true,
                isEnded = false
            )
        )
    }
}
