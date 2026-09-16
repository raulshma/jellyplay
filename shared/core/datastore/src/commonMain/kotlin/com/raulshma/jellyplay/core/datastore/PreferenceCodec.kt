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
 *  - the [ParsedCache] memoisation holder for JSON-decoded preference blobs,
 *    and [cachedJson] — the shared compare/decode/publish read built on it.
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

    // ------------------------------------------------------------------
    // Cached JSON decode (ParsedCache memoisation)
    // ------------------------------------------------------------------

    /**
     * Shared cached JSON decode for the blob readers, collapsing the per-key
     * "compare-raw → try/decode → update-cache" boilerplate every domain store
     * had hand-copied. Returns the cached value when [cacheKey] (the raw string
     * by default) is unchanged; otherwise computes the value — [parse] for a
     * non-null raw (falling back to [default] on decode failure), [onNull] or
     * [default] for a null raw — publishes the new [ParsedCache] through
     * [cacheRef], and returns the value.
     *
     * **Decisions and named policies:**
     *
     *  - **A decode failure caches its result.** The fallback [default] is
     *    published through [cacheRef] like any other value, so a corrupt blob
     *    degrades to [default] exactly once instead of re-running the decode
     *    (and re-failing) on every `dataStore.data` emission — matching what
     *    every hand-copied site did with its `try/catch { default }.also { … }`.
     *  - **Null-raw handling is an explicit per-site policy** ([nullPolicy],
     *    deliberately NOT defaulted — each conversion names the policy the site
     *    already had, never silently unified):
     *      - [CachedJsonNullPolicy.MemoizeNull] — a null raw is a cacheable
     *        input like any other: the first null read computes [onNull]'s
     *        value (or [default] when [onNull] is absent) and memoises it, and
     *        later null reads are served from the cache.
     *      - [CachedJsonNullPolicy.NoMemoOnNull] — a null raw NEVER consults
     *        the memo: the null-raw value may depend on inputs the raw string
     *        does not reflect (e.g. VideoPlayerStore's legacy-boolean
     *        fallback), so every null read re-derives it via [onNull]. A
     *        non-null raw memoises normally; this policy keys on [raw] alone
     *        and ignores [cacheKey].
     *  - **[cacheKey] supports readers whose decoded value depends on more
     *    than the raw string** (HomeDiscoveryStore's version-unioned
     *    enabled-section set folds the schema-version stamp into it). A
     *    composite key is only meaningful under [CachedJsonNullPolicy.MemoizeNull].
     *  - **[onNull] owns the null-raw value** for readers whose null path is
     *    not a constant (the NoMemoOnNull legacy fallback); absent it, null
     *    yields [default]. The try/catch → [default] guard wraps only the
     *    non-null [parse] path — an [onNull] closure owns its own failure
     *    policy.
     */
    fun <T> cachedJson(
        raw: String?,
        cache: ParsedCache<T>,
        default: T,
        parse: (String) -> T,
        cacheRef: (ParsedCache<T>) -> Unit,
        nullPolicy: CachedJsonNullPolicy,
        cacheKey: String? = raw,
        onNull: (() -> T)? = null,
    ): T {
        fun compute(): T = when {
            raw != null -> try { parse(raw) } catch (_: Exception) { default }
            onNull != null -> onNull()
            else -> default
        }
        return when (nullPolicy) {
            CachedJsonNullPolicy.MemoizeNull -> {
                if (cacheKey == cache.key) return cache.value
                val value = compute()
                cacheRef(ParsedCache(cacheKey, value))
                value
            }
            CachedJsonNullPolicy.NoMemoOnNull -> {
                // The null-key write below mirrors the pre-promotion
                // VideoPlayerStore shape: it is stored but can never hit,
                // because a hit requires a non-null raw under this policy.
                if (raw != null && raw == cache.key) return cache.value
                val value = compute()
                cacheRef(ParsedCache(raw, value))
                value
            }
        }
    }

}

/**
 * Memoisation holder for a JSON-decoded preference blob, keyed on the raw
 * string — or a composite of it, for readers whose decoded value depends on
 * more than the raw string alone — so the decode is skipped when the inputs
 * have not changed on a given `dataStore.data` emission.
 */
internal data class ParsedCache<T>(
    val key: String?,
    val value: T,
)

/**
 * The two named null-input policies of [PreferenceCodec.cachedJson], promoted
 * from the two hand-rolled shapes the domain stores had diverged into. A null
 * raw means "the blob key is absent from this snapshot" — whether that absence
 * is memoisable is a per-reader decision, so it is named at every call site
 * rather than defaulted.
 */
internal enum class CachedJsonNullPolicy {
    /**
     * A null raw is a cacheable input like any other (the HomeDiscovery /
     * PlayerEngine / Widget — and, pre-promotion, every hand-copied — shape):
     * the first null read resolves the null value once and memoises it, so
     * later null reads are served from the cache. Right choice when the
     * null-raw value is a pure function of the raw input (i.e. it is just the
     * [PreferenceCodec.cachedJson] `default`).
     */
    MemoizeNull,

    /**
     * A null raw never consults the memo (the VideoPlayerStore shape): the
     * null-raw value may be derived from OTHER preference keys (the legacy
     * boolean fallback), which the raw string does not reflect, so every null
     * read re-derives it. A non-null raw memoises normally, keyed on the raw
     * string alone.
     */
    NoMemoOnNull,
}
