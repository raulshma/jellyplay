package com.raulshma.jellyplay.core.network.api

import kotlinx.coroutines.flow.Flow

/**
 * The role split of the JellyPlay plugin seam (ADR-0010 §5's one-family rule,
 * applied at the type level): [JellyPlayPluginApiClient] remains THE family —
 * every `jellyplay/` route still rides the one family impl over
 * [com.raulshma.jellyplay.core.network.JellyfinRawRequester] — but consumers
 * and test fakes depend on the narrow ROLE their feature actually reads, one
 * per docs/CONTRACT.md contract area (and per the [com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures]
 * registry keys). The composite extends every role, so the same instance
 * satisfies them all and Koin resolution never re-instantiates.
 *
 * Roles extend nothing shared by default; capabilities is its own tiny role
 * because it is the ONE cross-cutting bootstrap probe every face gates
 * through (the plugin status store's availability pattern — never per-endpoint
 * 404 handling).
 */

/**
 * The bootstrap probe role — `GET jellyplay/capabilities`, the ONE
 * availability/feature-gate check in the whole contract. Every 404 elsewhere
 * means "route predates the installed plugin wave"; only THIS endpoint's 404
 * means "plugin absent". Main consumer: the core:data plugin status store
 * (`JellyPlayPluginStatusStore`), whose single probe feeds every feature gate.
 */
interface JellyPlayCapabilitiesRoutes {
    suspend fun getCapabilities(): Result<JellyPlayCapabilities>
}

/**
 * The settings-sync role — the `jellyplay/settings*` + `jellyplay/sync/…`
 * contract area (`settings-sync` and its additive waves: the change-log
 * delta, pagination, sync observability ledger, restore points, export/
 * import, and the settings SSE stream). Main consumers: the core:data sync
 * engine (`ProfileSyncRepository`), its live re-sync connector, and the
 * settings-sync screen's view model.
 */
interface JellyPlaySettingsSyncRoutes {
    suspend fun getSettings(profile: String? = null): Result<JellyPlaySettingsSnapshot>

    /**
     * The delta since the change-log cursor [since], optionally paged: [limit]
     * caps the page and [cursor] continues a previous page (the response's
     * `nextCursor`, null = last page). Both null = the legacy unpaged read —
     * byte-identical wire to pre-pagination plugins, which ignore the params.
     */
    suspend fun getChangedSettings(
        since: Long,
        profile: String? = null,
        limit: Int? = null,
        cursor: Long? = null,
    ): Result<JellyPlaySettingsSnapshot>

    suspend fun applySettings(
        profile: String?,
        deviceId: String?,
        writes: List<JellyPlaySettingWrite>,
    ): Result<JellyPlaySettingsBatchResult>

    suspend fun resetNamespace(ns: String, profile: String? = null): Result<Unit>

    suspend fun resolveProfile(profile: String? = null): Result<JellyPlaySettingsSnapshot>

    /**
     * The sync engine's server-side status (usage, quotas, per-device last
     * sync). null = the server's plugin predates the sync-status wave (404) —
     * callers degrade quietly (hide the status/history surfaces), never error.
     */
    suspend fun getSyncStatus(): Result<JellyPlaySyncStatus?>

    /**
     * The sync history ledger, newest first. null = the same pre-wave 404
     * degradation contract as [getSyncStatus].
     */
    suspend fun getSyncHistory(since: Long? = null, limit: Int = 50): Result<JellyPlaySyncHistory?>

    /**
     * The changed keys behind one history entry ([seq]) — the per-key diff
     * face of the ledger. Reset rows and entries the server no longer details
     * return an empty [JellyPlaySyncHistoryKeys.keys]; null = the server's
     * plugin predates the per-key history wave (404) — callers degrade
     * quietly, never error. [limit] is server-clamped (default 200).
     */
    suspend fun getSyncHistoryKeys(seq: Long, limit: Int = 200): Result<JellyPlaySyncHistoryKeys?>

