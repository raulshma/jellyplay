package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilities
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilitiesRoutes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the plugin capability gate's three-piece discipline (the transcodes /
 * analytics / dashboard / settings ViewModels' shared seam):
 *
 *  - **probe-once while UNKNOWN** — [JellyPlayCapabilityGate.arm] refreshes
 *    the store exactly when no verdict exists; an AVAILABLE/UNAVAILABLE
 *    answer stands for the session (one probe per visit against a stock
 *    server);
 *  - **re-arm on identity reset** — a [SessionCacheRegistry] transition
 *    flips the store back to UNKNOWN and [JellyPlayCapabilityGate.state]
 *    follows it to [JellyPlayCapabilityState.Unknown]; the next [arm] (the
 *    next visit) probes again;
 *  - **gate reads the store** — [state] is the status ⊕ availability combine
 *    (the toggle-aware seam when wired, the probe-only combine when not),
 *    re-emitting on toggle flips without re-probing.
 *
 * The store and the registry are the REAL session machinery over fakes: the
 * capabilities route is a mockk (the probe verdict's dial) and the toggle
 * seam a mocked [JellyPlayFeatureGate] over a controllable flow (its own
 * DataStore-backed behavior is its own suite's business) — the harness shape
 * the transcodes/analytics VM suites use. Everything rides the test
 * scheduler (`backgroundScope`, the SessionCacheRegistryTest pattern) and
 * each test holds ONE [JellyPlayCapabilityGate.state] flow — each call
 * builds a fresh pipeline whose first emission still needs a dispatch, so a
 * re-invocation per read would read the initial [JellyPlayCapabilityState.Unknown]
 * forever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JellyPlayCapabilityGateTest {

    private lateinit var capabilitiesApi: JellyPlayCapabilitiesRoutes
    private lateinit var toggleFlow: MutableStateFlow<Boolean>
    private lateinit var featureGate: JellyPlayFeatureGate
    private lateinit var statusStore: JellyPlayPluginStatusStore

    /** The identity stream the registry reacts to — tests push resets onto it. */
    private lateinit var transitions: MutableSharedFlow<HomeSessionTransition>

    private fun capabilities(vararg features: String) = Result.success(
        JellyPlayCapabilities(contractVersion = 1, pluginVersion = "1.0.0", features = features.toList()),
    )

    @BeforeTest
    fun setUp() {
        capabilitiesApi = mockk()
        toggleFlow = MutableStateFlow(true)
        transitions = MutableSharedFlow(replay = 1)
    }

    /**
     * The harness, ALL on the test scheduler (the SessionCacheRegistryTest
     * pattern — no real-thread scopes): store + registry + gate + its ONE
     * derived verdict flow. [runCurrent] settles the registry's collector and
     * the combine's initial verdict before the first assert.
     */
    private fun TestScope.buildGate(wiredToggle: Boolean = true): JellyPlayCapabilityGateHarness {
        val identityProvider = object : SessionIdentityProvider {
            override val transitions = this@JellyPlayCapabilityGateTest.transitions
            override suspend fun currentIdentity(): SessionIdentity? = null
            override fun currentIdentitySnapshot(): SessionIdentity? = null
            override suspend fun cacheIdentity(): CacheIdentity = CacheIdentity.UNKNOWN
            override fun cacheIdentitySnapshot(): CacheIdentity = CacheIdentity.UNKNOWN
        }
        statusStore = JellyPlayPluginStatusStore(capabilitiesApi, SessionCacheRegistry(identityProvider, backgroundScope))
        // A FAITHFUL toggle seam: the real [JellyPlayFeatureGate.isAvailable]
        // folds the probe registry in (false until the store is AVAILABLE),
        // so the mock must answer from the live store too — a constant-true
        // stub would make the tri-state combine read Available pre-probe,
        // a contract the real seam cannot produce.
        val isAvailableFlow = kotlinx.coroutines.flow.combine(
            statusStore.status,
            statusStore.features,
            toggleFlow,
        ) { status, features, toggle ->
            toggle && status == JellyPlayPluginStatus.AVAILABLE &&
                JellyPlayPluginFeatures.Transcodes in features
        }.stateIn(backgroundScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)
        featureGate = mockk {
            every { isAvailable(JellyPlayPluginFeatures.Transcodes) } returns isAvailableFlow
        }
        val gate = JellyPlayCapabilityGate(statusStore, backgroundScope)
        val state = gate.state(
            JellyPlayPluginFeatures.Transcodes,
            featureGate.takeIf { wiredToggle },
        )
        runCurrent()
        return JellyPlayCapabilityGateHarness(gate, state)
    }

    private class JellyPlayCapabilityGateHarness(
        val gate: JellyPlayCapabilityGate,
        val state: kotlinx.coroutines.flow.StateFlow<JellyPlayCapabilityState>,
    )

    // ── probe-once while UNKNOWN ──

    @Test
    fun arm_probes_exactly_once_while_unknown_and_never_again_once_answered() = runTest {
        coEvery { capabilitiesApi.getCapabilities() } returns capabilities(JellyPlayPluginFeatures.Transcodes)
        val harness = buildGate()
        val gate = harness.gate
        val state = harness.state
        assertEquals(JellyPlayCapabilityState.Unknown, state.value)

        gate.arm()
        runCurrent() // the arm's launch is queued on the test dispatcher

        assertEquals(JellyPlayCapabilityState.Available, state.value)
        coVerify(exactly = 1) { capabilitiesApi.getCapabilities() }

        // An AVAILABLE verdict stands: the second arm (the manual refresh
        // path, or a re-entry into START) is a no-op — one probe per visit.
        gate.arm()
        runCurrent() // a no-op arm — nothing re-probes
        coVerify(exactly = 1) { capabilitiesApi.getCapabilities() }
    }

    @Test
    fun an_unavailable_verdict_also_stands_stock_server_eats_exactly_one_probe() = runTest {
        coEvery { capabilitiesApi.getCapabilities() } returns Result.failure(RuntimeException("plugin absent"))
        val harness = buildGate()
        val gate = harness.gate
        val state = harness.state

        gate.arm()
        runCurrent()
        gate.arm()
        runCurrent()

        coVerify(exactly = 1) { capabilitiesApi.getCapabilities() }
        assertEquals(JellyPlayCapabilityState.Unavailable, state.value)
    }

    // ── re-arm on identity reset ──

    @Test
    fun an_identity_reset_returns_the_gate_to_unknown_and_the_next_arm_reprobes() = runTest {
        coEvery { capabilitiesApi.getCapabilities() } returns capabilities(JellyPlayPluginFeatures.Transcodes)
        val harness = buildGate()
        val gate = harness.gate
        val state = harness.state

        gate.arm()
        runCurrent()
        assertEquals(JellyPlayCapabilityState.Available, state.value)

        // The session machinery's reset (user switch) flips the store back to
        // UNKNOWN — the gate follows reactively, no probe is issued.
        transitions.tryEmit(HomeSessionTransition.UserSwitched(SessionIdentity("srv-1", "user-2")))
        runCurrent() // the registry's collector runs the store's reset action
        assertEquals(JellyPlayPluginStatus.UNKNOWN, statusStore.status.value)
        runCurrent() // the state pipeline follows the reset
        assertEquals(JellyPlayCapabilityState.Unknown, state.value)
        coVerify(exactly = 1) { capabilitiesApi.getCapabilities() }

        // The next visit's arm re-probes — the re-arm contract.
        gate.arm()
        runCurrent()
        coVerify(exactly = 2) { capabilitiesApi.getCapabilities() }
        assertEquals(JellyPlayCapabilityState.Available, state.value)
    }

    // ── gate reads the store ──

    @Test
    fun available_requires_the_feature_exposed_and_the_toggle_on() = runTest {
        coEvery { capabilitiesApi.getCapabilities() } returns capabilities("settings-sync")
        val harness = buildGate()
        val gate = harness.gate
        val state = harness.state

        gate.arm()
        runCurrent()

        // Probe AVAILABLE but the `transcodes` key absent from the registry.
        assertEquals(JellyPlayCapabilityState.Unavailable, state.value)

        // The plugin exposes the key — light-up, without a re-probe.
        coEvery { capabilitiesApi.getCapabilities() } returns capabilities("settings-sync", JellyPlayPluginFeatures.Transcodes)
        statusStore.refresh()
        runCurrent() // the derived availability + the verdict re-emit
        assertEquals(JellyPlayCapabilityState.Available, state.value)
        coVerify(exactly = 2) { capabilitiesApi.getCapabilities() }

        // The user's toggle flips off — the gate closes reactively, still
        // without a probe.
        toggleFlow.value = false
        runCurrent()
        assertEquals(JellyPlayCapabilityState.Unavailable, state.value)
        coVerify(exactly = 2) { capabilitiesApi.getCapabilities() }
    }

    @Test
    fun the_unwired_toggle_seam_falls_back_to_the_probe_only_combine() = runTest {
        coEvery { capabilitiesApi.getCapabilities() } returns capabilities(JellyPlayPluginFeatures.Transcodes)
        val harness = buildGate(wiredToggle = false)
        val gate = harness.gate
        val state = harness.state

        gate.arm()
        runCurrent()

        // Registry has the key → open, even though the mocked toggle seam
        // was never consulted (direct-construction harnesses' shape).
        assertEquals(JellyPlayCapabilityState.Available, state.value)
    }
}
