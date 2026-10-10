package com.raulshma.jellyplay.core.datastore.spec

/**
 * The user_prefs namespace's sync-exclusion policy, in one home so its two
 * consumers cannot drift (docs/jellyplay-plugin.md's "never synced" list):
 * the sync adapter's registration (core/data DI) passes
 * [EXCLUDED_PREFIXES] as its `excludedPrefixes`, and the settings-catalog
 * generator skips the same prefixes so the catalog describes exactly what
 * syncs. Key-level exclusions (secrets, identity) are not duplicated here —
 * the owning stores export those as `SyncExcludedKeys` sets.
 */
object PreferenceSyncPolicy {

    /**
     * The `u_<userId>::` per-user key-namespacing grammar's prefix — the ONE
     * raw-name marker every per-user namespaced key shares
     * (`UserNamespacedKeys`, internal to the datastore module; the raw string
     * is mirrored here because the prefs adapter matches raw key names).
     * Namespaced keys never sync through the `prefs` namespace: their
     * syncable domains (the home layout) own dedicated sync namespaces whose
     * adapters read the store's typed projection instead of raw names, and
     * the rest (per-user rows of OTHER installs' users riding along under
     * their embedded user ids) was never state this device should push.
     */
    const val PER_USER_NAMESPACED_PREFIX = "u_"

    /** Per-device namespaces that never sync, by key prefix. */
    val EXCLUDED_PREFIXES: List<String> = listOf(PER_USER_NAMESPACED_PREFIX, "dream", "screensaver")
}
