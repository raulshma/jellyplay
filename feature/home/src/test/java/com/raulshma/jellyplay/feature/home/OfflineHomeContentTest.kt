package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the one-pass offline render model ([buildOfflineHomeContent]): one
 * aggregate feeding sections, the id→item lookup and the filtered lists the
 * rest of the home reads — previously each consumer re-derived its own slice
 * and the lookup was built twice per tree.
 */
class OfflineHomeContentTest {

    private val titles = OfflineHomeSectionTitles(
        continueWatching = "Continue Watching",
        nextUp = "Next Up",
        recentlyDownloaded = "Recently Downloaded",
        movies = "Movies",
        series = "Series",
        music = "Music",
    )

    private val library = listOf(
        OfflineMediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
        OfflineMediaItem(id = "s1", name = "Series", mediaType = MediaType.SERIES),
        OfflineMediaItem(id = "a1", name = "Album", mediaType = MediaType.MUSIC),
    )
    private val episodes = listOf(
        OfflineMediaItem(id = "e1", name = "Ep", mediaType = MediaType.EPISODE, seriesId = "s1"),
    )

    @Test
    fun `sections and lookup are consistent with the filtered lists`() {
        val content = buildOfflineHomeContent(library, episodes, HomeMode.VIDEO, titles, OfflineHomeSectionPrefs())

        // Video mode drops music from the lookup too.
        assertEquals(setOf("m1", "s1", "e1"), content.itemsById.keys)
        assertEquals(setOf("m1", "s1", "e1"), content.sections.flatMap { it.items }.map { it.id }.toSet())

        // Every derived section item resolves back to its offline original.
        for (section in content.sections) {
            for (item in section.items) {
                assertTrue(content.itemsById.containsKey(item.id))
            }
        }
        assertEquals(HomeSectionType.DOWNLOADED, content.sections[0].type)
    }

    @Test
    fun `music mode keeps music in the lookup and drops video`() {
        val content = buildOfflineHomeContent(library, episodes, HomeMode.MUSIC, titles, OfflineHomeSectionPrefs())

        assertEquals(setOf("a1"), content.itemsById.keys)
        // The music row is the only partitioned row (plus Recently Downloaded,
        // which carries the same single item); no video rows appear.
        assertTrue(content.sections.any { it.id == "offline_music" })
        assertTrue(content.sections.none { it.id == "offline_movies" || it.id == "offline_series" })
    }

    @Test
    fun `empty inputs yield an empty aggregate`() {
        val content = buildOfflineHomeContent(emptyList(), emptyList(), HomeMode.VIDEO, titles, OfflineHomeSectionPrefs())

        assertTrue(content.library.isEmpty())
        assertTrue(content.sections.isEmpty())
        assertTrue(content.itemsById.isEmpty())
    }
}
