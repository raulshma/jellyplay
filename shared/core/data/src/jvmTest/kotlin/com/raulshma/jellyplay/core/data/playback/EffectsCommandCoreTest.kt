package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Choreography pin for [EffectsCommandCore] — the shared command half the
 * two feature effects controllers ride. Pins three things:
 *
 *  1. the leg ordering per declared [EffectsCommandCore.Order]:
 *     APPLY_FIRST interleaves apply → state write, STATE_FIRST
 *     interleaves state write → apply — the divergence is the contract,
 *     not an accident (see the core KDoc for why neither adapter may
 *     silently adopt the other's order);
 *  2. the persist leg runs LAST and launched: it executes on the injected
 *     scope, never inline in the command, and the state it can read
 *     [EffectsCommandCore.state] from is already post-write;
 *  3. the state-mirror semantics: [EffectsCommandCore.state] is the owned
 *     slice (read-only outside the core) and [EffectsCommandCore.updateState]
 *     is the bare write the mirror collectors / seeding paths use — no
 *     apply leg, no persist leg, no launch.
 *
 * Uses a tiny recording state (a list-backed data class) rather than either
 * feature's `AudioEffectsState`: the core is generic and must stay pinned
 * to the choreography, not to one slice's vocabulary.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EffectsCommandCoreTest {

    /** Minimal slice: one Int cell so writes are assertable and diffable. */
    private data class Slice(val value: Int = 0, val tag: String = "")

    /** Chronological log shared by all three legs' recording lambdas. */
    private val log = mutableListOf<String>()

    private val testScope = TestScope(UnconfinedTestDispatcher())

    private lateinit var core: EffectsCommandCore<Slice>

    @BeforeTest
    fun setUp() {
        log.clear()
    }

    private fun core(order: EffectsCommandCore.Order) =
        EffectsCommandCore(
            initialState = Slice(),
            scope = testScope,
            order = order,
        )

    // ── Ordering: the declared divergence is the contract ──────────────────

    @Test
    fun applyFirst_runsApplyBeforeTheStateWrite_beforeLaunchingPersist() {
        core = core(EffectsCommandCore.Order.APPLY_FIRST)

        core.applyAndPersist(
            apply = { log += "apply" },
            update = { log += "write"; it.copy(value = 7) },
            persist = { log += "persist" },
        )

        assertEquals(listOf("apply", "write", "persist"), log)
        assertEquals(7, core.state.value.value)
    }

    @Test
    fun stateFirst_runsTheStateWriteBeforeApply_beforeLaunchingPersist() {
        core = core(EffectsCommandCore.Order.STATE_FIRST)

        core.applyAndPersist(
            apply = { log += "apply" },
            update = { log += "write"; it.copy(value = 7) },
            persist = { log += "persist" },
        )

        assertEquals(listOf("write", "apply", "persist"), log)
        assertEquals(7, core.state.value.value)
    }

    @Test
    fun applyFirst_withoutAnUpdateLeg_stillAppliesThenPersists() {
        // The common no-mirror case (player-audio's toggles): `update` is
        // null and the two remaining legs keep their order.
        core = core(EffectsCommandCore.Order.APPLY_FIRST)

        core.applyAndPersist(
            apply = { log += "apply" },
            persist = { log += "persist" },
        )

        assertEquals(listOf("apply", "persist"), log)
        assertEquals(Slice(), core.state.value, "no update leg, no state change")
    }

    // ── Persist leg: launched on the scope, last, reading post-write state ──

    @Test
    fun persist_readsStateThatIsAlreadyWritten() {
        core = core(EffectsCommandCore.Order.STATE_FIRST)

        core.applyAndPersist(
            apply = {},
            update = { it.copy(tag = "written") },
            persist = { assertEquals("written", core.state.value.tag, "persist must see the post-write slice") },
        )
    }

    @Test
    fun consecutiveCommands_keepTheirRelativeLegOrder() {
        core = core(EffectsCommandCore.Order.APPLY_FIRST)

        core.applyAndPersist(apply = { log += "apply1" }, persist = { log += "persist1" })
        core.applyAndPersist(
            apply = { log += "apply2" },
            update = { it.copy(value = 2) },
            persist = { log += "persist2" },
        )

        assertEquals(listOf("apply1", "persist1", "apply2", "persist2"), log)
        assertEquals(2, core.state.value.value)
    }

    // ── State ownership + the bare mirror write ────────────────────────────

    @Test
    fun updateState_writesTheSlice_withoutApplyOrPersistLegs() {
        core = core(EffectsCommandCore.Order.APPLY_FIRST)

        core.updateState { it.copy(value = 5, tag = "mirror") }

        assertEquals(Slice(value = 5, tag = "mirror"), core.state.value)
    }

    @Test
    fun stateCarriesTheInitialSliceUntilWritten() {
        val seeded = Slice(value = 42, tag = "initial")
        val seededCore = EffectsCommandCore(
            initialState = seeded,
            scope = testScope,
            order = EffectsCommandCore.Order.STATE_FIRST,
        )

        assertEquals(seeded, seededCore.state.value)
    }
}
