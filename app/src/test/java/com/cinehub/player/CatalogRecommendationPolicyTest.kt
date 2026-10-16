package com.cinehub.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogRecommendationPolicyTest {
    private fun entry(id: String, title: String, genres: List<String>) =
        CatalogClient.CatalogEntry(id, title, "movie", "catalog/vod/details/movies/A-000.json", "", "", genres)

    @Test
    fun ranksByGenreOverlapAndExcludesCurrentItem() {
        val entries = listOf(
            entry("current", "Current", listOf("Drama", "Ação")),
            entry("one", "One", listOf("Drama")),
            entry("two", "Two", listOf("Drama", "Ação")),
            entry("three", "Three", listOf("Comédia"))
        )
        val result = CatalogRecommendationPolicy.select(entries, "current", listOf("drama", "ação"), 5)
        assertEquals(listOf("two", "one"), result.map { it.id })
    }

    @Test
    fun returnsEmptyWhenNoGenresAreAvailable() {
        val result = CatalogRecommendationPolicy.select(
            listOf(entry("one", "One", listOf("Drama"))), "current", emptyList()
        )
        assertEquals(emptyList<String>(), result.map { it.id })
    }
}
