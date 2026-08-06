package com.raulshma.jellyplay.core.data.sync

import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.StudioInfo
import com.raulshma.jellyplay.core.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSyncComparatorTest {

    private val comparator = OfflineSyncComparator()
    private val itemId = "item-1"

    private fun detail(
        name: String = "Test Movie",
        overview: String? = "An overview.",
        year: Int? = 2024,
        posterTag: String? = "poster-1",
        backdropTag: String? = "backdrop-1",
        people: List<PersonInfo> = emptyList(),
        genres: List<String> = listOf("Drama"),
        studios: List<StudioInfo> = emptyList(),
        taglines: List<String> = emptyList(),
        mediaSourceId: String? = "src-1",
        mediaSize: Long? = 1_000_000L,
        criticRating: Float? = null,
    ): MediaDetail {
        val item = MediaItem(
            id = itemId,
            name = name,
            overview = overview,
            mediaType = MediaType.MOVIE,
            year = year,
            genres = genres,
            studios = studios.map { it.name },
        )
        return MediaDetail(
            item = item,
            posterImageTag = posterTag,
            backdropImageTag = backdropTag,
            taglines = taglines,
            people = people,
            criticRating = criticRating,
            mediaSources = listOfNotNull(
                if (mediaSourceId != null) {
                    MediaSource(id = mediaSourceId, name = "src", size = mediaSize)
                } else null,
            ),
        )
    }

    @Test
    fun `identical detail against its own baseline reports CURRENT`() {
        val fresh = detail()
        val baseline = comparator.baseline(fresh)
        val result = comparator.diff(baseline, fresh, itemId)
        assertEquals(SyncStatus.CURRENT, result.state.status)
        assertFalse(result.state.metadataChanged)
        assertFalse(result.state.imagesChanged)
        assertFalse(result.state.mediaFileChanged)
        assertFalse(result.state.needsResync)
    }

    @Test
    fun `overview change flips metadata flag`() {
        val baseline = comparator.baseline(detail())
        val fresh = detail(overview = "A revised overview.")
        val result = comparator.diff(baseline, fresh, itemId)
        assertTrue(result.state.metadataChanged)
        assertFalse(result.state.imagesChanged)
        assertTrue(result.state.needsResync)
        assertEquals(SyncStatus.UPDATE_AVAILABLE, result.state.status)
    }

    @Test
    fun `poster tag change flips images flag`() {
        val baseline = comparator.baseline(detail())
        val fresh = detail(posterTag = "poster-2")
        val result = comparator.diff(baseline, fresh, itemId)
        assertTrue(result.state.imagesChanged)
        assertFalse(result.state.metadataChanged)
        assertTrue(result.state.needsResync)
    }

    @Test
    fun `backdrop tag change flips images flag`() {
        val baseline = comparator.baseline(detail())
        val fresh = detail(backdropTag = "backdrop-2")
        val result = comparator.diff(baseline, fresh, itemId)
        assertTrue(result.state.imagesChanged)
    }

    @Test
    fun `media source id change flips media file flag`() {
        val baseline = comparator.baseline(detail())
        val fresh = detail(mediaSourceId = "src-2")
        val result = comparator.diff(baseline, fresh, itemId)
        assertTrue(result.state.mediaFileChanged)
        assertEquals(SyncStatus.UPDATE_AVAILABLE, result.state.status)
    }

    @Test
    fun `media source size change flips media file flag`() {
        val baseline = comparator.baseline(detail())
        val fresh = detail(mediaSize = 2_000_000L)
        val result = comparator.diff(baseline, fresh, itemId)
        assertTrue(result.state.mediaFileChanged)
    }

    @Test
    fun `null vs blank image tags treated as equal`() {
        val baseline = comparator.baseline(detail(backdropTag = null))
        val fresh = detail(backdropTag = "")
        val result = comparator.diff(baseline, fresh, itemId)
        assertFalse(result.state.imagesChanged)
    }

    @Test
    fun `gaining a backdrop where none existed flips images flag`() {
        val baseline = comparator.baseline(detail(backdropTag = null))
        val fresh = detail(backdropTag = "new-backdrop")
        val result = comparator.diff(baseline, fresh, itemId)
        assertTrue(result.state.imagesChanged)
    }

    @Test
    fun `signature is deterministic across calls`() {
        val d = detail()
        assertEquals(comparator.metadataSignature(d), comparator.metadataSignature(d))
    }

    @Test
    fun `signature is order-insensitive for genres and cast`() {
        val a = detail(
            genres = listOf("Drama", "Thriller"),
            people = listOf(
                PersonInfo(id = "p1", name = "Alice", type = "Actor"),
                PersonInfo(id = "p2", name = "Bob", type = "Actor"),
            ),
        )
        val b = detail(
            genres = listOf("Thriller", "Drama"),
            people = listOf(
                PersonInfo(id = "p2", name = "Bob", type = "Actor"),
                PersonInfo(id = "p1", name = "Alice", type = "Actor"),
            ),
        )
        assertEquals(comparator.metadataSignature(a), comparator.metadataSignature(b))
    }

    @Test
    fun `cast member change detected`() {
        val baseline = comparator.baseline(detail(people = listOf(
            PersonInfo(id = "p1", name = "Alice", type = "Actor"),
        )))
        val fresh = detail(people = listOf(
            PersonInfo(id = "p1", name = "Alice", type = "Actor"),
            PersonInfo(id = "p2", name = "Bob", type = "Actor"),
        ))
        val result = comparator.diff(baseline, fresh, itemId)
        assertTrue(result.state.metadataChanged)
    }

    @Test
    fun `baseline extraction captures tags signature and media source`() {
        val fresh = detail()
        val baseline = comparator.baseline(fresh)
        assertEquals("poster-1", baseline.posterTag)
        assertEquals("backdrop-1", baseline.backdropTag)
        assertEquals("src-1", baseline.mediaSourceId)
        assertEquals(1_000_000L, baseline.mediaSizeBytes)
        assertNotEquals("", baseline.metadataSignature)
    }

    @Test
    fun `no media sources on baseline and fresh reports no media change`() {
        val baseline = SyncBaseline(
            posterTag = "p",
            backdropTag = "b",
            metadataSignature = "sig",
            mediaSourceId = null,
            mediaSizeBytes = null,
        )
        val fresh = detail(mediaSourceId = null)
        val result = comparator.diff(baseline, fresh, itemId)
        assertFalse(result.state.mediaFileChanged)
    }
}
