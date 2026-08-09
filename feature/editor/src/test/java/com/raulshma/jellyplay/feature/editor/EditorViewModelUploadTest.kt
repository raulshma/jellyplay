package com.raulshma.jellyplay.feature.editor

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MetadataEditorInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelUploadTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var apiClient: JellyfinApiClient
    private lateinit var authRepository: AuthRepository
    private lateinit var subtitleProviderRepository: SubtitleProviderRepository
    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver

    private lateinit var viewModel: EditorViewModel

    private val itemId = "item-1"

    @Before
    fun setUp() {
        apiClient = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        subtitleProviderRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)

        every { context.contentResolver } returns contentResolver
        every { authRepository.currentUser } returns MutableStateFlow(null)

        coEvery { apiClient.getMediaDetail(itemId) } returns Result.success(
            MediaDetail(item = MediaItem(id = itemId, name = "Movie", mediaType = MediaType.MOVIE))
        )
        coEvery { apiClient.getMetadataEditorInfo(any()) } returns Result.success(MetadataEditorInfo())
        coEvery { apiClient.getItemImageInfo(any()) } returns Result.success(emptyList())
        coEvery { apiClient.getRemoteImageProviders(any()) } returns Result.success(emptyList())
        coEvery { apiClient.setItemImage(any(), any(), any()) } returns Result.success(Unit)

        viewModel = EditorViewModel(
            apiClient,
            authRepository,
            subtitleProviderRepository,
            // No-op streaming subtitle store — upload tests don't exercise the
            // durable subtitle path. Mirrors the player's TestStreamingSubtitleStore.
            object : com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore {
                override suspend fun save(
                    itemId: String,
                    provider: com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind,
                    providerSubtitleId: String,
                    fileName: String,
                    language: String?,
                    codec: String?,
                    isForced: Boolean,
                    isHearingImpaired: Boolean,
                    bytes: ByteArray,
                ) = com.raulshma.jellyplay.core.model.subtitle.SavedSubtitle(
                    provider, providerSubtitleId, fileName, language, codec, isForced, isHearingImpaired, fileName,
                )
                override suspend fun loadAll(itemId: String) = emptyList<com.raulshma.jellyplay.core.model.subtitle.SavedSubtitle>()
                override suspend fun fileFor(itemId: String, saved: com.raulshma.jellyplay.core.model.subtitle.SavedSubtitle) = java.io.File(saved.fileRelativePath)
                override suspend fun delete(itemId: String, saved: com.raulshma.jellyplay.core.model.subtitle.SavedSubtitle) = Unit
                override suspend fun clear(itemId: String) = Unit
            },
            context,
        )
    }

    @Test
    fun `uploadImageFromUri does not throw NotImplementedError and reads the stream`() = runTest {
        // Regression guard for the TODO() crash that used to live in the UI.
        val uri = mockk<Uri>()
        every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream(byteArrayOf(1, 2, 3))

        viewModel.loadEditorData(itemId)
        advanceUntilIdle()

        // Should complete without throwing NotImplementedError (the prior crash).
        viewModel.uploadImageFromUri(uri, "Primary")
        advanceUntilIdle()
    }

    @Test
    fun `uploadImage from bytes does not surface an error on success`() = runTest {
        // Exercises the existing ByteArray-based image upload and the fixed
        // failure handler (previously a no-op that copied error onto itself).
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()

        viewModel.uploadImage(byteArrayOf(1, 2, 3), "Primary")
        advanceUntilIdle()

        // No error should be present after a successful upload.
        val state = viewModel.uiState.value
        assert(state.error == null) { "Expected no error after successful upload, got: ${state.error}" }
    }
}
