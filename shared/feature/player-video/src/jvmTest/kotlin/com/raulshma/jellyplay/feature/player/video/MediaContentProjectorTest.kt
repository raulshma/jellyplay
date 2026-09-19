package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.state.MediaContentState
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for [MediaContentProjector] — the `media` slice's single writer
 * (A8). All dependencies are plain constructor-lambda fakes over a real
 * [MediaContentState] mirror; no ViewModel, no uiState. The headline pin is
 * the refreshed-detail ORDER (detail apply → session-manager re-sync →
 * streams write → track-rebuild fan-outs) that used to live only as prose in
 * the VM's applyMediaDetailAndSourceState.
 */
class MediaContentProjectorTest {

    // The slice mirror the update seam writes into.
    private var media = MediaContentState()

    // Ordered event log — the ordering pin.
    private val log = mutableListOf<String>()
    private var lastAttachToEngine: Boolean? = null
    private var lastRefreshedStreams: List<MediaStream>? = null
    private var lastRefreshedSubtitleIndex: Int? = null
    private var matchedSource: MediaSource? = source("version-a")

    private lateinit var projector: MediaContentProjector

    private fun source(id: String, streams: List<MediaStream> = emptyList()) =
        MediaSource(id = id, name = "Source $id", mediaStreams = streams)

    private fun detail(seriesId: String? = null, overview: String? = "An overview") = MediaDetail(
        item = MediaItem(
            id = "item-1",
            name = "Test Movie",
            mediaType = MediaType.MOVIE,
            overview = overview,
            seriesId = seriesId,
        ),
    )

    @BeforeTest
    fun setUp() {
        projector = MediaContentProjector(
            updateMedia = { update ->
                media = update(media)
                log += "mediaWrite"
            },
            applyDetail = { log += "applyDetail" },
            applyRefreshedDetail = { _, attachToEngine ->
                lastAttachToEngine = attachToEngine
                log += "applyRefreshedDetail"
            },
            matchMediaSource = { log += "matchMediaSource"; matchedSource },
            onStreamsRefreshed = { streams, newSubtitleStreamIndex ->
                lastRefreshedStreams = streams
                lastRefreshedSubtitleIndex = newSubtitleStreamIndex
                log += "onStreamsRefreshed"
            },
        )
    }

    @Test
    fun onStreamUrl_writesOnlyTheUrl() {
        projector.onStreamUrl("https://jellyfin/stream")

        assertEquals("https://jellyfin/stream", media.streamUrl)
        assertEquals(MediaContentState(streamUrl = "https://jellyfin/stream"), media)
    }

    @Test
    fun onSessionState_mirrorsSessionFieldsAndDerivedSeriesId() {
        projector.onDetail(detail(seriesId = "series-1"), artworkUrl = "https://art/400")

        projector.onSessionState(
            session = PlayerSessionState(
                currentItemId = "item-1",
                title = "Episode 2",
                currentMediaSource = source("version-b"),
                mediaStreams = listOf(MediaStream(index = 0, type = StreamType.VIDEO)),
                playMethodString = "Transcode",
                transcodeReasons = listOf("ContainerNotSupported"),
                isDirectPlayForced = true,
            ),
            seriesId = "series-9",
        )

        assertEquals(source("version-b"), media.currentMediaSource)
        assertEquals(listOf(MediaStream(index = 0, type = StreamType.VIDEO)), media.mediaStreams)
        assertEquals("Transcode", media.playMethod)
        assertEquals(listOf("ContainerNotSupported"), media.transcodeReasons)
        assertEquals(true, media.isDirectPlayForced)
        assertEquals("series-9", media.seriesId, "the caller-derived series id wins (session transition refresh)")
        // Unrelated slice fields from the prior detail apply survive.
        assertEquals("An overview", media.overview)
        assertEquals("https://art/400", media.artworkUrl)
    }

    @Test
    fun onDetail_writesDetailFields() {
        projector.onDetail(detail(seriesId = "series-1"), artworkUrl = "https://art/400")

        assertEquals("An overview", media.overview)
        assertEquals("https://art/400", media.artworkUrl)
        assertEquals("series-1", media.seriesId)

        // A null overview normalizes to empty, not null.
        projector.onDetail(detail(overview = null), artworkUrl = "x")
        assertEquals("", media.overview)
    }

    @Test
    fun onLyrics_writesAndClears() {
        val lines = listOf(LyricsLine(timeMs = 0L, text = "la"))
        projector.onLyrics(lines)
        assertEquals(lines, media.lyricsLines)

        projector.onLyrics(emptyList())
        assertEquals(emptyList<LyricsLine>(), media.lyricsLines)
    }

    @Test
    fun onDetailRefreshed_sessionManagerFirst_thenStreams_thenTrackRebuild() {
        val streams = listOf(MediaStream(index = 1, type = StreamType.SUBTITLE))
        matchedSource = source("version-a", streams)

        projector.onDetailRefreshed(
            MediaDetailRefresh(
                detail = detail(),
                attachToEngine = false,
                newSubtitleStreamIndex = 7,
            )
        )

        // THE ordering pin: the session-manager re-sync must run BEFORE the
        // streams write (so reloads rebuild side-loads from the refreshed
        // detail and the session collector cannot revert the write to stale
        // streams), and the track-rebuild fan-outs LAST (so the picker reads
        // the refreshed streams).
        assertEquals(
            listOf(
                "applyDetail",
                "applyRefreshedDetail",
                "matchMediaSource",
                "mediaWrite",
                "onStreamsRefreshed",
            ),
            log,
        )
        assertEquals(false, lastAttachToEngine)
        assertEquals(streams, media.mediaStreams)
        assertEquals(source("version-a", streams), media.currentMediaSource)
        assertEquals(streams, lastRefreshedStreams)
        assertEquals(7, lastRefreshedSubtitleIndex)
    }

    @Test
    fun onDetailRefreshed_nullSourceClearsStreamsToEmpty() {
        matchedSource = null

        projector.onDetailRefreshed(MediaDetailRefresh(detail = detail()))

        assertNull(media.currentMediaSource)
        assertEquals(emptyList(), media.mediaStreams)
        assertEquals(emptyList(), lastRefreshedStreams)
        assertNull(lastRefreshedSubtitleIndex)
    }
}
