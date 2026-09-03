package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.ServerHealth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the pure priority reducer [resolveHeaderStatus] that picks the single
 * header status chip from (loading, error, connectivity, server health):
 *
 *  - priority is **Offline > ServerUnreachable > Local > Error > Loading >
 *    None**, regardless of how many lower-priority signals are also set;
 *  - ServerUnreachable requires connectivity to be Online AND the health to
 *    be [ServerHealth.Unreachable] — an unreachable server while the device is
 *    on a Local-only network degrades to Local (health is indeterminable);
 *  - all health states other than Unreachable never alter the result;
 *  - the default `serverHealth` parameter is Unknown, so plain call sites get
 *    the loading/error/none ladder.
 */
class ResolveHeaderStatusTest {

    @Test
    fun offline_beatsEveryOtherSignal() {
        listOf(
            ServerHealth.Unknown,
            ServerHealth.Unreachable,
            ServerHealth.Healthy(5),
        ).forEach { health ->
            assertEquals(
                HeaderStatus.Offline,
                resolveHeaderStatus(
                    isLoading = true,
                    hasError = true,
                    networkStatus = NetworkStatus.Offline,
                    serverHealth = health,
                ),
                "offline must win over loading/error/health=$health",
            )
        }
    }

    @Test
    fun onlinePlusUnreachableServer_yieldsServerUnreachable() {
        assertEquals(
            HeaderStatus.ServerUnreachable,
            resolveHeaderStatus(
                isLoading = true,
                hasError = true,
                networkStatus = NetworkStatus.Online,
                serverHealth = ServerHealth.Unreachable,
            ),
        )
    }

    @Test
    fun localNetwork_beatsErrorAndLoading() {
        assertEquals(
            HeaderStatus.Local,
            resolveHeaderStatus(
                isLoading = true,
                hasError = true,
                networkStatus = NetworkStatus.Local,
            ),
        )
    }

    @Test
    fun localNetworkWithUnreachableServer_staysLocal() {
        // Server health cannot be measured without internet; Local wins.
        assertEquals(
            HeaderStatus.Local,
            resolveHeaderStatus(
                isLoading = false,
                hasError = false,
                networkStatus = NetworkStatus.Local,
                serverHealth = ServerHealth.Unreachable,
            ),
        )
    }

    @Test
    fun error_beatsLoading() {
        assertEquals(
            HeaderStatus.Error,
            resolveHeaderStatus(
                isLoading = true,
                hasError = true,
                networkStatus = NetworkStatus.Online,
            ),
        )
    }

    @Test
    fun loading_yieldsLoadingWhenNothingWorse() {
        assertEquals(
            HeaderStatus.Loading,
            resolveHeaderStatus(
                isLoading = true,
                hasError = false,
                networkStatus = NetworkStatus.Online,
            ),
        )
    }

    @Test
    fun nothingSet_yieldsNone() {
        assertEquals(
            HeaderStatus.None,
            resolveHeaderStatus(
                isLoading = false,
                hasError = false,
                networkStatus = NetworkStatus.Online,
            ),
        )
    }

    @Test
    fun healthyOrCheckingServer_neverAltersTheLadder() {
        listOf(ServerHealth.Unknown, ServerHealth.Checking, ServerHealth.Healthy(42L)).forEach { health ->
            assertEquals(
                HeaderStatus.None,
                resolveHeaderStatus(false, false, NetworkStatus.Online, health),
                "health=$health must not surface as a status on its own",
            )
        }
    }

    @Test
    fun defaultServerHealth_isUnknown() {
        // Compile-time pin of the default parameter: the two-arg-shape call
        // must behave identically to an explicit Unknown.
        assertEquals(
            resolveHeaderStatus(false, false, NetworkStatus.Online, ServerHealth.Unknown),
            resolveHeaderStatus(false, false, NetworkStatus.Online),
        )
    }
}
