package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.json.Json

/**
 * Shared low-level preference encoding helpers used by [UserPreferencesStore] and
 * the per-domain preference stores being extracted from it.
 *
 * These are **not** domain logic — they are the typed-key read/migrate primitives
 * that every preference slice needs:
 *
 *  - the one-shot legacy-string → typed-key migration (gated on
 *    [TYPED_MIGRATION_DONE]) and the dual-read fallback that supports it;
 *  - the shared [Json] instances;
 *  - the [ParsedCache] memoisation holder for JSON-decoded preference blobs.
 *
 * Centralising them here lets a domain store own its keys + invariants without
 * re-implementing the legacy-read dance, and keeps the migration gate keyed off
 * a single flag. Nothing here references a specific preference domain.
 */
internal object PreferenceCodec {

    /** Shared lenient decoder for JSON-encoded preference blobs. */
    val json: Json = Json { ignoreUnknownKeys = true }

    /** Encoder that writes default values (used by `restorePreferences`). */
    val encodeDefaultsJson: Json = Json { encodeDefaults = true }

    /**
     * One-shot guard for the legacy-string → typed-key migration. Read by
     * [readBool]/[readInt]/[readFloat]/[readLong] to decide whether the legacy
     * string fallback is still needed.
     */
    val TYPED_MIGRATION_DONE = booleanPreferencesKey("_typed_migration_done")

    // ------------------------------------------------------------------
    // Typed reads with legacy string-key fallback
    // ------------------------------------------------------------------

    fun readBool(prefs: Preferences, key: Preferences.Key<Boolean>, name: String, default: Boolean): Boolean {
        val typed = try { prefs[key] } catch (_: ClassCastException) { null }
        // Once the one-shot typed-key migration has run, the legacy string-key
        // fallback is no longer needed — every key was rewritten in place — so
        // skip the extra string lookup on every preference emission.
        if (typed != null || prefs[TYPED_MIGRATION_DONE] != true) {
            return typed ?: prefs[stringPreferencesKey(name)]?.toBoolean() ?: default
        }
        return typed ?: default
    }

    fun readInt(prefs: Preferences, key: Preferences.Key<Int>, name: String, default: Int): Int {
        val typed = try { prefs[key] } catch (_: ClassCastException) { null }
        if (typed != null || prefs[TYPED_MIGRATION_DONE] != true) {
            return typed ?: prefs[stringPreferencesKey(name)]?.toIntOrNull() ?: default
        }
        return typed ?: default
    }

    fun readFloat(prefs: Preferences, key: Preferences.Key<Float>, name: String, default: Float): Float {
        val typed = try { prefs[key] } catch (_: ClassCastException) { null }
        if (typed != null || prefs[TYPED_MIGRATION_DONE] != true) {
            return typed ?: prefs[stringPreferencesKey(name)]?.toFloatOrNull() ?: default
        }
        return typed ?: default
    }

    fun readLong(prefs: Preferences, key: Preferences.Key<Long>, name: String, default: Long): Long {
        val typed = try { prefs[key] } catch (_: ClassCastException) { null }
        if (typed != null || prefs[TYPED_MIGRATION_DONE] != true) {
            return typed ?: prefs[stringPreferencesKey(name)]?.toLongOrNull() ?: default
        }
        return typed ?: default
    }

    // ------------------------------------------------------------------
    // One-shot legacy-string → typed-key migration
    // ------------------------------------------------------------------

    /**
     * Rewrites every legacy string-keyed slot in [legacyNames] to its typed
     * equivalent, iff the one-shot migration has not already run. Idempotent:
     * a typed value already present is left untouched, and the gate flag is set
     * once for the whole batch. Intended to be called once at construction from
     * a single owner (today [UserPreferencesStore]; later a dedicated
     * orchestrator), not per store.
     */
    suspend fun runTypedKeyMigration(
        dataStore: DataStore<Preferences>,
        booleans: Array<String>,
        ints: Array<String>,
        floats: Array<String>,
        longs: Array<String>,
    ) {
        dataStore.edit { prefs ->
            if (prefs[TYPED_MIGRATION_DONE] == true) return@edit
            migrateBooleans(prefs, *booleans)
            migrateInts(prefs, *ints)
            migrateFloats(prefs, *floats)
            migrateLongs(prefs, *longs)
            prefs[TYPED_MIGRATION_DONE] = true
        }
    }

    fun migrateBooleans(prefs: MutablePreferences, vararg names: String) {
        for (name in names) {
            val legacy = prefs.legacyString(name) ?: continue
            prefs[booleanPreferencesKey(name)] = legacy.toBoolean()
        }
    }

    fun migrateInts(prefs: MutablePreferences, vararg names: String) {
        for (name in names) {
            val legacy = prefs.legacyString(name) ?: continue
            legacy.toIntOrNull()?.let { prefs[intPreferencesKey(name)] = it }
        }
    }

    fun migrateFloats(prefs: MutablePreferences, vararg names: String) {
        for (name in names) {
            val legacy = prefs.legacyString(name) ?: continue
            legacy.toFloatOrNull()?.let { prefs[floatPreferencesKey(name)] = it }
        }
    }

    fun migrateLongs(prefs: MutablePreferences, vararg names: String) {
        for (name in names) {
            val legacy = prefs.legacyString(name) ?: continue
            legacy.toLongOrNull()?.let { prefs[longPreferencesKey(name)] = it }
        }
    }

    /**
     * Reads a legacy string slot, tolerating a typed value (Boolean/Int/...)
     * already living under [name] — e.g. after `clearAllPreferences` preserved
     * some typed state but reset the migration flag. Returns null when the slot
     * is absent or holds a non-string value, so callers `?: continue`.
     */
    fun MutablePreferences.legacyString(name: String): String? =
        try { this[stringPreferencesKey(name)] } catch (_: ClassCastException) { null }

}

/**
 * Memoisation holder for a JSON-decoded preference blob, keyed on the raw
 * string so the decode is skipped when the underlying key has not changed on a
 * given `dataStore.data` emission.
 */
internal data class ParsedCache<T>(
    val raw: String?,
    val value: T,
)
