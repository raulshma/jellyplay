package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
 * The Privacy & Data hub's destructive one-shot actions: cache wipe
 * (image cache preserved), image-cache wipe, per-user search-history clear,
 * and the full factory reset — each must reach its store/repository seam AND
 * post exactly one [PrivacyUserMessage] confirmation (the buffered Channel is
 * the commonMain replacement for the legacy UserMessageBus, so the message IS
 * the observable "the action completed" contract).
 *
 * All collaborators are mockk'd (interfaces / final classes); Main-dispatcher
 * rule inlined (StandardTestDispatcher + setMain/resetMain — module jvmTest
 * pattern).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyDataViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var editor: PreferencesEditor
    private lateinit var serverIdentityStore: ServerIdentityStore
    private lateinit var searchHistoryRepository: SearchHistoryRepository
    private lateinit var storageAreas: StorageAreas

    private val activeUserId = MutableStateFlow<String?>(null)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        editor = mockk(relaxed = true)
        serverIdentityStore = mockk(relaxed = true)
        searchHistoryRepository = mockk(relaxed = true)
        storageAreas = mockk(relaxed = true)

        every { serverIdentityStore.activeUserId } returns activeUserId
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): PrivacyDataViewModel = PrivacyDataViewModel(
        editor = editor,
        serverIdentityStore = serverIdentityStore,
        searchHistoryRepository = searchHistoryRepository,
        storageAreas = storageAreas,
    )

    // ---------------------------------------------------------------- cache wipes

    @Test
    fun `clearCache wipes via the seam and posts CacheCleared`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.clearCache()
        advanceUntilIdle()

        coVerify(exactly = 1) { storageAreas.clearCache() }
        assertEquals(PrivacyUserMessage.CacheCleared, vm.messages.first())
    }

    @Test
    fun `clearImageCache wipes only the image cache and posts ImageCacheCleared`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.clearImageCache()
        advanceUntilIdle()

        coVerify(exactly = 1) { storageAreas.clearImageCache() }
        coVerify(exactly = 0) { storageAreas.clearCache() }
        assertEquals(PrivacyUserMessage.ImageCacheCleared, vm.messages.first())
    }

    // NOTE (deliberate gap): the FAILURE path of `clearCache` / `clearImageCache`
    // — the `finally` that still posts the confirmation when the wipe throws —
    // has no unit test here. The VM runs the action fire-and-forget in
    // viewModelScope; when the seam throws, coroutines-test collects the
    // uncaught exception from the shared scheduler and fails the whole test at
    // completion (uninterceptable from the test body), so the message
    // assertion can never be reached. The success-path message contract for
    // both actions is pinned above; the finally-posting is manually verified.

    // ---------------------------------------------------------------- search history

    @Test
    fun `clearSearchHistory clears only the active user's history`() = runTest(testDispatcher) {
        activeUserId.value = "user-7"
        val vm = viewModel()
        advanceUntilIdle()

        vm.clearSearchHistory()
        advanceUntilIdle()

        coVerify(exactly = 1) { searchHistoryRepository.clearAll("user-7") }
        assertEquals(PrivacyUserMessage.SearchHistoryCleared, vm.messages.first())
    }

    @Test
    fun `clearSearchHistory without an active user is a repo no-op but still confirms`() = runTest(testDispatcher) {
        activeUserId.value = null
        val vm = viewModel()
        advanceUntilIdle()

        vm.clearSearchHistory()
        advanceUntilIdle()

        coVerify(exactly = 0) { searchHistoryRepository.clearAll(any()) }
        assertEquals(PrivacyUserMessage.SearchHistoryCleared, vm.messages.first())
    }

    // ---------------------------------------------------------------- factory reset

    @Test
    fun `factoryReset clears all preferences and posts FactoryResetDone`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.factoryReset()

        verify(exactly = 1) { editor.clearAllPreferences() }
        assertEquals(PrivacyUserMessage.FactoryResetDone, vm.messages.first())
    }
}
