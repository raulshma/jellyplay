package com.raulshma.jellyplay.feature.editor

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
import com.raulshma.jellyplay.core.data.repository.MergedSubtitleSearch
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MetadataEditorInfo
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.subtitle.SavedSubtitle
import com.raulshma.jellyplay.core.model.subtitle.SubtitleFile
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the subtitle paths of [EditorViewModel] beyond the image upload path
 * covered by [EditorViewModelUploadTest]:
 *
 *  - `uploadSubtitle` / `uploadSubtitleFromFile`: base64-encoded bytes reach the
 *    repository, a success re-polls media detail, a failure surfaces in
 *    [EditorUiState.error] without a reload.
 *  - `deleteSubtitle`: forwards the stream index, purges the durable local copy
 *    of the removed server stream on success (and only then), and refreshes.
 *  - `searchRemoteSubtitles` / `downloadRemoteSubtitle`: result/error handling,
 *    download success re-polling media detail for the new stream.
 *  - Multi-provider search: `loadConfiguredSubtitleProviders` chip set,
 *    `searchAllSubtitleProviders` query derivation (item ids + language),
 *    streaming partial results, per-kind error map, searching-flag toggle, and
 *    the no-media early return.
 *  - `downloadProviderSubtitle`: external rows saved durably then uploaded to
 *    the server (with attribution on upload success), the "Saved to device
 *    only" note when the upload fails, download-failure surfacing, and Jellyfin
 *    rows routed through the server-side download instead of `downloadExternal`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelSubtitlesTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (same conveyor port pattern as the upload
    // suite).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var editorRepository: MetadataEditorRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var subtitleProviderRepository: SubtitleProviderRepository

    /** Recording store so the durable save/purge/attribute calls are observable. */
    private lateinit var subtitleStore: RecordingSubtitleStore

    private lateinit var viewModel: EditorViewModel

    private val itemId = "item-1"

    private val subtitleStream = MediaStream(
        index = 2,
        type = StreamType.SUBTITLE,
        codec = "subrip",
        language = "eng",
        isExternal = true,
    )

    private fun subtitleDetail(): MediaDetail = MediaDetail(
        item = MediaItem(id = itemId, name = "Movie", mediaType = MediaType.MOVIE),
        mediaSources = listOf(
            MediaSource(
                id = "ms-1",
                name = "Main",
                mediaStreams = listOf(
                    MediaStream(index = 0, type = StreamType.VIDEO, codec = "h264"),
                    MediaStream(index = 1, type = StreamType.AUDIO, codec = "aac"),
                    subtitleStream,
                ),
            ),
        ),
        providerIds = mapOf("tmdb" to "550"),
    )

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
        subtitleStore = RecordingSubtitleStore()

        every { authRepository.currentUser } returns MutableStateFlow(null)
        // Real flow — the relaxed mock's default Flow ClassCasts in .first()
        // (LibraryViewModelTest trap).
        every {
            subtitleProviderRepository.configuredProviders()
        } returns MutableStateFlow(setOf(SubtitleProviderKind.JELLYFIN, SubtitleProviderKind.WYZIE))

        // Stub every suspend seam the VM may call with real Result values — the
        // relaxed mock's default Result mock ClassCasts inside onSuccess/onFailure.
        coEvery { editorRepository.getMediaDetail(itemId) } returns Result.success(subtitleDetail())
        coEvery { editorRepository.getMetadataEditorInfo(any()) } returns Result.success(MetadataEditorInfo())
        coEvery { editorRepository.getItemImageInfo(any()) } returns Result.success(emptyList())
        coEvery { editorRepository.getRemoteImageProviders(any()) } returns Result.success(emptyList())
        coEvery { editorRepository.uploadSubtitle(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { editorRepository.deleteSubtitle(any(), any()) } returns Result.success(Unit)
        coEvery { editorRepository.searchRemoteSubtitles(any(), any()) } returns Result.success(emptyList())
        coEvery { editorRepository.downloadRemoteSubtitle(any(), any()) } returns Result.success(Unit)

        viewModel = EditorViewModel(editorRepository, authRepository, subtitleProviderRepository, subtitleStore)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uploadSubtitle sends base64-encoded bytes and reloads editor data on success`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        val bytes = byteArrayOf(1, 2, 3)

        viewModel.uploadSubtitle(bytes, "sub.srt", "ger", isForced = true, isHearingImpaired = false)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            editorRepository.uploadSubtitle(
                itemId,
                Base64.getEncoder().encodeToString(bytes),
                "sub.srt",
                "ger",
                true,
                false,
            )
        }
        // Initial load + the success-triggered reload.
        coVerify(exactly = 2) { editorRepository.getMediaDetail(itemId) }
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `uploadSubtitle failure surfaces error without reloading editor data`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        coEvery {
            editorRepository.uploadSubtitle(any(), any(), any(), any(), any(), any())
        } returns Result.failure(RuntimeException("upload boom"))

        viewModel.uploadSubtitle(byteArrayOf(1), "sub.srt", "eng", isForced = false, isHearingImpaired = false)
        advanceUntilIdle()

        assertEquals("upload boom", viewModel.uiState.value.error)
        coVerify(exactly = 1) { editorRepository.getMediaDetail(itemId) }
    }

    @Test
    fun `uploadSubtitleFromFile reads the picked file's bytes and forwards them with the given file name`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        val bytes = byteArrayOf(4, 5, 6)
        val picked = EditorPickedFile(
            fileName = "picked.srt",
            previewUrl = null,
            readBytes = { bytes },
        )

        viewModel.uploadSubtitleFromFile(picked, "given.srt", "fre", isForced = false, isHearingImpaired = true)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            editorRepository.uploadSubtitle(
                itemId,
                Base64.getEncoder().encodeToString(bytes),
                "given.srt",
                "fre",
                false,
                true,
            )
        }
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `deleteSubtitle forwards the stream index and purges the local copy on success`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()

        viewModel.deleteSubtitle(2)
        advanceUntilIdle()

        coVerify(exactly = 1) { editorRepository.deleteSubtitle(itemId, 2) }
        // The removed server stream is captured before the delete so the store
        // can attribute-match legacy local copies.
        val expectedPurge: List<Triple<String, Int, MediaStream?>> =
            listOf(Triple(itemId, 2, subtitleStream))
        assertEquals(expectedPurge, subtitleStore.purgedCopies)
        // Initial load + the success-triggered reload.
        coVerify(exactly = 2) { editorRepository.getMediaDetail(itemId) }
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `deleteSubtitle failure surfaces error and skips the local purge`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        coEvery {
            editorRepository.deleteSubtitle(any(), any())
        } returns Result.failure(RuntimeException("delete boom"))

        viewModel.deleteSubtitle(2)
        advanceUntilIdle()

        assertEquals("delete boom", viewModel.uiState.value.error)
        assertTrue(subtitleStore.purgedCopies.isEmpty())
        coVerify(exactly = 1) { editorRepository.getMediaDetail(itemId) }
    }

    @Test
    fun `searchRemoteSubtitles stores server results on success`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        val rows = listOf(
            RemoteSubtitleInfo(id = "remote-1", threeLetterISOLanguageName = "eng", name = "English SRT"),
        )
        coEvery { editorRepository.searchRemoteSubtitles(itemId, "eng") } returns Result.success(rows)

        viewModel.searchRemoteSubtitles("eng")
        advanceUntilIdle()

        assertEquals(rows, viewModel.uiState.value.remoteSubtitleResults)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `searchRemoteSubtitles failure surfaces error`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        coEvery {
            editorRepository.searchRemoteSubtitles(any(), any())
        } returns Result.failure(RuntimeException("search boom"))

        viewModel.searchRemoteSubtitles("eng")
        advanceUntilIdle()

        assertEquals("search boom", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.remoteSubtitleResults.isEmpty())
    }

    @Test
    fun `downloadRemoteSubtitle success re-polls media detail for the new stream`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()

        viewModel.downloadRemoteSubtitle("remote-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { editorRepository.downloadRemoteSubtitle(itemId, "remote-1") }
        coVerify(exactly = 2) { editorRepository.getMediaDetail(itemId) }
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `downloadRemoteSubtitle failure surfaces error`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        coEvery {
            editorRepository.downloadRemoteSubtitle(any(), any())
        } returns Result.failure(RuntimeException("download boom"))

        viewModel.downloadRemoteSubtitle("remote-1")
        advanceUntilIdle()

        assertEquals("download boom", viewModel.uiState.value.error)
        coVerify(exactly = 1) { editorRepository.getMediaDetail(itemId) }
    }

    @Test
    fun `loadConfiguredSubtitleProviders exposes the configured provider kinds`() = runTest {
        viewModel.loadConfiguredSubtitleProviders()
        advanceUntilIdle()

        assertEquals(
            setOf(SubtitleProviderKind.JELLYFIN, SubtitleProviderKind.WYZIE),
            viewModel.uiState.value.configuredSubtitleProviders,
        )
    }

    @Test
    fun `searchAllSubtitleProviders fans out with the item query, streams partials, and toggles the searching flag`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        viewModel.loadConfiguredSubtitleProviders()
        advanceUntilIdle()

        val jellyfinRow = searchResult(SubtitleProviderKind.JELLYFIN, "jf-row")
        val wyzieRow = searchResult(SubtitleProviderKind.WYZIE, "wz-row")
        val partial = MergedSubtitleSearch(listOf(jellyfinRow), emptyMap())
        val final = MergedSubtitleSearch(
            listOf(jellyfinRow, wyzieRow),
            mapOf(SubtitleProviderKind.WYZIE to "wyzie quota exceeded"),
        )
        var searchingDuringRepositoryCall = false
        coEvery {
            subtitleProviderRepository.searchAllStreaming(any(), any(), any(), any())
        } coAnswers {
            // The VM flips the flag on before fanning out.
            searchingDuringRepositoryCall = viewModel.uiState.value.isSearchingProviderSubtitles
            arg<(MergedSubtitleSearch) -> Unit>(3)(partial)
            final
        }

        viewModel.searchAllSubtitleProviders("eng")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(
            searchingDuringRepositoryCall,
            "Expected the searching flag set while the provider fan-out runs",
        )
        assertFalse(state.isSearchingProviderSubtitles)
        assertEquals(final.results, state.providerSubtitleResults)
        assertEquals(final.errors, state.providerSubtitleErrors)
        assertEquals(
            setOf(SubtitleProviderKind.JELLYFIN, SubtitleProviderKind.WYZIE),
            state.configuredSubtitleProviders,
        )
        // The query derives from the item's provider ids, with the UI language
        // appended by the VM (SubtitleProviderIds.buildQuery leaves it empty).
        val querySlot = slot<SubtitleQuery>()
        coVerify(exactly = 1) {
            subtitleProviderRepository.searchAllStreaming(capture(querySlot), itemId, "eng", any())
        }
        assertEquals(550, querySlot.captured.tmdbId)
        assertEquals(listOf("eng"), querySlot.captured.languages)
    }

    @Test
    fun `searchAllSubtitleProviders without loaded media does not query providers`() = runTest {
        viewModel.searchAllSubtitleProviders("eng")
        advanceUntilIdle()

        coVerify(exactly = 0) { subtitleProviderRepository.searchAllStreaming(any(), any(), any(), any()) }
        assertFalse(viewModel.uiState.value.isSearchingProviderSubtitles)
    }

    @Test
    fun `downloadProviderSubtitle saves an external subtitle durably and uploads it to the server`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        val bytes = byteArrayOf(1, 2, 3, 4)
        val file = SubtitleFile(bytes, fileName = "ext.srt", format = "srt", language = "eng")
        val row = searchResult(SubtitleProviderKind.WYZIE, "wz-1")
        var downloadingDuringRepositoryCall = false
        coEvery { subtitleProviderRepository.downloadExternal(row) } coAnswers {
            downloadingDuringRepositoryCall = viewModel.uiState.value.isDownloadingProviderSubtitle
            Result.success(file)
        }

        viewModel.downloadProviderSubtitle(row)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(downloadingDuringRepositoryCall, "Expected the downloading flag set during the provider download")
        assertFalse(state.isDownloadingProviderSubtitle)
        assertNull(state.error)
        assertEquals(1, subtitleStore.savedSubtitles.size)
        assertEquals(itemId, subtitleStore.attributedItemIds.single())
        coVerify(exactly = 1) {
            editorRepository.uploadSubtitle(
                itemId,
                Base64.getEncoder().encodeToString(bytes),
                "ext.srt",
                "eng",
                false,
                false,
            )
        }
        // Initial load + the upload-success reload.
        coVerify(exactly = 2) { editorRepository.getMediaDetail(itemId) }
    }

    @Test
    fun `downloadProviderSubtitle reports device-only save when the server upload fails`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        val file = SubtitleFile(byteArrayOf(9), fileName = "ext.srt", format = "srt", language = "eng")
        val row = searchResult(SubtitleProviderKind.WYZIE, "wz-2")
        coEvery { subtitleProviderRepository.downloadExternal(row) } returns Result.success(file)
        coEvery {
            editorRepository.uploadSubtitle(any(), any(), any(), any(), any(), any())
        } returns Result.failure(RuntimeException("server offline"))

        viewModel.downloadProviderSubtitle(row)
        advanceUntilIdle()

        // Best-effort upload: the durable local copy backs the subtitle, so the
        // failure is an info note rather than the raw error.
        assertEquals("Saved to device only: server offline", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isDownloadingProviderSubtitle)
        assertTrue(subtitleStore.attributedItemIds.isEmpty())
        coVerify(exactly = 1) { editorRepository.getMediaDetail(itemId) }
    }

    @Test
    fun `downloadProviderSubtitle surfaces provider download failures`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        val row = searchResult(SubtitleProviderKind.OPENSUBTITLES, "os-1")
        coEvery {
            subtitleProviderRepository.downloadExternal(row)
        } returns Result.failure(IllegalStateException("provider down"))

        viewModel.downloadProviderSubtitle(row)
        advanceUntilIdle()

        assertEquals("provider down", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isDownloadingProviderSubtitle)
        assertTrue(subtitleStore.savedSubtitles.isEmpty())
        coVerify(exactly = 0) { editorRepository.uploadSubtitle(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `downloadProviderSubtitle routes Jellyfin rows through the server download path`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        val row = SubtitleSearchResult(
            provider = SubtitleProviderKind.JELLYFIN,
            id = "jf-1",
            language = "eng",
            displayName = "Server row",
            jellyfinInfo = RemoteSubtitleInfo(id = "jf-1"),
        )

        viewModel.downloadProviderSubtitle(row)
        advanceUntilIdle()

        coVerify(exactly = 1) { editorRepository.downloadRemoteSubtitle(itemId, "jf-1") }
        coVerify(exactly = 0) { subtitleProviderRepository.downloadExternal(any()) }
        assertNotNull(viewModel.uiState.value.mediaDetail)
    }
}

