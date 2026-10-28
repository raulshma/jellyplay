package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.repository.LibraryFeed
import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.NotificationPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/**
 * Pins the Notification settings preference-mirror wiring (LibraryLayout
 * jvmTest pattern): the library list reuses the existing "non-music folders"
 * filter with the error surfaced verbatim, and
 * [NotificationSettingsViewModel.updateNotificationPreferences] applies the
 * transform through the notification store **and** reschedules the worker in
 * the SAME `editor.edit { }` block — captured and replayed against a stub
 * scope since a relaxed editor mock never runs the block.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var projections: PreferenceProjections
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var editor: PreferencesEditor
    private lateinit var libraryFeed: LibraryFeed
    private lateinit var detailFeed: DetailFeed
    private lateinit var notificationSync: NotificationSync
    private lateinit var imageUrlProvider: com.raulshma.jellyplay.core.data.util.ImageUrlProvider
    private lateinit var editScope: PreferencesEditScope
    private lateinit var notificationStore: NotificationStore

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        projections = mockk(relaxed = true)
        appearanceStore = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        libraryFeed = mockk(relaxed = true)
        detailFeed = mockk(relaxed = true)
        notificationSync = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        editScope = mockk(relaxed = true)
        notificationStore = mockk(relaxed = true)
        every { projections.notificationPreferences } returns MutableStateFlow(NotificationPreferences())
        every { appearanceStore.showAdvancedSettings } returns MutableStateFlow(false)
        every { editScope.notification } returns notificationStore
        coEvery { libraryFeed.getLibraryFolders(force = false) } returns Result.success(emptyList())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = NotificationSettingsViewModel(
        projections, AdvancedSettingsGate(appearanceStore, editor), editor, libraryFeed, detailFeed, notificationSync,
        imageUrlProvider = imageUrlProvider,
    )

    @Test
    fun `libraryFolders filters music libraries and reports no error`() = runTest {
        coEvery { libraryFeed.getLibraryFolders(force = false) } returns Result.success(
            listOf(
                LibraryFolder(id = "movies", name = "Movies", collectionType = "movies"),
                LibraryFolder(id = "music", name = "Music", collectionType = "music"),
                LibraryFolder(id = "shows", name = "Shows", collectionType = "tvshows"),
            )
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("movies", "shows"), viewModel.libraryFolders.value.map { it.id })
        assertNull(viewModel.libraryError)
    }

    @Test
    fun `libraryFolders surfaces the failure message`() = runTest {
        coEvery { libraryFeed.getLibraryFolders(force = false) } returns
            Result.failure(RuntimeException("offline"))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("offline", viewModel.libraryError)
        assertEquals(emptyList(), viewModel.libraryFolders.value)
    }

    @Test
    fun `updateNotificationPreferences persists the transform and reschedules`() = runTest {
        val viewModel = viewModel()
        val edit = slot<suspend PreferencesEditScope.() -> Unit>()
        every { editor.edit(capture(edit)) } returns mockk<Job>()
        val transform = slot<(NotificationPreferences) -> NotificationPreferences>()

        viewModel.updateNotificationPreferences { it.copy(enabled = true) }
        advanceUntilIdle()
        edit.captured.invoke(editScope)

        coVerify(exactly = 1) { notificationStore.updateNotificationPreferences(capture(transform)) }
        // The transform is applied, not replaced — the store receives the
        // caller's mutation of the current slice.
        assertEquals(true, transform.captured(NotificationPreferences()).enabled)
        // Rescheduling rides the same edit block: prefs and worker stay in sync.
        coVerify(exactly = 1) { notificationSync.scheduleOrUpdate() }
    }

    @Test
    fun `removeSeriesNotifications routes through the store RMW command`() = runTest {
        val viewModel = viewModel()
        val edit = slot<suspend PreferencesEditScope.() -> Unit>()
        every { editor.edit(capture(edit)) } returns mockk<Job>()

        viewModel.removeSeriesNotifications("series-1")
        advanceUntilIdle()
        edit.captured.invoke(editScope)

        coVerify(exactly = 1) { notificationStore.setSeriesNotificationEnabled("series-1", false) }
    }

    @Test
    fun `construction survives the synchronous first store emission (DEF-1)`() = runTest {
        // Android's viewModelScope runs on Dispatchers.Main.immediate, so the
        // preferences StateFlow's first emission was delivered synchronously
        // DURING construction — an init block declared before the state
        // fields reached reconcileSeries with a still-null resolvedSeriesItems
        // (the device-side Map.keySet() NPE crash loop). Unconfined reproduces
        // that delivery timing; the class dispatcher above deliberately does
        // not.
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.seriesNotifications.value.series)
        assertFalse(viewModel.seriesNotifications.value.loading)
    }
}
