package com.raulshma.jellyplay.feature.home.navigation

/**
 * The Play-On redirect surface the home nav entry needs: probe whether a
 * remote session is connected and fling playback to it (V3 conveyor
 * transform). The legacy parameter type — the concrete
 * `JellyfinRemotePlayCastStrategy` from legacy `:core:data` — is
 * Android-bound (Context, Settings.Secure), so this module's commonMain
 * narrows it to the two members the routing actually uses; the app adapts
 * the real strategy at the `homeSection` call site:
 *
 * ```kotlin
 * HomePlayOnRedirect { itemId, startPositionMs ->
 *     strategy.isConnected.value.also { connected ->
 *         if (connected) strategy.loadMedia(itemId = itemId, startPositionMs = startPositionMs)
 *     }
 * }
 * ```
 *
 * Semantics match the legacy inline check-then-call: `loadMedia` itself
 * no-ops when the session died between the probe and the call, so collapsing
 * probe+call into one member changes nothing observable.
 */
fun interface HomePlayOnRedirect {

    /**
     * Flings [itemId] (at [startPositionMs]) to the connected remote session.
     * Returns true when a remote session was connected and the fling was
     * issued; false when not connected (the caller falls through to local
     * playback routing).
     */
    fun playOn(itemId: String, startPositionMs: Long): Boolean
}
