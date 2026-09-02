package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Covers the `homeBackdropEnabled` setter wiring on
 * [AppearanceSettingsViewModel] — mirroring [LibraryLayoutViewModelTest]'s
 * mockk style — without touching the DataStore.
 *
 * Ported from the legacy Android unit test: the :core:testing
 * MainDispatcherRule is inlined (StandardTestDispatcher + setMain/resetMain —
 * downloads-conveyor jvmTest pattern) because jvmTest has no access to that
 * module.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceSettingsViewModelHomeBackdropTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var store: UserPreferencesStore
    private lateinit var projections: PreferenceProjections
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var editor: PreferencesEditor

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        store = mockk(relaxed = true)
        projections = mockk(relaxed = true)
        appearanceStore = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { projections.appearanceScreenPreferences } returns
            MutableStateFlow(AppearanceScreenPreferences())
        every { projections.navigationCustomizationPreferences } returns
            MutableStateFlow(NavigationCustomizationPreferences())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setHomeBackdropEnabled delegates to editor`() {
        val viewModel = AppearanceSettingsViewModel(store, projections, appearanceStore, editor)

        viewModel.setHomeBackdropEnabled(false)

        verify { editor.setHomeBackdropEnabled(false) }
    }
}
