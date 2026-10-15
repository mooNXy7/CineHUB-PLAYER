package com.cinehub.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackUrlPolicyTest {
    @Test
    fun acceptsAbsoluteHttpAndHttpsStreams() {
        assertTrue(PlaybackUrlPolicy.isSafeHttpUrl("https://media.example/video.m3u8"))
        assertTrue(PlaybackUrlPolicy.isSafeHttpUrl("http://media.example:8080/live/1"))
    }

    @Test
    fun rejectsRelativeMalformedAndNonHttpUrls() {
        assertFalse(PlaybackUrlPolicy.isSafeHttpUrl("/video.m3u8"))
        assertFalse(PlaybackUrlPolicy.isSafeHttpUrl("file:///sdcard/video.mp4"))
        assertFalse(PlaybackUrlPolicy.isSafeHttpUrl("javascript:alert(1)"))
        assertFalse(PlaybackUrlPolicy.isSafeHttpUrl("https:///missing-host.m3u8"))
        assertFalse(PlaybackUrlPolicy.isSafeHttpUrl("https://media.example/video with spaces.m3u8"))
    }

    @Test
    fun rejectsEmbeddedCredentials() {
        assertFalse(PlaybackUrlPolicy.isSafeHttpUrl("https://user:password@media.example/video.m3u8"))
    }
}
