package com.raulshma.jellyplay.feature.editor

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
import com.raulshma.jellyplay.core.data.repository.MergedSubtitleSearch
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MetadataEditorInfo
import com.raulshma.jellyplay.core.model.subtitle.SubtitleFile
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Focused pins for the v0.11.10 sealed-phase migration of [EditorUiState]
 * (ex-`isLoading` / `isSaving` / `isSearchingProviderSubtitles` /
 * `isDownloadingProviderSubtitle` / shared `error: String?` — see
 * [EditorLoadPhase] / [EditorSavePhase] / [ProviderSubtitleSearchPhase] /
 * [ProviderSubtitleDownloadPhase] / `EditorUiState.actionError`). The
 * per-flow suites ([EditorViewModelMetadataTest] / [EditorViewModelSubtitlesTest]
 * / [EditorViewModelImageAndSaveGapsTest]) migrated their end-state asserts;
 * THIS suite pins the transition semantics the booleans could only hint at:
 *
 *  1. the load ladder raises `Loading` BEFORE the fetch (mid-flight
 *     observable) and settles `Ready` on success;
 *  2. a reload failure settles `Failed` while the previously-loaded content
 *     survives under it (the ex-`isLoading = false` + stale-content pin);
 *  3. a save from `Ready` runs entirely inside `savePhase` — the load phase
 *     and content stay untouched, and the save's start clears a lingering
 *     action banner (the old `error = null` choreography);
 *  4. a save failure surfaces on the `actionError` banner channel (exactly
 *     where the old shared `error` put it) with the load phase untouched;
 *  5. the provider-subtitle search and download phases are SEPARATE
 *     machines: a download started mid-search raises `Downloading` while
 *     `Searching` stays up, and the download's settle does not extinguish
 *     the search spinner (the overlap that forbids one merged op phase);
 *  6. `ClearError` (the banner dismissal) clears only the banner's sources —
 *     the action channel and a `Failed` load — never content, the save
 *     phase, or the provider op phases;
 *  7. `bannerError` precedence is last-write-wins on the REACHABLE path: an
 *     action over a `Failed` load is a silent no-op (the preserved
 *     no-item guard), an action failure over `Ready` owns the banner, and a
 *     load start dismisses a prior action banner — the old single-`error`
 *     field's exact behavior.
 *
 * The editor module HAS a real VM test harness (mockk + runTest, the
 * EditorViewModelMetadataTest pattern), so these are behavior pins, not
 * source pins.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelPhasePinsTest {

    // MainDispatcherRule (:core:testing) inlined — jvmTest has no access to
    // that module (EditorViewModelMetadataTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var editorRepository: MetadataEditorRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var subtitleProviderRepository: SubtitleProviderRepository

    private lateinit var viewModel: EditorViewModel

    private val itemId = "item-1"

    private fun editorDetail(): MediaDetail =
        MediaDetail(item = MediaItem(id = itemId, name = "Movie", mediaType = MediaType.MOVIE))

    private fun searchResult(provider: SubtitleProviderKind, id: String) = SubtitleSearchResult(
        provider = provider,
        id = id,
        language = "eng",
        displayName = "row $id",
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        editorRepository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        subtitleProviderRepository = mockk(relaxed = true)

        every { authRepository.currentUser } returns MutableStateFlow(null)

        // Stub every suspend seam the VM may call with real Result values — the
        // relaxed mock's default Result mock ClassCasts inside onSuccess/onFailure
        // (LibraryViewModelTest trap).
        coEvery { editorRepository.getMediaDetail(itemId) } returns Result.success(editorDetail())
        coEvery { editorRepository.getMetadataEditorInfo(any()) } returns Result.success(MetadataEditorInfo())
        coEvery { editorRepository.getItemImageInfo(any()) } returns Result.success(emptyList())
        coEvery { editorRepository.getRemoteImageProviders(any()) } returns Result.success(emptyList())
        coEvery { editorRepository.downloadRemoteImage(any(), any(), any()) } returns Result.success(Unit)
        coEvery { editorRepository.updateItem(any(), any()) } returns Result.success(Unit)
        coEvery { editorRepository.uploadSubtitle(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel = EditorViewModel(
            editorRepository,
            authRepository,
            subtitleProviderRepository,
            // No-op subtitle store seam — phase pins never assert the durable
            // path (mirrors the metadata suite's fake).
            object : EditorSubtitleStore {
                override suspend fun save(save: ProviderSubtitleSave) = Unit
                override suspend fun attributeUploaded(
                    save: ProviderSubtitleSave,
                    streamsAfterUpload: List<MediaStream>,
                    preUploadExternalIndices: Set<Int>,
                ) = Unit
                override suspend fun purgeDeletedServerStreamCopies(itemId: String, index: Int, deletedStream: MediaStream?) = Unit
            },
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 1. the load ladder's Loading raise is mid-flight observable ──────

    @Test
    fun `load ladder raises Loading before the fetch and settles Ready on success`() = runTest {
        var phaseDuringFetch: EditorLoadPhase? = null
        var bannerDuringFetch: String? = "sentinel"
        coEvery { editorRepository.getMediaDetail(itemId) } coAnswers {
            phaseDuringFetch = viewModel.uiState.value.loadPhase
            bannerDuringFetch = viewModel.uiState.value.bannerError
            Result.success(editorDetail())
        }

        viewModel.onEvent(EditorUiEvent.LoadEditorData(itemId))
        runCurrent() // runs the ladder up to the parked fetch

        assertEquals(
            EditorLoadPhase.Loading,
            phaseDuringFetch,
            "the start arm must raise Loading before the fetch runs",
        )
        assertNull(
            bannerDuringFetch,
            "the start arm must clear the banner channel before the fetch runs",
        )

        advanceUntilIdle()
        assertEquals(EditorLoadPhase.Ready, viewModel.uiState.value.loadPhase)
        assertNull(viewModel.uiState.value.bannerError)
    }

    // ── 2. a reload failure keeps the stale content under Failed ─────────

    @Test
    fun `reload failure settles Failed and preserves the previously loaded content`() = runTest {
        viewModel.onEvent(EditorUiEvent.LoadEditorData(itemId))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.mediaDetail)

        coEvery { editorRepository.getMediaDetail(itemId) } returns Result.failure(RuntimeException("reload boom"))
        viewModel.onEvent(EditorUiEvent.LoadEditorData(itemId))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(EditorLoadPhase.Failed("reload boom"), state.loadPhase)
        // The failure arm never touches content — the stale detail survives
        // under the Failed banner (the reload-after-subtitle-op dependency).
        assertEquals("Movie", state.mediaDetail?.item?.name)
        assertEquals("reload boom", state.bannerError)
        assertNull(state.actionError)
    }

    // ── 3. save-while-Ready: the load machine is not the save machine ────

    @Test
    fun `save from Ready runs in savePhase and leaves the load phase content and a lingering banner handled`() = runTest {
        viewModel.onEvent(EditorUiEvent.LoadEditorData(itemId))
        advanceUntilIdle()
        viewModel.onEvent(EditorUiEvent.UpdateField { it.copy(name = "Changed") })

        // A failed image op leaves an action banner up…
        coEvery { editorRepository.downloadRemoteImage(any(), any(), any()) } returns Result.failure(RuntimeException("img boom"))
        viewModel.onEvent(EditorUiEvent.UploadImageFromUrl("https://remote/art.jpg", "Primary"))
        advanceUntilIdle()
        assertEquals("img boom", viewModel.uiState.value.actionError)

        val detailBeforeSave = viewModel.uiState.value.mediaDetail
        var savePhaseDuringCall: EditorSavePhase? = null
        coEvery { editorRepository.updateItem(any(), any()) } coAnswers {
            savePhaseDuringCall = viewModel.uiState.value.savePhase
            Result.success(Unit)
        }

        viewModel.onEvent(EditorUiEvent.SaveMetadata)
        advanceUntilIdle()

        // The save ran inside its own machine…
        assertEquals(EditorSavePhase.Saving, savePhaseDuringCall, "Saving must be raised while updateItem runs")
        assertEquals(EditorSavePhase.Idle, viewModel.uiState.value.savePhase)
        // …the load machine never moved…
        assertEquals(EditorLoadPhase.Ready, viewModel.uiState.value.loadPhase)
        assertEquals(detailBeforeSave, viewModel.uiState.value.mediaDetail)
        // …and the save's start cleared the lingering action banner (the old
        // `error = null` retry choreography).
        assertNull(viewModel.uiState.value.actionError)
        assertNull(viewModel.uiState.value.bannerError)
    }

    // ── 4. a save failure surfaces where the old shared error did ────────

    @Test
    fun `save failure surfaces on the banner channel with the load phase untouched`() = runTest {
        viewModel.onEvent(EditorUiEvent.LoadEditorData(itemId))
        advanceUntilIdle()
        viewModel.onEvent(EditorUiEvent.UpdateField { it.copy(name = "Changed") })
        coEvery { editorRepository.updateItem(any(), any()) } returns Result.failure(RuntimeException("save boom"))

        viewModel.onEvent(EditorUiEvent.SaveMetadata)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("save boom", state.actionError)
        assertEquals("save boom", state.bannerError)
        assertEquals(EditorSavePhase.Idle, state.savePhase)
        assertEquals(EditorLoadPhase.Ready, state.loadPhase)
    }

    // ── 5. provider ops: two machines, and their overlap is representable ─

    @Test
    fun `a download started mid-search overlaps the Searching phase and its settle leaves the search spinner up`() = runTest {
        viewModel.onEvent(EditorUiEvent.LoadEditorData(itemId))
        advanceUntilIdle()

        // Hold the search fan-out mid-flight so Searching stays raised.
        val searchGate = CompletableDeferred<MergedSubtitleSearch>()
        coEvery {
            subtitleProviderRepository.searchAllStreaming(any(), any(), any(), any())
        } coAnswers { searchGate.await() }

        viewModel.onEvent(EditorUiEvent.SearchAllSubtitleProviders("eng"))
        runCurrent() // the fan-out parks on the gate, Searching raised

        // The search start never touched the sibling machine.
        assertEquals(ProviderSubtitleSearchPhase.Searching, viewModel.uiState.value.providerSubtitleSearchPhase)
        assertEquals(ProviderSubtitleDownloadPhase.Idle, viewModel.uiState.value.providerSubtitleDownloadPhase)

        // Download a row WHILE the search streams: reachable in the UI (the
        // per-row download buttons stay enabled during Searching), so the two
        // phases must be separately representable.
        val row = searchResult(SubtitleProviderKind.WYZIE, "wz-1")
        val file = SubtitleFile(byteArrayOf(1), fileName = "ext.srt", format = "srt", language = "eng")
        var phasesDuringDownload: Pair<ProviderSubtitleSearchPhase, ProviderSubtitleDownloadPhase>? = null
        coEvery { subtitleProviderRepository.downloadExternal(row) } coAnswers {
            phasesDuringDownload = viewModel.uiState.value.providerSubtitleSearchPhase to
                viewModel.uiState.value.providerSubtitleDownloadPhase
            Result.success(file)
        }

        viewModel.onEvent(EditorUiEvent.DownloadProviderSubtitle(row))
        advanceUntilIdle()

        assertEquals(
            ProviderSubtitleSearchPhase.Searching to ProviderSubtitleDownloadPhase.Downloading,
            phasesDuringDownload,
            "download and search genuinely overlap — the reason they are two phase fields",
        )
        // The download settled to Idle on ITS field only…
        assertEquals(ProviderSubtitleDownloadPhase.Idle, viewModel.uiState.value.providerSubtitleDownloadPhase)
        assertEquals(
            ProviderSubtitleSearchPhase.Searching,
            viewModel.uiState.value.providerSubtitleSearchPhase,
            "the download's settle must not extinguish the still-running search spinner",
        )

        // …and the search settles on its own field once the fan-out returns.
        searchGate.complete(MergedSubtitleSearch(emptyList(), emptyMap()))
        advanceUntilIdle()
        assertEquals(ProviderSubtitleSearchPhase.Idle, viewModel.uiState.value.providerSubtitleSearchPhase)
    }

    // ── 6. ClearError clears only the banner's sources ────────────────────

    @Test
    fun `ClearError clears the action banner without touching load save or provider phases`() = runTest {
        viewModel.onEvent(EditorUiEvent.LoadEditorData(itemId))
        advanceUntilIdle()
        coEvery { editorRepository.downloadRemoteImage(any(), any(), any()) } returns Result.failure(RuntimeException("img boom"))
        viewModel.onEvent(EditorUiEvent.UploadImageFromUrl("https://remote/art.jpg", "Primary"))
        advanceUntilIdle()
        assertEquals("img boom", viewModel.uiState.value.bannerError)

        viewModel.onEvent(EditorUiEvent.ClearError)

        val state = viewModel.uiState.value
        assertNull(state.actionError)
        assertNull(state.bannerError)
        // Its own concern only: the load machine stays settled, the content
        // stays loaded, and the other machines stay where they were.
        assertEquals(EditorLoadPhase.Ready, state.loadPhase)
        assertNotNull(state.mediaDetail)
        assertEquals(EditorSavePhase.Idle, state.savePhase)
        assertEquals(ProviderSubtitleSearchPhase.Idle, state.providerSubtitleSearchPhase)
        assertEquals(ProviderSubtitleDownloadPhase.Idle, state.providerSubtitleDownloadPhase)
    }

    @Test
    fun `ClearError settles a Failed load without touching content or the action channel`() = runTest {
        coEvery { editorRepository.getMediaDetail(itemId) } returns Result.failure(RuntimeException("load boom"))
        viewModel.onEvent(EditorUiEvent.LoadEditorData(itemId))
        advanceUntilIdle()
        assertEquals(EditorLoadPhase.Failed("load boom"), viewModel.uiState.value.loadPhase)

        viewModel.onEvent(EditorUiEvent.ClearError)

        val state = viewModel.uiState.value
        assertEquals(EditorLoadPhase.Ready, state.loadPhase)
        assertNull(state.bannerError)
        // Content was never loaded and stays that way (the old dismissal left
        // isLoading=false with whatever content existed — null here).
        assertNull(state.mediaDetail)
        assertNull(state.actionError)
    }

    // ── 7. banner precedence is last-write-wins (the old single field) ────

    @Test
    fun `an action over a Failed load is a silent no-op, an action failure over Ready owns the banner`() = runTest {
        // Load fails first…
        coEvery { editorRepository.getMediaDetail(itemId) } returns Result.failure(RuntimeException("load boom"))
        viewModel.onEvent(EditorUiEvent.LoadEditorData(itemId))
        advanceUntilIdle()
        assertEquals(EditorLoadPhase.Failed("load boom"), viewModel.uiState.value.loadPhase)

        // …then an action fired on top of the Failed load: the preserved
        // `mediaDetail?.item?.id ?: return` guard (present at HEAD on every
        // action handler) makes it a SILENT no-op — with no item loaded there
        // is nothing for the download to act on, so no phantom action message
        // may steal the banner. The load failure stays the last REAL failure.
        coEvery { editorRepository.downloadRemoteImage(any(), any(), any()) } returns Result.failure(RuntimeException("img boom"))
        viewModel.onEvent(EditorUiEvent.UploadImageFromUrl("https://remote/art.jpg", "Primary"))
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.actionError)
        assertEquals("load boom", viewModel.uiState.value.bannerError)
        assertEquals(EditorLoadPhase.Failed("load boom"), viewModel.uiState.value.loadPhase)

        // The reachable precedence direction: over a Ready load, an action
        // failure owns the banner — the last failure wins, exactly as the old
        // shared error field's overwrite did.
        coEvery { editorRepository.getMediaDetail(itemId) } returns Result.success(editorDetail())
        viewModel.onEvent(EditorUiEvent.LoadEditorData(itemId))
        advanceUntilIdle()
        assertEquals(EditorLoadPhase.Ready, viewModel.uiState.value.loadPhase)
        assertNull(viewModel.uiState.value.bannerError)
        viewModel.onEvent(EditorUiEvent.UploadImageFromUrl("https://remote/art.jpg", "Primary"))
        advanceUntilIdle()
        assertEquals("img boom", viewModel.uiState.value.actionError)
        assertEquals(
            "img boom",
            viewModel.uiState.value.bannerError,
            "the action failure landed last — it must own the banner",
        )

        // Dismissal clears both sources at once (one banner, one dismiss).
        viewModel.onEvent(EditorUiEvent.ClearError)
        assertNull(viewModel.uiState.value.bannerError)
        assertEquals(EditorLoadPhase.Ready, viewModel.uiState.value.loadPhase)

        // The reverse direction: a load START dismisses the action banner
        // (the old `error = null` in the ladder's start arm), so a load
        // failure replaces — never stacks with — an action message.
        coEvery { editorRepository.getMediaDetail(itemId) } returns Result.failure(RuntimeException("reload boom"))
        viewModel.onEvent(EditorUiEvent.LoadEditorData(itemId))
        advanceUntilIdle()
        assertEquals("reload boom", viewModel.uiState.value.bannerError)
        assertNull(viewModel.uiState.value.actionError)
    }
}
