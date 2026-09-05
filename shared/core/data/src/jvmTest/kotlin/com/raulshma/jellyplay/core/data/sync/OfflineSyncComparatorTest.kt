package com.raulshma.jellyplay.core.data.sync

import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.StudioInfo
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SyncStatus
import com.raulshma.jellyplay.core.model.TrickplayInfo
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import com.raulshma.jellyplay.core.data.util.TimeSource
import java.time.LocalDate
import java.time.ZoneId

class OfflineSyncComparatorTest {

    private val comparator = OfflineSyncComparator(FakeTimeSource())
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
        mediaStreams: List<MediaStream> = emptyList(),
        trickplayInfo: TrickplayInfo? = null,
        chapters: List<ChapterInfo> = emptyList(),
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
            chapters = chapters,
            mediaSources = listOfNotNull(
                if (mediaSourceId != null) {
                    MediaSource(
                        id = mediaSourceId,
                        name = "src",
                        size = mediaSize,
                        mediaStreams = mediaStreams,
                        trickplayInfo = trickplayInfo,
                    )
                } else null,
            ),
        )
    }

    private fun subtitle(
        index: Int = 0,
        codec: String = "srt",
        language: String = "eng",
        isForced: Boolean = false,
        isHearingImpaired: Boolean = false,
        isExternal: Boolean = true,
        displayTitle: String = "English",
    ): MediaStream = MediaStream(
        index = index,
        type = StreamType.SUBTITLE,
        codec = codec,
        language = language,
        isForced = isForced,
        isHearingImpaired = isHearingImpaired,
        isExternal = isExternal,
        displayTitle = displayTitle,
    )

    private fun segment(
        type: MediaSegmentType = MediaSegmentType.INTRO,
        startTicks: Long = 0L,
        endTicks: Long = 10_000_000L,
    ): MediaSegment = MediaSegment(
        id = "seg-$type-$startTicks",
        itemId = itemId,
        type = type,
        startTicks = startTicks,
        endTicks = endTicks,
    )

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

    // ── Chapters in the metadata signature ─────────────────────────────────

    private fun chapter(name: String, startTicks: Long, imageTag: String? = null) =
        ChapterInfo(name = name, startPositionTicks = startTicks, imageTag = imageTag)

    @Test
    fun `chapter name tick and imageTag changes flip the metadata signature`() {
        val base = detail(chapters = listOf(chapter("Cold Open", 0L)))
        assertNotEquals(
            comparator.metadataSignature(base),
            comparator.metadataSignature(detail(chapters = listOf(chapter("Cold Open", 0L, imageTag = "tag-1")))),
        )
        assertNotEquals(
            comparator.metadataSignature(base),
            comparator.metadataSignature(detail(chapters = listOf(chapter("Cold Open", 1_000_000L)))),
        )
        assertNotEquals(
            comparator.metadataSignature(base),
            comparator.metadataSignature(detail(chapters = listOf(
                chapter("Cold Open", 0L),
                chapter("Credits", 90_000_000L),
            ))),
        )
    }

    @Test
    fun `metadata signature ignores a pure chapter reorder`() {
        // Ordered by start ticks (not by the serialized string, which starts
        // with the name), so any permutation of the same chapters hashes equal.
        val a = detail(chapters = listOf(chapter("Zeta", 0L), chapter("Alpha", 10_000_000L)))
        val b = detail(chapters = listOf(chapter("Alpha", 10_000_000L), chapter("Zeta", 0L)))
        assertEquals(comparator.metadataSignature(a), comparator.metadataSignature(b))
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
            subtitleSignature = "",
            trickplaySignature = "",
            segmentsSignature = "",
            mediaSourceId = null,
            mediaSizeBytes = null,
        )
        val fresh = detail(mediaSourceId = null)
        val result = comparator.diff(baseline, fresh, itemId)
        assertFalse(result.state.mediaFileChanged)
    }

    // ── Sidecar signatures: subtitles, trickplay, segments ─────────────────

    @Test
    fun `subtitle signature is stable across stream reordering`() {
        val a = detail(mediaStreams = listOf(subtitle(index = 2), subtitle(index = 1)))
        val b = detail(mediaStreams = listOf(subtitle(index = 1), subtitle(index = 2)))
        assertEquals(comparator.subtitleSignature(a), comparator.subtitleSignature(b))
    }

    @Test
    fun `subtitle signature changes when codec language or index changes`() {
        val base = detail(mediaStreams = listOf(subtitle()))
        assertNotEquals(
            comparator.subtitleSignature(base),
            comparator.subtitleSignature(detail(mediaStreams = listOf(subtitle(codec = "ass")))),
        )
        assertNotEquals(
            comparator.subtitleSignature(base),
            comparator.subtitleSignature(detail(mediaStreams = listOf(subtitle(language = "spa")))),
        )
        assertNotEquals(
            comparator.subtitleSignature(base),
            comparator.subtitleSignature(detail(mediaStreams = listOf(subtitle(index = 3)))),
        )
    }

    @Test
    fun `subtitle signature is empty when there are no deliverable subtitle streams`() {
        assertEquals("", comparator.subtitleSignature(detail()))
        // Embedded-image or non-subtitle streams don't count either.
        val videoStream = MediaStream(index = 0, type = StreamType.VIDEO)
        assertEquals("", comparator.subtitleSignature(detail(mediaStreams = listOf(videoStream))))
    }

    @Test
    fun `trickplay signature changes when thumbnail count width or interval changes`() {
        val info = TrickplayInfo(width = 320, height = 180, tileWidth = 10, tileHeight = 10, thumbnailCount = 100, interval = 10000, bandwidth = 200000)
        val base = detail(trickplayInfo = info)
        assertNotEquals(
            comparator.trickplaySignature(base),
            comparator.trickplaySignature(detail(trickplayInfo = info.copy(thumbnailCount = 120))),
        )
        assertNotEquals(
            comparator.trickplaySignature(base),
            comparator.trickplaySignature(detail(trickplayInfo = info.copy(width = 480))),
        )
        assertNotEquals(
            comparator.trickplaySignature(base),
            comparator.trickplaySignature(detail(trickplayInfo = info.copy(interval = 20000))),
        )
    }

    @Test
    fun `trickplay signature is bandwidth-insensitive`() {
        val info = TrickplayInfo(width = 320, height = 180, tileWidth = 10, tileHeight = 10, thumbnailCount = 100, interval = 10000, bandwidth = 200000)
        assertEquals(
            comparator.trickplaySignature(detail(trickplayInfo = info)),
            comparator.trickplaySignature(detail(trickplayInfo = info.copy(bandwidth = 999999))),
        )
    }

    @Test
    fun `segments signature changes on count type and bounds`() {
        val one = listOf(segment())
        val base = comparator.segmentsSignature(one)
        assertNotEquals(base, comparator.segmentsSignature(one + segment(type = MediaSegmentType.OUTRO, startTicks = 50_000_000L, endTicks = 60_000_000L)))
        assertNotEquals(base, comparator.segmentsSignature(listOf(segment(type = MediaSegmentType.OUTRO))))
        assertNotEquals(base, comparator.segmentsSignature(listOf(segment(endTicks = 99_999_999L))))
        assertEquals("", comparator.segmentsSignature(emptyList()))
    }

    @Test
    fun `diff flags subtitles changed against a seeded baseline`() {
        val baseline = comparator.baseline(detail(mediaStreams = listOf(subtitle())))
        val fresh = detail(mediaStreams = listOf(subtitle(codec = "ass")))
        val result = comparator.diff(baseline, fresh, itemId)
        assertTrue(result.state.subtitlesChanged)
        assertEquals(SyncStatus.UPDATE_AVAILABLE, result.state.status)
    }

    @Test
    fun `diff flags trickplay changed against a seeded baseline`() {
        val info = TrickplayInfo(width = 320, height = 180, tileWidth = 10, tileHeight = 10, thumbnailCount = 100, interval = 10000, bandwidth = 200000)
        val baseline = comparator.baseline(detail(trickplayInfo = info))
        val fresh = detail(trickplayInfo = info.copy(thumbnailCount = 200))
        val result = comparator.diff(baseline, fresh, itemId)
        assertTrue(result.state.trickplayChanged)
    }

    @Test
    fun `diff flags segments changed only when fresh segments are supplied`() {
        val baseline = comparator.baseline(detail(), listOf(segment()))
        val fresh = detail()
        // No fresh segments passed -> segments axis not evaluated -> no flag.
        val withoutSegments = comparator.diff(baseline, fresh, itemId)
        assertFalse(withoutSegments.state.segmentsChanged)
        // Fresh segments that differ -> flag.
        val withSegments = comparator.diff(baseline, fresh, itemId, listOf(segment(endTicks = 99_999_999L)))
        assertTrue(withSegments.state.segmentsChanged)
    }

    @Test
    fun `retired metadata signature seeds silently instead of flagging`() {
        // Post-MIGRATION_50_51 row shape: image tags + media source survive,
        // but syncedMetadataSignature was nulled because the signature payload
        // format changed when chapters joined it. The first fresh fetch must
        // re-seed the axis, not flag every download as update-available.
        val fresh = detail()
        val baseline = emptyBaseline().copy(
            posterTag = "poster-1",
            backdropTag = "backdrop-1",
            mediaSourceId = "src-1",
            mediaSizeBytes = 1_000_000L,
        )
        val result = comparator.diff(baseline, fresh, itemId)
        assertFalse(result.state.metadataChanged)
        assertFalse(result.state.needsResync)
    }

    @Test
    fun `first-contact baseline (empty signature) never flags sidecar axes`() {
        // A pre-feature row has empty sidecar signatures; a fresh fetch with
        // subtitles/trickplay must not spuriously flag them.
        val baseline = SyncBaseline(
            posterTag = "poster-1",
            backdropTag = "backdrop-1",
            metadataSignature = comparator.metadataSignature(detail()),
            subtitleSignature = "",
            trickplaySignature = "",
            segmentsSignature = "",
            mediaSourceId = "src-1",
            mediaSizeBytes = 1_000_000L,
        )
        val fresh = detail(
            mediaStreams = listOf(subtitle()),
            trickplayInfo = TrickplayInfo(width = 320, height = 180, tileWidth = 10, tileHeight = 10, thumbnailCount = 100, interval = 10000, bandwidth = 200000),
        )
        val result = comparator.diff(baseline, fresh, itemId, listOf(segment()))
        assertFalse(result.state.subtitlesChanged)
        assertFalse(result.state.trickplayChanged)
        assertFalse(result.state.segmentsChanged)
        assertFalse(result.state.needsResync)
    }

    @Test
    fun `baseline captures subtitle and trickplay signatures from detail`() {
        val fresh = detail(
            mediaStreams = listOf(subtitle()),
            trickplayInfo = TrickplayInfo(width = 320, height = 180, tileWidth = 10, tileHeight = 10, thumbnailCount = 100, interval = 10000, bandwidth = 200000),
        )
        val baseline = comparator.baseline(fresh)
        assertNotEquals("", baseline.subtitleSignature)
        assertNotEquals("", baseline.trickplaySignature)
        assertEquals("", baseline.segmentsSignature) // segments not derivable from detail
    }

    @Test
    fun `baseline captures segments signature when segments supplied`() {
        val fresh = detail()
        val baseline = comparator.baseline(fresh, listOf(segment()))
        assertNotEquals("", baseline.segmentsSignature)
    }

    @Test
    fun `isSubtitleChanged and isTrickplayChanged return false for empty baseline`() {
        val fresh = detail(mediaStreams = listOf(subtitle()))
        assertFalse(comparator.isSubtitleChanged(emptyBaseline(), fresh))
        assertFalse(comparator.isTrickplayChanged(emptyBaseline(), fresh))
    }

    @Test
    fun `pending subtitle bundle flags subtitles changed even against a matching signature`() {
        // A failed-and-never-fetched bundle must drive a retry regardless of
        // what the server's inventory looks like now.
        val fresh = detail(mediaStreams = listOf(subtitle()))
        val baseline = comparator.baseline(fresh).copy(subtitlesPending = true)
        assertTrue(comparator.isSubtitleChanged(baseline, fresh))
        val result = comparator.diff(baseline, fresh, itemId)
        assertTrue(result.state.subtitlesChanged)
        assertEquals(SyncStatus.UPDATE_AVAILABLE, result.state.status)
    }

    @Test
    fun `non-pending baseline does not flag subtitles on identical signatures`() {
        val fresh = detail(mediaStreams = listOf(subtitle()))
        val result = comparator.diff(comparator.baseline(fresh), fresh, itemId)
        assertFalse(result.state.subtitlesChanged)
    }

    // Hand-rolled on purpose: comparator.baseline(fresh) derives NON-empty
    // signatures from a detail, and these tests need the "never recorded"
    // first-contact shape (empty strings) that only a pre-feature row has.
    private fun emptyBaseline(): SyncBaseline = SyncBaseline(
        posterTag = null,
        backdropTag = null,
        metadataSignature = "",
        subtitleSignature = "",
        trickplaySignature = "",
        segmentsSignature = "",
        mediaSourceId = null,
        mediaSizeBytes = null,
    )

    /**
     * Controllable [TimeSource] — same shape as the fake in
     * LyricsRepositoryImplTest (core:data deliberately hosts no shared test
     * fakes; see TimeSource's KDoc).
     */
    private class FakeTimeSource(var nowMs: Long = 1_000L) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
    }
}
