package com.raulshma.jellyplay.feature.subtitle.tester

import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import android.content.Context
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleTesterViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var engineFactory: PlayerEngineFactory
    private lateinit var preferencesStore: UserPreferencesStore
    private lateinit var fakeEngine: MediaEngine
    private lateinit var prefsFlow: MutableStateFlow<UserPreferences>
    private val appContext: Context = mockk(relaxed = true)

    private lateinit var viewModel: SubtitleTesterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeEngine = mockk(relaxed = true)
        engineFactory = mockk(relaxed = true)
        every { engineFactory.create(any()) } returns fakeEngine

        prefsFlow = MutableStateFlow(UserPreferences())
        preferencesStore = mockk(relaxed = true)
        every { preferencesStore.preferences } returns prefsFlow

        viewModel = SubtitleTesterViewModel(
            engineFactory = engineFactory,
            preferencesStore = preferencesStore,
            context = appContext,
        )
        // Drain init coroutine (Unconfined runs eagerly).
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_snapshotsCurrentPrefsAsWorkingAndOriginal() {
        val sdrStyle = SubtitleStyle(fontSize = 30, fontColor = SubtitleColor.RED)
        val hdrStyle = SubtitleStyle(fontSize = 40)
        prefsFlow.value = UserPreferences(
            subtitleStyle = sdrStyle,
            hdrSubtitleStyle = hdrStyle,
            hdrSubtitleStyleEnabled = true,
        )
        // Re-create VM to consume the new prefs.
        viewModel = SubtitleTesterViewModel(
            engineFactory = engineFactory,
            preferencesStore = preferencesStore,
            context = appContext,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(sdrStyle, state.workingSdrStyle)
        assertEquals(sdrStyle, state.originalSdrStyle)
        assertEquals(hdrStyle, state.workingHdrStyle)
        assertEquals(hdrStyle, state.originalHdrStyle)
        assertTrue(state.hdrSubtitleEnabled)
        assertFalse(state.isDirty)
    }

    @Test
    fun updateStyle_inSdrMode_updatesSdrWorkingCopy() {
        viewModel.setMode(SubtitleStyleMode.SDR)
        viewModel.updateStyle(viewModel.uiState.value.workingSdrStyle.copy(fontSize = 36))

        assertEquals(36, viewModel.uiState.value.workingSdrStyle.fontSize)
        // HDR untouched.
        assertEquals(viewModel.uiState.value.originalHdrStyle, viewModel.uiState.value.workingHdrStyle)
        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun updateStyle_inHdrMode_updatesHdrWorkingCopy() {
        viewModel.setMode(SubtitleStyleMode.HDR)
        viewModel.updateStyle(viewModel.uiState.value.workingHdrStyle.copy(fontSize = 44))

        assertEquals(44, viewModel.uiState.value.workingHdrStyle.fontSize)
        // SDR untouched.
        assertEquals(viewModel.uiState.value.originalSdrStyle, viewModel.uiState.value.workingSdrStyle)
        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun reset_restoresOriginalSnapshots() {
        viewModel.setMode(SubtitleStyleMode.SDR)
        viewModel.updateStyle(viewModel.uiState.value.workingSdrStyle.copy(fontSize = 50))
        assertTrue(viewModel.uiState.value.isDirty)

        viewModel.reset()

        assertEquals(viewModel.uiState.value.originalSdrStyle, viewModel.uiState.value.workingSdrStyle)
        assertEquals(viewModel.uiState.value.originalHdrStyle, viewModel.uiState.value.workingHdrStyle)
        assertFalse(viewModel.uiState.value.isDirty)
    }

    @Test
    fun setMode_updatesModeAndKeepsBothCopies() {
        viewModel.setMode(SubtitleStyleMode.HDR)
        assertEquals(SubtitleStyleMode.HDR, viewModel.uiState.value.mode)
        viewModel.setMode(SubtitleStyleMode.SDR)
        assertEquals(SubtitleStyleMode.SDR, viewModel.uiState.value.mode)
    }

    @Test
    fun applyAndExit_writesAllThreePersistedValues() {
        viewModel.setMode(SubtitleStyleMode.SDR)
        val newSdr = viewModel.uiState.value.workingSdrStyle.copy(fontSize = 33)
        viewModel.updateStyle(newSdr)
        viewModel.setMode(SubtitleStyleMode.HDR)
        val newHdr = viewModel.uiState.value.workingHdrStyle.copy(fontSize = 55)
        viewModel.updateStyle(newHdr)
        viewModel.setHdrSubtitleEnabled(true)

        var exited = false
        viewModel.applyAndExit { exited = true }
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesStore.setSubtitleStyle(newSdr.copy(applyCustomStyle = true)) }
        coVerify { preferencesStore.setHdrSubtitleStyle(newHdr.copy(applyCustomStyle = true)) }
        coVerify { preferencesStore.setHdrSubtitleStyleEnabled(true) }
        assertTrue(exited)
    }

    @Test
    fun reset_doesNotPersist() {
        viewModel.updateStyle(viewModel.uiState.value.workingSdrStyle.copy(fontSize = 99))
        viewModel.reset()
        coVerify(exactly = 0) { preferencesStore.setSubtitleStyle(any()) }
    }

    @Test
    fun switchEngine_releasesOldEngine_andCreatesNew() {
        // Engine created on init for EXO_PLAYER.
        verify(exactly = 1) { engineFactory.create(PlayerType.EXO_PLAYER) }

        viewModel.switchEngine(PlayerType.MPV)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { fakeEngine.release() }
        verify(exactly = 1) { engineFactory.create(PlayerType.MPV) }
        assertEquals(PlayerType.MPV, viewModel.uiState.value.previewEngine)
        verify(atLeast = 1) { fakeEngine.load(any()) }
    }

    @Test
    fun onCleared_releasesEngine() {
        // onCleared() is protected; invoke via reflection to exercise the release path.
        val clearedVm = SubtitleTesterViewModel(engineFactory, preferencesStore, appContext)
        testDispatcher.scheduler.advanceUntilIdle()
        val onCleared = androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("onCleared")
        onCleared.isAccessible = true
        onCleared.invoke(clearedVm)
        verify { fakeEngine.release() }
    }

    @Test
    fun updateStyle_pushesEngineConfigWithResolvedStyleForcedApply() {
        viewModel.setMode(SubtitleStyleMode.SDR)
        val newStyle = viewModel.uiState.value.workingSdrStyle.copy(fontSize = 38)
        viewModel.updateStyle(newStyle)
        testDispatcher.scheduler.advanceUntilIdle()

        val capturedConfig = mutableListOf<EngineConfig>()
        verify { fakeEngine.updateConfig(capture(capturedConfig)) }
        assertEquals(newStyle.copy(applyCustomStyle = true), capturedConfig.last().subtitleStyle)
    }

    @Test
    fun init_loadsHostClipWithSampleSubtitle() {
        verify(atLeast = 1) { fakeEngine.load(any()) }
    }

    @Test
    fun activeEngine_surfacesAfterInit() {
        val surfaced = viewModel.activeEngine.value
        assertNotNull(surfaced)
        assertSame(fakeEngine, surfaced)
    }

    @Test
    fun switchEngine_surfacesNewEngine() {
        // Sanity: pre-switch engine is the init one.
        assertSame(fakeEngine, viewModel.activeEngine.value)

        viewModel.switchEngine(PlayerType.MPV)
        testDispatcher.scheduler.advanceUntilIdle()

        // Old engine was released and a new one surfaced (same fake mock here).
        verify { fakeEngine.release() }
        verify(exactly = 1) { engineFactory.create(PlayerType.MPV) }
        val surfaced = viewModel.activeEngine.value
        assertNotNull(surfaced)
        assertSame(fakeEngine, surfaced)
    }

    @Test
    fun onCleared_clearsActiveEngine() {
        val clearedVm = SubtitleTesterViewModel(engineFactory, preferencesStore, appContext)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(clearedVm.activeEngine.value)

        val onCleared = androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("onCleared")
        onCleared.isAccessible = true
        onCleared.invoke(clearedVm)

        assertNull(clearedVm.activeEngine.value)
    }
}
