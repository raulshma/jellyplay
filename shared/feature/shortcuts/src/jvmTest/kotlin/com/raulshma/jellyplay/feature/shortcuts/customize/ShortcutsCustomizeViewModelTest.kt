package com.raulshma.jellyplay.feature.shortcuts.customize

import androidx.datastore.preferences.core.emptyPreferences
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.shortcuts.ShortcutsSlice
import com.raulshma.jellyplay.core.datastore.shortcuts.ShortcutsStore
import com.raulshma.jellyplay.core.model.ShortcutDefinition
import com.raulshma.jellyplay.core.model.ShortcutSurface
import com.raulshma.jellyplay.core.model.ShortcutTargetKind
import com.raulshma.jellyplay.core.ui.navigation.ShortcutAction
import com.raulshma.jellyplay.feature.shortcuts.DefaultShortcutCatalog
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The customize editor's ViewModel bootstrap: the adopt-then-prune
 * initialization (in that order, one coroutine — see [ShortcutsCustomizeViewModel.init])
 * and the capability threading — the picker's rows arrive in UiState already
 * filtered to the shell's executable-action set, so a desktop-shaped
 * capability set never offers [ShortcutAction.PLAY_ON].
 *
 * Harness: MockK over the store/editor seams (the home suite's
 * concrete-store precedent) with a [MutableStateFlow] slice standing in for
 * the store's read projection and the store's two write commands updating it,
 * so the bootstrap's effects are observable in order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShortcutsCustomizeViewModelTest {

    // MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module.
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var sliceFlow: MutableStateFlow<ShortcutsSlice>
    private lateinit var shortcutsStore: ShortcutsStore
    private lateinit var editor: PreferencesEditor

    private val catalog = DefaultShortcutCatalog()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        sliceFlow = MutableStateFlow(ShortcutsSlice())
        shortcutsStore = mockk {
            every { shortcuts } returns sliceFlow
            // The store's guard (write only while unconfigured) mimicked so the
            // adopt step's observable effect matches the real store's. (The
            // store's write commands return the DataStore edit's Preferences —
            // the answers mirror that shape.)
            coEvery { seedDefaultsIfUnconfigured(any(), any(), any()) } answers {
                val definitions = arg<List<ShortcutDefinition>>(0)
                val headerOrder = arg<List<String>>(1)
                val overflowOrder = arg<List<String>>(2)
                if (sliceFlow.value.isUnconfigured) {
                    sliceFlow.value = ShortcutsSlice(definitions, headerOrder, overflowOrder)
                }
                emptyPreferences()
            }
            coEvery { setShortcutsConfig(any(), any(), any()) } answers {
                val definitions = arg<List<ShortcutDefinition>>(0)
                val headerOrder = arg<List<String>>(1)
                val overflowOrder = arg<List<String>>(2)
                // The real store normalizes on every write: order lists
                // reference defined ids only.
                sliceFlow.value = ShortcutsSlice(
                    definitions,
                    headerOrder.filter { id -> definitions.any { it.id == id } },
                    overflowOrder.filter { id -> definitions.any { it.id == id } },
                )
                emptyPreferences()
            }
        }
        // editor.edit runs its block synchronously against an edit scope whose
        // `shortcuts` is the mocked store, and returns a completed Job for the
        // bootstrap's `.join()` ordering.
        val editScope = PreferencesEditScope(
            stores = mockk { every { shortcuts } returns shortcutsStore },
            appRuntimeState = mockk(),
        )
        editor = mockk {
            every { edit(any()) } answers {
                val block = firstArg<suspend PreferencesEditScope.() -> Unit>()
                runBlocking { block(editScope) }
                Job().apply { complete() }
            }
        }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(supportedActions: Set<ShortcutAction>): ShortcutsCustomizeViewModel =
        ShortcutsCustomizeViewModel(
            editor = editor,
            shortcutsStore = shortcutsStore,
            catalog = catalog,
            authRepository = mockk { every { currentUser } returns MutableStateFlow(null) },
            supportedActions = supportedActions,
        )

    // ------------------------------------------------------------------
    // Bootstrap: adopt THEN prune
    // ------------------------------------------------------------------

    @Test
    fun `init adopts the shipped layout for an unconfigured user`() = runTest {
        assertTrue(sliceFlow.value.isUnconfigured)
        buildViewModel(ALL_SHELL_ACTIONS)
        runCurrent()
        val slice = sliceFlow.value
        assertFalse(slice.isUnconfigured)
        // The shipped overflow defaults, adopted as the stored config (the
        // header ships empty).
        assertEquals(
            listOf(
                "default:surprise_me",
                "default:SyncPlay",
                "default:Downloads",
                "default:toggle_offline",
                "default:play_on",
                "default:Shortcuts",
                "default:Settings",
            ),
            slice.overflowOrder,
        )
        assertTrue(slice.headerOrder.isEmpty())
    }

    @Test
    fun `prune runs after adopt — a configured slice keeps stale-target drops, an unconfigured one keeps its adoption`() = runTest {
        // Unconfigured lane: adopt writes the shipped defaults, the prune then
        // runs against them — and drops the PLAY_ON default this shell cannot
        // execute (the capability-aware prune reads the POST-adopt slice).
        buildViewModel(setOf(ShortcutAction.SURPRISE_ME, ShortcutAction.TOGGLE_OFFLINE))
        runCurrent()
        val adopted = sliceFlow.value
        assertFalse(adopted.isUnconfigured)
        assertEquals(
            listOf(
                "default:surprise_me",
                "default:SyncPlay",
                "default:Downloads",
                "default:toggle_offline",
                "default:Shortcuts",
                "default:Settings",
            ),
            adopted.definitions.map { it.id },
        )
        // The invocation ORDER on the store mock pins the sequence:
        // seedDefaultsIfUnconfigured (adopt) before any setShortcutsConfig
        // (prune) — the bootstrap's documented order, not a race.
        io.mockk.coVerifyOrder {
            shortcutsStore.seedDefaultsIfUnconfigured(any(), any(), any())
        }

        // Configured lane: adopt is a no-op (store guard), the prune still
        // drops the PLAY_ON pin this shell cannot execute.
        sliceFlow.value = ShortcutsSlice(
            definitions = listOf(
                ShortcutDefinition("s", ShortcutTargetKind.ACTION, ShortcutAction.SURPRISE_ME.id),
                ShortcutDefinition("p", ShortcutTargetKind.ACTION, ShortcutAction.PLAY_ON.id),
                ShortcutDefinition("a", ShortcutTargetKind.SCREEN, "Downloads"),
            ),
            headerOrder = emptyList(),
            overflowOrder = listOf("s", "p", "a"),
        )
        buildViewModel(setOf(ShortcutAction.SURPRISE_ME, ShortcutAction.TOGGLE_OFFLINE))
        runCurrent()
        val slice = sliceFlow.value
        // PLAY_ON pruned; screens and supported actions stay; orders normalize
        // around the kept ids.
        assertEquals(listOf("s", "a"), slice.definitions.map { it.id })
        assertEquals(listOf("s", "a"), slice.overflowOrder)
        io.mockk.coVerifyOrder {
            shortcutsStore.seedDefaultsIfUnconfigured(any(), any(), any())
            shortcutsStore.setShortcutsConfig(any(), any(), any())
        }
    }

    // ------------------------------------------------------------------
    // Capability threading: the picker never offers an unsupported action
    // ------------------------------------------------------------------

    @Test
    fun `desktop capability set never offers PLAY_ON in the picker`() = runTest {
        val vm = buildViewModel(setOf(ShortcutAction.SURPRISE_ME, ShortcutAction.TOGGLE_OFFLINE))
        val state = vm.uiState.first { it.pickerByCategory.isNotEmpty() }
        val offeredActions = state.pickerByCategory
            .values.flatten()
            .filter { it.entry.isAction }
            .map { it.entry.targetId }
        // The desktop-shaped set keeps its armed actions and never offers the
        // cast arm — the row would be a silent dead-end pill.
        assertTrue(ShortcutAction.SURPRISE_ME.id in offeredActions)
        assertTrue(ShortcutAction.TOGGLE_OFFLINE.id in offeredActions)
        assertFalse(ShortcutAction.PLAY_ON.id in offeredActions)
        // Screens are never capability-gated.
        assertTrue(state.pickerByCategory.values.flatten().any { !it.entry.isAction })
    }

    @Test
    fun `the permissive set offers every action`() = runTest {
        val vm = buildViewModel(ALL_SHELL_ACTIONS)
        val state = vm.uiState.first { it.pickerByCategory.isNotEmpty() }
        val offeredActions = state.pickerByCategory
            .values.flatten()
            .filter { it.entry.isAction }
            .map { it.entry.targetId }
        assertEquals(ShortcutAction.entries.map { it.id }, offeredActions)
    }

    // ------------------------------------------------------------------
    // The per-surface view rides UiState
    // ------------------------------------------------------------------

    @Test
    fun `uiState exposes the per-surface views the tabs render`() = runTest {
        sliceFlow.value = ShortcutsSlice(
            definitions = listOf(
                ShortcutDefinition("a", ShortcutTargetKind.SCREEN, "Downloads"),
                ShortcutDefinition("b", ShortcutTargetKind.SCREEN, "Favorites"),
            ),
            headerOrder = listOf("b"),
            overflowOrder = listOf("a"),
        )
        val vm = buildViewModel(ALL_SHELL_ACTIONS)
        val state = vm.uiState.first { it.sections.header.pinnedRows.isNotEmpty() }
        assertEquals(listOf("b"), state.sections.header.pinnedRows.map { it.definition.id })
        assertEquals(listOf("a"), state.sections.overflow.pinnedRows.map { it.definition.id })
        assertEquals(4, state.sections.header.capacity)
        assertEquals(12, state.sections.overflow.capacity)
        assertFalse(state.sections.overflow.full)
        // The tab switch is one lookup, both surfaces alive in one state.
        assertEquals(state.sections.header, state.sections[ShortcutSurface.HEADER])
        assertEquals(state.sections.overflow, state.sections[ShortcutSurface.OVERFLOW])
    }
}
