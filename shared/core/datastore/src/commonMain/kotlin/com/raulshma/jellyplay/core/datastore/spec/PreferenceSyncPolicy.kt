package com.raulshma.jellyplay.core.datastore.spec

import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore

/**
 * The user_prefs namespace's sync-exclusion policy, in one home so its two
 * consumers cannot drift (docs/jellyplay-plugin.md's "never synced" list):
 * the sync adapter's registration (core/data DI) passes
 * [EXCLUDED_PREFIXES] as its `excludedPrefixes` and [EXCLUDED_KEYS] as its
 * `excludedKeys`, and the settings-catalog generator skips the same prefixes
 * and denylists the same keys so the catalog describes exactly what syncs.
 * The owning stores keep exporting their own [SecurityStore.SyncExcludedKeys]-
 * style sets — this object only assembles them.
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

    /**
     * The assembled key-level exclusion set — the four owning stores'
     * `SyncExcludedKeys` exports summed (secrets, per-device lock state,
     * device/session identity, the widget payload cache). The ONE set both
     * the sync adapter's registration and the catalog denylist consume.
     */
    val EXCLUDED_KEYS: Set<String> =
        SecurityStore.SyncExcludedKeys +
            PinRateLimiter.SyncExcludedKeys +
            ServerIdentityStore.SyncExcludedKeys +
            WidgetDataStore.SyncExcludedKeys
}
