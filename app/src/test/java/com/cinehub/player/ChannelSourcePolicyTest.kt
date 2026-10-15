package com.cinehub.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelSourcePolicyTest {
    @Test
    fun removesUnsafeAndDuplicateSourcesAndKeepsOrder() {
        val result = ChannelSourcePolicy.normalize(
            "https://one.example/live.m3u8",
            listOf(
                "https://one.example/live.m3u8",
                "file:///tmp/stream",
                " https://two.example/live.m3u8 ",
                "https://user:pass@bad.example/live.m3u8"
            )
        )
        assertEquals(
            listOf("https://one.example/live.m3u8", "https://two.example/live.m3u8"),
            result
        )
    }

    @Test
    fun boundsSourceCountForTvIntent() {
        val urls = (1..30).map { "https://stream$it.example/live.m3u8" }
        assertEquals(ChannelSourcePolicy.MAX_SOURCES_PER_CHANNEL,
            ChannelSourcePolicy.normalize("", urls).size)
    }
}
