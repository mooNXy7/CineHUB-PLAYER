package com.cinehub.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleSyncPolicyTest {
    @Test
    fun shiftsSrtCueStartAndEndTimes() {
        val input = "1\n00:00:01,000 --> 00:00:03,500\nOlá\n"
        val expected = "1\n00:00:03,000 --> 00:00:05,500\nOlá\n"
        assertEquals(expected, SubtitleSyncPolicy.shiftTimestamps(input, 2_000L))
    }

    @Test
    fun shiftsWebVttAndPreservesText() {
        val input = "WEBVTT\n\n00:01.000 --> 00:02.000\nHello\n"
        val expected = "WEBVTT\n\n00:03.000 --> 00:04.000\nHello\n"
        assertEquals(expected, SubtitleSyncPolicy.shiftTimestamps(input, 2_000L))
    }

    @Test
    fun clampsNegativeTimesAtZero() {
        val input = "00:00:01.000 --> 00:00:03.000"
        val expected = "00:00:00.000 --> 00:00:01.000"
        assertEquals(expected, SubtitleSyncPolicy.shiftTimestamps(input, -2_000L))
    }
}
