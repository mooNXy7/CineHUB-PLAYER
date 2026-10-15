package com.cinehub.player.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackCommandPolicyTest {
    @Test
    fun liveIgnoresPauseAndToggle() {
        assertTrue(PlaybackCommandPolicy.shouldIgnore(PlaybackCommand.PAUSE, PlaybackMode.LIVE))
        assertTrue(PlaybackCommandPolicy.shouldIgnore(PlaybackCommand.TOGGLE_PLAY_PAUSE, PlaybackMode.LIVE))
    }

    @Test
    fun liveStillAllowsOtherCommands() {
        assertFalse(PlaybackCommandPolicy.shouldIgnore(PlaybackCommand.SEEK_FORWARD, PlaybackMode.LIVE))
        assertFalse(PlaybackCommandPolicy.shouldIgnore(PlaybackCommand.STOP, PlaybackMode.LIVE))
    }

    @Test
    fun vodDoesNotIgnorePause() {
        assertFalse(PlaybackCommandPolicy.shouldIgnore(PlaybackCommand.PAUSE, PlaybackMode.VOD))
    }
}
