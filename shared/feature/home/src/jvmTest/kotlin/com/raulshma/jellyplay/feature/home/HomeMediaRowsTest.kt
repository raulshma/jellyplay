package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NameGuidPair
import com.raulshma.jellyplay.core.ui.components.progressFraction
import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Asserts the PRODUCTION pure helpers the card rows read —
 * [MediaItem.progressFraction], [fallbackImageUrls],
 * [photoFolderPrefetchTargets] — previously these were re-declared inline at
 * their (untested) call sites and asserted against the copy.
 */
class HomeMediaRowsTest {

    @Test
    fun progressFraction_halfWatched_isHalf() {
        val item = MediaItem(
            id = "i1",
            name = "Item",
            mediaType = MediaType.MOVIE,
            runTimeTicks = 600_000_000L,
            playbackPositionTicks = 300_000_000L,
        )

        assertEquals(0.5f, item.progressFraction()!!, 0.01f)
    }

    @Test
    fun progressFraction_noPosition_isNull() {
        val item = MediaItem(
            id = "i1",
            name = "Item",
            mediaType = MediaType.MOVIE,
            runTimeTicks = 600_000_000L,
        )

        assertEquals(null, item.progressFraction())
    }

    @Test
    fun progressFraction_pastRuntime_clampsToOne() {
        val item = MediaItem(
            id = "i1",
            name = "Item",
            mediaType = MediaType.MOVIE,
            runTimeTicks = 600_000_000L,
            playbackPositionTicks = 700_000_000L,
        )

        assertEquals(1.0f, item.progressFraction()!!, 0.001f)
    }

    // ── fallbackImageUrls truth table ──

    private fun audioItem(
        parentId: String? = null,
        artistIds: List<String> = emptyList(),
    ) = MediaItem(
        id = "track",
        name = "Track",
        mediaType = MediaType.AUDIO,
        parentId = parentId,
        artistItems = artistIds.map { NameGuidPair(name = "Artist", id = it) },
    )

    @Test
    fun fallbackImageUrls_audioWithParent_resolvesParentArtOnly() {
        val urls = fallbackImageUrls(
            audioItem(parentId = "album-1"),
            getImageUrl = { id -> "img/$id" },
        )

        assertEquals(listOf("img/album-1"), urls)
    }

    @Test
    fun fallbackImageUrls_audioWithoutParentButWithArtist_resolvesArtistArt() {
        val urls = fallbackImageUrls(
            audioItem(artistIds = listOf("artist-1")),
            getImageUrl = { id -> "img/$id" },
        )

        assertEquals(listOf("img/artist-1"), urls)
    }

    @Test
    fun fallbackImageUrls_audioWithBoth_parentFirstThenArtist() {
        val urls = fallbackImageUrls(
            audioItem(parentId = "album-1", artistIds = listOf("artist-1")),
            getImageUrl = { id -> "img/$id" },
        )

        assertEquals(listOf("img/album-1", "img/artist-1"), urls)
    }

    @Test
    fun fallbackImageUrls_nonAudio_isEmpty() {
        val item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE, parentId = "p")

        assertEquals(emptyList(), fallbackImageUrls(item, getImageUrl = { "img/$it" }))
    }

    @Test
    fun fallbackImageUrls_musicTypeBehavesLikeAudio() {
        val item = MediaItem(
            id = "track",
            name = "Track",
            mediaType = MediaType.MUSIC,
            parentId = "album-1",
            artistItems = listOf(NameGuidPair(name = "Artist", id = "artist-1")),
        )

        assertEquals(
            listOf("img/album-1", "img/artist-1"),
            fallbackImageUrls(item, getImageUrl = { id -> "img/$id" }),
        )
    }

    @Test
    fun fallbackImageUrls_audioWithNoParentNorArtist_isEmpty() {
        assertEquals(
            emptyList(),
            fallbackImageUrls(audioItem(), getImageUrl = { "img/$it" }),
        )
    }

    // ── photoFolderPrefetchTargets ──

    private fun section(id: String, type: HomeSectionType, items: List<MediaItem>) =
        HomeSection(id = id, title = id, type = type, items = items)

    @Test
    fun photoFolderPrefetchTargets_keepsOnlyPhotoFolders_inOrder() {
        val movie = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)
        val folderA = MediaItem(id = "f1", name = "Folder A", mediaType = MediaType.PHOTO_FOLDER)
        val photo = MediaItem(id = "p1", name = "Photo", mediaType = MediaType.PHOTO)
        val folderB = MediaItem(id = "f2", name = "Folder B", mediaType = MediaType.PHOTO_FOLDER)

        val targets = photoFolderPrefetchTargets(
            listOf(
                section("s1", HomeSectionType.LATEST_MEDIA, listOf(movie, folderA)),
                section("s2", HomeSectionType.RECENTLY_ADDED, listOf(photo, folderB)),
            ),
        )

        assertEquals(listOf(folderA, folderB), targets)
    }

    @Test
    fun photoFolderPrefetchTargets_noFolders_isEmpty() {
        val targets = photoFolderPrefetchTargets(
            listOf(
                section("s1", HomeSectionType.LATEST_MEDIA, listOf(MediaItem(id = "m1", name = "M", mediaType = MediaType.MOVIE))),
                section("s2", HomeSectionType.NEXT_UP, emptyList()),
            ),
        )

        assertEquals(emptyList(), targets)
    }
}
