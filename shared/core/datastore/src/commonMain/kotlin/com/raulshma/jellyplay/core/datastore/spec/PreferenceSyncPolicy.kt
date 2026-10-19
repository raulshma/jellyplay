package com.raulshma.jellyplay.core.datastore.spec

import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryPreferenceSpecs

/**
 * The user_prefs namespace's sync-exclusion policy, in one home so its two
 * consumers cannot drift (docs/jellyplay-plugin.md's "never synced" list):
 * the sync adapter's registration (core/data DI) passes
 * [EXCLUDED_PREFIXES] as its `excludedPrefixes` and [SYNC_EXCLUDED_KEYS] as
 * its `excludedKeys`, and the settings-catalog generator skips the same
 * prefixes plus [EXCLUDED_BOOKKEEPING_KEYS] and hard-errors on [EXCLUDED_KEYS]
 * (the denylist) so the catalog describes exactly what syncs. The owning
 * stores keep exporting their own [SecurityStore.SyncExcludedKeys]-style
 * sets — this object only assembles them.
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

    /**
     * Install-global bookkeeping keys — one-time migration/baseline markers
     * that persist under their RAW canonical names. Every OTHER spec row is
     * written namespaced (`u_<userId>::`, covered by [EXCLUDED_PREFIXES]);
     * these markers live outside any namespace, so without this face they
     * would sync raw — and a synced marker claims, or rather SKIPS, another
     * device's one-time fold (`home_ns_migrated=true` arriving on an
     * updating device would fast-path its namespace migration past claiming
     * that device's legacy keys; `home_v3_upgrade_baseline_done=true` would
     * skip its v3 baseline and turn the new home sections on after all).
     *
     * A separate face from [EXCLUDED_KEYS] on purpose: those are keys that
     * must never EXIST as spec rows (the generator hard-errors if one
     * reaches the catalog), while these ARE spec rows deliberately —
     * declared for factory-reset coverage and the derivation-integrity
     * reflection — so the sync registration excludes them alongside
     * [EXCLUDED_KEYS] while the catalog walk SKIPS them. Derived from the
     * specs' own global-marker declarations so a future marker joins
     * automatically (pinned byte-for-byte in PreferenceSyncPolicyTest).
     */
    val EXCLUDED_BOOKKEEPING_KEYS: Set<String> = HomeDiscoveryPreferenceSpecs.globalMarkerKeys

    /**
     * The key-level set the sync adapter's registration consumes:
     * [EXCLUDED_KEYS] plus [EXCLUDED_BOOKKEEPING_KEYS] — the sum named here
     * so the adapter's exclusion and the generator's skip cannot drift the
     * way two hand-summed expressions at the two call sites would. The
     * generator itself skips ONLY [EXCLUDED_BOOKKEEPING_KEYS]: the
     * [EXCLUDED_KEYS] denylist must never reach it at all (it hard-errors
     * there — those keys are not legal spec rows).
     */
    val SYNC_EXCLUDED_KEYS: Set<String> = EXCLUDED_KEYS + EXCLUDED_BOOKKEEPING_KEYS
}