    /**
     * The admin's cross-user sync usage overview. Admin-only route: a 403
     * (non-admin) degrades to null exactly like a 404 (old plugin) — the
     * sync screen hides the admin face quietly, never errors.
     */
    suspend fun adminSyncOverview(): Result<JellyPlaySyncAdminOverview?>

    /**
     * The user's rolling restore points (server-side settings snapshots),
     * newest first. null = the server's plugin predates the restore-points
     * wave (404) — callers degrade quietly (hide the surface), never error.
     */
    suspend fun getSnapshots(): Result<List<JellyPlaySnapshot>?>

    /** Captures a manual restore point; [JellyPlaySnapshotCreated.id] is its handle. null = pre-wave 404. */
    suspend fun createSnapshot(): Result<JellyPlaySnapshotCreated?>

    /**
     * Restores [id]: a server-orchestrated tombstone batch over the current
     * rows followed by the snapshot re-applied (the restore always wins LWW).
     * The result rides the ordinary batch pipeline's shape. null = pre-wave
     * 404 (or the snapshot is not the caller's).
     */
    suspend fun restoreSnapshot(id: String): Result<JellyPlaySettingsBatchResult?>

    /**
     * One restore point's full row content (the restore-preview read): the
     * stored rows grouped per device profile exactly like the export bundle,
     * values verbatim. null = the id is unknown or not the caller's, or the
     * server's plugin predates the read (pre-wave 404) — callers degrade
     * quietly (hide the snapshot PREVIEW; full restore via [restoreSnapshot]
     * stays available), never error.
     */
    suspend fun getSnapshotContent(id: String): Result<JellyPlaySnapshotContent?>

    /**
     * The caller's whole synced store as one JSON export bundle (all profiles
     * + resolved modes + catalog stamp), verbatim — the payload is opaque to
     * the client (share/save it; hand it back to [importSettings]). null =
     * pre-wave 404.
     */
    suspend fun exportSettings(): Result<String?>

    /**
     * Re-applies a bundle produced by [exportSettings] (the same wire shape)
     * for the caller, server-now stamped — it beats anything older but never
     * clobbers a legitimately newer local change. [deviceId] attributes the
     * import. null = pre-wave 404.
     */
    suspend fun importSettings(bundleJson: String, deviceId: String? = null): Result<JellyPlaySettingsBatchResult?>

    /**
     * Cold SSE stream of `settings.changed` / `settings.reset` events for the
     * signed-in user. Cancelling collection closes the connection.
     *
     * [resumeFromEventId] rides the `Last-Event-ID` header (0/omitted = no
     * header — the legacy connect): a stream whose ids are anchored to the
     * change-log head replays everything after that id, closing the reconnect
     * gap. Callers own the cursor: read it off [JellyPlaySseEvent.id] and pass
     * the last seen value back on the next (re)connect.
     */
    fun settingsStream(resumeFromEventId: Long = 0): Flow<JellyPlaySseEvent>
}

/**
 * The device-registry role — `jellyplay/devices` (the registry-v7 rows,
 * re/rename/revoke and the push-registration half of a registration). Main
 * consumers: the core:data push registration flow (`JellyPushRepository`),
 * the events repository's startup registration, and the sync screen's device
 * rows (rename/revoke).
 */
interface JellyPlayDeviceRegistryRoutes {
    /**
     * Registers (or re-registers) this device. [push] is the explicit
     * tri-state push half (the plugin's push wave): [JellyPlayDevicePush.Attach]
     * attaches/rotates the push endpoint for [deviceId],
     * [JellyPlayDevicePush.Detach] sends `push: null` — an explicit push-off
     * re-registration that clears any endpoint the server holds — and
     * [JellyPlayDevicePush.Keep] (the default) omits the field entirely, so
     * the legacy wire shape stays byte-identical and the server keeps
     * whatever push state it already holds.
     *
     * [caps] is the registry-v7 self-reported capability list (what gates
     * silent push kinds server-side). It REPLACES the stored caps on every
     * registration — a re-POST (push rotation, re-registration) that omits it
     * wipes the device's caps — so every caller asserts its caps on every
     * registration ([CAP_SILENT_PUSH] today).
     */
    suspend fun registerDevice(
        deviceId: String,
        name: String,
        platform: String,
        appVersion: String,
        push: JellyPlayDevicePush = JellyPlayDevicePush.Keep,
        caps: List<String> = emptyList(),
        /** Hardware model (e.g. "Nokia 6.1 Plus") for the dashboard's device rows. */
        model: String? = null,
    ): Result<Unit>

