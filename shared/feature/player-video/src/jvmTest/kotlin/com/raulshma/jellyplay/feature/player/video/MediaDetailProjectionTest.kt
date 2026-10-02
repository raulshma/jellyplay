package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.datastore.volume.VolumeProfileStore
import com.raulshma.jellyplay.core.datastore.volume.VolumeProfileSlice
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.VolumeBucket
import com.raulshma.jellyplay.core.testfixtures.FakeMediaEngine
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the media-detail application projection extracted verbatim from
 * [VideoPlayerViewModel]: the ordered fan-out (detail-holder → chapters →
 * media slice → episode adoption → companion-lyrics → volume memory) and the
 * refreshed-detail pass-through. Order is load-bearing (the KDoc forbids
 * reordering) — the recording harness asserts the exact sequence.
 */
class MediaDetailProjectionTest {

    /** Records every sink invocation in call order. */
    private class Recording {
        val order = mutableListOf<String>()
        var chaptersCount = 0
        var slicedDetailId: String? = null
        var adoptedSeasonId: String? = null
        var lyrics: List<LyricsLine> = emptyList()
        var refreshed: MediaDetailRefresh? = null
    }

    private fun fakeLyricsRepository(
        result: Result<LyricsResult>,
    ): LyricsRepository = object : LyricsRepository {
        override suspend fun getLyrics(itemId: String) = result
        override suspend fun getLyricsWithFallback(
            itemId: String,
            artistName: String?,
            trackName: String?,
            duration: Double?,
        ) = result

        override suspend fun searchLyrics(query: String): Result<List<LrcLibTrack>> =
            Result.success(emptyList())

        override suspend fun getLyricsById(lrcLibId: Long, itemId: String) = result
        override suspend fun cleanupLyricsCache() {}
    }

    private fun stubStore(
        volumes: Map<VolumeBucket, Float> = emptyMap(),
        remember: Boolean = true,
    ): VolumeProfileStore = mockk {
        every { volumeProfile } returns MutableStateFlow(
            VolumeProfileSlice(volumes = volumes, rememberVolumePerContentType = remember),
        )
        coEvery { setVolume(any(), any()) } just runs
    }

    private fun recordingProjection(
        recording: Recording,
        scope: kotlinx.coroutines.CoroutineScope,
        lyricsResult: Result<LyricsResult> =
            Result.success(LyricsResult(lines = listOf(LyricsLine(0, "la")))),
        store: VolumeProfileStore = stubStore(),
        engine: com.raulshma.jellyplay.feature.player.video.engine.MediaEngine? = FakeMediaEngine(),
    ): MediaDetailProjection = MediaDetailProjection(
        scope = scope,
        lyricsRepository = fakeLyricsRepository(lyricsResult),
        volumeProfileStore = store,
        setDetail = { recording.order += "setDetail" },
        setChapters = {
            recording.chaptersCount = it.size
            recording.order += "chapters"
        },
        onDetail = { detail, artwork ->
            recording.slicedDetailId = detail.item.id
            recording.order += "onDetail:$artwork"
        },
        artworkUrl = { id -> "art/$id" },
        adoptSeasonOf = { recording.adoptedSeasonId = it.item.id; recording.order += "adoptSeason" },
        onLyrics = { recording.lyrics = it; recording.order += "lyrics" },
        onDetailRefreshed = { recording.refreshed = it; recording.order += "refreshed" },
        getEngine = { engine },
    )

    private fun audioDetail(id: String = "track-1") = MediaDetail(
        item = MediaItem(
            id = id,
            name = "Song",
            mediaType = MediaType.MUSIC,
            albumArtist = "Artist",
            runTimeTicks = 200_000_000L,
        ),
    )

    private fun videoDetail(id: String = "movie-1") = MediaDetail(
        item = MediaItem(id = id, name = "Film", mediaType = MediaType.MOVIE),
    )

