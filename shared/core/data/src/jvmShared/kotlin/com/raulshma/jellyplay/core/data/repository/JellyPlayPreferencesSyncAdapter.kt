package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/**
 * The first [ProfileSyncAdapter]: syncs the raw key/value map of one
 * `DataStore<Preferences>` under namespace [namespace], mirroring the
 * last-synced snapshot INSIDE the same DataStore under a reserved
 * `jpsync.mirror.` prefix (one file, no second DataStore instance; mirror
 * keys are excluded from the synced set so state can't feed back).
 *
 * Excluded from sync by design — in BOTH directions: `ByteArray` values
 * (opaque binaries never sync), anything matching [excludedPrefixes]
 * (per-device namespaces), and anything in [excludedKeys] (secrets,
 * device/session identity, and the widget payload cache — see
 * docs/jellyplay-plugin.md's "never synced" list; the exclusion sets are
 * exported by the owning stores so the key names cannot drift). Outbound
 * ([snapshot]) they never leave the device;
 * inbound ([applyRemote]/[markSynced]) they are dropped even if a server
 * still holds pre-exclusion leaked rows or is hostile — the whole reserved
 * `jpsync.*` space (mirrors, device identity, sync cursors) is likewise
 * ignored so server state can't feed back.
 *
 * KIND PRESERVATION is the adapter's one hard rule: DataStore keys are
 * kind-locked by name — a `Float` read as `Double` crashes the app's readers.
 * Incoming values are coerced into the kind already stored locally for that
 * key; only keys new to this device take the incoming kind (nothing to break).
 */
