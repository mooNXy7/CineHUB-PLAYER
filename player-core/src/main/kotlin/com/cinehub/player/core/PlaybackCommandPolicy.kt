package com.cinehub.player.core

enum class PlaybackCommand {
    PLAY,
    PAUSE,
    TOGGLE_PLAY_PAUSE,
    SEEK_FORWARD,
    SEEK_BACK,
    STOP
}

object PlaybackCommandPolicy {
    /** Live streams intentionally ignore pause/toggle to match CineHUB requirements. */
    fun shouldIgnore(command: PlaybackCommand, mode: PlaybackMode): Boolean =
        mode == PlaybackMode.LIVE &&
            (command == PlaybackCommand.PAUSE ||
             command == PlaybackCommand.TOGGLE_PLAY_PAUSE)
}
