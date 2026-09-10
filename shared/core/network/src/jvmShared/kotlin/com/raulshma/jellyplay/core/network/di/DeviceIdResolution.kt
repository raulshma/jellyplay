package com.raulshma.jellyplay.core.network.di

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bound on the in-definition `ensureDeviceId()`
 * fallback. Generous on purpose — the identity prewarms (JellyPlayApplication on
 * Android, Main.kt on desktop) already bound their own attempt at 5 s, so the
 * bound below only fires on a first resolution racing that prewarm, and by the
 * time it gives up the DataStore has had 10 s to land one local file read. The
 * random-UUID fallback is the path of last resort, not something a healthy
 * install should ever see.
 */
private const val DEVICE_ID_RESOLVE_TIMEOUT_MS = 10_000L

/**
 * Resolves the SDK device id from the app's persistent DataStore UUID so the
 * REST/session API, the WebSocket connection, and the server all agree on one
 * identity per install.
 *
 * [ServerIdentityStore.identity] is a StateFlow shared with
 * `SharingStarted.Eagerly`, so after the very first process launch its current
 * value is the persisted UUID held in memory. Reading `.value` is non-blocking
 * and avoids a DataStore disk read on the DI critical path (every screen
 * transitively pulls this definition on first resolution). Only on the rare
 * first-launch case where the Eagerly flow hasn't populated yet do we fall
 * back to the blocking `ensureDeviceId()` — which generates + persists the id.
 * The resolved id is identical either way; the fast path simply skips the IO.
 *
 * The fallback used to be an UNBOUNDED
 * runBlocking — mitigated by the identity prewarm winning the race in practice,
 * but structurally a thread (usually main, via the first ViewModel pulling the
 * graph) could block on DataStore forever. It is now bounded like the prewarm
 * itself. The `UUID.randomUUID()` last-resort arm is unreachable unless
 * DataStore is wedged past BOTH the identity prewarm (5 s) and the bound above —
 * the tradeoff there is deliberate: burn a fresh id (which merely registers a
 * new device row server-side, same class of drift as the pre-prewarm
 * first-launch race) rather than block the resolving thread indefinitely.
 * `ensureDeviceId()` is still fired fire-and-forget into the application scope
 * so whatever id the store eventually persists wins every later process; the
 * Jellyfin instance keeps the fallback id for the remainder of this one.
 */
internal fun resolveDeviceId(
    serverIdentityStore: ServerIdentityStore,
    applicationScope: CoroutineScope,
): String =
    serverIdentityStore.identity.value.deviceId
        ?: runBlocking {
            withTimeoutOrNull(DEVICE_ID_RESOLVE_TIMEOUT_MS) { serverIdentityStore.ensureDeviceId() }
        }
        ?: run {
            applicationScope.launch {
                runCatchingRethrowingCancellation { serverIdentityStore.ensureDeviceId() }
            }
            UUID.randomUUID().toString()
        }