class JellyPlayPreferencesSyncAdapter(
    private val dataStore: DataStore<Preferences>,
    namespace: String = "prefs",
    private val excludedPrefixes: List<String> = emptyList(),
    private val excludedKeys: Set<String> = emptySet(),
) : MirrorProfileSyncAdapter(
    namespace = namespace,
    mirrorStore = dataStore,
    // The mirror rides the SAME store as the synced set, under the reserved
    // ROOT prefix (`jpsync.mirror.<key>` — that stored shape predates
    // per-namespace mirroring and is frozen, so this adapter departs from
    // the base's per-namespace default), with the adapter's exclusion rule
    // handed to SyncMirror so the dirty/mark/clear comparison keeps exactly
    // one implementation.
    mirrorPrefix = JpsyncReservation.MIRROR_ROOT,
    shouldMirror = { name ->
        !JpsyncReservation.isReserved(name) &&
            !(name in excludedKeys || excludedPrefixes.any { name.startsWith(it) })
    },
) {

    // One-time stale-mirror prune: a key-level exclusion that landed after a
    // key had synced strands its mirror entry (`jpsync.mirror.<key>` — every
    // mirror face skips excluded keys now, so nothing would ever clear it).
    // The whole excluded set is swept once per process at the first snapshot
    // (each cycle's first adapter read): one DataStore edit, a no-op for
    // keys that never synced, idempotent after.
    private var staleMirrorPruned = false

    private fun isExcluded(name: String) =
        name in excludedKeys || excludedPrefixes.any { name.startsWith(it) }

    override suspend fun snapshot(): Map<String, JsonElement> {
        pruneStaleMirrorOnce()
        val prefs = dataStore.data.first()
        return prefs.asMap().keys
            .filter { key -> !JpsyncReservation.isReserved(key.name) && !isExcluded(key.name) && prefs[key] !is ByteArray }
            .associate { key -> key.name to toJson(prefs[key]) }
    }

    private suspend fun pruneStaleMirrorOnce() {
        if (staleMirrorPruned) return
        mirror.prune(excludedKeys)
        // Only after the sweep succeeded: a failed prune must retry on the
        // next cycle, not be abandoned for the process.
        staleMirrorPruned = true
    }

    override suspend fun applyRemote(entries: Map<String, JsonElement>) {
        if (entries.isEmpty()) return
        val current = dataStore.data.first()
        // Name-keyed once for the whole batch: a fresh-device restore can
        // pull hundreds of rows, and the per-row `firstOrNull` scan made the
        // kind lookup O(entries × store).
        val currentByName = current.asMap().mapKeys { it.key.name }
        dataStore.edit { prefs ->
            entries.forEach { (name, value) ->
                // Never-synced and reserved names are dropped inbound too: a
                // server holding pre-exclusion leaked rows (or a hostile one)
                // must not re-write secrets, identity, or mirror state here.
                if (JpsyncReservation.isReserved(name) || isExcluded(name)) return@forEach
                if (value is JsonNull) {
                    prefs.remove(stringPreferencesKey(name))
                    return@forEach
                }
                if (value !is JsonPrimitive) return@forEach // objects/arrays-of-objects unsupported in Preferences

                // Name-based lookup: Preferences.get(key) casts by key type and would
                // throw reading e.g. an Int value under a String key.
                val existing = currentByName[name]
                // Coerce FIRST, write after: an uncoercible remote value skips the
                // key entirely — the local value and its kind survive a garbage or
                // hostile row (removing first would have wiped the setting, and
                // markSynced would then bury the old value for good).
                when (existing) {
                    is Boolean -> value.booleanOrNull?.let {
                        prefs.remove(stringPreferencesKey(name))
                        prefs[booleanPreferencesKey(name)] = it
                    }
                    is Int -> coerceInt(value)?.let {
                        prefs.remove(stringPreferencesKey(name))
                        prefs[intPreferencesKey(name)] = it
                    }
                    is Long -> value.longOrNull?.let {
                        prefs.remove(stringPreferencesKey(name))
                        prefs[longPreferencesKey(name)] = it
                    }
                    is Float -> value.doubleOrNull?.let {
                        prefs.remove(stringPreferencesKey(name))
                        prefs[floatPreferencesKey(name)] = it.toFloat()
                    }
                    is Double -> value.doubleOrNull?.let {
                        prefs.remove(stringPreferencesKey(name))
                        prefs[doublePreferencesKey(name)] = it
                    }
                    is String -> {
                        prefs.remove(stringPreferencesKey(name))
                        prefs[stringPreferencesKey(name)] = value.content
                    }
                    is Set<*> -> (value as? JsonArray)
                        // A non-primitive element cannot become a String — partial
                        // conversion would silently shrink the set, so skip too.
                        ?.takeIf { array -> array.all { it is JsonPrimitive } }
                        ?.let { array ->
                            prefs.remove(stringPreferencesKey(name))
                            prefs[stringSetPreferencesKey(name)] = array.map { (it as JsonPrimitive).content }.toSet()
                        }
                    // Key new on this device: take the incoming kind.
                    null -> writeInferred(prefs, name, value)
                }
            }
        }
    }

    /**
     * Overrides [deleteRemote] WHOLE (ADR 0011 §2): an inbound prefs tombstone
     * (a namespace reset's batch, or another device's roaming delete) adopts
     * as "reset to default here" — the local value AND its mirror entry go,
     * so the adopted delete neither resurrects the setting nor re-reads as a
     * local edit. Reserved/excluded names are dropped inbound like everywhere
     * else (a hostile or stale row cannot touch secrets, identity, or mirror
     * state). [deletedKeys] stays the SPI default: a removed pref means
     * "reset to default", never "deleted everywhere", so prefs never pushes
     * tombstones.
     */
    override suspend fun deleteRemote(keys: Set<String>) {
        if (keys.isEmpty()) return
        dataStore.edit { prefs ->
            keys.forEach { name ->
                if (JpsyncReservation.isReserved(name) || isExcluded(name)) return@forEach
                // Key equality in DataStore is name-based, so the String-typed
                // key removes the entry whatever kind is stored under the name.
                prefs.remove(stringPreferencesKey(name))
            }
        }
        // The mirror entries go with the values, under the same exclusion rule
        // (SyncMirror applies the [shouldMirror] predicate): the adopted delete
        // neither resurrects the setting nor re-reads as a local edit.
        mirror.clear(keys)
    }

    private fun writeInferred(prefs: androidx.datastore.preferences.core.MutablePreferences, name: String, value: JsonPrimitive) {
        when {
            value.booleanOrNull != null -> prefs[booleanPreferencesKey(name)] = value.boolean
            value.intOrNull != null -> prefs[intPreferencesKey(name)] = value.int
            value.longOrNull != null -> prefs[longPreferencesKey(name)] = value.long
            value.doubleOrNull != null -> prefs[doublePreferencesKey(name)] = value.double
            else -> prefs[stringPreferencesKey(name)] = value.content
        }
    }

    private fun coerceInt(value: JsonPrimitive): Int? = value.intOrNull
        ?: value.longOrNull?.toInt()
        ?: value.doubleOrNull?.toInt()

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value.toDouble())
        is Double -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Set<*> -> JsonArray(value.map { JsonPrimitive(it.toString()) })
        else -> JsonPrimitive(value.toString())
    }
}
