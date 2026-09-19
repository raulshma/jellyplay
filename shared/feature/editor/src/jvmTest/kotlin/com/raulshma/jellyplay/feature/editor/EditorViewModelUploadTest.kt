package com.raulshma.jellyplay.feature.editor

import com.raulshma.jellyplay.core.model.MediaStream

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MetadataEditorInfo
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelUploadTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv/admin conveyor port
    // pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var editorRepository: MetadataEditorRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var subtitleProviderRepository: SubtitleProviderRepository

    private lateinit var viewModel: EditorViewModel

    private val itemId = "item-1"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        editorRepository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        subtitleProviderRepository = mockk(relaxed = true)

        every { authRepository.currentUser } returns MutableStateFlow(null)

        coEvery { editorRepository.getMediaDetail(itemId) } returns Result.success(
            MediaDetail(item = MediaItem(id = itemId, name = "Movie", mediaType = MediaType.MOVIE))
        )
        coEvery { editorRepository.getMetadataEditorInfo(any()) } returns Result.success(MetadataEditorInfo())
        coEvery { editorRepository.getItemImageInfo(any()) } returns Result.success(emptyList())
        coEvery { editorRepository.getRemoteImageProviders(any()) } returns Result.success(emptyList())
        coEvery { editorRepository.setItemImage(any(), any(), any()) } returns Result.success(Unit)

        viewModel = EditorViewModel(
            editorRepository,
            authRepository,
            subtitleProviderRepository,
            // No-op subtitle store seam — upload tests don't exercise the
            // durable subtitle path. Mirrors the player's TestStreamingSubtitleStore.
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

    @Test
    fun `uploadImageFromFile reads the picked file's bytes and uploads them`() = runTest {
        // Regression guard for the TODO() crash that used to live in the UI —
        // ported onto the picker seam: the legacy test exercised the
        // contentResolver read through uploadImageFromUri; the read moved into
        // the Android actual's EditorPickedFile.readBytes, so the seam-typed
        // equivalent feeds the VM a picked file whose bytes resolve like a
        // freshly-read stream.
        val bytes = byteArrayOf(1, 2, 3)
        val picked = EditorPickedFile(
            fileName = "poster.jpg",
            previewUrl = null,
            readBytes = { bytes },
        )

        viewModel.onEvent(EditorUiEvent.LoadEditorData(itemId))
        advanceUntilIdle()

        // Should complete without throwing (the prior NotImplementedError crash)
        // and surface no error after a successful upload.
        viewModel.onEvent(EditorUiEvent.UploadImageFromFile(picked, "Primary"))
        advanceUntilIdle()

        coVerify(exactly = 1) { editorRepository.setItemImage(itemId, "Primary", bytes) }
        val state = viewModel.uiState.value
        assertNull(state.error, "Expected no error after successful upload, got: ${state.error}")
    }

    @Test
    fun `uploadImageFromFile surfaces a read failure as uiState error`() = runTest {
        // The Android actual throws IOException("Cannot open input stream…")
        // from readBytes when the picked document cannot be opened; the VM must
        // route that failure into the error banner instead of crashing.
        val picked = EditorPickedFile(
            fileName = "poster.jpg",
            previewUrl = null,
            readBytes = { throw java.io.IOException("Cannot open input stream for selected image") },
        )

        viewModel.onEvent(EditorUiEvent.LoadEditorData(itemId))
        advanceUntilIdle()

        viewModel.onEvent(EditorUiEvent.UploadImageFromFile(picked, "Primary"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(
            state.error?.contains("Cannot open input stream") == true,
            "Expected stream-open failure in uiState.error, got: ${state.error}",
        )
        coVerify(exactly = 0) { editorRepository.setItemImage(any(), any(), any()) }
    }

    @Test
    fun `uploadImage from bytes does not surface an error on success`() = runTest {
        // Exercises the existing ByteArray-based image upload and the fixed
        // failure handler (previously a no-op that copied error onto itself).
        viewModel.onEvent(EditorUiEvent.LoadEditorData(itemId))
        advanceUntilIdle()

        viewModel.onEvent(EditorUiEvent.UploadImage(byteArrayOf(1, 2, 3), "Primary"))
        advanceUntilIdle()

        // No error should be present after a successful upload.
        val state = viewModel.uiState.value
        assertNull(state.error, "Expected no error after successful upload, got: ${state.error}")
    }
}
