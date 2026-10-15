package com.cinehub.player

import org.junit.Assert.assertEquals
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
}
