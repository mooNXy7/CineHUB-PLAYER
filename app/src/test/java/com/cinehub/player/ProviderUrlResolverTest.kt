package com.cinehub.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderUrlResolverTest {
    @Test
    fun appendsMp4ToExtensionlessReference() {
        assertEquals(
            "https://cdn.example/series/2275264.mp4",
            ProviderUrlResolver.resolve("https://cdn.example/series/", "2275264")
        )
    }

    @Test
    fun preservesRelativePathAndQueryWhenExtensionExists() {
        assertEquals(
            "https://cdn.example/series/234455/1/1.mp4?lang=leg",
            ProviderUrlResolver.resolve("https://cdn.example/series/", "234455/1/1.mp4?lang=leg")
        )
    }

    @Test
    fun rejectsTraversalAndInvalidHosts() {
        assertNull(ProviderUrlResolver.resolve("https://cdn.example/", "../private"))
        assertNull(ProviderUrlResolver.resolve("http://disabled.invalid/", "12345"))
        assertNull(ProviderUrlResolver.resolve("javascript:alert(1)", "12345"))
    }

    @Test
    fun extractsAndUnescapesDadinhoHlsUrl() {
        val html = """<script>const STREAM_URLS = ["https:\/\/cdn.example/live/master.m3u8"];</script>"""
        assertEquals(
            "https://cdn.example/live/master.m3u8",
            ProviderUrlResolver.extractDadinhoStreamUrl(html)
        )
    }

    @Test
    fun rejectsNonHttpDadinhoCandidates() {
        assertNull(ProviderUrlResolver.extractDadinhoStreamUrl("""STREAM_URLS = ["file:///tmp/stream"]"""))
    }

    @Test
    fun returnsNullWhenProviderMarkupChanges() {
        assertNull(ProviderUrlResolver.extractDadinhoStreamUrl("<html>no playlist here</html>"))
    }


    @Test
    fun onlyAllowsEmbedPagesWithAnImplementedResolver() {
        assertTrue(
            CatalogClient.isDirectPlaybackSource(
                CatalogClient.PlaybackSource(
                    "https://dadinhodetapioca.fun/play/movie-123", "", 1, "dadinho", "embed"
                )
            )
        )
        assertTrue(
            CatalogClient.isDirectPlaybackSource(
                CatalogClient.PlaybackSource(
                    "https://mgeb.top/embed/123", "", 1, "mgeb", "embed"
                )
            )
        )
        assertTrue(
            CatalogClient.isDirectPlaybackSource(
                CatalogClient.PlaybackSource(
                    "https://nhdapi.com/embed/movie/123", "", 1, "nhd", "direct"
                )
            )
        )
        assertFalse(
            CatalogClient.isDirectPlaybackSource(
                CatalogClient.PlaybackSource(
                    "https://unknown.example/embed/123", "", 1, "unknown", "embed"
                )
            )
        )
    }


    @Test
    fun extractsMediaUrlsAndNormalizesEscapedSlashes() {
        val html = """<script>var file = "https:\/\/cdn.example/video/master.m3u8";</script>"""
        assertEquals(
            listOf("https://cdn.example/video/master.m3u8"),
            ProviderUrlResolver.extractMediaUrls(html, "https://mgeb.top/embed/123")
        )
    }

    @Test
    fun buildsNhdRoutesAndParsesPublicApiConfiguration() {
        val html = """var API_PATH = "/api/fetchStream.php"; var API_KEY = "abc"; var PROVIDER_LIST = ["P1","P2"];"""
        val config = ProviderUrlResolver.parseNhdApiConfig(html, "https://nhdapi.com/embed/movie/123")
        assertEquals("https://nhdapi.com/api/fetchStream.php", config?.apiPath)
        assertEquals("abc", config?.apiKey)
        assertEquals(listOf("P1", "P2"), config?.providers)
        assertTrue(ProviderUrlResolver.nhdApiEndpoints(config!!).any { it.contains("provider=P1") })
    }

    @Test
    fun extractsNhdDirectPlayUrlOnlyFromSuccessfulResponse() {
        assertEquals(
            "https://cdn.example/video.m3u8",
            ProviderUrlResolver.extractNhdPlayUrl("""{"success":true,"playUrl":"https://cdn.example/video.m3u8"}""")
        )
        assertNull(ProviderUrlResolver.extractNhdPlayUrl("""{"success":false,"playUrl":"https://cdn.example/video.m3u8"}"""))
    }

}
