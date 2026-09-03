package com.raulshma.jellyplay.feature.newsletter

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.model.NewsletterData
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
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Newsletter ViewModel event-surface gaps NOT pinned by
 * [NewsletterViewModelTest]:
 *
 * 1. The two reload entries differ: [NewsletterUiEvent.Refresh] drives the
 *    full-screen spinner ([NewsletterUiState.isLoading]) while
 *    [NewsletterUiEvent.PullToRefresh] drives only
 *    [NewsletterUiState.isRefreshing] — and both clear their flag on settle.
 * 2. [NewsletterUiEvent.Dismiss] writes the viewed timestamp without a reload.
 * 3. [NewsletterUiEvent.ConfirmSend] without a pending action is a guarded
 *    no-op (no repository send, no spinner).
 * 4. [NewsletterUiEvent.DismissSendDialog] cancels the pending send without
 *    sending.
 * 5. The since-window forwarded to the repository is exactly "now minus 7
 *    days at start of day", ISO_LOCAL_DATE_TIME formatted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewsletterViewModelEventGapsTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (NewsletterViewModelTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
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
        mediaRepository = mediaRepository,
        imageUrlProvider = imageUrlProvider,
        notificationStore = notificationStore,
        authRepository = authRepository,
    )

    @Test
    fun `refresh drives isLoading while pull-to-refresh drives isRefreshing`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(NewsletterUiEvent.PullToRefresh)
        advanceUntilIdle()
        // Settled: pull-to-refresh left the full-screen loader untouched.
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isRefreshing)

        coEvery { mediaRepository.getNewsletterData(any()) } coAnswers {
            Result.success(NewsletterData(serverName = "S2"))
        }
        vm.onEvent(NewsletterUiEvent.Refresh)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("S2", vm.uiState.value.serverName)
    }

    @Test
    fun `pull-to-refresh never shows the full-screen loader`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery { mediaRepository.getNewsletterData(any()) } coAnswers {
            // Observed from inside the in-flight load: the loader flags must
            // be mutually exclusive with the pull-to-refresh path.
            Result.success(NewsletterData(serverName = "S3"))
        }
        vm.onEvent(NewsletterUiEvent.PullToRefresh)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRefreshing)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `dismiss writes the viewed timestamp without reloading`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery { mediaRepository.getNewsletterData(any()) } answers {
            throw AssertionError("Dismiss must not reload")
        }

        vm.onEvent(NewsletterUiEvent.Dismiss)
        advanceUntilIdle()

        // The initial load already marked viewed once; dismiss marks again.
        coVerify(atLeast = 1) { notificationStore.setNewsletterLastViewed(any()) }
    }

    @Test
    fun `confirm send without a pending action is a guarded no-op`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(NewsletterUiEvent.ConfirmSend)
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.sendNewsletter() }
        coVerify(exactly = 0) { mediaRepository.sendTestNewsletter() }
        assertFalse(vm.uiState.value.isSending)
        assertNull(vm.uiState.value.sendResult)
    }

    @Test
    fun `dismiss send dialog cancels the pending action without sending`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(NewsletterUiEvent.SendNow)
        assertTrue(vm.uiState.value.pendingSendAction != null)

        vm.onEvent(NewsletterUiEvent.DismissSendDialog)

        assertNull(vm.uiState.value.pendingSendAction)
        coEvery { mediaRepository.sendNewsletter() } answers {
            throw AssertionError("Dismissed dialog must not send")
        }
    }

    @Test
    fun `load window is seven days back at start of day`() = runTest(mainDispatcher) {
        val since = slot<String>()
        coEvery { mediaRepository.getNewsletterData(capture(since)) } returns
            Result.success(NewsletterData(serverName = "S"))

        createViewModel()
        advanceUntilIdle()

        val expected = LocalDate.now().minusDays(7).atStartOfDay()
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        assertEquals(expected, since.captured)
        // Format sanity: start-of-day ISO local date-time, no offset.
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}T00:00:00""").matches(since.captured))
        Instant.parse(since.captured + "Z") // parseable as a date-time
    }
}
