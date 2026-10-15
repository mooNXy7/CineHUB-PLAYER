package com.cinehub.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntroSkipPolicyTest {
    private val intro = IntroMarker(IntroSegmentType.INTRO, 60_000L, 120_000L)
    private val recap = IntroMarker(IntroSegmentType.RECAP, 0L, 25_000L)
    private val credits = IntroMarker(IntroSegmentType.CREDITS, 700_000L, 760_000L)

    @Test
    fun offersSkipOnlyWhileInsideAValidMarkedSegment() {
        assertEquals(
            intro,
            IntroSkipPolicy.activeMarker(75_000L, 900_000L, listOf(intro), emptySet())
        )
        assertNull(IntroSkipPolicy.activeMarker(59_000L, 900_000L, listOf(intro), emptySet()))
        assertNull(IntroSkipPolicy.activeMarker(120_000L, 900_000L, listOf(intro), emptySet()))
    }

    @Test
    fun skipsRecapButNeverOffersCreditsAsIntroSkip() {
        assertEquals(
            recap,
            IntroSkipPolicy.activeMarker(10_000L, 900_000L, listOf(recap, credits), emptySet())
        )
        assertNull(IntroSkipPolicy.activeMarker(720_000L, 900_000L, listOf(credits), emptySet()))
    }

    @Test
    fun doesNotPromptTwiceForSameMarker() {
        assertNull(
            IntroSkipPolicy.activeMarker(75_000L, 900_000L, listOf(intro), setOf(intro.key))
        )
    }

    @Test
    fun ignoresMarkerThatExtendsBeyondKnownDuration() {
        val invalid = IntroMarker(IntroSegmentType.INTRO, 60_000L, 950_000L)
        assertNull(IntroSkipPolicy.activeMarker(75_000L, 900_000L, listOf(invalid), emptySet()))
    }
}
