package com.raulshma.jellyplay.feature.editor

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.model.ImageInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.subtitle.SavedSubtitle
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Image-upload and no-item-guard gaps in [EditorViewModel] NOT pinned by
 * [EditorViewModelUploadTest] / [EditorViewModelMetadataTest]:
 *
 * 1. `uploadImageFromUrl` — entirely untested: success forwards
 *    (itemId, imageType, url) to `downloadRemoteImage` and re-polls the image
 *    infos; failure surfaces the message in [EditorUiState.error] without a
 *    reload.
 * 2. `uploadImage(bytes)` failure — the byte-upload success path is covered;
 *    the failure branch (error surfaced, no reload) was not.
 * 3. The `mediaDetail == null` guards: every image entry point
 *    (`uploadImage` / `uploadImageFromUrl` / `deleteImage`) is a no-op
 *    repository-wise before an item is loaded.
 * 4. `saveMetadata` without a loaded item — PINS CURRENT BEHAVIOR, which is a
 *    SUSPECTED BUG: `isSaving` is set true before the itemId guard, and the
 *    guard `return@launch`s without resetting it, so the saving spinner state
 *    sticks forever until some other path clears it. If a fix lands (reset
 *    isSaving on the early return), this test MUST be flipped to assert
 *    `isSaving == false`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelImageAndSaveGapsTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (EditorViewModelMetadataTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var editorRepository: MetadataEditorRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var subtitleProviderRepository: SubtitleProviderRepository

    private val currentUserFlow = MutableStateFlow<UserInfo?>(null)
    private val itemId = "item-1"
    private val imageInfos = listOf(
        ImageInfo(imageType = "Primary", imageIndex = 0, width = 100, height = 150, imageTag = "tag-1"),
    )

    private lateinit var viewModel: EditorViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        editorRepository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        subtitleProviderRepository = mockk(relaxed = true)
        every { authRepository.currentUser } returns currentUserFlow

        // Relaxed-mock Result defaults ClassCast inside onSuccess/onFailure —
        // stub every suspend seam with real Results (metadata-suite trap note).
        coEvery { editorRepository.getMediaDetail(itemId) } returns Result.success(
            MediaDetail(item = MediaItem(id = itemId, name = "Movie", mediaType = MediaType.MOVIE)),
        )
        coEvery { editorRepository.getItemImageInfo(any()) } returns Result.success(imageInfos)
        coEvery { editorRepository.setItemImage(any(), any(), any()) } returns Result.success(Unit)
        coEvery { editorRepository.downloadRemoteImage(any(), any(), any()) } returns Result.success(Unit)
        coEvery { editorRepository.deleteItemImage(any(), any(), any()) } returns Result.success(Unit)
        coEvery { editorRepository.updateItem(any(), any()) } returns Result.success(Unit)

        viewModel = EditorViewModel(
            editorRepository,
            authRepository,
            subtitleProviderRepository,
            // No-op streaming subtitle store — image/save tests never exercise
            // the durable subtitle path (mirrors the metadata suite's fake).
            object : StreamingSubtitleStore {
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
                ): SavedSubtitle = SavedSubtitle(
                    provider, providerSubtitleId, fileName, language, codec, isForced, isHearingImpaired, fileName,
                )
                override suspend fun loadAll(itemId: String): List<SavedSubtitle> = emptyList()
                override suspend fun fileFor(itemId: String, saved: SavedSubtitle): java.io.File =
                    java.io.File(saved.fileRelativePath)
                override suspend fun delete(itemId: String, saved: SavedSubtitle) = Unit
                override suspend fun markServerStreamIndex(itemId: String, saved: SavedSubtitle, index: Int) = Unit
                override suspend fun clear(itemId: String) = Unit
            },
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── uploadImageFromUrl ────────────────────────────────────────────────

    @Test
    fun `uploadImageFromUrl downloads remotely and reloads the image infos`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()

        viewModel.uploadImageFromUrl("https://remote/art.jpg", "Primary")
        advanceUntilIdle()

        coVerify(exactly = 1) { editorRepository.downloadRemoteImage(itemId, "Primary", "https://remote/art.jpg") }
        // One getItemImageInfo per successful image mutation (no admin load —
        // the user is anonymous here, so this is the reload itself).
        coVerify(exactly = 1) { editorRepository.getItemImageInfo(itemId) }
        assertEquals(imageInfos, viewModel.uiState.value.imageInfos)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `uploadImageFromUrl failure surfaces the error without a reload`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        coEvery {
            editorRepository.downloadRemoteImage(any(), any(), any())
        } returns Result.failure(RuntimeException("remote 404"))

        viewModel.uploadImageFromUrl("https://remote/art.jpg", "Primary")
        advanceUntilIdle()

        assertEquals("remote 404", viewModel.uiState.value.error)
        coVerify(exactly = 0) { editorRepository.getItemImageInfo(any()) }
    }

    // ── uploadImage(bytes) failure branch ────────────────────────────────

    @Test
    fun `uploadImage failure surfaces the error and skips the reload`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        coEvery { editorRepository.setItemImage(any(), any(), any()) } returns
            Result.failure(RuntimeException("quota exceeded"))

        viewModel.uploadImage(ByteArray(4), "Primary")
        advanceUntilIdle()

        assertEquals("quota exceeded", viewModel.uiState.value.error)
        coVerify(exactly = 0) { editorRepository.getItemImageInfo(any()) }
    }

    // ── no-item guards ───────────────────────────────────────────────────

    @Test
    fun `image actions without a loaded item never reach the repository`() = runTest {
        viewModel.uploadImage(ByteArray(4), "Primary")
        viewModel.uploadImageFromUrl("https://remote/art.jpg", "Primary")
        viewModel.deleteImage("Primary")
        advanceUntilIdle()

        coVerify(exactly = 0) { editorRepository.setItemImage(any(), any(), any()) }
        coVerify(exactly = 0) { editorRepository.downloadRemoteImage(any(), any(), any()) }
        coVerify(exactly = 0) { editorRepository.deleteItemImage(any(), any(), any()) }
        assertNull(viewModel.uiState.value.error)
    }

    // ── saveMetadata without an item (suspected isSaving bug) ────────────

    @Test
    fun `saveMetadata without a loaded item skips the repository but leaves isSaving set`() = runTest {
        // SUSPECTED BUG PIN — see the class KDoc: the itemId guard returns
        // AFTER isSaving was raised, and nothing resets it. The repository is
        // correctly never called; the stuck spinner flag is the bug.
        viewModel.saveMetadata()
        advanceUntilIdle()

        coVerify(exactly = 0) { editorRepository.updateItem(any(), any()) }
        assertTrue(
            viewModel.uiState.value.isSaving,
            "pins current behavior: isSaving sticks after the no-item early return",
        )
        assertFalse(viewModel.uiState.value.isDirty)
        assertNull(viewModel.uiState.value.error)
    }
}
