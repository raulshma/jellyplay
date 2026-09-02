package com.raulshma.jellyplay.feature.editor

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.model.EditableItemMetadata
import com.raulshma.jellyplay.core.model.EditorPerson
import com.raulshma.jellyplay.core.model.ImageInfo
import com.raulshma.jellyplay.core.model.ImageProviderInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MetadataEditorInfo
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.RemoteImageInfo
import com.raulshma.jellyplay.core.model.RemoteImageResult
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.subtitle.SavedSubtitle
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the metadata-editor side of [EditorViewModel] beyond the upload path
 * covered by [EditorViewModelUploadTest]:
 *
 *  - `loadEditorData`: failure → error + loading cleared; success → admin-gated
 *    population of image infos / image providers / editor info, the editable
 *    field mapping, and the isAdmin flag flow-through (admin-only endpoints are
 *    skipped for non-admins instead of firing guaranteed-to-fail 403s).
 *  - `saveMetadata`: exact `EditableItemMetadata` mapping submitted to the
 *    repository (incl. blank→null and runtimeMinutes→ticks derivations),
 *    isDirty cleared on success, error surfaced on failure.
 *  - `deleteImage` / `loadRemoteImages` / `refreshMetadata` repository
 *    forwarding (incl. provider/startIndex pagination and the refresh mode
 *    being reused for both metadata and image refresh).
 *  - `updateField` dirty tracking, `clearError`, and image URL delegation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelMetadataTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (same conveyor port pattern as the upload
    // suite).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var editorRepository: MetadataEditorRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var subtitleProviderRepository: SubtitleProviderRepository

    private lateinit var viewModel: EditorViewModel

    /** Held so tests can flip the signed-in user (admin gating) before the VM reads it. */
    private val currentUserFlow = MutableStateFlow<UserInfo?>(null)

    private val itemId = "item-1"
    private val adminUser = UserInfo(
        id = "user-1",
        name = "Admin",
        serverAddress = "http://server",
        accessToken = "token",
        isAdmin = true,
    )
    private val nonAdminUser = UserInfo(
        id = "user-2",
        name = "User",
        serverAddress = "http://server",
        accessToken = "token",
        isAdmin = false,
    )

    private val imageInfos = listOf(
        ImageInfo(imageType = "Primary", imageIndex = 0, width = 100, height = 150, imageTag = "tag-1"),
    )
    private val imageProviders = listOf(
        ImageProviderInfo(name = "TheMovieDb", supportedImages = listOf("Primary", "Backdrop")),
    )

    private fun editorDetail(): MediaDetail = MediaDetail(
        item = MediaItem(
            id = itemId,
            name = "Stolen Movie",
            originalTitle = "Film Volé",
            overview = "An overview.",
            mediaType = MediaType.MOVIE,
            year = 1999,
            communityRating = 8.5f,
            officialRating = "PG-13",
            runTimeTicks = 90L * 600_000_000,
            premiereDate = "1999-07-02",
            genres = listOf("Drama"),
            tags = listOf("award-winner"),
            studios = listOf("Studio A"),
            indexNumber = 3,
            seasonNumber = 2,
        ),
        sortName = "Movie Stolen",
        customRating = "custom-rating",
        criticRating = 7.5f,
        taglines = listOf("A tagline"),
        productionLocations = listOf("Paris"),
        lockData = true,
        lockedFields = listOf("Overview"),
        status = "Ended",
        airDays = listOf("Monday"),
        airTime = "20:00",
        displayOrder = "SortName",
        preferredMetadataLanguage = "eng",
        preferredMetadataCountryCode = "USA",
        dateCreated = "2020-01-01",
        people = listOf(
            PersonInfo(id = "p1", name = "Alice", role = "Hero", type = "Actor", primaryImageTag = "tag-1"),
        ),
        providerIds = mapOf("tmdb" to "12345"),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        editorRepository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        subtitleProviderRepository = mockk(relaxed = true)

        every { authRepository.currentUser } returns currentUserFlow

        // Stub every suspend seam the VM may call with real Result values — the
        // relaxed mock's default Result mock ClassCasts inside onSuccess/onFailure
        // (LibraryViewModelTest trap).
        coEvery { editorRepository.getMediaDetail(itemId) } returns Result.success(editorDetail())
        coEvery { editorRepository.getMetadataEditorInfo(any()) } returns Result.success(MetadataEditorInfo())
        coEvery { editorRepository.getItemImageInfo(any()) } returns Result.success(imageInfos)
        coEvery { editorRepository.getRemoteImageProviders(any()) } returns Result.success(imageProviders)
        coEvery { editorRepository.setItemImage(any(), any(), any()) } returns Result.success(Unit)
        coEvery { editorRepository.updateItem(any(), any()) } returns Result.success(Unit)
        coEvery { editorRepository.deleteItemImage(any(), any(), any()) } returns Result.success(Unit)
        coEvery {
            editorRepository.getRemoteImages(any(), any(), any(), any(), any())
        } returns Result.success(RemoteImageResult())
        coEvery { editorRepository.refreshItemMetadata(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel = EditorViewModel(
            editorRepository,
            authRepository,
            subtitleProviderRepository,
            // No-op streaming subtitle store — metadata tests never exercise the
            // durable subtitle path (mirrors the upload suite's fake).
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

    @Test
    fun `loadEditorData surfaces a media detail failure as error and clears loading`() = runTest {
        coEvery { editorRepository.getMediaDetail(itemId) } returns Result.failure(RuntimeException("detail boom"))

        viewModel.loadEditorData(itemId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("detail boom", state.error)
        assertFalse(state.isLoading)
        assertNull(state.mediaDetail)
    }

    @Test
    fun `loadEditorData populates image info providers and fields for an admin user`() = runTest {
        // The admin endpoints are fetched only for admins; settle the init-block
        // isAdmin collector first so loadEditorData observes the signed-in admin.
        currentUserFlow.value = adminUser
        advanceUntilIdle()

        viewModel.loadEditorData(itemId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isAdmin)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNotNull(state.mediaDetail)
        assertNotNull(state.editorInfo)
        assertEquals(imageInfos, state.imageInfos)
        assertEquals(imageProviders, state.imageProviders)
        // Spot-check the editable field mapping the save path round-trips.
        assertEquals("Stolen Movie", state.name)
        assertEquals("Film Volé", state.originalTitle)
        assertEquals("Movie Stolen", state.sortName)
        assertEquals("An overview.", state.overview)
        assertEquals("A tagline", state.tagline)
        assertEquals("8.5", state.communityRating)
        assertEquals("7.5", state.criticRating)
        assertEquals("PG-13", state.officialRating)
        assertEquals("1999", state.productionYear)
        assertEquals("1999-07-02", state.premiereDate)
        assertEquals("90", state.runtimeMinutes)
        assertEquals("3", state.indexNumber)
        assertEquals("2", state.parentIndexNumber)
        assertEquals(listOf("Drama"), state.genres)
        assertEquals(listOf("award-winner"), state.tags)
        assertEquals(listOf("Studio A"), state.studios)
        assertEquals(mapOf("tmdb" to "12345"), state.providerIds)
        assertTrue(state.lockData)
        assertEquals(listOf("Overview"), state.lockedFields)
        assertEquals("eng", state.preferredMetadataLanguage)
        coVerify(exactly = 1) { editorRepository.getMetadataEditorInfo(itemId) }
        coVerify(exactly = 1) { editorRepository.getItemImageInfo(itemId) }
        coVerify(exactly = 1) { editorRepository.getRemoteImageProviders(itemId) }
    }

    @Test
    fun `loadEditorData skips admin-only endpoints for a non-admin user`() = runTest {
        currentUserFlow.value = nonAdminUser
        advanceUntilIdle()

        viewModel.loadEditorData(itemId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAdmin)
        assertNull(state.error)
        assertNotNull(state.mediaDetail)
        assertTrue(state.imageInfos.isEmpty())
        assertTrue(state.imageProviders.isEmpty())
        coVerify(exactly = 0) { editorRepository.getMetadataEditorInfo(any()) }
        coVerify(exactly = 0) { editorRepository.getItemImageInfo(any()) }
        coVerify(exactly = 0) { editorRepository.getRemoteImageProviders(any()) }
    }

    @Test
    fun `saveMetadata maps the editor fields into the submitted metadata and clears dirty on success`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()

        viewModel.updateField { it.copy(name = "New Name", overview = "Edited overview") }
        assertTrue(viewModel.uiState.value.isDirty)

        viewModel.saveMetadata()
        advanceUntilIdle()

        val expected = EditableItemMetadata(
            name = "New Name",
            originalTitle = "Film Volé",
            sortName = "Movie Stolen",
            overview = "Edited overview",
            tagline = "A tagline",
            genres = listOf("Drama"),
            tags = listOf("award-winner"),
            studios = listOf("Studio A"),
            communityRating = 8.5f,
            criticRating = 7.5f,
            officialRating = "PG-13",
            customRating = "custom-rating",
            productionYear = 1999,
            premiereDate = "1999-07-02",
            endDate = null,
            runtimeTicks = 90L * 600_000_000,
            indexNumber = 3,
            parentIndexNumber = 2,
            displayOrder = "SortName",
            status = "Ended",
            airDays = listOf("Monday"),
            airTime = "20:00",
            people = listOf(
                EditorPerson(id = "p1", name = "Alice", role = "Hero", type = "Actor", primaryImageTag = "tag-1"),
            ),
            providerIds = mapOf("tmdb" to "12345"),
            lockData = true,
            lockedFields = listOf("Overview"),
            preferredMetadataLanguage = "eng",
            preferredMetadataCountryCode = "USA",
            taglines = listOf("A tagline"),
            productionLocations = listOf("Paris"),
            dateCreated = "2020-01-01",
        )
        coVerify(exactly = 1) { editorRepository.updateItem(itemId, expected) }
        val state = viewModel.uiState.value
        assertFalse(state.isDirty)
        assertFalse(state.isSaving)
        assertNull(state.error)
    }

    @Test
    fun `saveMetadata submits blank text fields as nulls and an empty tagline list`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()

        viewModel.updateField {
            it.copy(
                originalTitle = "",
                tagline = "",
                communityRating = "",
                criticRating = "",
                runtimeMinutes = "",
                productionYear = "",
            )
        }

        viewModel.saveMetadata()
        advanceUntilIdle()

        val expected = EditableItemMetadata(
            name = "Stolen Movie",
            originalTitle = null,
            sortName = "Movie Stolen",
            overview = "An overview.",
            tagline = null,
            genres = listOf("Drama"),
            tags = listOf("award-winner"),
            studios = listOf("Studio A"),
            communityRating = null,
            criticRating = null,
            officialRating = "PG-13",
            customRating = "custom-rating",
            productionYear = null,
            premiereDate = "1999-07-02",
            endDate = null,
            runtimeTicks = null,
            indexNumber = 3,
            parentIndexNumber = 2,
            displayOrder = "SortName",
            status = "Ended",
            airDays = listOf("Monday"),
            airTime = "20:00",
            people = listOf(
                EditorPerson(id = "p1", name = "Alice", role = "Hero", type = "Actor", primaryImageTag = "tag-1"),
            ),
            providerIds = mapOf("tmdb" to "12345"),
            lockData = true,
            lockedFields = listOf("Overview"),
            preferredMetadataLanguage = "eng",
            preferredMetadataCountryCode = "USA",
            taglines = emptyList(),
            productionLocations = listOf("Paris"),
            dateCreated = "2020-01-01",
        )
        coVerify(exactly = 1) { editorRepository.updateItem(itemId, expected) }
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `saveMetadata surfaces a repository failure and keeps the dirty flag`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()

        viewModel.updateField { it.copy(name = "Changed") }
        coEvery { editorRepository.updateItem(any(), any()) } returns Result.failure(RuntimeException("save boom"))

        viewModel.saveMetadata()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("save boom", state.error)
        assertFalse(state.isSaving)
        assertTrue(state.isDirty)
        coVerify(exactly = 1) { editorRepository.updateItem(any(), any()) }
    }

    @Test
    fun `deleteImage forwards image type and index and refreshes the image infos`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()

        viewModel.deleteImage("Backdrop", 2)
        advanceUntilIdle()

        coVerify(exactly = 1) { editorRepository.deleteItemImage(itemId, "Backdrop", 2) }
        // The image-info reload runs after a successful delete (initial load is
        // admin-gated, so this call is the reload itself).
        coVerify(exactly = 1) { editorRepository.getItemImageInfo(itemId) }
        assertEquals(imageInfos, viewModel.uiState.value.imageInfos)

        viewModel.deleteImage("Primary")
        advanceUntilIdle()
        coVerify(exactly = 1) { editorRepository.deleteItemImage(itemId, "Primary", null) }
    }

    @Test
    fun `deleteImage surfaces a repository failure as error`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        coEvery {
            editorRepository.deleteItemImage(any(), any(), any())
        } returns Result.failure(RuntimeException("delete boom"))

        viewModel.deleteImage("Backdrop", 2)
        advanceUntilIdle()

        assertEquals("delete boom", viewModel.uiState.value.error)
    }

    @Test
    fun `loadRemoteImages forwards provider and startIndex pagination to the repository`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        val result = RemoteImageResult(
            images = listOf(
                RemoteImageInfo(
                    providerName = "TheMovieDb",
                    url = "https://img/1.jpg",
                    thumbnailUrl = "https://img/1_t.jpg",
                ),
            ),
            totalRecordCount = 1,
            providers = listOf("TheMovieDb"),
        )
        coEvery { editorRepository.getRemoteImages(itemId, "Primary", "TheMovieDb", 20, 50) } returns Result.success(result)

        viewModel.loadRemoteImages(imageType = "Primary", provider = "TheMovieDb", startIndex = 20)
        advanceUntilIdle()

        coVerify(exactly = 1) { editorRepository.getRemoteImages(itemId, "Primary", "TheMovieDb", 20, 50) }
        assertEquals(result, viewModel.uiState.value.remoteImages)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `updateField marks the state dirty and reverting the change clears it`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        val loaded = viewModel.uiState.value
        assertFalse(loaded.isDirty)

        viewModel.updateField { it.copy(name = "Changed") }
        assertTrue(viewModel.uiState.value.isDirty)

        // The dirty hash is content-based, so undoing the edit returns to clean.
        viewModel.updateField { it.copy(name = loaded.name) }
        assertFalse(viewModel.uiState.value.isDirty)
    }

    @Test
    fun `clearError clears a surfaced error`() = runTest {
        coEvery { editorRepository.getMediaDetail(itemId) } returns Result.failure(RuntimeException("load boom"))
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()
        assertEquals("load boom", viewModel.uiState.value.error)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `getImageUrl and getFullImageUrl delegate to the repository with thumbnail and full width`() {
        val info = ImageInfo(imageType = "Primary", imageIndex = 2, imageTag = "abc")
        every { editorRepository.getItemImageUrl(any(), any(), any(), any(), any()) } returns ""
        every { editorRepository.getItemImageUrl(itemId, "Primary", 400, 2, "abc") } returns "https://img/thumb"
        every { editorRepository.getItemImageUrl(itemId, "Primary", null, 2, "abc") } returns "https://img/full"

        assertEquals("https://img/thumb", viewModel.getImageUrl(itemId, info))
        assertEquals("https://img/full", viewModel.getFullImageUrl(itemId, info))

        verify(exactly = 1) { editorRepository.getItemImageUrl(itemId, "Primary", 400, 2, "abc") }
        verify(exactly = 1) { editorRepository.getItemImageUrl(itemId, "Primary", null, 2, "abc") }
    }

    @Test
    fun `refreshMetadata forwards the mode to both metadata and image refresh`() = runTest {
        viewModel.loadEditorData(itemId)
        advanceUntilIdle()

        viewModel.refreshMetadata()
        advanceUntilIdle()
        coVerify(exactly = 1) { editorRepository.refreshItemMetadata(itemId, "FullRefresh", "FullRefresh", false, false) }

        viewModel.refreshMetadata("Default", replaceAllMetadata = true, replaceAllImages = true)
        advanceUntilIdle()
        coVerify(exactly = 1) { editorRepository.refreshItemMetadata(itemId, "Default", "Default", true, true) }
        assertNull(viewModel.uiState.value.error)
    }
}
