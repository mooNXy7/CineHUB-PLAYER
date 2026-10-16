package com.cinehub.player.saimo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaimoSourceTest {
    @Test
    fun recognizesHlsManifestsAndLegacyTextPlaylists() {
        assertTrue(Source("https://cdn.example/live/index.m3u8").isHls)
        assertTrue(Source("https://cdn.example/embed/playlist.txt?token=x").isHls)
        assertTrue(Source("https://dadinhodetapioca.fun/play/123").isHls)
        assertFalse(Source("https://cdn.example/movie.mp4").isHls)
    }

    @Test
    fun recognizesDashManifests() {
        assertTrue(Source("https://cdn.example/manifest.mpd").isDash)
        assertFalse(Source("https://cdn.example/movie.mp4").isDash)
    }
}
