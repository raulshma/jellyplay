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

    /** Per-device namespaces that never sync, by key prefix. */
    val EXCLUDED_PREFIXES: List<String> = listOf("dream", "screensaver")
}
