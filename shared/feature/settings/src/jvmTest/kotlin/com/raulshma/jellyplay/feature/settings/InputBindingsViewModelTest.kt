package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerBinding
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputKey
import com.raulshma.jellyplay.core.model.PlayerInputMap
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the binding editor's write commands against a mocked
 * [VideoPlayerStore] — mirroring [AppearanceSettingsViewModelHomeBackdropTest]'s
 * mockk style (the :core:testing MainDispatcherRule inlined). The store's
 * read-modify-write transforms are captured and applied to a test-side
 * stored map, so the duplicate-pattern write guard semantics stay visible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InputBindingsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var projections: PreferenceProjections
    private lateinit var videoPlayerStore: VideoPlayerStore
    private lateinit var editor: PreferencesEditor
    private lateinit var editScope: PreferencesEditScope
    private lateinit var playbackFlow: MutableStateFlow<PlaybackPreferences>

    /** Every `edit { }` block the VM hands the editor, in call order. */
    private val editBlocks = mutableListOf<suspend PreferencesEditScope.() -> Unit>()

    /** Test-side stand-in for the stored blob the transforms act on. */
    private var storedMap: PlayerInputMap = PlayerInputDefaults.defaultMap()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        storedMap = PlayerInputDefaults.defaultMap()
        playbackFlow = MutableStateFlow(PlaybackPreferences())
        projections = mockk(relaxed = true)
        videoPlayerStore = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        editScope = mockk(relaxed = true)
        every { projections.playbackPreferences } returns playbackFlow
        every { editScope.videoPlayer } returns videoPlayerStore
        every { editor.edit(capture(editBlocks)) } returns mockk<Job>()
        // The RMW transform acts on the test-side stored map, honoring the
        // store's actual contract (a no-op or duplicate candidate skips the
        // write).
        coEvery { videoPlayerStore.updateVideoInputBindings(any()) } coAnswers {
            val transform = firstArg<(PlayerInputMap) -> PlayerInputMap>()
            val candidate = transform(storedMap)
            if (candidate != storedMap && candidate.hasNoDuplicates()) storedMap = candidate
            Unit
        }
        coEvery { videoPlayerStore.setVideoInputBindings(any()) } answers {
            storedMap = firstArg()
            Unit
        }
        coEvery { videoPlayerStore.setVideoGestureMode(any()) } answers {
            storedMap = PlayerInputDefaults.applyGestureModePreset(storedMap, firstArg())
            Unit
        }
        editBlocks.clear()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun runEdits() {
        editBlocks.forEach { it.invoke(editScope) }
        editBlocks.clear()
    }

    @Test
    fun `addKeyBinding on a new combo appends an unbound custom row`() = runTest {
        val viewModel = InputBindingsViewModel(projections, editor)

        val result = viewModel.addKeyBinding(InputPattern.Key(PlayerInputKey.M, ctrl = true))
        runEdits()

        assertTrue(result.created)
        assertEquals("key.ctrl.m", result.bindingId)
        val added = storedMap.bindings.last()
        assertEquals("key.ctrl.m", added.id)
        assertEquals(InputPattern.Key(PlayerInputKey.M, ctrl = true), added.pattern)
        assertEquals(PlayerAction.NONE, added.action)
        assertTrue(added.enabled)
        assertTrue(storedMap.hasNoDuplicates())
    }

    @Test
    fun `addKeyBinding on an already-bound combo writes nothing and reports the row`() = runTest {
        val viewModel = InputBindingsViewModel(projections, editor)

        val result = viewModel.addKeyBinding(InputPattern.Key(PlayerInputKey.M))
        runEdits()

        assertFalse(result.created)
        assertEquals("key.m", result.bindingId)
        assertEquals(PlayerInputDefaults.defaultMap(), storedMap, "an existing pattern must not be touched")
        coVerify(exactly = 0) { videoPlayerStore.updateVideoInputBindings(any()) }
    }

    @Test
    fun `removeBinding drops only the named row`() = runTest {
        val viewModel = InputBindingsViewModel(projections, editor)

        viewModel.removeBinding(PlayerInputDefaults.ID_KEY_M)
        runEdits()

        assertEquals(
            PlayerInputDefaults.defaultMap().bindings.size - 1,
            storedMap.bindings.size,
        )
        assertTrue(storedMap.bindings.none { it.id == PlayerInputDefaults.ID_KEY_M })
    }

    @Test
    fun `resetBinding restores the row the parameterized reset would give`() = runTest {
        playbackFlow.value = PlaybackPreferences(videoGestureMode = GestureMode.TAP_ONLY)
        val viewModel = InputBindingsViewModel(projections, editor)
        storedMap = PlayerInputDefaults.applyGestureModePreset(storedMap, GestureMode.TAP_ONLY)
            .withBindingAction(PlayerInputDefaults.ID_SWIPE_VOLUME, PlayerAction.EXIT_PLAYER)

        viewModel.resetBinding(PlayerInputDefaults.ID_SWIPE_VOLUME)
        runEdits()

        val restored = storedMap.bindings.first { it.id == PlayerInputDefaults.ID_SWIPE_VOLUME }
        assertEquals(PlayerAction.SWIPE_VOLUME, restored.action)
        assertFalse(restored.enabled, "reset must restore the TAP_ONLY preset's flag, not the ALL one")
    }

    @Test
    fun `setGestureMode writes the atomic store preset`() = runTest {
        val viewModel = InputBindingsViewModel(projections, editor)

        viewModel.setGestureMode(GestureMode.TAP_ONLY)
        runEdits()

        coVerify(exactly = 1) { videoPlayerStore.setVideoGestureMode(GestureMode.TAP_ONLY) }
        assertFalse(
            storedMap.bindings.first { it.id == PlayerInputDefaults.ID_SWIPE_VOLUME }.enabled,
            "the preset must mass-disable the swipe tier through the same store call",
        )
    }

    @Test
    fun `resetToDefaults honors the stored behavior flags`() = runTest {
        playbackFlow.value = PlaybackPreferences(
            videoHoldSpeedEnabled = false,
            videoDoubleTapHoldSeekEnabled = false,
        )
        val viewModel = InputBindingsViewModel(projections, editor)

        viewModel.resetToDefaults()
        runEdits()

        assertFalse(
            storedMap.bindings.first { it.id == PlayerInputDefaults.ID_LONG_PRESS }.enabled,
            "hold-speed row must seed off the stored flag",
        )
        assertFalse(
            storedMap.bindings.first { it.id == PlayerInputDefaults.ID_DOUBLE_TAP_HOLD_LEFT }.enabled,
            "double-tap-hold row must seed off the stored flag",
        )
        coVerify(exactly = 1) { videoPlayerStore.setVideoInputBindings(any()) }
    }

    @Test
    fun `the duplicate-pattern write guard still blocks a candidate that would duplicate`() = runTest {
        val viewModel = InputBindingsViewModel(projections, editor)
        // A hand-edited blob carrying a duplicate pattern (only reachable
        // outside this editor): the guard must swallow the flip.
        storedMap = PlayerInputMap(
            PlayerInputDefaults.defaultMap().bindings +
                PlayerBinding("key.m.copy", InputPattern.Key(PlayerInputKey.M), PlayerAction.EXIT_PLAYER),
        )

        viewModel.setBindingEnabled(PlayerInputDefaults.ID_KEY_SPACE, false)
        runEdits()

        assertTrue(
            storedMap.bindings.any { it.id == PlayerInputDefaults.ID_KEY_SPACE && it.enabled },
            "the flip must be swallowed — the candidate preserves a duplicate",
        )
    }
}
