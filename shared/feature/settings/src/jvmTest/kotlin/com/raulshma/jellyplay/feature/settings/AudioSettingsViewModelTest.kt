package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.AudioPreferences
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
 * Pins the Audio settings preference-mirror wiring (LibraryLayout jvmTest
 * pattern): the screen's state is the [PreferenceProjections.audioPreferences]
 * slice, named editor setters route to [PreferencesEditor], lambda-routed
 * setters persist through the owning store inside `editor.edit { }` (captured
 * and replayed against a stub scope, since a relaxed editor mock never runs
 * the block), and the clear-cache action reaches the [AudioCacheClearer] seam.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioSettingsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var store: UserPreferencesStore
    private lateinit var projections: PreferenceProjections
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var editor: PreferencesEditor
    private lateinit var audioCacheClearer: AudioCacheClearer
    private lateinit var editScope: PreferencesEditScope
    private lateinit var audioStore: AudioStore
    private lateinit var audioEffectsStore: AudioEffectsStore
    private lateinit var audioCacheStore: AudioCacheStore

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        store = mockk(relaxed = true)
        projections = mockk(relaxed = true)
        appearanceStore = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        audioCacheClearer = mockk(relaxed = true)
        editScope = mockk(relaxed = true)
        audioStore = mockk(relaxed = true)
        audioEffectsStore = mockk(relaxed = true)
        audioCacheStore = mockk(relaxed = true)
        every { projections.audioPreferences } returns MutableStateFlow(AudioPreferences())
        every { appearanceStore.showAdvancedSettings } returns MutableStateFlow(false)
        every { editScope.audio } returns audioStore
        every { editScope.audioEffects } returns audioEffectsStore
        every { editScope.audioCache } returns audioCacheStore
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
        AudioSettingsViewModel(store, projections, appearanceStore, editor, audioCacheClearer)

    @Test
    fun `preferences exposes the audio projection flow`() = runTest {
        val seeded = MutableStateFlow(AudioPreferences(audioDefaultSpeed = 1.25f))
        every { projections.audioPreferences } returns seeded
        val viewModel = viewModel()
        advanceUntilIdle()

        assertSame(seeded, viewModel.preferences)
        assertEquals(1.25f, viewModel.preferences.value.audioDefaultSpeed)
    }

    @Test
    fun `gapless toggle delegates to the editor named setter`() = runTest {
        val viewModel = viewModel()

        viewModel.setGaplessEnabled(true)
        advanceUntilIdle()

        verify(exactly = 1) { editor.setGaplessEnabled(true) }
    }

    @Test
    fun `equalizer toggle persists through the audioEffects store`() = runTest {
        val viewModel = viewModel()
        val edit = captureEdit()

        viewModel.setEqualizerEnabled(true)
        advanceUntilIdle()
        edit.captured.invoke(editScope)

        coVerify(exactly = 1) { audioEffectsStore.setEqualizerEnabled(true) }
    }

    @Test
    fun `audio caching toggle persists through the audioCache store`() = runTest {
        val viewModel = viewModel()
        val edit = captureEdit()

        viewModel.setAudioCachingEnabled(false)
        advanceUntilIdle()
        edit.captured.invoke(editScope)

        coVerify(exactly = 1) { audioCacheStore.setAudioCachingEnabled(false) }
    }

    @Test
    fun `clearAudioCache delegates to the cache-clearer seam`() = runTest {
        val viewModel = viewModel()

        viewModel.clearAudioCache()
        advanceUntilIdle()

        coVerify(exactly = 1) { audioCacheClearer.clear() }
    }
}
