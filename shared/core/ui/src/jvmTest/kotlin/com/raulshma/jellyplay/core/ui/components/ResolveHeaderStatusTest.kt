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
 *  - ServerUnreachable requires the health to be [ServerHealth.Unreachable]
 *    on a usable network (Online or Local) — a probed-unreachable server on
 *    a Local-only network is a dead server or portal, not a working offline
 *    setup, so it outranks the calm Local wifi-off; only a fully-gone
 *    network (Offline) beats it, since health can no longer be measured;
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
    fun localNetworkWithHealthyServer_staysLocal() {
        // The working-LAN shape: no internet, but the server answers — the
        // calm wifi-off icon, not the error server icon.
        assertEquals(
            HeaderStatus.Local,
            resolveHeaderStatus(
                isLoading = false,
                hasError = false,
                networkStatus = NetworkStatus.Local,
                serverHealth = ServerHealth.Healthy(50L),
            ),
        )
    }

    @Test
    fun localNetworkWithUnreachableServer_yieldsServerUnreachable() {
        // A LAN-only network that fails the health probe is a dead server or
        // portal, not a working offline setup — the error-tinted server icon
        // outranks the calm wifi-off.
        assertEquals(
            HeaderStatus.ServerUnreachable,
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
