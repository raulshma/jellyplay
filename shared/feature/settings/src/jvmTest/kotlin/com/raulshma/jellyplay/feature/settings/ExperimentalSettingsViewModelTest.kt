package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.ExperimentalPreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

/**
 * Pins the Experimental settings preference-mirror wiring (LibraryLayout
 * jvmTest pattern): the screen's state is the
 * [PreferenceProjections.experimentalPreferences] slice, and
 * `setExperimentalFeatureEnabled` computes the new set from the CURRENT
 * projection value (so concurrent toggles never clobber each other) and
 * persists it through the experimental store inside `editor.edit { }` —
 * captured and replayed against a stub scope since a relaxed editor mock
 * never runs the block.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExperimentalSettingsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var store: UserPreferencesStore
    private lateinit var projections: PreferenceProjections
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var editor: PreferencesEditor
    private lateinit var editScope: PreferencesEditScope
    private lateinit var experimentalStore: ExperimentalStore

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        store = mockk(relaxed = true)
        projections = mockk(relaxed = true)
        appearanceStore = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        editScope = mockk(relaxed = true)
        experimentalStore = mockk(relaxed = true)
        every { projections.experimentalPreferences } returns MutableStateFlow(
            ExperimentalPreferences(enabledExperimentalFeatures = setOf(ExperimentalFeature.HOME_CARD_CLIPPING)),
        )
        every { appearanceStore.showAdvancedSettings } returns MutableStateFlow(false)
        every { editScope.experimental } returns experimentalStore
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Captures the `edit { }` block so the test can run it against the stub scope. */
    private fun captureEdit() = slot<suspend PreferencesEditScope.() -> Unit>().also { captured ->
        every { editor.edit(capture(captured)) } returns mockk<Job>()
    }

    private fun viewModel() =
        ExperimentalSettingsViewModel(store, projections, appearanceStore, editor)

    @Test
    fun `preferences exposes the experimental projection flow`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            setOf(ExperimentalFeature.HOME_CARD_CLIPPING),
            viewModel.preferences.value.enabledExperimentalFeatures,
        )
    }

    @Test
    fun `enabling a feature unions it with the current set`() = runTest {
        val viewModel = viewModel()
        val edit = captureEdit()

        viewModel.setExperimentalFeatureEnabled(ExperimentalFeature.MEDIA_CARD_PEEK, true)
        advanceUntilIdle()
        edit.captured.invoke(editScope)

        coVerify(exactly = 1) {
            experimentalStore.setEnabledExperimentalFeatures(
                setOf(ExperimentalFeature.HOME_CARD_CLIPPING, ExperimentalFeature.MEDIA_CARD_PEEK),
            )
        }
    }

    @Test
    fun `disabling a feature removes it from the current set`() = runTest {
        val viewModel = viewModel()
        val edit = captureEdit()

        viewModel.setExperimentalFeatureEnabled(ExperimentalFeature.HOME_CARD_CLIPPING, false)
        advanceUntilIdle()
        edit.captured.invoke(editScope)

        coVerify(exactly = 1) {
            experimentalStore.setEnabledExperimentalFeatures(emptySet())
        }
    }
}
