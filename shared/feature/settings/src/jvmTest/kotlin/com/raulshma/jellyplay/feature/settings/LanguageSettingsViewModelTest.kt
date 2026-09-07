package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.model.LanguagePreferences
import com.raulshma.jellyplay.core.model.SubtitleStyle
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
import kotlin.test.assertSame

/**
 * Pins the Language/subtitle preference-mirror wiring (LibraryLayout jvmTest
 * pattern): the screen's state is the [PreferenceProjections.languagePreferences]
 * slice, every write persists through the owning store inside the VM's
 * `edit { }` command (captured and replayed against a stub scope, since a
 * relaxed editor mock never runs the block), and — the load-bearing one —
 * `setAppLanguage` persists the choice through the subtitle store **and then**
 * applies the platform locale via the [AppLocaleSetter] seam, in that order
 * within the same launched job.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanguageSettingsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var appLocaleSetter: AppLocaleSetter
    private lateinit var store: UserPreferencesStore
    private lateinit var projections: PreferenceProjections
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var editor: PreferencesEditor
    private lateinit var editScope: PreferencesEditScope
    private lateinit var subtitleStore: SubtitleLanguageStore
    private lateinit var playbackStore: PlaybackStore

    /** Every `edit { }` block the VM hands the editor, in call order. */
    private val editBlocks = mutableListOf<suspend PreferencesEditScope.() -> Unit>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        appLocaleSetter = mockk(relaxed = true)
        store = mockk(relaxed = true)
        projections = mockk(relaxed = true)
        appearanceStore = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        editScope = mockk(relaxed = true)
        subtitleStore = mockk(relaxed = true)
        playbackStore = mockk(relaxed = true)
        editBlocks.clear()
        every { projections.languagePreferences } returns MutableStateFlow(LanguagePreferences())
        every { appearanceStore.showAdvancedSettings } returns MutableStateFlow(false)
        every { editScope.subtitle } returns subtitleStore
        every { editScope.playback } returns playbackStore
        // List capture: blocks append across calls (the per-test slot in
        // [captureEdit] re-stubs over this when a suite wants a single block).
        every { editor.edit(capture(editBlocks)) } returns mockk<Job>()
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
        LanguageSettingsViewModel(
            appLocaleSetter,
            store,
            projections,
            AdvancedSettingsGate(appearanceStore, editor),
            editor,
        )

    @Test
    fun `preferences exposes the language projection flow`() = runTest {
        val seeded = MutableStateFlow(LanguagePreferences(preferredAudioLanguage = "ja"))
        every { projections.languagePreferences } returns seeded
        val viewModel = viewModel()
        advanceUntilIdle()

        assertSame(seeded, viewModel.preferences)
        assertEquals("ja", viewModel.preferences.value.preferredAudioLanguage)
    }

    @Test
    fun `subtitle writes persist through the subtitle store inside edit`() = runTest {
        val viewModel = viewModel()
        val style = SubtitleStyle(applyCustomStyle = true, fontSize = 30)

        viewModel.edit { it.subtitle.setPreferredSubtitleLanguage("en") }
        viewModel.edit { it.subtitle.setPreferredAudioLanguage("ja") }
        viewModel.edit { it.subtitle.setSubtitleStyle(style) }
        viewModel.edit { it.subtitle.setSubtitlesForcedOnly(true) }
        viewModel.edit { it.subtitle.setHighContrastSubtitles(true) }
        viewModel.edit { it.subtitle.setHdrSubtitleStyleEnabled(true) }
        viewModel.edit { it.subtitle.setHdrSubtitleStyle(style) }
        advanceUntilIdle()
        editBlocks.forEach { it.invoke(editScope) }

        coVerify(exactly = 1) { subtitleStore.setPreferredSubtitleLanguage("en") }
        coVerify(exactly = 1) { subtitleStore.setPreferredAudioLanguage("ja") }
        coVerify(exactly = 1) { subtitleStore.setSubtitleStyle(style) }
        coVerify(exactly = 1) { subtitleStore.setSubtitlesForcedOnly(true) }
        coVerify(exactly = 1) { subtitleStore.setHighContrastSubtitles(true) }
        coVerify(exactly = 1) { subtitleStore.setHdrSubtitleStyleEnabled(true) }
        coVerify(exactly = 1) { subtitleStore.setHdrSubtitleStyle(style) }
    }

    @Test
    fun `pgs direct play persists through the playback store inside edit`() = runTest {
        val viewModel = viewModel()

        viewModel.edit { it.playback.setPgsSubtitleDirectPlay(true) }
        advanceUntilIdle()
        editBlocks.forEach { it.invoke(editScope) }

        coVerify(exactly = 1) { playbackStore.setPgsSubtitleDirectPlay(true) }
    }

    @Test
    fun `setAppLanguage persists the choice then applies the platform locale`() = runTest {
        val viewModel = viewModel()
        val edit = captureEdit()

        viewModel.setAppLanguage("de")
        advanceUntilIdle()
        edit.captured.invoke(editScope)

        coVerify(exactly = 1) { subtitleStore.setAppLanguage("de") }
        verify(exactly = 1) { appLocaleSetter.setAppLocale("de") }
    }
}
