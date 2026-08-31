package com.raulshma.jellyplay.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Projection tests for [OfflineMediaItem.toMediaDetail] — the lossy offline →
 * [MediaDetail] mapping the unified detail screen renders for a local origin.
 *
 * Asserts the persisted fields with a direct target, the watched-state
 * normalization inherited from [OfflineMediaItem.toMediaItem], `tagline` →
 * `taglines`, provider ids, urls, people, persisted chapters, and that no fake
 * studio id is ever invented. Also pins the lossy defaults (empty mediaSources /
 * relatedItems / lockData) so nulls are never silently inherited, and confirms
 * local cast artwork paths are represented *outside* [MediaDetail] (on the
 * source [OfflinePersonInfo]) rather than dropped.
 */
class OfflineMediaItemDetailProjectionTest {

    private fun fixture(): OfflineMediaItem = OfflineMediaItem(
        id = "movie-1",
        name = "Test Movie",
        mediaType = MediaType.MOVIE,
        overview = "An overview",
        year = 2024,
        communityRating = 8.1f,
        officialRating = "PG-13",
        runTimeTicks = 123L,
        genres = listOf("Drama", "Sci-Fi"),
        studios = listOf("Studio A", "Studio B"),
        tagline = "A tagline",
        criticRating = 77f,
        originalTitle = "Original",
        providerIds = mapOf("Tmdb" to "123", "Imdb" to "tt456"),
        externalUrls = listOf(ExternalUrl("Site", "https://example.com")),
        chapters = listOf(
            ChapterInfo(name = "Opening", startPositionTicks = 0L),
            ChapterInfo(
                name = "Credits",
                startPositionTicks = 100_000_000L,
                imageTag = "chapter-tag",
                imageDateModified = "2024-01-01T00:00:00Z",
            ),
        ),
        cast = listOf(
            OfflinePersonInfo(
                id = "person-1",
                name = "Actor One",
                role = "Self",
                type = "Actor",
                imageTag = "tag-1",
                blurHash = "blur",
                localImagePath = "/data/offline/person-1.jpg",
            ),
            OfflinePersonInfo(
                id = "person-2",
                name = "Director Two",
                type = "Director",
            ),
        ),
    )

    @Test
    fun toMediaDetail_itemMatchesToMediaItem() {
        val source = fixture()
        val detail = source.toMediaDetail()

        assertEquals(source.toMediaItem(), detail.item)
    }

    @Test
    fun toMediaDetail_inheritsWatchedNormalization() {
        val runtime = 10_000L * 10_000L
        val source = fixture().copy(
            mediaType = MediaType.EPISODE,
            runTimeTicks = runtime,
            playbackPositionTicks = (runtime.toDouble() * 0.96).toLong(),
            isPlayed = false,
        )

        val item = source.toMediaDetail().item

        // 96% resume normalizes to watched, position cleared.
        assertTrue(item.isPlayed)
        assertNull(item.playbackPositionTicks)
    }

    @Test
    fun toMediaDetail_mapsTaglineToSingleElementTaglines() {
        val detail = fixture().toMediaDetail()

        assertEquals(listOf("A tagline"), detail.taglines)
    }

    @Test
    fun toMediaDetail_nullTaglineProducesEmptyTaglines() {
        val detail = fixture().copy(tagline = null).toMediaDetail()

        assertTrue(detail.taglines.isEmpty())
    }

    @Test
    fun toMediaDetail_mapsCastToPeopleWithPrimaryImageTag() {
        val people = fixture().toMediaDetail().people

        assertEquals(2, people.size)
        val actor = people[0]
        assertEquals("person-1", actor.id)
        assertEquals("Actor One", actor.name)
        assertEquals("Self", actor.role)
        assertEquals("Actor", actor.type)
        // imageTag is projected onto primaryImageTag (not lost).
        assertEquals("tag-1", actor.primaryImageTag)
    }

    @Test
    fun toMediaDetail_carriesProviderIdsAndExternalUrls() {
        val detail = fixture().toMediaDetail()

        assertEquals(mapOf("Tmdb" to "123", "Imdb" to "tt456"), detail.providerIds)
        assertEquals(listOf(ExternalUrl("Site", "https://example.com")), detail.externalUrls)
    }

    @Test
    fun toMediaDetail_carriesCriticRating() {
        assertEquals(77f, fixture().toMediaDetail().criticRating)
    }

    @Test
    fun toMediaDetail_doesNotInventStudioIds() {
        val detail = fixture().toMediaDetail()

        // Studios are names only; StudioInfo requires a server id, so the
        // MediaDetail.studios collection must stay empty. The names survive on
        // the embedded item for non-navigable label rendering.
        assertTrue(
            "MediaDetail.studios must be empty for a local projection",
            detail.studios.isEmpty(),
        )
        assertEquals(listOf("Studio A", "Studio B"), detail.item.studios)
    }

    @Test
    fun toMediaDetail_carriesChapters() {
        val chapters = fixture().toMediaDetail().chapters

        assertEquals(2, chapters.size)
        assertEquals("Opening", chapters[0].name)
        assertEquals(0L, chapters[0].startPositionTicks)
        assertEquals("Credits", chapters[1].name)
        assertEquals(100_000_000L, chapters[1].startPositionTicks)
        assertEquals("chapter-tag", chapters[1].imageTag)
    }

    @Test
    fun toMediaDetail_lossyServerCollectionsDefaultToEmpty() {
        val detail = fixture().copy(chapters = emptyList()).toMediaDetail()

        assertTrue(detail.chapters.isEmpty())
        assertTrue(detail.mediaSources.isEmpty())
        assertTrue(detail.relatedItems.isEmpty())
        assertTrue(detail.tagItems.isEmpty())
        assertTrue(detail.productionLocations.isEmpty())
        assertTrue(detail.airDays.isEmpty())
        assertTrue(detail.imageInfos.isEmpty())
        assertTrue(detail.lockedFields.isEmpty())
        assertFalse(detail.lockData)
        assertNull(detail.displayOrder)
        assertNull(detail.airTime)
        assertNull(detail.dateCreated)
        assertNull(detail.sortName)
        assertNull(detail.customRating)
        assertNull(detail.logoImageTag)
        assertNull(detail.overviewImageTag)
        assertNull(detail.backdropImageTag)
        assertNull(detail.posterImageTag)
    }

    @Test
    fun toMediaDetail_emptyCastProducesEmptyPeople() {
        val detail = fixture().copy(cast = emptyList()).toMediaDetail()

        assertTrue(detail.people.isEmpty())
    }

    @Test
    fun localCastArtworkPathsRemainOnSourceAndAreNotOnPersonInfo() {
        // Local artwork paths are surfaced via DetailAssets (built by the
        // provider from the source row), not via MediaDetail. Confirm the
        // source still carries localImagePath and the projected PersonInfo does
        // not (it has no such field) — i.e. the path is not silently lost, just
        // deliberately rehomed.
        val source = fixture()
        val personInfo = source.toMediaDetail().people[0]

        assertEquals("/data/offline/person-1.jpg", source.cast.first().localImagePath)
        // PersonInfo exposes only primaryImageTag; the local path lives on the source.
        assertNull(personInfo.primaryBlurHash)
    }
}
