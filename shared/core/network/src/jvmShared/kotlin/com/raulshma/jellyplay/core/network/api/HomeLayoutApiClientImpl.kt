package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.TimeSource
import org.jellyfin.sdk.api.client.extensions.*
import org.jellyfin.sdk.model.api.DisplayPreferencesDto
import org.jellyfin.sdk.model.api.ScrollDirection
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.serializer.toUUID

/**
 * The [HomeLayoutApiClient] family adapter: the home-layout DisplayPreferences
 * read/write pair moved verbatim off [LibraryApiClientImpl]. Closes over the
 * SAME [JellyfinApiEngine] single the wide client uses plus the [TimeSource]
 * clock seam (the write-time savedAt stamp) — the exact construction the Koin
 * graph already resolves for the wide client.
 */
class HomeLayoutApiClientImpl(
    private val engine: JellyfinApiEngine,
    /** The write-time stamp clock — the manual sync dialog's newer-copy signal. */
    private val timeSource: TimeSource,
) : HomeLayoutApiClient {

    /**
     * The home-layout DisplayPreferences slice. Transport: the SDK's
     * DisplayPreferencesApi keyed by (displayPreferencesId, user, client) —
     * the client value ("jellyplay") namespaces our store away from
     * jellyfin-web's; the payload rides the DTO's CustomPrefs string map
     * under the HomeLayoutSync keys. The write is a single POST with a fresh
     * DTO: this wrapper owns the whole namespace, so replacing the custom
     * prefs is safe — no foreign keys to preserve, and no stale key can
     * outlive a layout delete.
     */
    override suspend fun readHomeLayoutDisplayPreferences(userId: String): Result<HomeLayoutServerState> =
        engine.withApi { api ->
            val dto = api.displayPreferencesApi.getDisplayPreferences(
                displayPreferencesId = HOME_LAYOUT_DISPLAY_PREFERENCES_ID,
                userId = userId.toUUID(),
                client = HOME_LAYOUT_CLIENT,
            ).content
            // Missing keys decode to null state (never synced / partial
            // store) — a value, not a failure; the caller's lenient decode
            // treats absent as "nothing on server".
            HomeLayoutServerState(
                json = dto.customPrefs[HOME_LAYOUT_JSON_KEY],
                schemaVersion = dto.customPrefs[HOME_LAYOUT_SCHEMA_VERSION_KEY]?.toIntOrNull(),
                savedAtEpochMillis = dto.customPrefs[HOME_LAYOUT_SAVED_AT_KEY]?.toLongOrNull(),
            )
        }

    override suspend fun writeHomeLayoutDisplayPreferences(userId: String, json: String, schemaVersion: Int): Result<Unit> =
        engine.withApi { api ->
            // savedAt stamps the write-time client clock (the DisplayPreferences
            // DTO carries no server timestamp) — the manual sync dialog's
            // newer-copy signal, not a clock-sync guarantee.
            api.displayPreferencesApi.updateDisplayPreferences(
                displayPreferencesId = HOME_LAYOUT_DISPLAY_PREFERENCES_ID,
                userId = userId.toUUID(),
                client = HOME_LAYOUT_CLIENT,
                data = DisplayPreferencesDto(
                    id = userId,
                    viewType = null,
                    sortBy = null,
                    indexBy = null,
                    rememberIndexing = false,
                    primaryImageHeight = 0,
                    primaryImageWidth = 0,
                    customPrefs = mapOf(
                        HOME_LAYOUT_JSON_KEY to json,
                        HOME_LAYOUT_SCHEMA_VERSION_KEY to schemaVersion.toString(),
                        HOME_LAYOUT_SAVED_AT_KEY to timeSource.nowEpochMillis().toString(),
                    ),
                    scrollDirection = ScrollDirection.HORIZONTAL,
                    showBackdrop = false,
                    rememberSorting = false,
                    sortOrder = SortOrder.ASCENDING,
                    showSidebar = false,
                    client = HOME_LAYOUT_CLIENT,
                ),
            ).content
            Unit
        }
}
