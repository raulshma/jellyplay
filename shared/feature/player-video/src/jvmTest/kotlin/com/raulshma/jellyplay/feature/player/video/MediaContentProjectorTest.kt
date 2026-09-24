package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.state.MediaContentState
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Tests for [MediaContentProjector] — the `media` slice's single writer
 * (A8). All dependencies are plain constructor-lambda fakes over a real
 * [MediaContentState] mirror; no ViewModel, no uiState. The headline pins:
 * the refreshed-detail ORDER (detail apply → session-manager re-sync →
 * streams write → track-rebuild fan-outs) and the session-state FOLD
 * (unguarded title/subtitle + stored-seed every emission; item-change =
 * refresh THEN the fire-and-forget render poke) that used to live as the
 * VM's inline collector body.
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

    // Session-fold mirrors (the new seams' captured writes).
    private var lastTitle: String? = null
    private var lastSubtitle: String? = null
    private var lastStoredSelection: MediaStreamSelection? = null
    private val storedSelectionsByItem = mutableMapOf<String, MediaStreamSelection>()

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
            setTitleSubtitle = { title, subtitle ->
                lastTitle = title
                lastSubtitle = subtitle
                log += "titleSubtitle"
            },
            onStoredSelectionChanged = { stored ->
                lastStoredSelection = stored
                log += "storedSelection"
            },
            getStoredSelection = { itemId -> itemId?.let { storedSelectionsByItem[it] } },
            refreshPlaybackPreferences = { log += "refreshPlaybackPreferences" },
            onSessionItemChanged = { itemId, seriesId ->
                log += "renderPoke:$itemId:$seriesId"
            },
            // Runs the launched block inline so the refresh → poke ordering
            // is deterministic in the log; the real seam never awaits it.
            launchAsync = { block ->
                log += "launchAsync"
                kotlinx.coroutines.runBlocking { block() }
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
    fun onSessionState_firstEmissionFiresItemChange_refreshBeforeLaunchBeforeRenderPoke() {
        projector.onSessionState(
            session = PlayerSessionState(currentItemId = "item-1", title = "Episode 2"),
            seriesId = "series-9",
        )

        // THE session-fold ordering pin (the VM's former inline body): the
        // unguarded mirrors first, then — on the FIRST emission (fold state
        // starts null) — refreshPlaybackPreferences BEFORE the render poke,
        // and the poke goes through the launch seam (fire-and-forget).
        assertEquals(
            listOf(
                "titleSubtitle",
                "mediaWrite",
                "storedSelection",
                "refreshPlaybackPreferences",
                "launchAsync",
                "renderPoke:item-1:series-9",
            ),
            log,
        )
        assertEquals("Episode 2", lastTitle)
    }

    @Test
    fun onSessionState_sameItemAndSeries_doesNotRefireItemChange() {
        projector.onSessionState(
            session = PlayerSessionState(currentItemId = "item-1", title = "Episode 2"),
            seriesId = "series-9",
        )
        log.clear()

        projector.onSessionState(
            session = PlayerSessionState(currentItemId = "item-1", title = "Episode 2 (updated)"),
            seriesId = "series-9",
        )

        // Same item + series: only the unguarded per-emission mirrors run —
        // no refresh, no poke.
        assertEquals(listOf("titleSubtitle", "mediaWrite", "storedSelection"), log)
        assertEquals("Episode 2 (updated)", lastTitle, "title still forwarded on every emission")
    }

    @Test
    fun onSessionState_seriesOnlyChangeFiresItemChange() {
        projector.onSessionState(
            session = PlayerSessionState(currentItemId = "item-1"),
            seriesId = "series-9",
        )
        log.clear()

        projector.onSessionState(
            session = PlayerSessionState(currentItemId = "item-1"),
            seriesId = "series-10",
        )

        assertEquals(
            listOf("titleSubtitle", "mediaWrite", "storedSelection", "refreshPlaybackPreferences", "launchAsync", "renderPoke:item-1:series-10"),
            log,
        )
    }

    @Test
    fun onSessionState_titleSubtitleForwardedEveryEmission_unguarded() {
        projector.onSessionState(
            session = PlayerSessionState(currentItemId = "item-1", title = "A", subtitle = "S1"),
            seriesId = null,
        )
        projector.onSessionState(
            session = PlayerSessionState(currentItemId = "item-1", title = "B", subtitle = "S2"),
            seriesId = null,
        )

        assertEquals("B", lastTitle)
        assertEquals("S2", lastSubtitle)
        assertEquals(2, log.count { it == "titleSubtitle" }, "one mirror write per emission, no equality guard")
    }

    @Test
    fun onSessionState_storedSelectionForwardedEveryEmission_derivedPerItem() {
        val stored = MediaStreamSelection(audioStreamIndex = 1, subtitleStreamIndex = 2)
        storedSelectionsByItem["item-1"] = stored

        projector.onSessionState(
            session = PlayerSessionState(currentItemId = "item-1"),
            seriesId = null,
        )
        assertEquals(stored, lastStoredSelection)

        // A different item with no stored row forwards null (seeds the
        // track slice's override flags OFF) — still every emission.
        projector.onSessionState(
            session = PlayerSessionState(currentItemId = "item-2"),
            seriesId = null,
        )
        assertNull(lastStoredSelection)
        assertEquals(2, log.count { it == "storedSelection" })
    }

    @Test
    fun onSessionState_nullItemAndNullSeriesFirstEmission_doesNotFireItemChange() {
        // Fold vars start null: an all-null first emission (pre-load session
        // state) matches the fold and does NOT fire refresh/poke — the
        // inline collector's null == null comparison verbatim.
        projector.onSessionState(session = PlayerSessionState(title = "Loading"), seriesId = null)

        assertFalse(log.contains("refreshPlaybackPreferences"))
        assertFalse(log.contains("launchAsync"))
        assertEquals(listOf("titleSubtitle", "mediaWrite", "storedSelection"), log)
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