    @Test
    fun applyDetail_fanOutRunsInTheLoadBearingOrder() = runTest {
        val recording = Recording()
        val projection = recordingProjection(recording, this)

        projection.applyDetail(videoDetail())

        assertEquals(
            listOf("setDetail", "chapters", "onDetail:art/movie-1", "adoptSeason", "lyrics"),
            recording.order,
        )
        assertEquals("movie-1", recording.slicedDetailId)
        assertEquals("movie-1", recording.adoptedSeasonId)
    }

    @Test
    fun applyDetail_audioItemFetchesCompanionLyrics_andFoldsThemIntoTheSlice() = runTest {
        val recording = Recording()
        val projection = recordingProjection(recording, this)

        projection.applyDetail(audioDetail())
        advanceUntilIdle()

        assertEquals("lyrics", recording.order.last(), "lyrics land after the synchronous fan-out")
        assertEquals(1, recording.lyrics.size)
    }

    @Test
    fun applyDetail_failedLyricsFetchFoldsEmpty() = runTest {
        val recording = Recording()
        val projection = recordingProjection(
            recording,
            this,
            lyricsResult = Result.failure(IllegalStateException("lookup failed")),
        )

        projection.applyDetail(audioDetail())
        advanceUntilIdle()

        assertEquals(emptyList(), recording.lyrics, "a failed companion fetch folds an empty list")
        assertEquals("lyrics", recording.order.last(), "the failure path still folds (an empty list) into the slice")
    }

    @Test
    fun applyDetail_desktopRestoresRememberedVolumeLevel() = runTest {
        val engine = FakeMediaEngine()
        val projection = recordingProjection(
            Recording(),
            this,
            store = stubStore(volumes = mapOf(VolumeBucket.MUSIC to 0.42f)),
            engine = engine,
        )

        projection.applyDetail(audioDetail())
        advanceUntilIdle()

        assertEquals(0.42f, engine.volume, "the bucket's remembered level is restored programmatically")
    }

    @Test
    fun applyDetail_captureArmsWhenRemembering_onAndStaysOffWhenDisabled() = runTest {
        val engineOn = mockk<FakeMediaEngine>()
        every { engineOn.setVolume(any(), any()) } just runs
        every { engineOn.onUserVolumeChange } returns null
        every { engineOn.onUserVolumeChange = any() } just runs
        recordingProjection(
            Recording(),
            this,
            store = stubStore(remember = true),
            engine = engineOn,
        ).applyDetail(audioDetail())
        advanceUntilIdle()
        verify { engineOn.onUserVolumeChange = any() }

        val engineOff = mockk<FakeMediaEngine>()
        every { engineOff.setVolume(any(), any()) } just runs
        every { engineOff.onUserVolumeChange } returns null
        every { engineOff.onUserVolumeChange = any() } just runs
        recordingProjection(
            Recording(),
            this,
            store = stubStore(remember = false),
            engine = engineOff,
        ).applyDetail(audioDetail())
        advanceUntilIdle()
        verify { engineOff.onUserVolumeChange = null }
    }

    @Test
    fun applyDetail_volumeMemoryWithoutStoredLevelLeavesVolumeUntouched() = runTest {
        val engine = FakeMediaEngine()
        val projection = recordingProjection(
            Recording(),
            this,
            store = stubStore(),
            engine = engine,
        )

        projection.applyDetail(audioDetail())
        advanceUntilIdle()

        assertEquals(1f, engine.volume, "nothing to restore — the engine default stays")
    }

    @Test
    fun applyRefreshedDetail_isAPassThroughToTheReSyncSink() = runTest {
        val recording = Recording()
        val projection = recordingProjection(recording, this)
        val refresh = MediaDetailRefresh(detail = videoDetail(), attachToEngine = false)

        projection.applyRefreshedDetail(refresh)

        assertEquals(refresh, recording.refreshed)
        assertEquals(listOf("refreshed"), recording.order)
    }
}
