package com.raulshma.jellyplay.feature.newsletter

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.NewsletterRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.model.NewsletterData
import io.mockk.coEvery
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
import kotlin.test.assertTrue

/**
 * Error-copy and in-flight-flag gaps in [NewsletterViewModel] NOT pinned by
 * [NewsletterViewModelTest] / [NewsletterViewModelEventGapsTest]:
 *
 * 1. A load failure whose exception carries NO message falls back to the
 *    "Failed to load newsletter" literal (byte-identical to the legacy
 *    `e.message ?: "Failed to load newsletter"` pair — the message-present
 *    branch is covered in the main suite).
 * 2. The pull-to-refresh in-flight window: [NewsletterUiState.isRefreshing]
 *    is up (and [NewsletterUiState.isLoading] stays down) WHILE the load is
 *    suspended — the existing suite could only observe the settled state.
 * 3. A successful reload clears a previously surfaced load error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewsletterViewModelErrorFallbackTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (NewsletterViewModelTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: NewsletterRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var notificationStore: NotificationStore
    private lateinit var authRepository: AuthRepository
    private lateinit var notificationSlice: MutableStateFlow<NotificationSlice>

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk()
        imageUrlProvider = mockk()
        notificationStore = mockk()
        authRepository = mockk()
        notificationSlice = MutableStateFlow(NotificationSlice())
        every { notificationStore.notification } returns notificationSlice
        coEvery { notificationStore.setNewsletterLastViewed(any()) } returns Unit
        every { authRepository.currentUser } returns MutableStateFlow(null)
        every { imageUrlProvider.getImageUrl(any()) } returns "https://img/item"
        every { imageUrlProvider.getBackdropUrl(any()) } returns "https://img/backdrop"
        coEvery { mediaRepository.getNewsletterData(any()) } returns
            Result.success(NewsletterData(serverName = "TestServer"))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): NewsletterViewModel = NewsletterViewModel(
        newsletterRepository = mediaRepository,
        imageUrlProvider = imageUrlProvider,
        notificationStore = notificationStore,
        authRepository = authRepository,
    )

    @Test
    fun `load failure without a message falls back to the literal`() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getNewsletterData(any()) } returns
            Result.failure(RuntimeException(null as String?))

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals("Failed to load newsletter", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `pull-to-refresh raises isRefreshing while the load is in flight`() = runTest(mainDispatcher) {
        val gate = kotlinx.coroutines.CompletableDeferred<Result<NewsletterData>>()
        coEvery { mediaRepository.getNewsletterData(any()) } coAnswers { gate.await() }

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(NewsletterUiEvent.PullToRefresh)
        advanceUntilIdle()

        // In flight: only the pull-to-refresh flag is up.
        assertTrue(vm.uiState.value.isRefreshing)
        assertFalse(vm.uiState.value.isLoading)

        gate.complete(Result.success(NewsletterData(serverName = "S4")))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals("S4", vm.uiState.value.serverName)
    }

    @Test
    fun `a successful reload clears a previously surfaced load error`() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getNewsletterData(any()) } returns
            Result.failure(IllegalStateException("offline"))
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals("offline", vm.uiState.value.error)

        coEvery { mediaRepository.getNewsletterData(any()) } returns
            Result.success(NewsletterData(serverName = "Back"))
        vm.onEvent(NewsletterUiEvent.Refresh)
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.error)
        assertEquals("Back", vm.uiState.value.serverName)
    }
}
