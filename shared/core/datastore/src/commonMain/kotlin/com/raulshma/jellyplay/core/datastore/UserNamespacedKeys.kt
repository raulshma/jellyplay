package com.raulshma.jellyplay.core.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * The ONE `u_<userId>::<canonical>` per-user key namespacing grammar — the
 * build side ([name] and the typed key factories) plus the recognition side
 * ([isNamespaced]) — shared by the stores that namespace some or all of their
 * keys per active user (`HomeDiscoveryStore`, `DownloadsStore`'s
 * auto-download allow-list). One implementation instead of the per-store
 * private copies, so the grammar can never drift between them: the key
 * strings are the persisted contract (the jvmTest suites pin them raw).
 *
 * Recognition splits on the **LAST** `::`: no canonical name contains `::`,
 * but a user id might (`setActiveUser` input is unvalidated) — splitting on
 * the first separator would mis-parse `u_a::b::home_mode` as canonical
 * `b::home_mode`. lastIndexOf matches the construction side for every id,
 * `::`-containing or not.
 */
internal object UserNamespacedKeys {
    private const val PREFIX = "u_"
    private const val SEPARATOR = "::"

    /** Namespaced key name for [userId]: `u_<userId>::<canonical>`. */
    fun name(userId: String, canonical: String): String = "u_$userId::$canonical"

    fun stringKey(userId: String, canonical: Preferences.Key<String>): Preferences.Key<String> =
        stringPreferencesKey(name(userId, canonical.name))

    fun booleanKey(userId: String, canonical: Preferences.Key<Boolean>): Preferences.Key<Boolean> =
        booleanPreferencesKey(name(userId, canonical.name))

    fun intKey(userId: String, canonical: Preferences.Key<Int>): Preferences.Key<Int> =
        intPreferencesKey(name(userId, canonical.name))

    /**
     * Whether [keyName] is a `u_<userId>::<canonical>` key whose canonical
     * suffix is in [canonicalNames]. The `separator <= 2` guard ("u_" alone is
     * not a user id) keeps a bare `u_::`-shaped name from matching.
     */
    fun isNamespaced(keyName: String, canonicalNames: Set<String>): Boolean {
        if (!keyName.startsWith(PREFIX)) return false
        val separator = keyName.lastIndexOf(SEPARATOR)
        if (separator <= 2) return false // "u_" alone is not a user id
        return keyName.substring(separator + 2) in canonicalNames
    }
}