    /**
     * Renames (and/or re-models) a registered device — registry v7's
     * `POST jellyplay/devices/{id}`. Null fields keep their stored value.
     * 404 surfaces as a failed Result (unknown id, or a pre-registry plugin).
     */
    suspend fun renameDevice(deviceId: String, name: String? = null, model: String? = null): Result<Unit>

    /**
     * Revokes a device — registry v7's `DELETE jellyplay/devices/{id}` is a
     * REVOKE + wipe, not a row removal: the row survives flagged `revoked`,
     * every push fan-out excludes it, and every settings row it wrote is
     * tombstone-wiped server-side. 404 surfaces as a failed Result.
     */
    suspend fun revokeDevice(deviceId: String): Result<Unit>

    suspend fun getDevices(): Result<List<JellyPlayDevice>>
}

/**
 * The events/messages role — the live SSE face (`jellyplay/events/stream`),
 * the admin broadcast fan-out (`POST jellyplay/broadcast`), and the inbox
 * messages (`jellyplay/messages*`). Main consumers: the events repository
 * (stream + inbox) and the admin dashboard's broadcast composer.
 */
interface JellyPlayEventsRoutes {
    /** Cold SSE stream of the events channel (new-media, broadcast, session notices). */
    fun eventsStream(): Flow<JellyPlaySseEvent>

    suspend fun broadcast(title: String, body: String, url: String? = null): Result<Unit>

    suspend fun getMessages(): Result<List<JellyPlayMessage>>

    suspend fun markMessageRead(messageId: String): Result<Unit>
}

/**
 * The Seerr-bridge role — `jellyplay/seerr/…` (status, login, logout of the
 * "via server" bridge). Main consumer: the settings screen's Seerr section
 * view model.
 */
interface JellyPlaySeerrRoutes {
    suspend fun seerrStatus(): Result<JellyPlaySeerrStatus>

    suspend fun seerrLogin(
        authType: String,
        username: String?,
        password: String?,
        quickConnectSecret: String?,
    ): Result<Unit>

    suspend fun seerrLogout(): Result<Unit>
}

/**
 * The ratings role — the `ratings` feature family (`jellyplay/mdblist/…`,
 * `jellyplay/tmdb/seasonRatings`). Main consumer: the detail screen's plugin
 * enrichments (the mdblist chips row and the per-season TMDB episode scores).
 */
interface JellyPlayRatingsRoutes {
    /** null = MDBList unconfigured or no data for the id (plugin 404). */
    suspend fun getMdbListRatings(imdbId: String): Result<JellyPlayRatingsResult?>

    suspend fun getTmdbSeasonRatings(tmdbId: String, seasonNumber: Int): Result<Map<Int, JellyPlayEpisodeRatings>?>
}

/**
 * The recommendations role — `jellyplay/items/{id}/similar` (the plugin's
 * server-scored similar-items row). Main consumer: the detail screen's
 * "More like this" plugin enrichment.
 */
interface JellyPlayRecommendationsRoutes {
    suspend fun getJellyPlaySimilarItems(itemId: String, limit: Int = 12): Result<List<JellyPlayScoredItem>>
}

/**
 * The anime-markers role — `jellyplay/animemarkers/…` (series-level
 * filler/recap markers). Main consumer: the detail screen's seasons section
 * badges.
 */
interface JellyPlayMarkersRoutes {
    /** null = no markers cached for the series (plugin 404). */
    suspend fun getAnimeMarkers(seriesId: String, providerSeriesId: String): Result<JellyPlaySeriesMarkers?>
}

