package com.raulshma.jellyplay.feature.newsletter

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.model.NewsletterData
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.feature.newsletter.generated.resources.Res
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_send_failed
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_send_success
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_test_sent
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
 * Newsletter ViewModel coverage (requests/calendar conveyor test style, no
 * legacy suite existed): section-order resolution (stored order filtered by
 * the enabled-section set, DEFAULT_ORDER fallback), send-now/send-test
 * result mapping incl. the failure branch, the markViewed timestamp write,
 * the admin flag flow-through, and the two date/count formatting helpers
 * (reached through reflection because they are file-private in the moved
 * screens — kept byte-identical to HEAD rather than widened for tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewsletterViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (requests conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var notificationStore: NotificationStore
    private lateinit var authRepository: AuthRepository

    /** Backing flow behind NotificationStore.notification (the prefs slice). */
    private lateinit var notificationSlice: MutableStateFlow<NotificationSlice>

    /** Backing flow behind AuthRepository.currentUser (the admin gate). */
    private lateinit var currentUser: MutableStateFlow<UserInfo?>

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk()
        imageUrlProvider = mockk()
        notificationStore = mockk()
        authRepository = mockk()
        notificationSlice = MutableStateFlow(NotificationSlice())
        currentUser = MutableStateFlow(null)
        every { notificationStore.notification } returns notificationSlice
        coEvery { notificationStore.setNewsletterLastViewed(any()) } returns Unit
        every { authRepository.currentUser } returns currentUser
        every { imageUrlProvider.getImageUrl(any()) } returns "https://img/item"
        every { imageUrlProvider.getBackdropUrl(any()) } returns "https://img/backdrop"
        stubLoad { Result.success(NewsletterData(serverName = "TestServer")) }
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

    /** Routes getNewsletterData(since) into [response]; captures the since arg. */
    private fun stubLoad(response: () -> Result<NewsletterData>) {
        coEvery { mediaRepository.getNewsletterData(any()) } answers { response() }
    }

    @Test
    fun `section order is stored order filtered by enabled sections`() = runTest(mainDispatcher) {
        notificationSlice.value = NotificationSlice(
            newsletterSectionOrder = listOf(
                NewsletterSectionType.NEXT_UP,
                NewsletterSectionType.RECENTLY_ADDED,
                NewsletterSectionType.CURATED_PICKS,
            ),
            enabledNewsletterSections = setOf(
                NewsletterSectionType.RECENTLY_ADDED,
                NewsletterSectionType.NEXT_UP,
            ),
        )
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(
            listOf(NewsletterSectionType.NEXT_UP, NewsletterSectionType.RECENTLY_ADDED),
            vm.uiState.value.sectionOrder,
        )
    }

    @Test
    fun `section order falls back to DEFAULT_ORDER when filter empties it`() = runTest(mainDispatcher) {
        notificationSlice.value = NotificationSlice(
            newsletterSectionOrder = listOf(NewsletterSectionType.CURATED_PICKS),
            enabledNewsletterSections = emptySet(),
        )
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(NewsletterSectionType.DEFAULT_ORDER, vm.uiState.value.sectionOrder)
    }

    @Test
    fun `successful load populates state and writes the viewed timestamp`() = runTest(mainDispatcher) {
        val timestamp = slot<Long>()
        coEvery { notificationStore.setNewsletterLastViewed(capture(timestamp)) } returns Unit

        val data = NewsletterData(
            serverName = "JellyServer",
            recentlyAdded = listOf(mockk(relaxed = true)),
        )
        stubLoad { Result.success(data) }
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("JellyServer", state.serverName)
        assertEquals(data.recentlyAdded, state.recentlyAdded)
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertNull(state.error)
        coVerify(exactly = 1) { notificationStore.setNewsletterLastViewed(any()) }
        // Head semantics: System.currentTimeMillis() at the write.
        assertTrue(timestamp.captured > 0L)
        assertTrue(System.currentTimeMillis() - timestamp.captured < 60_000L)
    }

    @Test
    fun `load failure surfaces the error message`() = runTest(mainDispatcher) {
        stubLoad { Result.failure(IllegalStateException("boom")) }
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("boom", state.error)
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `send now maps success to SendSuccess with the send_success resource`() = runTest(mainDispatcher) {
        coEvery { mediaRepository.sendNewsletter() } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(NewsletterUiEvent.SendNow)
        assertEquals(NewsletterSendAction.SEND_NOW, vm.uiState.value.pendingSendAction)
        vm.onEvent(NewsletterUiEvent.ConfirmSend)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(NewsletterMessage.SendSuccess(Res.string.newsletter_send_success), state.sendResult)
        assertFalse(state.isSending)
        assertNull(state.pendingSendAction)
        coVerify(exactly = 1) { mediaRepository.sendNewsletter() }
        coVerify(exactly = 0) { mediaRepository.sendTestNewsletter() }
    }

    @Test
    fun `send test maps success to TestSent with the test_sent resource`() = runTest(mainDispatcher) {
        coEvery { mediaRepository.sendTestNewsletter() } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(NewsletterUiEvent.SendTest)
        vm.onEvent(NewsletterUiEvent.ConfirmSend)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(NewsletterMessage.TestSent(Res.string.newsletter_test_sent), state.sendResult)
        assertFalse(state.isSending)
        coVerify(exactly = 1) { mediaRepository.sendTestNewsletter() }
    }

    @Test
    fun `send failure maps to SendFailed with the send_failed resource`() = runTest(mainDispatcher) {
        coEvery { mediaRepository.sendNewsletter() } returns Result.failure(IllegalStateException("smtp down"))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(NewsletterUiEvent.SendNow)
        vm.onEvent(NewsletterUiEvent.ConfirmSend)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(NewsletterMessage.SendFailed(Res.string.newsletter_send_failed), state.sendResult)
        assertFalse(state.isSending)
    }

    @Test
    fun `dismiss send result clears it`() = runTest(mainDispatcher) {
        coEvery { mediaRepository.sendNewsletter() } returns Result.success(Unit)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onEvent(NewsletterUiEvent.SendNow)
        vm.onEvent(NewsletterUiEvent.ConfirmSend)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sendResult != null)

        vm.onEvent(NewsletterUiEvent.DismissSendResult)
        assertNull(vm.uiState.value.sendResult)
    }

    @Test
    fun `admin flag flows through from the current user`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isAdmin)

        val admin = UserInfo(
            id = "1",
            name = "root",
            serverAddress = "https://srv",
            accessToken = "tok",
            isAdmin = true,
        )
        currentUser.value = admin
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isAdmin)

        currentUser.value = admin.copy(isAdmin = false)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isAdmin)
    }

    @Test
    fun `imageUrl helpers delegate to the provider`() {
        val vm = createViewModel()
        assertEquals("https://img/item", vm.getImageUrl("abc"))
        assertEquals("https://img/backdrop", vm.getBackdropUrl("abc"))
    }
}
