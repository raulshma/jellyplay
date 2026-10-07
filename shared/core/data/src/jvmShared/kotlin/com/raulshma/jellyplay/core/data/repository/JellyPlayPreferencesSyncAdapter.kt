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
 * (per-device namespaces), and anything in [excludedKeys] (secrets and
 * device/session identity — see docs/jellyplay-plugin.md's "never synced"
 * list; the exclusion sets are exported by the owning stores so the key
 * names cannot drift). Outbound ([snapshot]) they never leave the device;
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
    override val namespace: String = "prefs",
    private val excludedPrefixes: List<String> = emptyList(),
    private val excludedKeys: Set<String> = emptySet(),
) : ProfileSyncAdapter {

    private fun isReserved(name: String) = name.startsWith(RESERVED_PREFIX)

    private fun isExcluded(name: String) =
        name in excludedKeys || excludedPrefixes.any { name.startsWith(it) }

    override suspend fun snapshot(): Map<String, JsonElement> {
        val prefs = dataStore.data.first()
        return prefs.asMap().keys
            .filter { key -> !isReserved(key.name) && !isExcluded(key.name) && prefs[key] !is ByteArray }
            .associate { key -> key.name to toJson(prefs[key]) }
    }

    override suspend fun dirtyValues(current: Map<String, JsonElement>): Map<String, JsonElement> {
        val prefs = dataStore.data.first()
        return current.filter { (name, value) ->
            val mirrored = prefs[stringPreferencesKey(MIRROR_PREFIX + name)]
            // Never-synced key = dirty by definition (mirror holds no entry).
            mirrored == null || mirrored != value.toString()
        }
    }

    override suspend fun applyRemote(entries: Map<String, JsonElement>) {
        if (entries.isEmpty()) return
        val current = dataStore.data.first()
        dataStore.edit { prefs ->
            entries.forEach { (name, value) ->
                // Never-synced and reserved names are dropped inbound too: a
                // server holding pre-exclusion leaked rows (or a hostile one)
                // must not re-write secrets, identity, or mirror state here.
                if (isReserved(name) || isExcluded(name)) return@forEach
                if (value is JsonNull) {
                    prefs.remove(stringPreferencesKey(name))
                    return@forEach
                }
                if (value !is JsonPrimitive) return@forEach // objects/arrays-of-objects unsupported in Preferences

                // Name-based lookup: Preferences.get(key) casts by key type and would
                // throw reading e.g. an Int value under a String key.
                val existing = current.asMap().entries.firstOrNull { it.key.name == name }?.value
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

    override suspend fun markSynced(values: Map<String, JsonElement>) {
        dataStore.edit { prefs ->
            values.forEach { (name, value) ->
                if (isReserved(name) || isExcluded(name)) return@forEach
                prefs[stringPreferencesKey(MIRROR_PREFIX + name)] = value.toString()
            }
        }
    }

    /**
     * Applies remote tombstones: the server deleted these keys (a namespace
     * reset's tombstone batch, or another device's roaming delete) — the local
     * values AND their mirror entries go, so the adopted delete neither
     * resurrects the setting nor re-reads as a local edit. Reserved/excluded
     * names are dropped inbound like everywhere else (a hostile or stale row
     * cannot touch secrets, identity, or mirror state). [deletedKeys] stays
     * the SPI default: a removed pref means "reset to default", never
     * "deleted everywhere", so prefs never push tombstones.
     */
    override suspend fun deleteRemote(keys: Set<String>) {
        if (keys.isEmpty()) return
        dataStore.edit { prefs ->
            keys.forEach { name ->
                if (isReserved(name) || isExcluded(name)) return@forEach
                // Key equality in DataStore is name-based, so the String-typed
                // key removes the entry whatever kind is stored under the name.
                prefs.remove(stringPreferencesKey(name))
                prefs.remove(stringPreferencesKey(MIRROR_PREFIX + name))
            }
        }
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

    private companion object {
        /**
         * The whole reserved `jpsync.` space — mirror state (`jpsync.mirror.`),
         * device identity (`jpsync.device.`), sync cursors (`jpsync.cursor.`)
         * — never syncs in either direction.
         */
        const val RESERVED_PREFIX = "jpsync."
        const val MIRROR_PREFIX = "jpsync.mirror."
        const val DEVICE_PREFIX = "jpsync.device."
    }
}
