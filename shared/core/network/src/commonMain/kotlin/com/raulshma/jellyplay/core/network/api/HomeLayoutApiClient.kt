package com.raulshma.jellyplay.core.network.api

/**
 * The home-layout DisplayPreferences family seam of [LibraryApiClient]: the
 * server-persisted home-layout slice read/write pair whose only consumer is
 * the data layer's [com.raulshma.jellyplay.core.data.repository.HomeLayoutSyncController]
 * (the autopush + manual Upload/Restore bridge) — not library-browse
 * taxonomy, so it carves onto its own adapter over the SAME [JellyfinApiEngine]
 * single (the [PlaylistApiClient] / [CollectionApiClient] shape), and the
 * controller injects this seam, never the wide library surface. Members moved
 * verbatim off [LibraryApiClient]; the wire vocabulary lives beside this file
 * ([HomeLayoutServerState] + the `HOME_LAYOUT_*` constants).
 */
interface HomeLayoutApiClient {
    /**
     * Reads the server-persisted home-layout slice from Jellyfin's
     * DisplayPreferences API — the stored JSON payload plus its schema
     * version and write-time stamp (see [HomeLayoutServerState]). Never
     * fails on "nothing stored": a fresh user decodes to all-null state.
     */
    suspend fun readHomeLayoutDisplayPreferences(userId: String): Result<HomeLayoutServerState>

    /**
     * Writes the home-layout slice into the server's DisplayPreferences
     * store: the [json] payload under the `jellyplay.homeLayout` custom-pref
     * key, plus the [schemaVersion] and a write-time stamp companion keys —
     * the transport documented on the HomeLayoutSync constants. A manual
     * "upload layout" action or the debounced autopush; no auto-pull exists
     * (silent overwrites avoided by design).
     */
    suspend fun writeHomeLayoutDisplayPreferences(userId: String, json: String, schemaVersion: Int): Result<Unit>
}