/**
 * The rows role — the admin-defined home rows (`custom-rows`,
 * `seasonal-rows`: catalog, per-row items, seasonal row). Main consumer: the
 * home fetcher's plugin-row transport
 * (`JellyPlayHomeSectionSources`, core:network library).
 */
interface JellyPlayRowsRoutes {
    /** null = the admin-defined row does not exist (plugin 404). */
    suspend fun getCustomRow(title: String): Result<JellyPlayRowResult?>

    /** The admin-defined custom row catalog (titles + sources, plugin config order). */
    suspend fun getCustomRowCatalog(): Result<JellyPlayRowCatalog?>

    /** null = seasonal rows unconfigured (no TMDB key / no keyword this season). */
    suspend fun getSeasonalRow(keyword: String? = null): Result<JellyPlayRowResult?>
}

/**
 * The user-data role — the `user-ratings` + `bookmarks` feature families
 * (`jellyplay/bookmarks/…`, `jellyplay/userratings/…`). Main consumers: the
 * bookmarks sync repository and the settings screen's "My ratings" view model.
 */
interface JellyPlayUserDataRoutes {
    suspend fun getBookmarks(itemId: String): Result<List<JellyPlayBookmark>>

    suspend fun upsertBookmark(itemId: String, request: JellyPlayBookmarkRequest): Result<JellyPlayBookmark>

    suspend fun deleteBookmark(itemId: String, bookmarkId: String): Result<Unit>

    suspend fun getUserRatings(filter: String? = null): Result<List<JellyPlayUserRating>>
}

/**
 * The transcodes role — the active-streams monitor (`jellyplay/transcodes/…`:
 * the admin's all-sessions read, the user's own, and cancel). Main consumer:
 * the admin transcodes monitor view model.
 */
interface JellyPlayTranscodesRoutes {
    /** Admin-only route; 403 for non-admins surfaces as a failed Result. */
    suspend fun getActiveTranscodes(): Result<List<JellyPlayActiveTranscode>>

    suspend fun getMyTranscodes(): Result<List<JellyPlayActiveTranscode>>

    suspend fun cancelTranscode(sessionId: String): Result<Unit>
}

/**
 * The analytics role — the playback-activity reporting face (`analytics`:
 * the admin overview + sessions pagination, and the signed-in user's own
 * `jellyplay/analytics/me`). Main consumers: the admin analytics screen and
 * the settings screen's "Your watching" view model.
 */
interface JellyPlayAnalyticsRoutes {
    /**
     * The admin analytics overview (plays / watch-time / transcode-time
     * aggregates over a [days] window the server clamps to 1..365). Admin-only
     * route: a 403 (non-admin) degrades to null exactly like a 404 (old
     * plugin / absent `analytics` key) — callers degrade quietly (hide the
     * surface), never error.
     */
    suspend fun getAnalyticsOverview(days: Int = 30): Result<JellyPlayAnalyticsOverview?>

    /**
     * The admin's recent play sessions, newest first, optionally scoped to
     * [userId] and to sessions ending at/after [since] (pagination cursor =
     * the oldest `endedAt` of the previous page). [limit] is server-clamped
     * (default 50, max 200). null = the same pre-wave 404 degradation contract
     * as [getAnalyticsOverview].
     */
    suspend fun getAnalyticsSessions(
        userId: String? = null,
        since: Long? = null,
        limit: Int = 50,
    ): Result<JellyPlayAnalyticsSessions?>

    /**
     * The signed-in user's OWN analytics (`jellyplay/analytics/me` — any
     * user, unlike the admin routes above; [days] is server-clamped to
     * 1..365). null = the server's plugin predates the per-user analytics
     * face or the `analytics` key is absent (404) — callers degrade quietly
     * (the "not available" state), never error.
     */
    suspend fun getMyAnalytics(days: Int = 30): Result<JellyPlayMyAnalytics?>
}
