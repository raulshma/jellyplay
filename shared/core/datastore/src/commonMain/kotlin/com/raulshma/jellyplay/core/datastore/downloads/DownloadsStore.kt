package com.raulshma.jellyplay.core.datastore.downloads

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.datastore.UserNamespacedKeys
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.DownloadScheduleWindow
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.isEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Deep module owning the **downloads** preference domain: wifi-only + per-stream
 * connection counts (with the 1..6 max-concurrent invariant), download quality,
 * smart/auto downloads (+ its retention policy: lookahead, max-per-pass,
 * keep-days), storage cap + location, the cellular size warning, and the
 * schedule window.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters (including the `MAX_CONCURRENT_DOWNLOADS` coerce invariant),
 * its read projection, and its reset-key list end-to-end. Mirrors the
 * `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Spec derivation:** every key this store owns is declared exactly once as a
 * row in [DownloadsPreferenceSpecs] (wire name, default, reset category,
 * read/write encoding) and the machinery below is derived from those rows —
 * each [Keys] member rebuilds its row's typed key from the row's wire name,
 * each [read] projection row delegates to its row encoding, the derived rows
 * of [restore] delegate to the rows' derived writes, and [resetKeysFor]
 * filters the rows by reset category. Every key this store owns resets under
 * the single legacy `DOWNLOADS_NETWORK` category (the network/offline keys now
 * belong to `NetworkOfflineStore`).
 *
 * **Hand-written residuals (the documented opt-outs — cross-key invariants,
 * per-user namespacing and band policies a row encoding cannot own):**
 *  - [setAutoDownloadMode] / [restore] write the canonical
 *    [DownloadsPreferenceSpecs.AUTO_DOWNLOAD_MODE] enum AND mirror it onto the
 *    legacy [DownloadsPreferenceSpecs.AUTO_DOWNLOAD_NEW_EPISODES] boolean in
 *    one edit (`mode.isEnabled`) so an app downgrade still reads a sane value;
 *    the mode row's read carries the legacy fallback the mirror serves (the
 *    row's encode stays null — the pair's write is a cross-key invariant).
 *  - the per-user server allow-list
 *    ([DownloadsPreferenceSpecs.AUTO_DOWNLOAD_SERVERS]) is the declaration-only
 *    [PreferenceSpec.custom] row: the `u_<userId>::`-namespaced read/write
 *    ([readAutoDownloadServers] / [setAutoDownloadServers] / the namespaced
 *    [restore] line, skipped pre-login) resolves the namespace off the very
 *    snapshot being read or edited — the `HomeDiscoveryStore` precedent.
 *  - the four band rows keep their read-side bands in the [read] projection
 *    (`max_concurrent_downloads` to 1..6, `auto_download_lookahead` to
 *    0..[MAX_LOOKAHEAD], `auto_download_max_per_pass` to 0..[MAX_PER_PASS_LIMIT],
 *    `auto_download_keep_days` floored at 0 — the videoplayer precedent: a
 *    plain-int row carries no read transform) and the clamps in their setters.
 *  - the declaration-only rows ([PreferenceSpec.boolean] /
 *    [PreferenceSpec.string] — `download_storage_location`,
 *    `auto_delete_after_watch`, the downloads-list trio,
 *    `downloads_selection_hint_seen`, `download_schedule_enabled/_wifi_only`,
 *    and the legacy `auto_download_new_episodes` surface) keep the store's
 *    hand-written setter/restore lines: a declaration-only row has no derived
 *    write (their hand reads were the plain typed `prefs[key] ?: default` —
 *    the keys were born in the typed-key era, so no legacy fallback applies).
 *  - the coalesced [setDownloadScheduleWindow] three-key write stays
 *    hand-written (one window, three keys, one transaction).
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; key strings match
 * the legacy `UserPreferencesStore.Keys` names — no migration file.
 *
 * The auto-download **server allow-list** (`auto_download_servers`) is the one
 * per-user key here: it is stored under the `u_<userId>::<canonical>` namespace
 * (the `HomeDiscoveryStore` precedent) so two accounts on one install keep
 * independent allow-lists. The canonical key string is shared with the backup
 * format; every read/write resolves the namespace from the very
 * `Preferences` snapshot being read or edited ([ServerIdentityStore.activeUserIdIn]),
 * pre-login reads serve the default (empty = all servers).
 */
class DownloadsStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
    private val identityStore: ServerIdentityStore,
) {
    private val scope = externalScope

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The store's DataStore keys, each derived from its
     * [DownloadsPreferenceSpecs] row — the member rebuilds the row's typed key
     * from the row's single-declared wire name (`Preferences.Key` equality is
     * name-based, so these interoperate with any hand-built key of the same
     * name). Kept as a plain object rather than folded into the rows because
     * it is the reflection anchor for the JVM reset-coverage guard and the
     * key-identity reference for the hand-written cross-key invariant setters
     * below.
     */
    internal object Keys {
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey(DownloadsPreferenceSpecs.WIFI_ONLY_DOWNLOADS.keyName)
        val DOWNLOAD_CONNECTIONS = intPreferencesKey(DownloadsPreferenceSpecs.DOWNLOAD_CONNECTIONS.keyName)
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey(DownloadsPreferenceSpecs.MAX_CONCURRENT_DOWNLOADS.keyName)
        val DOWNLOAD_QUALITY = stringPreferencesKey(DownloadsPreferenceSpecs.DOWNLOAD_QUALITY.keyName)
        val SMART_DOWNLOADS_ENABLED = booleanPreferencesKey(DownloadsPreferenceSpecs.SMART_DOWNLOADS_ENABLED.keyName)
        val AUTO_DOWNLOAD_MODE = stringPreferencesKey(DownloadsPreferenceSpecs.AUTO_DOWNLOAD_MODE.keyName)
        val AUTO_DOWNLOAD_NEW_EPISODES = booleanPreferencesKey(DownloadsPreferenceSpecs.AUTO_DOWNLOAD_NEW_EPISODES.keyName)
        val MAX_DOWNLOAD_STORAGE_GB = intPreferencesKey(DownloadsPreferenceSpecs.MAX_DOWNLOAD_STORAGE_GB.keyName)
        val DOWNLOAD_STORAGE_LOCATION = stringPreferencesKey(DownloadsPreferenceSpecs.DOWNLOAD_STORAGE_LOCATION.keyName)
        val AUTO_DELETE_AFTER_WATCH = booleanPreferencesKey(DownloadsPreferenceSpecs.AUTO_DELETE_AFTER_WATCH.keyName)
        val CELLULAR_DOWNLOAD_SIZE_WARNING_MB = intPreferencesKey(DownloadsPreferenceSpecs.CELLULAR_DOWNLOAD_SIZE_WARNING_MB.keyName)
        val DOWNLOAD_SCHEDULE_ENABLED = booleanPreferencesKey(DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_ENABLED.keyName)
        val DOWNLOAD_SCHEDULE_START = intPreferencesKey(DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_START.keyName)
        val DOWNLOAD_SCHEDULE_END = intPreferencesKey(DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_END.keyName)
        val DOWNLOAD_SCHEDULE_WIFI_ONLY = booleanPreferencesKey(DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_WIFI_ONLY.keyName)
        val AUTO_DOWNLOAD_LOOKAHEAD = intPreferencesKey(DownloadsPreferenceSpecs.AUTO_DOWNLOAD_LOOKAHEAD.keyName)
        val AUTO_DOWNLOAD_MAX_PER_PASS = intPreferencesKey(DownloadsPreferenceSpecs.AUTO_DOWNLOAD_MAX_PER_PASS.keyName)
        val AUTO_DOWNLOAD_KEEP_DAYS = intPreferencesKey(DownloadsPreferenceSpecs.AUTO_DOWNLOAD_KEEP_DAYS.keyName)
        val AUTO_DOWNLOAD_SERVERS = stringPreferencesKey(DownloadsPreferenceSpecs.AUTO_DOWNLOAD_SERVERS.keyName)
        val DOWNLOADS_LIST_FILTER = stringPreferencesKey(DownloadsPreferenceSpecs.DOWNLOADS_LIST_FILTER.keyName)
        val DOWNLOADS_LIST_SORT = stringPreferencesKey(DownloadsPreferenceSpecs.DOWNLOADS_LIST_SORT.keyName)
        val DOWNLOADS_LIST_STATUS_FILTER = stringPreferencesKey(DownloadsPreferenceSpecs.DOWNLOADS_LIST_STATUS_FILTER.keyName)
        val DOWNLOADS_SELECTION_HINT_SEEN = booleanPreferencesKey(DownloadsPreferenceSpecs.DOWNLOADS_SELECTION_HINT_SEEN.keyName)
    }

    val downloads: StateFlow<DownloadsSlice> =
        dataStore.sliceStateFlow(scope, seed = DownloadsSlice(), read = ::read)

    /**
     * Pure read of the downloads fields from a raw [Preferences] snapshot,
     * each field delegated to its [DownloadsPreferenceSpecs] row encoding —
     * except the four band rows, whose read-side bands post-clamp the row read
     * (the videoplayer precedent), and the namespaced allow-list, whose
     * per-user read stays the store's hand codec. Exposed so the facade can
     * fold these into the whole-`UserPreferences` projection without
     * duplicating the read logic.
     */
    internal fun read(prefs: Preferences): DownloadsSlice = DownloadsSlice(
        wifiOnlyDownloads = DownloadsPreferenceSpecs.WIFI_ONLY_DOWNLOADS.readFrom(prefs),
        downloadConnections = DownloadsPreferenceSpecs.DOWNLOAD_CONNECTIONS.readFrom(prefs),
        maxConcurrentDownloads = DownloadsPreferenceSpecs.MAX_CONCURRENT_DOWNLOADS.readFrom(prefs).coerceIn(1, 6),
        downloadQuality = DownloadsPreferenceSpecs.DOWNLOAD_QUALITY.readFrom(prefs),
        smartDownloadsEnabled = DownloadsPreferenceSpecs.SMART_DOWNLOADS_ENABLED.readFrom(prefs),
        autoDownloadMode = DownloadsPreferenceSpecs.AUTO_DOWNLOAD_MODE.readFrom(prefs),
        autoDownloadLookahead = DownloadsPreferenceSpecs.AUTO_DOWNLOAD_LOOKAHEAD.readFrom(prefs)
            .coerceIn(0, MAX_LOOKAHEAD),
        autoDownloadMaxPerPass = DownloadsPreferenceSpecs.AUTO_DOWNLOAD_MAX_PER_PASS.readFrom(prefs)
            .coerceIn(0, MAX_PER_PASS_LIMIT),
        autoDownloadKeepDays = DownloadsPreferenceSpecs.AUTO_DOWNLOAD_KEEP_DAYS.readFrom(prefs)
            .coerceAtLeast(0),
        autoDownloadServers = readAutoDownloadServers(prefs),
        maxDownloadStorageGb = DownloadsPreferenceSpecs.MAX_DOWNLOAD_STORAGE_GB.readFrom(prefs),
        downloadsListFilter = DownloadsPreferenceSpecs.DOWNLOADS_LIST_FILTER.readStored(prefs),
        downloadsListSort = DownloadsPreferenceSpecs.DOWNLOADS_LIST_SORT.readStored(prefs),
        downloadsListStatusFilter = DownloadsPreferenceSpecs.DOWNLOADS_LIST_STATUS_FILTER.readStored(prefs),
        selectionHintSeen = DownloadsPreferenceSpecs.DOWNLOADS_SELECTION_HINT_SEEN.readStored(prefs),
        downloadStorageLocation = DownloadsPreferenceSpecs.DOWNLOAD_STORAGE_LOCATION.readStored(prefs),
        autoDeleteAfterWatch = DownloadsPreferenceSpecs.AUTO_DELETE_AFTER_WATCH.readStored(prefs),
        cellularDownloadSizeWarningMb = DownloadsPreferenceSpecs.CELLULAR_DOWNLOAD_SIZE_WARNING_MB.readFrom(prefs),
        downloadScheduleEnabled = DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_ENABLED.readStored(prefs),
        downloadScheduleWindow = DownloadScheduleWindow(
            startHour = DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_START.readFrom(prefs),
            endHour = DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_END.readFrom(prefs),
            wifiOnly = DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_WIFI_ONLY.readStored(prefs),
        ),
    )

    /**
     * The active user's auto-download server allow-list, read straight from a
     * [Preferences] snapshot — the `HomeDiscoveryStore.read` shape: the
     * namespace is resolved from the snapshot itself, and pre-login (no active
     * user) serves the default (empty = all servers). A corrupted JSON blob
     * degrades to the default rather than throwing into the slice collector.
     * The [DownloadsPreferenceSpecs.AUTO_DOWNLOAD_SERVERS] row is the
     * declaration-only custom declaration (the namespaced slot never holds
     * data under the canonical name), so this hand read is the row's codec.
     */
    private fun readAutoDownloadServers(prefs: Preferences): Set<String> {
        val userId = identityStore.activeUserIdIn(prefs) ?: return emptySet()
        val raw = prefs[UserNamespacedKeys.stringKey(userId, Keys.AUTO_DOWNLOAD_SERVERS)] ?: return emptySet()
        return runCatching { json.decodeFromString<Set<String>>(raw) }.getOrDefault(emptySet())
    }

    // ------------------------------------------------------------------
    // Setters — derived rows delegate to their row's derived write (the
    // encoding — enum-by-name, plain slot — is the row's, not re-declared
    // here); the declaration-only rows, the mode's legacy mirror and the
    // coalesced schedule window keep their hand-written lines.
    // ------------------------------------------------------------------

    suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        dataStore.edit { DownloadsPreferenceSpecs.WIFI_ONLY_DOWNLOADS.writeTo(it, enabled) }
    }

    suspend fun setDownloadConnections(count: Int) {
        dataStore.edit { DownloadsPreferenceSpecs.DOWNLOAD_CONNECTIONS.writeTo(it, count) }
    }

    /** The 1..6 max-concurrent invariant: the setter is the write-side clamp owner. */
    suspend fun setMaxConcurrentDownloads(count: Int) {
        dataStore.edit {
            DownloadsPreferenceSpecs.MAX_CONCURRENT_DOWNLOADS.writeTo(it, count.coerceIn(1, 6))
        }
    }

    suspend fun setDownloadQuality(quality: DownloadQuality) {
        dataStore.edit { DownloadsPreferenceSpecs.DOWNLOAD_QUALITY.writeTo(it, quality) }
    }

    suspend fun setSmartDownloadsEnabled(enabled: Boolean) {
        dataStore.edit { DownloadsPreferenceSpecs.SMART_DOWNLOADS_ENABLED.writeTo(it, enabled) }
    }

    /**
     * Writes the mode to the canonical key and mirrors it onto the legacy
     * boolean (`mode != NEVER`) so an app downgrade keeps auto-download on
     * for INSTANT/ASK users rather than silently flipping it off — the
     * cross-key invariant the [DownloadsPreferenceSpecs.AUTO_DOWNLOAD_MODE]
     * row's null encode opts out of.
     */
    suspend fun setAutoDownloadMode(mode: AutoDownloadMode) {
        dataStore.edit {
            it[Keys.AUTO_DOWNLOAD_MODE] = mode.name
            it[Keys.AUTO_DOWNLOAD_NEW_EPISODES] = mode.isEnabled
        }
    }

    /** Lookahead window in episodes (0 = legacy all-missing behavior); banded to 0..[MAX_LOOKAHEAD]. */
    suspend fun setAutoDownloadLookahead(episodes: Int) {
        dataStore.edit {
            DownloadsPreferenceSpecs.AUTO_DOWNLOAD_LOOKAHEAD.writeTo(it, episodes.coerceIn(0, MAX_LOOKAHEAD))
        }
    }

    /** Per-pass enqueue budget across series (0 = unlimited); banded to 0..[MAX_PER_PASS_LIMIT]. */
    suspend fun setAutoDownloadMaxPerPass(count: Int) {
        dataStore.edit {
            DownloadsPreferenceSpecs.AUTO_DOWNLOAD_MAX_PER_PASS.writeTo(it, count.coerceIn(0, MAX_PER_PASS_LIMIT))
        }
    }

    /** Keep-days retention window (0 = off); floored at 0. */
    suspend fun setAutoDownloadKeepDays(days: Int) {
        dataStore.edit {
            DownloadsPreferenceSpecs.AUTO_DOWNLOAD_KEEP_DAYS.writeTo(it, days.coerceAtLeast(0))
        }
    }

    /**
     * Writes the active user's server allow-list into their `u_<userId>::`
     * namespace. The user is resolved from the very snapshot being edited (the
     * `HomeDiscoveryStore.editForUser` pattern) so the write is atomic with
     * respect to concurrent user switches; pre-login there is no namespace, so
     * the write is skipped.
     */
    suspend fun setAutoDownloadServers(serverIds: Set<String>) {
        dataStore.edit { prefs ->
            val userId = identityStore.activeUserIdIn(prefs) ?: return@edit
            prefs[UserNamespacedKeys.stringKey(userId, Keys.AUTO_DOWNLOAD_SERVERS)] = json.encodeToString(serverIds)
        }
    }

    suspend fun setMaxDownloadStorageGb(gb: Int) {
        dataStore.edit { DownloadsPreferenceSpecs.MAX_DOWNLOAD_STORAGE_GB.writeTo(it, gb) }
    }

    /** Persists the downloads-list filter (an enum name string; see [DownloadsPreferenceSpecs.DOWNLOADS_LIST_FILTER]). */
    suspend fun setDownloadsListFilter(value: String) {
        dataStore.edit { it[Keys.DOWNLOADS_LIST_FILTER] = value }
    }

    /** Persists the downloads-list sort (an enum name string; see [DownloadsPreferenceSpecs.DOWNLOADS_LIST_SORT]). */
    suspend fun setDownloadsListSort(value: String) {
        dataStore.edit { it[Keys.DOWNLOADS_LIST_SORT] = value }
    }

    /** Persists the downloads-list status filter (an enum name string; see [DownloadsPreferenceSpecs.DOWNLOADS_LIST_STATUS_FILTER]). */
    suspend fun setDownloadsListStatusFilter(value: String) {
        dataStore.edit { it[Keys.DOWNLOADS_LIST_STATUS_FILTER] = value }
    }

    /** Marks the long-press selection hint as seen (terminal; see [DownloadsPreferenceSpecs.DOWNLOADS_SELECTION_HINT_SEEN]). */
    suspend fun setSelectionHintSeen(seen: Boolean) {
        dataStore.edit { it[Keys.DOWNLOADS_SELECTION_HINT_SEEN] = seen }
    }

    suspend fun setDownloadStorageLocation(location: String) {
        dataStore.edit { it[Keys.DOWNLOAD_STORAGE_LOCATION] = location }
    }

    suspend fun setAutoDeleteAfterWatch(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_DELETE_AFTER_WATCH] = enabled }
    }

    suspend fun setCellularDownloadSizeWarningMb(sizeMb: Int) {
        dataStore.edit { DownloadsPreferenceSpecs.CELLULAR_DOWNLOAD_SIZE_WARNING_MB.writeTo(it, sizeMb) }
    }

    suspend fun setDownloadScheduleEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DOWNLOAD_SCHEDULE_ENABLED] = enabled }
    }

    /** One window, three keys, one transaction — the coalesced hand write. */
    suspend fun setDownloadScheduleWindow(window: DownloadScheduleWindow) {
        dataStore.edit {
            it[Keys.DOWNLOAD_SCHEDULE_START] = window.startHour
            it[Keys.DOWNLOAD_SCHEDULE_END] = window.endHour
            it[Keys.DOWNLOAD_SCHEDULE_WIFI_ONLY] = window.wifiOnly
        }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Derived as the
     * union of the [resetKeysFor] category lists (in enum declaration order) —
     * those lists are what the facade actually resets, so deriving from them
     * (instead of maintaining a parallel hand-written union) keeps this list
     * from drifting out of sync. This is the downloads subset of the legacy
     * `DOWNLOADS_NETWORK` reset category — the network/offline keys now belong
     * to [com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore].
     */
    internal val resetKeys: List<Preferences.Key<*>> =
        PreferenceResetCategory.entries.flatMap(::resetKeysFor)

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category] — the [DownloadsPreferenceSpecs] rows whose declared reset
     * category matches, mapped to their derived keys. The download keys all
     * sit in the legacy `DOWNLOADS_NETWORK` reset category (which the
     * network/offline keys also share), so all other categories return an
     * empty list.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        DownloadsPreferenceSpecs.resetKeysFor(category)

    /**
     * Factory-reset participation for the **dynamic** allow-list key: the static
     * [resetKeysFor] list cannot express `u_<userId>::auto_download_servers`
     * entries (one set per user that has ever signed in), so the reset machinery
     * calls this inside its edit (the `HomeDiscoveryStore.removeDynamicResetKeys`
     * pattern). Canonical-suffix matching
     * ([UserNamespacedKeys.isNamespaced]) keeps it precise: an unrelated key
     * that merely starts with `u_` is never touched.
     */
    internal fun removeDynamicResetKeys(category: PreferenceResetCategory, prefs: MutablePreferences) {
        if (category != PreferenceResetCategory.DOWNLOADS_NETWORK) return
        prefs.asMap().keys
            .filter { UserNamespacedKeys.isNamespaced(it.name, setOf(Keys.AUTO_DOWNLOAD_SERVERS.name)) }
            .forEach { prefs.remove(it) }
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore via its row's derived write (the same encoding the row reads
     * with) — except the mode's legacy mirror, the namespaced allow-list and
     * the declaration-only rows, whose hand-written lines mirror the setters
     * (see the class KDoc's opt-outs), plus the gap keys the legacy
     * facade-level `restorePreferences` omits (`cellular_download_size_warning_mb`,
     * `download_schedule_enabled`, and the schedule-window keys destructured
     * from [DownloadsSlice.downloadScheduleWindow]).
     */
    suspend fun restore(slice: DownloadsSlice) {
        dataStore.edit { prefs ->
            DownloadsPreferenceSpecs.WIFI_ONLY_DOWNLOADS.writeTo(prefs, slice.wifiOnlyDownloads)
            DownloadsPreferenceSpecs.DOWNLOAD_CONNECTIONS.writeTo(prefs, slice.downloadConnections)
            DownloadsPreferenceSpecs.MAX_CONCURRENT_DOWNLOADS.writeTo(prefs, slice.maxConcurrentDownloads)
            DownloadsPreferenceSpecs.DOWNLOAD_QUALITY.writeTo(prefs, slice.downloadQuality)
            DownloadsPreferenceSpecs.SMART_DOWNLOADS_ENABLED.writeTo(prefs, slice.smartDownloadsEnabled)
            // The mode's cross-key mirror: the canonical enum plus the legacy
            // boolean beside it (see the class KDoc's opt-outs).
            prefs[Keys.AUTO_DOWNLOAD_MODE] = slice.autoDownloadMode.name
            prefs[Keys.AUTO_DOWNLOAD_NEW_EPISODES] = slice.autoDownloadMode.isEnabled
            DownloadsPreferenceSpecs.AUTO_DOWNLOAD_LOOKAHEAD.writeTo(prefs, slice.autoDownloadLookahead)
            DownloadsPreferenceSpecs.AUTO_DOWNLOAD_MAX_PER_PASS.writeTo(prefs, slice.autoDownloadMaxPerPass)
            DownloadsPreferenceSpecs.AUTO_DOWNLOAD_KEEP_DAYS.writeTo(prefs, slice.autoDownloadKeepDays)
            DownloadsPreferenceSpecs.MAX_DOWNLOAD_STORAGE_GB.writeTo(prefs, slice.maxDownloadStorageGb)
            prefs[Keys.DOWNLOAD_STORAGE_LOCATION] = slice.downloadStorageLocation
            prefs[Keys.DOWNLOADS_LIST_FILTER] = slice.downloadsListFilter
            prefs[Keys.DOWNLOADS_LIST_SORT] = slice.downloadsListSort
            prefs[Keys.DOWNLOADS_LIST_STATUS_FILTER] = slice.downloadsListStatusFilter
            prefs[Keys.DOWNLOADS_SELECTION_HINT_SEEN] = slice.selectionHintSeen
            prefs[Keys.AUTO_DELETE_AFTER_WATCH] = slice.autoDeleteAfterWatch
            DownloadsPreferenceSpecs.CELLULAR_DOWNLOAD_SIZE_WARNING_MB.writeTo(prefs, slice.cellularDownloadSizeWarningMb)
            prefs[Keys.DOWNLOAD_SCHEDULE_ENABLED] = slice.downloadScheduleEnabled
            prefs[Keys.DOWNLOAD_SCHEDULE_START] = slice.downloadScheduleWindow.startHour
            prefs[Keys.DOWNLOAD_SCHEDULE_END] = slice.downloadScheduleWindow.endHour
            prefs[Keys.DOWNLOAD_SCHEDULE_WIFI_ONLY] = slice.downloadScheduleWindow.wifiOnly
            // The allow-list is namespaced: write into the active user's
            // namespace (skipped pre-login — no namespace to write into).
            val userId = identityStore.activeUserIdIn(prefs) ?: return@edit
            prefs[UserNamespacedKeys.stringKey(userId, Keys.AUTO_DOWNLOAD_SERVERS)] = json.encodeToString(slice.autoDownloadServers)
        }
    }

    companion object {
        /** Default lookahead window (episodes past the highest downloaded/watched). */
        internal const val DEFAULT_LOOKAHEAD = 3

        /** Upper bound of the lookahead picker. */
        internal const val MAX_LOOKAHEAD = 10

        /** Upper bound of the max-per-pass picker (0 = unlimited). */
        internal const val MAX_PER_PASS_LIMIT = 50
    }
}

/**
 * The downloads preference slice. Plain data class. Every default IS its
 * [DownloadsPreferenceSpecs] row's declared default — read off the row via a
 * plain accessor, so a preference's default is stated exactly once, in its
 * spec, and cannot drift between the spec (what an absent key reads) and the
 * slice (what a fresh in-memory instance serves). The four banded rows keep
 * their pre-band declared defaults here, exactly as the slice always has. The
 * legacy-only row (`auto_download_new_episodes`) has no slice property by
 * design. The spec-default equality is pinned field-for-field by
 * `DownloadsSpecDerivationTest`.
 */
@Immutable
@Serializable
data class DownloadsSlice(
    val wifiOnlyDownloads: Boolean = DownloadsPreferenceSpecs.WIFI_ONLY_DOWNLOADS.default,
    val downloadConnections: Int = DownloadsPreferenceSpecs.DOWNLOAD_CONNECTIONS.default,
    val maxConcurrentDownloads: Int = DownloadsPreferenceSpecs.MAX_CONCURRENT_DOWNLOADS.default,
    val downloadQuality: DownloadQuality = DownloadsPreferenceSpecs.DOWNLOAD_QUALITY.default,
    val smartDownloadsEnabled: Boolean = DownloadsPreferenceSpecs.SMART_DOWNLOADS_ENABLED.default,
    /**
     * The tri-state new-episode policy (gh-181). A pre-gh-181 settings backup
     * carries only the legacy boolean, so restoring one lands on the NEVER
     * default — the toggle must be re-set after such a restore.
     */
    val autoDownloadMode: AutoDownloadMode = DownloadsPreferenceSpecs.AUTO_DOWNLOAD_MODE.default,
    /** Episodes past the highest downloaded/watched per season (0 = all missing). */
    val autoDownloadLookahead: Int = DownloadsPreferenceSpecs.AUTO_DOWNLOAD_LOOKAHEAD.default,
    /** Global per-pass enqueue budget across series (0 = unlimited). */
    val autoDownloadMaxPerPass: Int = DownloadsPreferenceSpecs.AUTO_DOWNLOAD_MAX_PER_PASS.default,
    /** Keep-days retention window for completed downloads (0 = off). */
    val autoDownloadKeepDays: Int = DownloadsPreferenceSpecs.AUTO_DOWNLOAD_KEEP_DAYS.default,
    /** Active user's auto-download server allow-list (empty = all servers). */
    val autoDownloadServers: Set<String> = DownloadsPreferenceSpecs.AUTO_DOWNLOAD_SERVERS.default,
    val maxDownloadStorageGb: Int = DownloadsPreferenceSpecs.MAX_DOWNLOAD_STORAGE_GB.default,
    /** Downloads-list filter as an enum name string ("" = feature default). */
    val downloadsListFilter: String = DownloadsPreferenceSpecs.DOWNLOADS_LIST_FILTER.default,
    /** Downloads-list sort as an enum name string ("" = feature default). */
    val downloadsListSort: String = DownloadsPreferenceSpecs.DOWNLOADS_LIST_SORT.default,
    /** Downloads-list status filter as an enum name string ("" = feature default). */
    val downloadsListStatusFilter: String = DownloadsPreferenceSpecs.DOWNLOADS_LIST_STATUS_FILTER.default,
    /** The long-press selection hint was dismissed once; never show it again. */
    val selectionHintSeen: Boolean = DownloadsPreferenceSpecs.DOWNLOADS_SELECTION_HINT_SEEN.default,
    val downloadStorageLocation: String = DownloadsPreferenceSpecs.DOWNLOAD_STORAGE_LOCATION.default,
    val autoDeleteAfterWatch: Boolean = DownloadsPreferenceSpecs.AUTO_DELETE_AFTER_WATCH.default,
    val cellularDownloadSizeWarningMb: Int = DownloadsPreferenceSpecs.CELLULAR_DOWNLOAD_SIZE_WARNING_MB.default,
    val downloadScheduleEnabled: Boolean = DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_ENABLED.default,
    val downloadScheduleWindow: DownloadScheduleWindow = DownloadScheduleWindow(
        startHour = DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_START.default,
        endHour = DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_END.default,
        wifiOnly = DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_WIFI_ONLY.default,
    ),
)