/**
 * No-op [StreamingSubtitleStore] that records the durable lifecycle calls the
 * editor makes (save / purge on server delete / attribution after upload) so
 * the tests can assert on them.
 */
private class RecordingSubtitleStore : StreamingSubtitleStore {

    val savedSubtitles = mutableListOf<SavedSubtitle>()
    val attributedItemIds = mutableListOf<String>()
    /** (itemId, deleted stream index, captured deleted stream) per purge. */
    val purgedCopies = mutableListOf<Triple<String, Int, MediaStream?>>()

    override suspend fun save(
        itemId: String,
        provider: SubtitleProviderKind,
        providerSubtitleId: String,
        fileName: String,
        language: String?,
        codec: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
        bytes: ByteArray,
    ): SavedSubtitle {
        val saved = SavedSubtitle(
            provider, providerSubtitleId, fileName, language, codec, isForced, isHearingImpaired, fileName,
        )
        savedSubtitles.add(saved)
        return saved
    }

    override suspend fun loadAll(itemId: String): List<SavedSubtitle> = emptyList()

    override suspend fun fileFor(itemId: String, saved: SavedSubtitle): java.io.File =
        java.io.File(saved.fileRelativePath)

    override suspend fun delete(itemId: String, saved: SavedSubtitle) = Unit

    override suspend fun markServerStreamIndex(itemId: String, saved: SavedSubtitle, index: Int) = Unit

    override suspend fun clear(itemId: String) = Unit

    override suspend fun attributeUploadedSubtitle(
        itemId: String,
        saved: SavedSubtitle,
        streamsAfterUpload: List<MediaStream>,
        preUploadExternalIndices: Set<Int>,
    ) {
        attributedItemIds.add(itemId)
    }

    override suspend fun purgeDeletedServerStreamCopies(itemId: String, index: Int, deletedStream: MediaStream?) {
        purgedCopies.add(Triple(itemId, index, deletedStream))
    }
}
