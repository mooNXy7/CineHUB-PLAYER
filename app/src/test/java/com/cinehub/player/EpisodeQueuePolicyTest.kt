package com.cinehub.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeQueuePolicyTest {
    @Test
    fun removesDuplicateIdsAndDuplicateSources() {
        val queue = EpisodeQueuePolicy.normalize(
            listOf(
                QueuedEpisode("Episódio 2", "s1e2", listOf(
                    "https://media.example/e2.m3u8",
                    "https://media.example/e2.m3u8"
                )),
                QueuedEpisode("Duplicado", "s1e2", listOf("https://other.example/e2.m3u8"))
            )
        )

        assertEquals(1, queue.size)
        assertEquals(listOf("https://media.example/e2.m3u8"), queue.single().sources)
    }

    @Test
    fun removesUnsafeUrlsAndEpisodesWithoutUsableSources() {
        val queue = EpisodeQueuePolicy.normalize(
            listOf(
                QueuedEpisode("Inválido", "bad", listOf("file:///sdcard/video.mp4")),
                QueuedEpisode("Episódio 3", "s1e3", listOf(
                    "javascript:alert(1)",
                    "https://media.example/e3.m3u8"
                ))
            )
        )

        assertEquals(1, queue.size)
        assertEquals("s1e3", queue.single().contentId)
        assertEquals(listOf("https://media.example/e3.m3u8"), queue.single().sources)
    }

    @Test
    fun appliesEpisodeAndSourceLimits() {
        val sources = (1..20).map { "https://media.example/$it.m3u8" }
        val items = (1..120).map { index ->
            QueuedEpisode("Episódio $index", "episode-$index", sources)
        }

        val queue = EpisodeQueuePolicy.normalize(items)

        assertEquals(EpisodeQueuePolicy.MAX_EPISODES, queue.size)
        assertTrue(queue.all { it.sources.size == EpisodeQueuePolicy.MAX_SOURCES_PER_EPISODE })
    }

    @Test
    fun fillsBlankTitleAndTrimsValues() {
        val queue = EpisodeQueuePolicy.normalize(
            listOf(QueuedEpisode(" ", " episode-1 ", listOf(" https://media.example/e1.m3u8 ")))
        )

        assertEquals("Próximo episódio", queue.single().title)
        assertEquals("episode-1", queue.single().contentId)
        assertEquals("https://media.example/e1.m3u8", queue.single().sources.single())
    }
}
