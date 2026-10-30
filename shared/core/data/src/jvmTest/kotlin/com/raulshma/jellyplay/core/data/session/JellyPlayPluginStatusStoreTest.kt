package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.JellyPlayContractMismatch
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilities
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilitiesRoutes
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins [JellyPlayPluginStatusStore]'s verdict semantics — in particular the
 * contract-mismatch side channel: a refused contractVersion fails closed
 * (status UNAVAILABLE, capabilities/features cleared) exactly like an absent
 * plugin, but is distinguishable on [JellyPlayPluginStatusStore.contractMismatch]
 * so the UI can tell "update one side" from "plugin absent / network error".
 * The store and registry are the REAL session machinery over a mockk
 * capabilities route (the probe verdict's dial) — the JellyPlayCapabilityGateTest
 * harness shape.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JellyPlayPluginStatusStoreTest {

    private lateinit var capabilitiesApi: JellyPlayCapabilitiesRoutes
    private lateinit var transitions: MutableSharedFlow<HomeSessionTransition>

    @BeforeTest
    fun setUp() {
        capabilitiesApi = mockk()
        transitions = MutableSharedFlow(replay = 1)
    }

    private fun TestScope.buildStore(): JellyPlayPluginStatusStore {
        val identityProvider = object : SessionIdentityProvider {
            override val transitions = this@JellyPlayPluginStatusStoreTest.transitions
            override suspend fun currentIdentity(): SessionIdentity? = null
            override fun currentIdentitySnapshot(): SessionIdentity? = null
            override suspend fun cacheIdentity(): CacheIdentity = CacheIdentity.UNKNOWN
            override fun cacheIdentitySnapshot(): CacheIdentity = CacheIdentity.UNKNOWN
        }
        return JellyPlayPluginStatusStore(capabilitiesApi, SessionCacheRegistry(identityProvider, backgroundScope))
    }

    @Test
    fun an_in_range_contract_publishes_available_with_the_payload() = runTest {
        coEvery { capabilitiesApi.getCapabilities() } returns Result.success(
            JellyPlayCapabilities(
                contractVersion = 1,
                pluginVersion = "0.11.6.0",
                features = listOf(JellyPlayPluginFeatures.SettingsSync, JellyPlayPluginFeatures.Transcodes),
            ),
        )
        val store = buildStore()

        store.refresh()

        assertEquals(JellyPlayPluginStatus.AVAILABLE, store.status.value)
        assertEquals(1, store.capabilities.value?.contractVersion)
        assertEquals(setOf(JellyPlayPluginFeatures.SettingsSync, JellyPlayPluginFeatures.Transcodes), store.features.value)
        assertEquals("0.11.6.0", store.servedPluginVersion.value)
        assertNull(store.contractMismatch.value)
    }

    @Test
    fun an_out_of_range_contract_fails_closed_but_publishes_the_mismatch() = runTest {
        coEvery { capabilitiesApi.getCapabilities() } returns Result.success(
            JellyPlayCapabilities(contractVersion = 2, pluginVersion = "0.12.0.0", features = listOf("settings-sync")),
        )
        val store = buildStore()

        store.refresh()

        // Same fail-closed shape as an absent plugin...
        assertEquals(JellyPlayPluginStatus.UNAVAILABLE, store.status.value)
        assertNull(store.capabilities.value)
        assertEquals(emptySet(), store.features.value)
        // ...but the drift is the store's own diagnostic.
        assertEquals(
            JellyPlayContractMismatch(servedContractVersion = 2, supportedRange = 1..1, pluginVersion = "0.12.0.0"),
            store.contractMismatch.value,
        )
        assertEquals("0.12.0.0", store.servedPluginVersion.value)
    }

    @Test
    fun a_failed_probe_is_unavailable_without_a_mismatch() = runTest {
        coEvery { capabilitiesApi.getCapabilities() } returns Result.failure(RuntimeException("plugin absent (404)"))
        val store = buildStore()

        store.refresh()

        assertEquals(JellyPlayPluginStatus.UNAVAILABLE, store.status.value)
        assertNull(store.contractMismatch.value)
        assertNull(store.servedPluginVersion.value)
    }

    @Test
    fun the_mismatch_follows_the_latest_probe_in_both_directions() = runTest {
        val store = buildStore()
        coEvery { capabilitiesApi.getCapabilities() } returns Result.success(
            JellyPlayCapabilities(contractVersion = 2, pluginVersion = "0.12.0.0"),
        )
        store.refresh()
        assertEquals(JellyPlayPluginStatus.UNAVAILABLE, store.status.value)

        // The plugin catches up mid-session — the stale mismatch clears on
        // the next refresh (the settings screen's manual re-probe).
        coEvery { capabilitiesApi.getCapabilities() } returns Result.success(
            JellyPlayCapabilities(contractVersion = 1, pluginVersion = "0.12.1.0", features = listOf("settings-sync")),
        )
        store.refresh()
        assertEquals(JellyPlayPluginStatus.AVAILABLE, store.status.value)
        assertNull(store.contractMismatch.value)

        // And a later plugin regression re-publishes it.
        coEvery { capabilitiesApi.getCapabilities() } returns Result.success(
            JellyPlayCapabilities(contractVersion = 3, pluginVersion = "0.13.0.0"),
        )
        store.refresh()
        assertEquals(JellyPlayPluginStatus.UNAVAILABLE, store.status.value)
        assertEquals(3, store.contractMismatch.value?.servedContractVersion)
    }

    @Test
    fun an_identity_reset_clears_the_verdict_and_the_mismatch() = runTest {
        coEvery { capabilitiesApi.getCapabilities() } returns Result.success(
            JellyPlayCapabilities(contractVersion = 2, pluginVersion = "0.12.0.0"),
        )
        val store = buildStore()
        store.refresh()
        assertEquals(JellyPlayPluginStatus.UNAVAILABLE, store.status.value)

        transitions.tryEmit(HomeSessionTransition.UserSwitched(SessionIdentity("srv-1", "user-2")))
        runCurrent() // the registry's collector runs the store's reset action

        assertEquals(JellyPlayPluginStatus.UNKNOWN, store.status.value)
        assertNull(store.contractMismatch.value)
        assertNull(store.servedPluginVersion.value)
    }
}
