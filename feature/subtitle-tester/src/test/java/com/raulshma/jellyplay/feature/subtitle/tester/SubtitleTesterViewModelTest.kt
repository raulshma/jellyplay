package com.raulshma.jellyplay.feature.subtitle.tester

import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import android.content.Context
import io.mockk.coEvery
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
    private lateinit var subtitleLanguageStore: SubtitleLanguageStore
    private lateinit var fontProvider: FontProvider
    private lateinit var fakeEngine: MediaEngine
    private lateinit var subtitleFlow: MutableStateFlow<SubtitleSlice>
    private lateinit var tempFilesDir: java.io.File
    private val appContext: Context = mockk(relaxed = true)

    private lateinit var viewModel: SubtitleTesterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // The factory materializes raw resources to filesDir; give it a real temp
        // dir and a stable Resources mock returning an empty stream, so the VM
        // init path (which loads the preview) doesn't NPE on the relaxed context
        // mock. (A relaxed mock returns a fresh Resources each access, so the
        // openRawResource stub must target a pinned instance.)
        tempFilesDir = kotlin.io.path.createTempDirectory("subtitle_tester_test").toFile()
        every { appContext.filesDir } returns tempFilesDir
        val resources = mockk<android.content.res.Resources>(relaxed = true)
        every { appContext.resources } returns resources
        every { resources.openRawResource(any()) } returns
            java.io.ByteArrayInputStream(ByteArray(0))

        fakeEngine = mockk(relaxed = true)
        engineFactory = mockk(relaxed = true)
        every { engineFactory.create(any()) } returns fakeEngine

        subtitleFlow = MutableStateFlow(SubtitleSlice())
        subtitleLanguageStore = mockk(relaxed = true)
        every { subtitleLanguageStore.subtitle } returns subtitleFlow
        fontProvider = mockk(relaxed = true)

        viewModel = SubtitleTesterViewModel(
            engineFactory = engineFactory,
            subtitleLanguageStore = subtitleLanguageStore,
            fontProvider = fontProvider,
            context = appContext,
        )
        // Drain init coroutine (Unconfined runs eagerly).
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempFilesDir.deleteRecursively()
    }

    @Test
    fun init_snapshotsCurrentPrefsAsWorkingAndOriginal() {
        val sdrStyle = SubtitleStyle(fontSize = 30, fontColor = SubtitleColor.RED)
        val hdrStyle = SubtitleStyle(fontSize = 40)
        subtitleFlow.value = SubtitleSlice(
            subtitleStyle = sdrStyle,
            hdrSubtitleStyle = hdrStyle,
            hdrSubtitleStyleEnabled = true,
        )
        // Re-create VM to consume the new prefs.
        viewModel = SubtitleTesterViewModel(
            engineFactory = engineFactory,
            subtitleLanguageStore = subtitleLanguageStore,
            fontProvider = fontProvider,
            context = appContext,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        // The tester forces applyCustomStyle = true on the working copies (the
        // override toggle is hidden); originals stay verbatim for dirty checks.
        assertEquals(sdrStyle.copy(applyCustomStyle = true), state.workingSdrStyle)
        assertEquals(sdrStyle, state.originalSdrStyle)
        assertEquals(hdrStyle.copy(applyCustomStyle = true), state.workingHdrStyle)
        assertEquals(hdrStyle, state.originalHdrStyle)
        assertTrue(state.hdrSubtitleEnabled)
        assertFalse(state.isDirty)
    }

    @Test
    fun updateStyle_inSdrMode_updatesSdrWorkingCopy() {
        viewModel.setMode(SubtitleStyleMode.SDR)
        val hdrBefore = viewModel.uiState.value.workingHdrStyle
        viewModel.updateStyle(viewModel.uiState.value.workingSdrStyle.copy(fontSize = 36))

        assertEquals(36, viewModel.uiState.value.workingSdrStyle.fontSize)
        // HDR untouched.
        assertEquals(hdrBefore, viewModel.uiState.value.workingHdrStyle)
        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun updateStyle_inHdrMode_updatesHdrWorkingCopy() {
        viewModel.setMode(SubtitleStyleMode.HDR)
        val sdrBefore = viewModel.uiState.value.workingSdrStyle
        viewModel.updateStyle(viewModel.uiState.value.workingHdrStyle.copy(fontSize = 44))

        assertEquals(44, viewModel.uiState.value.workingHdrStyle.fontSize)
        // SDR untouched.
        assertEquals(sdrBefore, viewModel.uiState.value.workingSdrStyle)
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

        coVerify { subtitleLanguageStore.setSubtitleStyle(newSdr.copy(applyCustomStyle = true)) }
        coVerify { subtitleLanguageStore.setHdrSubtitleStyle(newHdr.copy(applyCustomStyle = true)) }
        coVerify { subtitleLanguageStore.setHdrSubtitleStyleEnabled(true) }
        assertTrue(exited)
    }

    @Test
    fun reset_doesNotPersist() {
        viewModel.updateStyle(viewModel.uiState.value.workingSdrStyle.copy(fontSize = 99))
        viewModel.reset()
        coVerify(exactly = 0) { subtitleLanguageStore.setSubtitleStyle(any()) }
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
        val clearedVm = SubtitleTesterViewModel(engineFactory, subtitleLanguageStore, fontProvider, appContext)
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
    fun switchPreset_releasesAndRebuildsEngine() {
        // Engine created once on init.
        verify(exactly = 1) { engineFactory.create(PlayerType.EXO_PLAYER) }

        viewModel.switchPreset(SampleSubtitlePresets.ALL.last().id)
        testDispatcher.scheduler.advanceUntilIdle()

        // Old engine released and a fresh one created (re-attaches the surface).
        verify { fakeEngine.release() }
        verify(exactly = 2) { engineFactory.create(any()) }
        assertEquals(SampleSubtitlePresets.ALL.last().id, viewModel.uiState.value.samplePresetId)
        assertFalse(viewModel.uiState.value.isApplying)
    }

    @Test
    fun loop_seeksAndPlaysOnEnded() {
        // Give the engine a controllable playbackState so we can emit ENDED.
        val stateFlow = MutableStateFlow(EnginePlaybackState.READY)
        every { fakeEngine.playbackState } returns stateFlow
        viewModel.switchEngine(PlayerType.EXO_PLAYER)
        testDispatcher.scheduler.advanceUntilIdle()

        stateFlow.value = EnginePlaybackState.ENDED
        testDispatcher.scheduler.advanceUntilIdle()

        verify(atLeast = 1) { fakeEngine.seekTo(0) }
        verify(atLeast = 1) { fakeEngine.play() }
    }

    @Test
    fun onCleared_clearsActiveEngine() {
        val clearedVm = SubtitleTesterViewModel(engineFactory, subtitleLanguageStore, fontProvider, appContext)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(clearedVm.activeEngine.value)

        val onCleared = androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("onCleared")
        onCleared.isAccessible = true
        onCleared.invoke(clearedVm)

        assertNull(clearedVm.activeEngine.value)
    }

    @Test
    fun installUserFont_stampsActiveModeStyleWithInstalledFont() {
        val uri: android.net.Uri = mockk()
        val installedFont = com.raulshma.jellyplay.feature.player.video.subtitle.InstalledFont(
            file = java.io.File("/data/cache/subtitle-fonts/MyFont.ttf"),
            familyName = "MyFont",
        )
        coEvery { fontProvider.installUserFont(uri) } returns installedFont

        viewModel.setMode(SubtitleStyleMode.SDR)
        viewModel.installUserFont(uri)
        testDispatcher.scheduler.advanceUntilIdle()

        val style = viewModel.uiState.value.workingSdrStyle
        assertEquals(installedFont.file.absolutePath, style.fontFamilyPath)
        assertEquals("MyFont", style.fontFamilyName)
        // HDR copy untouched.
        assertNull(viewModel.uiState.value.workingHdrStyle.fontFamilyPath)
    }

    @Test
    fun installUserFont_nullInstallLeavesStyleUntouched() {
        val uri: android.net.Uri = mockk()
        coEvery { fontProvider.installUserFont(uri) } returns null

        val before = viewModel.uiState.value.workingSdrStyle
        viewModel.installUserFont(uri)
        testDispatcher.scheduler.advanceUntilIdle()
        val after = viewModel.uiState.value.workingSdrStyle

        assertEquals(before, after)
    }
}
