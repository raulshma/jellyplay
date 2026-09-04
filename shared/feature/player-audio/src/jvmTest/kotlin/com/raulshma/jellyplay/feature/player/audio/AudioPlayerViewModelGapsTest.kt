package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.AudioPlayerUiPreferences
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.LyricsWord
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gap suite for [AudioPlayerViewModel] — the branches its sibling suite
 * ([AudioPlayerViewModelTest]) does not reach:
 *
 *  1. the lyrics-search choreography (isSearching flag, success/failure
 *     results, apply + offset passthrough) and the lyrics-flow mirror into
 *     [AudioPlayerUiState.lyrics] incl. the karaoke derived flag;
 *  2. the cast happy path (fling the current track at the live position,
 *     pause the local engine);
 *  3. the add-to-playlist picker lifecycle (editable filter, failure, the
 *     dismiss guard while a add is in flight);
 *  4. `downloadCurrentTrack`'s three-way routing (completed → re-download via
 *     delete, not-completed → detail + intake, no item → no-op) and the
 *     current-download mirror;
 *  5. the blurHash LRU cache (one detail fetch per item, including the
 *     negative-result sentinel) and the sleep-timer expiry pause contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlayerViewModelGapsTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var queueManager: AudioQueueManager
    private lateinit var effectsManager: AudioEffectsManager
    private lateinit var engine: AudioPlayerEngine
    private lateinit var projections: PreferenceProjections
    private lateinit var audioStore: AudioStore
    private lateinit var audioEffectsStore: AudioEffectsStore
    private lateinit var mediaRepository: MediaRepository
    private lateinit var userDataMutator: com.raulshma.jellyplay.core.data.repository.UserDataMutator
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var downloadIntake: DownloadIntake
    private lateinit var sleepTimerManager: SleepTimerManager
    private lateinit var cast: AudioPlayerCast

    private lateinit var viewModel: AudioPlayerViewModel

    /** currentPlayingItemId, swappable per test. */
    private val currentItemIdFlow = MutableStateFlow<String?>(null)

    /** Per-item download flows backing getDownloadByMediaItemIdFlow. */
    private val downloadFlows = mutableMapOf<String, MutableStateFlow<DownloadItem?>>()

    // Engine state surfaces — stubbed once, mutable per test.
    private val title = MutableStateFlow("")
    private val artist = MutableStateFlow("")
    private val artistId = MutableStateFlow<String?>(null)
    private val album = MutableStateFlow("")
    private val albumArtUrl = MutableStateFlow("")
    private val isPlaying = MutableStateFlow(false)
    private val currentPosition = MutableStateFlow(0L)
    private val duration = MutableStateFlow(0L)
    private val speed = MutableStateFlow(1.0f)
    private val playbackError = MutableStateFlow<String?>(null)
    private val isLoadingItem = MutableStateFlow(false)
    private val crossfadeDurationMs = MutableStateFlow(0L)
    private val lyrics = MutableStateFlow<List<LyricsLine>>(emptyList())
    private val currentLyricIndex = MutableStateFlow(-1)
    private val lyricsSource = MutableStateFlow(LyricsSource.UNKNOWN)
    private val isFetchingLyrics = MutableStateFlow(false)
    private val lyricsOffsetMs = MutableStateFlow(0L)
    private val queue = MutableStateFlow<List<com.raulshma.jellyplay.core.data.playback.AudioQueueItem>>(emptyList())
    private val queueIndex = MutableStateFlow(-1)
    private val shuffleMode = MutableStateFlow(false)
    private val repeatMode = MutableStateFlow(0)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        queueManager = mockk(relaxed = true)
        effectsManager = mockk(relaxed = true)
        engine = mockk(relaxed = true)
        projections = mockk(relaxed = true)
        audioStore = mockk(relaxed = true)
        audioEffectsStore = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        userDataMutator = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        downloadIntake = mockk(relaxed = true)
        sleepTimerManager = mockk(relaxed = true)
        cast = mockk(relaxed = true)

        every { projections.audioPlayerUiPreferences } returns MutableStateFlow(AudioPlayerUiPreferences())
        every { audioStore.audio } returns MutableStateFlow(AudioSlice())
        every { audioEffectsStore.audioEffects } returns MutableStateFlow(AudioEffectsSlice())
        every { effectsManager.replayGainMode } returns MutableStateFlow(com.raulshma.jellyplay.core.model.AudioNormalizationMode.NONE)
        every { effectsManager.replayGainPreAmpDb } returns MutableStateFlow(0.0f)
        every { queueManager.currentPlayingItemId } returns currentItemIdFlow
        every { queueManager.queue } returns queue
        every { queueManager.currentIndex } returns queueIndex
        every { queueManager.shuffleMode } returns shuffleMode
        every { queueManager.repeatMode } returns repeatMode
        every { engine.title } returns title
        every { engine.artist } returns artist
        every { engine.artistId } returns artistId
        every { engine.album } returns album
        every { engine.albumArtUrl } returns albumArtUrl
        every { engine.isPlaying } returns isPlaying
        every { engine.currentPosition } returns currentPosition
        every { engine.duration } returns duration
        every { engine.speed } returns speed
        every { engine.playbackError } returns playbackError
        every { engine.isLoadingItem } returns isLoadingItem
        every { engine.crossfadeDurationMs } returns crossfadeDurationMs
        every { engine.lyrics } returns lyrics
        every { engine.currentLyricIndex } returns currentLyricIndex
        every { engine.lyricsSource } returns lyricsSource
        every { engine.isFetchingLyrics } returns isFetchingLyrics
        every { engine.lyricsOffsetMs } returns lyricsOffsetMs
        every { engine.getImageUrl(any()) } returns "https://srv/Items/x/Images/Primary"
        every { engine.undoLastQueueOperation() } returns false
        every {
            downloadRepository.getDownloadByMediaItemIdFlow(any())
        } answers {
            downloadFlows.getOrPut(firstArg()) { MutableStateFlow(null) }
        }

        viewModel = AudioPlayerViewModel(
            queueManager = queueManager,
            effectsManager = effectsManager,
            engine = engine,
            projections = projections,
            audioStore = audioStore,
            audioEffectsStore = audioEffectsStore,
            mediaRepository = mediaRepository,
            userDataMutator = userDataMutator,
            downloadRepository = downloadRepository,
            downloadIntake = downloadIntake,
            sleepTimerManager = sleepTimerManager,
            cast = cast,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun detail(itemId: String, blurHash: String? = "hash-$itemId") = MediaDetail(
        item = MediaItem(
            id = itemId,
            name = "Song $itemId",
            mediaType = MediaType.AUDIO,
            blurHashes = com.raulshma.jellyplay.core.model.ImageBlurHashes(primary = blurHash),
        ),
    )

    private fun downloadItem(id: String, status: DownloadStatus) = DownloadItem(
        id = id,
        mediaItemId = "track-1",
        name = "Song",
        mediaType = MediaType.AUDIO,
        downloadPath = "/tmp/x",
        downloadUrl = "https://srv/x",
        totalSizeBytes = 10L,
        downloadedBytes = 10L,
        status = status,
    )

    // ── 1. Lyrics search / apply / offset + flow mirror ──────────────────────

    @Test
    fun searchLyrics_success_populatesResultsAndClearsSearching() {
        val track = LrcLibTrack(id = 7L, trackName = "Song", artistName = "Artist")
        every { engine.searchLyrics(any(), any()) } answers {
            secondArg<(Result<List<LrcLibTrack>>) -> Unit>()(Result.success(listOf(track)))
        }

        viewModel.searchLyrics("song")

        verify { engine.searchLyrics("song", any()) }
        with(viewModel.uiState.value.lyrics) {
            assertEquals(listOf(track), searchResults)
            assertFalse(isSearching, "the flag clears once the callback lands")
        }
    }

    @Test
    fun searchLyrics_failure_leavesResultsEmptyAndClearsSearching() {
        every { engine.searchLyrics(any(), any()) } answers {
            secondArg<(Result<List<LrcLibTrack>>) -> Unit>()(
                Result.failure(RuntimeException("lrclib down")),
            )
        }

        viewModel.searchLyrics("song")

        assertTrue(viewModel.uiState.value.lyrics.searchResults.isEmpty())
        assertFalse(viewModel.uiState.value.lyrics.isSearching)
    }

    @Test
    fun applyLyrics_delegatesWithTrackIdAndClearsSearchResults() {
        val track = LrcLibTrack(id = 42L, trackName = "Song", artistName = "Artist")
        every { engine.searchLyrics(any(), any()) } answers {
            secondArg<(Result<List<LrcLibTrack>>) -> Unit>()(Result.success(listOf(track)))
        }
        viewModel.searchLyrics("song")
        assertFalse(viewModel.uiState.value.lyrics.searchResults.isEmpty())

        viewModel.applyLyrics(track)

        verify { engine.applyLyrics(42L) }
        assertTrue(viewModel.uiState.value.lyrics.searchResults.isEmpty())
    }

    @Test
    fun setLyricsOffset_delegatesToEngine() {
        viewModel.setLyricsOffset(250L)
        verify { engine.setLyricsOffset(250L) }
    }

    @Test
    fun engineLyricsFlow_mirrorsIntoUiState_andDrivesTheKaraokeFlag() {
        assertFalse(viewModel.uiState.value.lyrics.hasKaraokeLyrics)

        val worded = listOf(
            LyricsLine(
                timeMs = 1_000L,
                text = "hello world",
                words = listOf(LyricsWord(timeMs = 1_000L, text = "hello", durationMs = 300L)),
            ),
        )
        lyrics.value = worded
        currentLyricIndex.value = 0
        lyricsSource.value = LyricsSource.LRCLIB
        isFetchingLyrics.value = true
        lyricsOffsetMs.value = 120L

        with(viewModel.uiState.value.lyrics) {
            assertEquals(worded, lyrics)
            assertEquals(0, currentLyricIndex)
            assertEquals(LyricsSource.LRCLIB, lyricsSource)
            assertTrue(isFetchingLyrics)
            assertEquals(120L, lyricsOffsetMs)
            assertTrue(hasKaraokeLyrics, "a word-timed line enables the karaoke view")
        }
    }

    @Test
    fun getImageUrl_delegatesToEngine() {
        assertEquals("https://srv/Items/x/Images/Primary", viewModel.getImageUrl("x"))
    }

    // ── 2. Cast happy path ───────────────────────────────────────────────────

    @Test
    fun castToDevice_flingsCurrentTrackAtLivePosition_andPausesLocalEngine() {
        currentItemIdFlow.value = "track-1"
        currentPosition.value = 42_000L

        viewModel.castToDevice()

        verify(exactly = 1) { cast.loadMedia("track-1", 42_000L) }
        verify(exactly = 1) { engine.pause() }
    }

    // ── 3. Add-to-playlist picker ────────────────────────────────────────────

    @Test
    fun openPlaylistPicker_listsEditablePlaylistsOnly() {
        currentItemIdFlow.value = "track-1"
        val editable = Playlist(id = "p1", name = "Mine", canEdit = true)
        val locked = Playlist(id = "p2", name = "Theirs", canEdit = false)
        coEvery { mediaRepository.getPlaylists(any()) } returns Result.success(listOf(editable, locked))

        viewModel.openPlaylistPicker()

        assertTrue(viewModel.uiState.value.showPlaylistPicker)
        assertFalse(viewModel.uiState.value.isLoadingPlaylists)
        assertEquals(listOf(editable), viewModel.uiState.value.playlists)
    }

    @Test
    fun openPlaylistPicker_withoutCurrentItem_neverOpens() {
        currentItemIdFlow.value = null

        viewModel.openPlaylistPicker()

        assertFalse(viewModel.uiState.value.showPlaylistPicker)
        coVerify(exactly = 0) { mediaRepository.getPlaylists(any()) }
    }

    @Test
    fun openPlaylistPicker_failure_stopsLoadingWithAnEmptyList() {
        currentItemIdFlow.value = "track-1"
        coEvery { mediaRepository.getPlaylists(any()) } returns Result.failure(RuntimeException("offline"))

        viewModel.openPlaylistPicker()

        assertTrue(viewModel.uiState.value.showPlaylistPicker, "the picker still opens on failure")
        assertFalse(viewModel.uiState.value.isLoadingPlaylists)
        assertTrue(viewModel.uiState.value.playlists.isEmpty())
    }

    @Test
    fun addToPlaylist_success_closesPickerAndPostsThePlaylistName() {
        currentItemIdFlow.value = "track-1"
        val playlist = Playlist(id = "p1", name = "Road Trip", canEdit = true)
        coEvery { mediaRepository.addItemsToPlaylist("p1", listOf("track-1")) } returns Result.success(Unit)

        viewModel.addToPlaylist(playlist)

        with(viewModel.uiState.value) {
            assertFalse(isAddingToPlaylist)
            assertFalse(showPlaylistPicker)
            assertTrue(playlists.isEmpty())
            assertEquals("Road Trip", playlistMessage)
        }
    }

    @Test
    fun addToPlaylist_failure_keepsPickerOpenAndSurfacesTheError() {
        currentItemIdFlow.value = "track-1"
        coEvery { mediaRepository.getPlaylists(limit = 100) } returns Result.success(emptyList())
        viewModel.openPlaylistPicker()
        val playlist = Playlist(id = "p1", name = "Road Trip", canEdit = true)
        coEvery { mediaRepository.addItemsToPlaylist(any(), any()) } returns
            Result.failure(RuntimeException("server rejected"))

        viewModel.addToPlaylist(playlist)

        assertFalse(viewModel.uiState.value.isAddingToPlaylist)
        assertTrue(viewModel.uiState.value.showPlaylistPicker, "a failed add leaves the picker open")
        assertEquals("server rejected", viewModel.uiState.value.playlistMessage)
    }

    @Test
    fun dismissPlaylistPicker_isGuardedWhileAnAddIsInFlight() {
        currentItemIdFlow.value = "track-1"
        val gate = CompletableDeferred<Unit>()
        coEvery { mediaRepository.getPlaylists(any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.addItemsToPlaylist(any(), any()) } coAnswers {
            gate.await()
            Result.success(Unit)
        }
        viewModel.openPlaylistPicker()
        viewModel.addToPlaylist(Playlist(id = "p1", name = "Road Trip"))

        // The add is in flight: a scrim tap must not close the picker.
        viewModel.dismissPlaylistPicker()
        assertTrue(viewModel.uiState.value.showPlaylistPicker)

        gate.complete(Unit)
        // The completed add closes the picker itself; a post-add dismiss is a
        // harmless no-op reset.
        assertFalse(viewModel.uiState.value.showPlaylistPicker)
        viewModel.dismissPlaylistPicker()
        assertFalse(viewModel.uiState.value.showPlaylistPicker)
        assertTrue(viewModel.uiState.value.playlists.isEmpty())
        assertNull(viewModel.uiState.value.playlistMessage)
    }

    @Test
    fun clearPlaylistMessage_clearsOnlyTheMessage() {
        currentItemIdFlow.value = "track-1"
        coEvery { mediaRepository.addItemsToPlaylist(any(), any()) } returns Result.success(Unit)
        viewModel.addToPlaylist(Playlist(id = "p1", name = "Road Trip"))
        assertEquals("Road Trip", viewModel.uiState.value.playlistMessage)

        viewModel.clearPlaylistMessage()

        assertNull(viewModel.uiState.value.playlistMessage)
    }

    // ── 4. downloadCurrentTrack routing + current-download mirror ────────────

    @Test
    fun downloadCurrentTrack_completedDownload_redownloadsByDeletingFirst() {
        downloadFlows["track-1"] = MutableStateFlow(downloadItem("dl-1", DownloadStatus.COMPLETED))
        currentItemIdFlow.value = "track-1"

        viewModel.downloadCurrentTrack()

        coVerify(exactly = 1) { downloadRepository.deleteDownload("dl-1") }
        coVerify(exactly = 0) { downloadIntake.start(any()) }
    }

    @Test
    fun downloadCurrentTrack_notCompleted_startsIntakeWithTheFetchedDetail() {
        currentItemIdFlow.value = "track-1"
        downloadFlows["track-1"] = MutableStateFlow(downloadItem("dl-1", DownloadStatus.DOWNLOADING))
        coEvery { mediaRepository.getMediaDetail("track-1", any()) } returns Result.success(detail("track-1"))
        coEvery { downloadIntake.start(any(), any(), any()) } returns
            com.raulshma.jellyplay.core.data.util.DownloadResult(downloadItem = null, error = null)

        viewModel.downloadCurrentTrack()

        coVerify(exactly = 0) { downloadRepository.deleteDownload(any()) }
        coVerify(exactly = 1) { downloadIntake.start(detail("track-1")) }
    }

    @Test
    fun downloadCurrentTrack_detailFailure_startsNothing() {
        currentItemIdFlow.value = "track-1"
        coEvery { mediaRepository.getMediaDetail("track-1", any()) } returns
            Result.failure(RuntimeException("offline"))

        viewModel.downloadCurrentTrack()

        coVerify(exactly = 0) { downloadIntake.start(any()) }
    }

    @Test
    fun downloadCurrentTrack_withoutCurrentItem_isNoOp() {
        currentItemIdFlow.value = null

        viewModel.downloadCurrentTrack()

        coVerify(exactly = 0) { downloadRepository.deleteDownload(any()) }
        coVerify(exactly = 0) { downloadIntake.start(any()) }
    }

    @Test
    fun currentDownloadItem_mirror_followsThePlayingItem() {
        // The per-item download flow must exist BEFORE the playing-item switch:
        // the eager collector binds to getDownloadByMediaItemIdFlow("track-1")
        // at the moment the itemId emission lands (getOrPut would otherwise
        // create and bind a permanent null flow).
        downloadFlows["track-1"] = MutableStateFlow(downloadItem("dl-1", DownloadStatus.COMPLETED))
        currentItemIdFlow.value = "track-1"
        assertEquals("dl-1", viewModel.currentDownloadItem.value?.id)

        // Moving to an item without a download clears the mirror (and the
        // previous per-item collector must have been cancelled — the null
        // emission from track-2's flow wins over track-1's stale item).
        currentItemIdFlow.value = "track-2"
        assertEquals(null, viewModel.currentDownloadItem.value)
    }

    // ── 5. BlurHash cache + sleep-timer expiry ──────────────────────────────

    @Test
    fun blurHash_isFetchedOncePerItem_andReusedAcrossReplays() {
        coEvery { mediaRepository.getMediaDetail("track-1", any()) } returns
            Result.success(detail("track-1", blurHash = "L6PZfSi_.AyE"))

        viewModel.play("track-1")
        assertEquals("L6PZfSi_.AyE", viewModel.uiState.value.albumArtBlurHash)

        // A replay (e.g. after the queue wraps) must not re-fetch the detail.
        viewModel.play("track-1")

        coVerify(exactly = 1) { mediaRepository.getMediaDetail("track-1", any()) }
        assertEquals("L6PZfSi_.AyE", viewModel.uiState.value.albumArtBlurHash)
    }

    @Test
    fun blurHash_nullResult_isCachedViaTheSentinel_soItStillFetchesOnce() {
        coEvery { mediaRepository.getMediaDetail("track-2", any()) } returns
            Result.success(detail("track-2", blurHash = null))

        viewModel.play("track-2")
        assertNull(viewModel.uiState.value.albumArtBlurHash)

        viewModel.play("track-2")

        coVerify(exactly = 1) { mediaRepository.getMediaDetail("track-2", any()) }
    }

    @Test
    fun sleepTimerExpiry_pausesTheEngine_ratherThanToggling() {
        val onExpired = slot<() -> Unit>()
        viewModel.startSleepTimer(60_000L)
        verify { sleepTimerManager.setOnTimerExpired(capture(onExpired)) }

        // Simulate the manager firing after the countdown: the callback must
        // PAUSE — if the user paused manually after arming, a toggle would
        // resume playback, the opposite of the timer's intent.
        onExpired.captured.invoke()

        verify(exactly = 1) { engine.pause() }
        verify(exactly = 0) { engine.togglePlayPause() }
    }

    @Test
    fun queueFlows_mirrorIntoTheUiStateQueueBlock() {
        val item = com.raulshma.jellyplay.core.data.playback.AudioQueueItem(
            id = "t1",
            name = "Song",
            artist = "Artist",
            album = "Album",
            imageUrl = "u",
            mediaSourceId = null,
            durationMs = 1_000L,
        )
        queue.value = listOf(item)
        queueIndex.value = 0
        shuffleMode.value = true
        repeatMode.value = 2

        with(viewModel.uiState.value.queue) {
            assertEquals(1, queue.size)
            assertEquals(0, currentIndex)
            assertTrue(shuffleMode)
            assertEquals(2, repeatMode)
        }
    }

    @Test
    fun engineMetadataFlows_mirrorIntoUiState() {
        title.value = "Song"
        artist.value = "Artist"
        artistId.value = "a-1"
        album.value = "Album"
        albumArtUrl.value = "https://srv/art"
        isPlaying.value = true
        duration.value = 200_000L
        speed.value = 1.5f
        playbackError.value = "decoder died"
        isLoadingItem.value = true
        crossfadeDurationMs.value = 3_000L

        with(viewModel.uiState.value) {
            assertEquals("Song", title)
            assertEquals("Artist", artist)
            assertEquals("a-1", artistId)
            assertEquals("Album", album)
            assertEquals("https://srv/art", albumArtUrl)
            assertTrue(isPlaying)
            assertEquals(200_000L, duration)
            assertEquals(1.5f, speed, 0f)
            assertEquals("decoder died", playbackError)
            assertTrue(isLoading)
            assertEquals(3_000L, crossfadeDurationMs)
        }
    }
}
