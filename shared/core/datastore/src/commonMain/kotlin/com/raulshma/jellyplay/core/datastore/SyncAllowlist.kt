package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

/**
 * The sync-allowlist machinery shared by the integration stores' sync-only
 * surfaces ([SeerrPreferencesStore.syncSnapshot], [ArrPreferencesStore],
 * [SubtitleProviderPreferencesStore]): the typed key entries and the
 * snapshot/apply bodies every store would otherwise keep a private copy of.
 * The allowlist MAPS stay in the owning stores — they are each store's single
 * source of truth for which key names may ever leave the device or be written
 * by sync.
 */

/**
 * One allowlisted sync key: the typed DataStore key with its raw-string
 * read/write lambdas and the raw default its reset writes. The type
 * parameter is pinned at construction, so the sync faces never dispatch
 * on the erased `Key<*>` — the entry knows its kind.
 */
internal class SyncEntry(
    val key: Preferences.Key<*>,
    /** The raw default a `null` sync write (a reset) stores — never a removal. */
    val default: String,
    val read: (Preferences) -> String?,
    val write: (MutablePreferences, String) -> Unit,
)

internal fun booleanEntry(
    key: Preferences.Key<Boolean>,
    default: Boolean,
) = SyncEntry(
    key,
    default = default.toString(),
    read = { prefs -> prefs[key]?.toString() },
    write = { prefs, raw -> raw.toBooleanStrictOrNull()?.let { prefs[key] = it } },
)

internal fun stringEntry(
    key: Preferences.Key<String>,
    default: String,
    normalize: (String) -> String = { it },
) = SyncEntry(
    key,
    default = default,
    read = { prefs -> prefs[key] },
    write = { prefs, raw -> prefs[key] = normalize(raw) },
)

internal fun intEntry(
    key: Preferences.Key<Int>,
    default: Int,
    floor: Int? = null,
) = SyncEntry(
    key,
    default = default.toString(),
    read = { prefs -> prefs[key]?.toString() },
    write = { prefs, raw ->
        raw.toIntOrNull()
            ?.let { if (floor != null) it.coerceAtLeast(floor) else it }
            ?.let { prefs[key] = it }
    },
)

/**
 * One store's compiled allowlist: the raw key names → their typed entries,
 * plus the snapshot/entry lookups the store's `syncSnapshot`/`syncApply`
 * faces delegate to. Unallowlisted keys are ignored in BOTH directions by
 * construction (a hostile or stale server row can never write a key the
 * store does not sync).
 */
internal class SyncAllowlist(
    private val entries: Map<String, SyncEntry>,
) {

    /** The raw key names the sync faces may ever touch. */
    val keys: Set<String> get() = entries.keys

    /** The raw stored value per allowlisted key (`null` = absent — readers fall back to that key's default). */
    fun snapshot(prefs: Preferences): Map<String, String?> = entries.mapValues { (_, entry) -> entry.read(prefs) }

    fun entry(key: String): SyncEntry? = entries[key]
}

/**
 * The integration stores' shared `syncSnapshot` body: one degraded read, the
 * allowlisted raw values out (`null` = the key is absent — readers fall back
 * to that key's default). A corrupt DataStore read degrades to all-absent per
 * the module's corrupt-read policy.
 */
internal suspend fun SyncAllowlist.snapshotFrom(
    dataStore: DataStore<Preferences>,
): Map<String, String?> = snapshot(dataStore.dataDegradingToDefaults().first())

/**
 * The integration stores' shared `syncApply` body: writes one allowlisted raw
 * value, or resets the key to its entry default when [value] is `null` — the
 * reset WRITES the default, never a removal: value-presence resets roam as
 * value writes through the value-only adapter, absence does not, so a removal
 * would leave the server's still-standing row to re-adopt on a later cycle
 * and undo the reset. Unallowlisted keys are ignored in BOTH directions; an
 * uncoercible value skips the write, leaving the local value (the
 * prefs-adapter coercion rule).
 */
internal suspend fun SyncAllowlist.applyTo(
    dataStore: DataStore<Preferences>,
    key: String,
    value: String?,
) {
    val entry = entry(key) ?: return
    dataStore.edit { prefs ->
        entry.write(prefs, if (value == null) entry.default else value)
    }
}
