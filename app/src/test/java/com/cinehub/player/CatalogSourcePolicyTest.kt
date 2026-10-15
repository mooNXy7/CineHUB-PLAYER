package com.cinehub.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSourcePolicyTest {
    @Test
    fun acceptsDirectHttpAndHttpsStreamUrls() {
        assertTrue(CatalogClient.isDirectPlaybackSource(
            CatalogClient.PlaybackSource("https://cdn.example/video.m3u8", "dub", 10, "test", "direct")
        ))
        assertTrue(CatalogClient.isDirectPlaybackSource(
            CatalogClient.PlaybackSource("http://cdn.example/video.mp4", "leg", 20, "test", "direct")
        ))
    }

    @Test
    fun admitsEmbedPagesOnlyForSupportedProviderResolvers() {
        assertTrue(CatalogClient.isDirectPlaybackSource(
            CatalogClient.PlaybackSource("https://nhdapi.com/embed/tv/1/1/1", "leg", 20, "test", "direct")
        ))
        assertTrue(CatalogClient.isDirectPlaybackSource(
            CatalogClient.PlaybackSource("https://mgeb.top/embed/1/1/1", "dub", 20, "test", "embed")
        ))
        assertFalse(CatalogClient.isDirectPlaybackSource(
            CatalogClient.PlaybackSource("https://unknown.example/embed/1/1/1", "dub", 20, "test", "embed")
        ))
    }

    @Test
    fun rejectsNonHttpSchemes() {
        assertFalse(CatalogClient.isDirectPlaybackSource(
            CatalogClient.PlaybackSource("file:///sdcard/video.mp4", "", 10, "test", "direct")
        ))
    }
}
