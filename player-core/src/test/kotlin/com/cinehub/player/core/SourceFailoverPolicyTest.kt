package com.cinehub.player.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceFailoverPolicyTest {
    @Test
    fun trimsDeduplicatesAndSkipsFailedSources() {
        val result = SourceFailoverPolicy().attempts(
            urls = listOf(" https://one.test/a.m3u8 ", "https://one.test/a.m3u8", "", "https://two.test/b.m3u8"),
            failedUrls = setOf("https://two.test/b.m3u8"),
            startPositionMs = 12_000
        )
        assertEquals(1, result.size)
        assertEquals("https://one.test/a.m3u8", result.single().url)
        assertEquals(12_000, result.single().startPositionMs)
    }

    @Test
    fun defaultStartupTimeoutIsFifteenSeconds() {
        assertEquals(15_000L, SourceFailoverPolicy().startupTimeoutMs)
    }
}
