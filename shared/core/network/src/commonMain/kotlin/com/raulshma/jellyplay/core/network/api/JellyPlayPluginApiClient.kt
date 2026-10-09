package com.raulshma.jellyplay.core.network.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The Jellyfin-plugin-jellyplay client seam — every `jellyplay/` route the
 * companion server plugin serves (see the plugin repo's docs/CONTRACT.md).
 * Split from the other families so the sync engine (data layer) can depend on
 * THIS interface alone, mirroring the NewsletterApiClient pass-through idiom.
 *
 * Since the roles wave, THIS type is the FAMILY composite: it extends every
 * role interface in [JellyPlayPluginApiRoles] (one per docs/CONTRACT.md
 * contract area) and adds nothing of its own. ADR-0010 §5 stands — all routes
 * ride the one family impl ([JellyPlayPluginApiClientImpl]) over the raw
 * requester — while consumers and test fakes depend on the single narrow
 * role they use. The composite stays what `JellyfinApiClient` registers.
 *
 * Availability contract: every call 404s when the plugin is absent. The
 * capability probe ([JellyPlayCapabilitiesRoutes.getCapabilities]) is the ONE
 * bootstrap check — callers gate on the resulting feature set, never on
 * per-endpoint 404 handling.
 */
interface JellyPlayPluginApiClient :
    JellyPlayCapabilitiesRoutes,
    JellyPlaySettingsSyncRoutes,
    JellyPlayDeviceRegistryRoutes,
    JellyPlayEventsRoutes,
    JellyPlaySeerrRoutes,
    JellyPlayRatingsRoutes,
    JellyPlayRecommendationsRoutes,
    JellyPlayMarkersRoutes,
    JellyPlayRowsRoutes,
    JellyPlayUserDataRoutes,
    JellyPlayTranscodesRoutes,
    JellyPlayAnalyticsRoutes

@Serializable
data class JellyPlayRatingEntry(
    val source: String,
    val score: Double? = null,
    val votes: Int? = null,
    val url: String? = null,
)

@Serializable
data class JellyPlayRatingsResult(
    val imdbId: String,
    val ratings: List<JellyPlayRatingEntry> = emptyList(),
)

@Serializable
data class JellyPlayEpisodeRatings(
    val tmdbScore: Double? = null,
    val tmdbVotes: Int? = null,
)

@Serializable
data class JellyPlayScoredItem(
    val itemId: String,
    val name: String,
    val score: Double,
)

@Serializable
data class JellyPlayAnimeMarker(
    val type: String,
    val episodeNumber: Int,
    val note: String? = null,
)

@Serializable
data class JellyPlaySeriesMarkers(
    val seriesId: String,
    val aniListId: String? = null,
    val malId: String? = null,
    val markers: List<JellyPlayAnimeMarker> = emptyList(),
)

@Serializable
data class JellyPlayRowItem(
    val title: String,
    val year: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val localItemId: String? = null,
)

@Serializable
data class JellyPlayRowTitle(
    val title: String,
    val source: String = "",
    val limit: Int = 20,
)

@Serializable
data class JellyPlayRowCatalog(
    val rows: List<JellyPlayRowTitle> = emptyList(),
)

@Serializable
data class JellyPlayRowResult(
    val title: String,
    val source: String,
    val items: List<JellyPlayRowItem> = emptyList(),
)

@Serializable
data class JellyPlayBookmark(
    val id: String,
    val itemId: String,
    val position: Double = 0.0,
    val chapterIndex: Int? = null,
    val label: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class JellyPlayBookmarkRequest(
    val id: String? = null,
    val position: Double = 0.0,
    val chapterIndex: Int? = null,
    val label: String = "",
    val notes: String = "",
)

@Serializable
data class JellyPlayUserRating(
    val itemId: String,
    val name: String,
    val itemType: String = "",
    val likes: Boolean? = null,
    val rating: Int? = null,
    val lastPlayedDate: String? = null,
    val playCount: Int = 0,
)

@Serializable
data class JellyPlayActiveTranscode(
    val sessionId: String,
    val userName: String? = null,
    val deviceName: String? = null,
    val itemName: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val playMethod: String? = null,
    val videoBitrate: Int? = null,
    /** Named flag bits the server decomposed its `[Flags]` reason word into. */
    val transcodeReasons: List<String>? = null,
    val positionTicks: Long = 0,
    val isPaused: Boolean = false,
)

@Serializable
data class JellyPlayCapabilities(
    val contractVersion: Int,
    val pluginVersion: String,
    val features: List<String> = emptyList(),
    val serverNow: Long = 0,
    val deviceProfiles: List<String> = emptyList(),
    /**
     * The plugin registered its scorer into the HOST's similar-items pipeline
     * (a Jellyfin-12+ reflection-guarded registration; no-op on 10.11 — the
     * plugin knows, the client cannot). Additive, default false: only when
     * true does the stock `/Items/{id}/Similar` return the same scored list
     * the plugin row renders — the detail screen's stock-row suppression
     * reads this, never assumes it.
     */
    val serverSimilarPipeline: Boolean = false,
)

@Serializable
data class JellyPlaySettingsEntry(
    val ns: String,
    val key: String,
    val schemaVersion: Int = 1,
    val updatedAt: Long,
    val deviceId: String = "",
    val profile: String = "",
    val value: JsonElement,
)

@Serializable
data class JellyPlaySettingsSnapshot(
    val head: Long = 0,
    val profile: String = "",
    val settings: List<JellyPlaySettingsEntry> = emptyList(),
    /**
     * The keys deleted since the delta cursor (the change-log's tombstones),
     * additive on `GET settings/changed` pages. Absent/empty = an older plugin
     * (no tombstone machinery on the wire) or nothing deleted — the read
     * behaves exactly as before either way.
     */
    val deleted: List<JellyPlaySettingDelete> = emptyList(),
    /**
     * The pagination continuation for `GET settings` / `GET settings/changed`
     * pages (null = last page). Absent = an older plugin — the unpaged read.
     */
    val nextCursor: Long? = null,
    /**
     * The admin-defaults tri-state per synced key, addressed by the composite
     * `"ns/key"` form (`"prefs/pluginFeature.events.enabled"`) →
     * `"unset" | "suggested" | "forced"`. Absent = an older plugin (no
     * admin-default machinery on the wire) — `null` reads as "no mode is in
     * force anywhere". A `"forced"` key is overwritten server-side every
     * cycle: its UI switch renders disabled.
     */
    val modes: Map<String, String>? = null,
)

/** One tombstoned key inside a delta snapshot's [JellyPlaySettingsSnapshot.deleted] list. */
@Serializable
data class JellyPlaySettingDelete(val ns: String, val key: String)

@Serializable
data class JellyPlaySettingWrite(
    val ns: String,
    val key: String,
    val schemaVersion: Int = 1,
    val updatedAt: Long,
    val value: JsonElement,
    /**
     * Tombstone marker for the plugin's delete op. `true` removes the stored
     * value server-side and appends a change-log 'del' row so the delete roams
     * to peers via the delta's `deleted[]`. This flag is the only tombstone
     * form on the wire — a JSON-null [value] without it is stored verbatim.
     */
    val deleted: Boolean = false,
)

@Serializable
data class JellyPlayAppliedSetting(
    val ns: String,
    val key: String,
    val updatedAt: Long,
    val seq: Long,
    /** True when the applied write was a tombstone (the server's delete op). */
    val deleted: Boolean = false,
)

/**
 * One rejected write. `reason` is the plugin's vocabulary — `stale-write`
 * (LWW loss, retryable), `key-too-large`/`quota-exceeded`/`key-limit-reached`,
 * and the non-retryable `clock-skew` (the write's `updatedAt` runs ahead of
 * the server clock) and `device-revoked` (the `deviceId` was revoked) — the
 * last two must surface in sync state, never silently retry.
 */
@Serializable
data class JellyPlayRejectedSetting(val ns: String, val key: String, val reason: String)

@Serializable
data class JellyPlaySettingsBatchResult(
    val head: Long = 0,
    val applied: List<JellyPlayAppliedSetting> = emptyList(),
    val rejected: List<JellyPlayRejectedSetting> = emptyList(),
)

// ---------------------------------------------------------------------------
// sync status / history / admin overview (the `jellyplay/sync/*` wave) — the
// usage-and-ledger face of the settings-sync engine. Every shape carries
// defaults so a payload trimmed by an older plugin still decodes.
// ---------------------------------------------------------------------------

/** Server-side sync usage: totals, quotas, per-namespace and per-device split. */
@Serializable
data class JellyPlaySyncStatus(
    val head: Long = 0,
    val keys: Int = 0,
    val bytes: Long = 0,
    val quotaBytes: Long = 0,
    val quotaKeys: Int = 0,
    val historyRetentionDays: Int = 0,
    val namespaces: List<JellyPlaySyncNamespaceUsage> = emptyList(),
    val perDevice: List<JellyPlaySyncDeviceStat> = emptyList(),
)

/** One namespace's contribution to the synced store. */
@Serializable
data class JellyPlaySyncNamespaceUsage(
    val ns: String,
    val keys: Int = 0,
    val bytes: Long = 0,
)

/** One device's last sync outcome as the server recorded it. */
@Serializable
data class JellyPlaySyncDeviceStat(
    val deviceId: String,
    val lastSyncAt: Long = 0,
    val lastOp: String = "",
)

/** The sync history ledger (newest first). */
@Serializable
data class JellyPlaySyncHistory(
    val entries: List<JellyPlaySyncHistoryEntry> = emptyList(),
)

/** One applied sync operation a device performed against the server. */
@Serializable
data class JellyPlaySyncHistoryEntry(
    val seq: Long,
    val ts: Long = 0,
    val deviceId: String = "",
    /** `push` | `pull` | `reset` | `wipe` — loosely matched by the UI, unknown → plain label. */
    val op: String = "",
    val keysApplied: Int = 0,
    val keysRejected: Int = 0,
    val rejects: List<JellyPlaySyncReject>? = null,
)

/** One per-key rejection inside a history entry. */
@Serializable
data class JellyPlaySyncReject(
    val ns: String,
    val key: String,
    val reason: String = "",
)

/** The changed keys behind one sync-history entry (the per-key diff detail). */
@Serializable
data class JellyPlaySyncHistoryKeys(
    val seq: Long,
    /** `push` | `pull` | `reset` | `wipe`; rows without a usable range (pre-v7 resets, no-ops) carry an empty [keys]. */
    val op: String = "",
    val keys: List<JellyPlaySyncHistoryKey> = emptyList(),
)

/** One changed key inside a sync-history entry's diff. */
@Serializable
data class JellyPlaySyncHistoryKey(
    val ns: String,
    val key: String,
    val updatedAt: Long = 0,
)

/** The admin's cross-user sync usage overview (`admin/sync/overview`). */
@Serializable
data class JellyPlaySyncAdminOverview(
    val users: List<JellyPlaySyncAdminUser> = emptyList(),
)

/** One user's synced-store usage as the admin overview reports it. */
@Serializable
data class JellyPlaySyncAdminUser(
    val userId: String,
    val userName: String = "",
    val keys: Int = 0,
    val bytes: Long = 0,
    val lastSyncAt: Long = 0,
    val deviceCount: Int = 0,
)

// ---------------------------------------------------------------------------
// restore points (the `jellyplay/settings/snapshots` wave) — the rolling
// per-user server-side snapshots of the WHOLE synced store. Defaults keep a
// payload trimmed by an older plugin decoding.
// ---------------------------------------------------------------------------

/** One restore point: a server-held settings snapshot (newest-first list order). */
@Serializable
data class JellyPlaySnapshot(
    val id: String,
    val createdAt: Long = 0,
    /** `manual` | `admin-push` | `profile-copy` — loosely matched by the UI. */
    val origin: String = "",
    val keys: Int = 0,
    val bytes: Long = 0,
)

/** The create-snapshot response — the new restore point's handle. */
@Serializable
data class JellyPlaySnapshotCreated(val id: String)

// ---------------------------------------------------------------------------
// admin analytics (the `jellyplay/admin/analytics/*` wave) — the play-history
// aggregates the plugin computes server-side. Every shape carries defaults so
// a payload trimmed by an older plugin still decodes.
// ---------------------------------------------------------------------------

/** The admin analytics overview: totals plus per-day/per-user/top-item splits. */
@Serializable
data class JellyPlayAnalyticsOverview(
    val days: Int = 30,
    val totals: JellyPlayAnalyticsTotals = JellyPlayAnalyticsTotals(),
    val perDay: List<JellyPlayAnalyticsDay> = emptyList(),
    val perUser: List<JellyPlayAnalyticsUser> = emptyList(),
    val topItems: List<JellyPlayAnalyticsTopItem> = emptyList(),
)

/** The window's roll-up numbers (playSeconds/transcodeSeconds are wall-clock sums). */
@Serializable
data class JellyPlayAnalyticsTotals(
    val plays: Long = 0,
    val playSeconds: Long = 0,
    val transcodeSeconds: Long = 0,
    val uniqueUsers: Int = 0,
    val uniqueItems: Int = 0,
)

/** One calendar day's play/watch/transcode counts (`day` is `yyyy-MM-dd`). */
@Serializable
data class JellyPlayAnalyticsDay(
    val day: String,
    val plays: Long = 0,
    val playSeconds: Long = 0,
    val transcodeSeconds: Long = 0,
)

/** One user's contribution to the window (the sessions filter's vocabulary). */
@Serializable
data class JellyPlayAnalyticsUser(
    val userId: String,
    val userName: String = "",
    val plays: Long = 0,
    val playSeconds: Long = 0,
    val transcodeSeconds: Long = 0,
)

/** One most-played item of the window (server-ranked, top 10). */
@Serializable
data class JellyPlayAnalyticsTopItem(
    val itemId: String,
    val itemName: String = "",
    val itemType: String = "",
    val plays: Long = 0,
    val playSeconds: Long = 0,
)

/** The admin's recent play-sessions page. */
@Serializable
data class JellyPlayAnalyticsSessions(
    val sessions: List<JellyPlayAnalyticsSession> = emptyList(),
)

/**
 * One recorded play session. [playMethod] is the plugin's free string
 * (`"DirectPlay"` | `"Transcode"` in practice — loosely matched by the UI,
 * the transcodes monitor's idiom); [since]/[endedAt] are the server's epoch
 * stamps ([endedAt] doubling as the pagination cursor).
 */
@Serializable
data class JellyPlayAnalyticsSession(
    val userId: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val itemType: String = "",
    val seriesName: String? = null,
    val playMethod: String = "",
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val bitrate: Long? = null,
    val transcodeReasons: List<String>? = null,
    val positionTicks: Long = 0,
    val durationTicks: Long? = null,
    val startedAt: Long = 0,
    val endedAt: Long = 0,
    val clientName: String? = null,
    val deviceName: String? = null,
)

// ---------------------------------------------------------------------------
// my analytics (the `jellyplay/analytics/me` wave) — the signed-in user's own
// face of the play-history aggregates (the admin overview's per-user slice).
// Every shape carries defaults so a payload trimmed by an older plugin still
// decodes; the per-day / top-item rows reuse the admin wave's DTOs verbatim.
// ---------------------------------------------------------------------------

/** The user's own analytics overview: window totals, per-day split, top items. */
@Serializable
data class JellyPlayMyAnalytics(
    val days: Int = 30,
    val totals: JellyPlayMyAnalyticsTotals = JellyPlayMyAnalyticsTotals(),
    val perDay: List<JellyPlayAnalyticsDay> = emptyList(),
    val topItems: List<JellyPlayAnalyticsTopItem> = emptyList(),
)

/** The user's window roll-up (the admin totals minus the cross-user counts). */
@Serializable
data class JellyPlayMyAnalyticsTotals(
    val plays: Long = 0,
    val playSeconds: Long = 0,
    val transcodeSeconds: Long = 0,
    val uniqueItems: Int = 0,
)

/**
 * One row of the device registry (`GET jellyplay/devices`). The registry-v7
 * fields ([model], [caps], [revoked], [push]) are additive with defaults, so
 * a payload from a pre-registry-wave plugin still decodes; [push] (holding
 * the secret endpoint URL) is only ever present on the caller's OWN rows.
 */
@Serializable
data class JellyPlayDevice(
    val deviceId: String,
    val userId: String = "",
    val name: String = "",
    val platform: String = "",
    val appVersion: String = "",
    val lastSeen: Long = 0,
    /** Self-reported hardware model (registry v7, informational). */
    val model: String? = null,
    /** Self-reported capability strings (registry v7) — what gates silent push. */
    val caps: List<String> = emptyList(),
    /** True once the device was revoked server-side (its writes reject, its rows were wiped). */
    val revoked: Boolean = false,
    /** The device's push registration, present only on the caller's own rows. */
    val push: JellyPlayPushRegistration? = null,
)

/**
 * The `push` half of a device registration (the plugin's push wave): the
 * kind mirrors the plugin contract's `"generic" | "ntfy" | "fcm"` (the app
 * only ever sends `"generic"`; the others are server/admin surfaces) and
 * [endpoint] is the UnifiedPush endpoint URL the distributor handed out.
 * Re-POSTing the same deviceId with a new endpoint rotates the registration
 * server-side.
 */
@Serializable
data class JellyPlayPushRegistration(
    val kind: String,
    val endpoint: String,
)

/**
 * The one device capability this client self-reports (registry v7): opts the
 * device into the `sync-nudge` SILENT push (a data-only frame the app turns
 * into a [profile-sync][com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository]
 * cycle, never a tray notification). Old clients that render unknown kinds as
 * visible notifications simply never send the cap, and the server never
 * nudges them. Every registerDevice call asserts it — the wire REPLACES caps.
 */
const val CAP_SILENT_PUSH = "silent-push"

/**
 * The wire tri-state of a registration's push half — attach, explicit
 * detach (`"push": null`), or leave the field out (keep whatever push state
 * the server holds). A nullable [JellyPlayPushRegistration] cannot express
 * all three: Kotlin `null` would have to mean both "detach" and "omit".
 */
sealed interface JellyPlayDevicePush {
    /** Attach/rotate the push endpoint (`"push": {kind, endpoint}`). */
    data class Attach(val registration: JellyPlayPushRegistration) : JellyPlayDevicePush

    /** Explicit push-off: `"push": null` clears the server-held endpoint. */
    data object Detach : JellyPlayDevicePush

    /** Omit the field — the legacy wire shape; the server's push state stands. */
    data object Keep : JellyPlayDevicePush
}

@Serializable
data class JellyPlayMessage(
    val id: String,
    val title: String,
    val body: String = "",
    val color: String = "",
    val linkUrl: String = "",
    val linkLabel: String = "",
    val startsAt: Long? = null,
    val endsAt: Long? = null,
    val orderIndex: Int = 0,
    val createdAt: Long = 0,
    val read: Boolean = false,
)

@Serializable
data class JellyPlaySeerrStatus(
    val configured: Boolean = false,
    val serverUrl: String? = null,
    val linked: Boolean = false,
    val createdAt: Long? = null,
)

/** One decoded server-sent event. [data] stays raw JSON — consumers decode per [event]. */
data class JellyPlaySseEvent(
    val id: Long,
    val event: String,
    val data: String,
)
