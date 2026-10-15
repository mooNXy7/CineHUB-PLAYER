package com.cinehub.player.core

/** Values are policy targets; Media3 adapter must translate them to LoadControl. */
data class BufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playbackBufferMs: Int,
    val rebufferMs: Int,
    val maxVideoWidth: Int
) {
    init {
        require(minBufferMs >= 0)
        require(maxBufferMs >= minBufferMs)
        require(playbackBufferMs >= 0)
        require(rebufferMs >= 0)
        require(maxVideoWidth > 0)
    }

    companion object {
        /** Conservative target for low-memory devices; validate on physical hardware. */
        val LOW_MEMORY = BufferProfile(
            minBufferMs = 8_000,
            maxBufferMs = 25_000,
            playbackBufferMs = 1_000,
            rebufferMs = 2_000,
            maxVideoWidth = 1280
        )

        val STANDARD = BufferProfile(
            minBufferMs = 15_000,
            maxBufferMs = 50_000,
            playbackBufferMs = 1_500,
            rebufferMs = 3_000,
            maxVideoWidth = 1920
        )
    }
}
