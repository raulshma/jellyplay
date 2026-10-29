package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.whatsnew.WhatsNewRepository
import com.raulshma.jellyplay.core.model.WhatsNewRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The What's New archive screen's model: the init choreography only — mirror
 * the repository's releases and fire one refresh on construction. The feed
 * assembly itself (cache ∪ fetch, newest first) lives in the repository
 * (pinned by core:data's WhatsNewRepositoryImplTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WhatsNewViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The two-member interface — a hand fake, no mocking library needed. */
    private class FakeWhatsNewRepository : WhatsNewRepository {
        val backing = MutableStateFlow<List<WhatsNewRelease>>(emptyList())
        var refreshCount = 0

        override val releases: StateFlow<List<WhatsNewRelease>> = backing

        override suspend fun releasesSnapshot(): List<WhatsNewRelease> = backing.value

        override suspend fun refresh(): Result<Unit> {
            refreshCount++
            return Result.success(Unit)
        }
    }

    @Test
    fun `mirrors the repository's releases`() = runTest {
        val repository = FakeWhatsNewRepository()
        val releases = listOf(
            WhatsNewRelease(version = "0.11.6"),
            WhatsNewRelease(version = "0.11.5"),
        )
        repository.backing.value = releases
        val viewModel = WhatsNewViewModel(repository)

        advanceUntilIdle()
        assertEquals(releases, viewModel.releases)

        // The collect is live: a later repository emission folds in.
        val updated = listOf(WhatsNewRelease(version = "0.11.7"), *releases.toTypedArray())
        repository.backing.value = updated
        advanceUntilIdle()
        assertEquals(updated, viewModel.releases)
    }

    @Test
    fun `refreshes once on construction`() = runTest {
        val repository = FakeWhatsNewRepository()

        WhatsNewViewModel(repository)
        advanceUntilIdle()

        assertEquals(1, repository.refreshCount)
    }
}
