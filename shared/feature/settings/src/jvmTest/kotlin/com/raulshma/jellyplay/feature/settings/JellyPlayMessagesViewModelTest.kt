package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.JellyPlayEventsRepository
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlayMessage
import com.raulshma.jellyplay.core.testfixtures.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule

/**
 * The companion-plugin inbox screen's model (the module's mockk idiom, the
 * [JellyPlayYourWatchingViewModelTest] pattern): the per-call messages gate
 * over the repository's two verbs. The inbox state itself lives in the
 * repository (the Plugin-tab-loader glossary's documented exception) — this
 * pins the VM's gate ladder and its pass-through, not the state machine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JellyPlayMessagesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var eventsRepository: JellyPlayEventsRepository
    private lateinit var statusStore: JellyPlayPluginStatusStore

    @BeforeTest
    fun setUp() {
        eventsRepository = mockk(relaxed = true)
        statusStore = mockk(relaxed = true)
    }

    /** featureGate stays null: the probe-only fallback keeps the pre-toggle behavior. */
    private fun viewModel(gateOpen: Boolean) = JellyPlayMessagesViewModel(
        eventsRepository = eventsRepository,
        statusStore = statusStore,
    ).also {
        every { eventsRepository.inbox } returns MutableStateFlow(emptyList())
        every { statusStore.status } returns MutableStateFlow(JellyPlayPluginStatus.AVAILABLE)
        every { statusStore.hasFeature(JellyPlayPluginFeatures.Messages) } returns gateOpen
    }

    @Test
    fun `messages mirrors the repository inbox`() = runTest {
        val inbox = listOf(JellyPlayMessage(id = "m1", title = "Maintenance window"))
        every { eventsRepository.inbox } returns MutableStateFlow(inbox)

        val viewModel = viewModel(gateOpen = true)

        assertEquals(inbox, viewModel.messages.value)
    }

    @Test
    fun `refresh fetches the inbox when the gate is open`() = runTest {
        val viewModel = viewModel(gateOpen = true)
        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { eventsRepository.refreshInbox() }
    }

    @Test
    fun `markRead routes through the repository when the gate is open`() = runTest {
        val viewModel = viewModel(gateOpen = true)
        viewModel.markRead("m1")
        advanceUntilIdle()

        coVerify(exactly = 1) { eventsRepository.markRead("m1") }
    }

    @Test
    fun `degraded gate never touches the repository`() = runTest {
        val viewModel = viewModel(gateOpen = false)
        viewModel.refresh()
        viewModel.markRead("m1")
        advanceUntilIdle()

        coVerify(exactly = 0) { eventsRepository.refreshInbox() }
        coVerify(exactly = 0) { eventsRepository.markRead(any()) }
    }
}
