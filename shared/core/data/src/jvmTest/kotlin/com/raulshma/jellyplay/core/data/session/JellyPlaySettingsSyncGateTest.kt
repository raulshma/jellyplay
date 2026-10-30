package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilities
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilitiesRoutes
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the meta-keyed settings-sync gate's ONE predicate
 * ([JellyPlayPluginStatusStore.settingsSyncGateOpen] — the sync ViewModel,
 * the restore wizard's server arm and the safety-snapshot capture's shared
 * seam): the gate opens ONLY on probe AVAILABLE **and** the `settings-sync`
 * key in the registry — each half missing keeps it closed, and the pre-probe
 * UNKNOWN keeps it closed. There is no toggle half to pin: meta keys carry
 * no user toggle (the probe-only rule's whole point — the predicate's KDoc
 * carries it once); the toggleable-feature surfaces' gate is
 * [JellyPlayCapabilityGate]'s business, its own suite's.
 *
 * The REAL store over a mockk capabilities route (the probe verdict's dial)
 * — the [JellyPlayCapabilityGateTest] harness shape minus the toggle seam,
 * everything riding the test scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JellyPlaySettingsSyncGateTest {

    private val capabilitiesApi: JellyPlayCapabilitiesRoutes = mockk()

    /** The REAL store, its registry collector on the test scheduler. */
    private fun TestScope.buildStore(): JellyPlayPluginStatusStore {
        val identityProvider = object : SessionIdentityProvider {
            override val transitions = MutableSharedFlow<HomeSessionTransition>()
            override suspend fun currentIdentity(): SessionIdentity? = null
            override fun currentIdentitySnapshot(): SessionIdentity? = null
            override suspend fun cacheIdentity(): CacheIdentity = CacheIdentity.UNKNOWN
            override fun cacheIdentitySnapshot(): CacheIdentity = CacheIdentity.UNKNOWN
        }
        return JellyPlayPluginStatusStore(capabilitiesApi, SessionCacheRegistry(identityProvider, backgroundScope))
    }

    private fun capabilities(vararg features: String) = Result.success(
        JellyPlayCapabilities(contractVersion = 1, pluginVersion = "1.0.0", features = features.toList()),
    )

    // ── open: both halves present ──

    @Test
    fun open_when_probe_available_and_the_settings_sync_key_in_the_registry() = runTest {
        coEvery { capabilitiesApi.getCapabilities() } returns capabilities(JellyPlayPluginFeatures.SettingsSync)
        val store = buildStore()

        store.refresh()

        assertTrue(store.settingsSyncGateOpen())
    }

    // ── closed: either half missing ──

    @Test
    fun closed_when_probe_available_but_the_meta_key_absent_from_the_registry() = runTest {
        coEvery { capabilitiesApi.getCapabilities() } returns capabilities(JellyPlayPluginFeatures.Transcodes)
        val store = buildStore()

        store.refresh()

        assertFalse(store.settingsSyncGateOpen())
    }

    @Test
    fun closed_when_the_probe_failed_unavailable() = runTest {
        coEvery { capabilitiesApi.getCapabilities() } returns Result.failure(RuntimeException("plugin absent"))
        val store = buildStore()

        store.refresh()

        assertFalse(store.settingsSyncGateOpen())
    }

    // ── closed: no verdict yet ──

    @Test
    fun closed_while_the_probe_is_still_unknown() = runTest {
        coEvery { capabilitiesApi.getCapabilities() } returns capabilities(JellyPlayPluginFeatures.SettingsSync)
        val store = buildStore()

        // No refresh — the store's pre-probe UNKNOWN start state stands.
        assertFalse(store.settingsSyncGateOpen())
    }
}
