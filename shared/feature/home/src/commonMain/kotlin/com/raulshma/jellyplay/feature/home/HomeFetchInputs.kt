package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeSectionPrefs
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences

/**
 * ONE construction seam for the refresher's per-call inputs: the VM's mutable
 * preference mirrors stay in the VM and cross the seam as read-only providers,
 * re-read on every fetch, so a preference change can never half-apply
 * mid-fetch. Previously these were five separate constructor lambdas
 * (sectionPrefsProvider / seerrPreferencesProvider / discoverEnabledProvider /
 * directArrEnabledProvider / androidTvWatchNextEnabledProvider), so every new
 * home preference widened [HomeRefresher]'s constructor, both platform
 * factories and every test harness with it; now a new pref is one documented
 * member here and a single argument site at each construction surface.
 *
 * Deliberately a bundle of LAMBDAS, not snapshot VALUES: the mirrors are
 * re-read at each use site (fetch start, the CW side-effect after the fetch
 * lands) exactly as the five separate providers were — folding them into a
 * value snapshot captured at construction would freeze the first read for the
 * refresher's lifetime. The class name says "inputs", not "snapshot", for that
 * reason: it bundles the INPUT SOURCES, and each invocation of a provider is a
 * fresh point-in-time read.
 *
 * A data class only so future members arrive with `copy` at the single
 * argument site; function equality is never relied on (each construction
 * surface builds exactly one instance).
 */
internal data class HomeFetchInputs(
    /**
     * The section plan: enabled types, order, merge flag and the discover-row
     * configs — what [HomeRefresher.fetchOnce] turns into the home-sections
     * query and what the SWR paint / ordering use. Backed by the VM's
     * `sectionPrefs` mirror, owned by its prefs collector.
     */
    val sectionPrefs: () -> HomeSectionPrefs,
    /**
     * The Seerr preference set (enabled, discover grid toggles) driving the
     * legacy discover-grid fan-out's shape — which of the five Seerr calls
     * fire. Backed by the VM's `seerrPreferences` mirror.
     */
    val seerrPreferences: () -> SeerrPreferences,
    /**
     * Whether the discover grid (Seerr section) fetches at all this pass —
     * the WHAT gate for the discover fetch group inside [HomeRefresher.fetchOnce]
     * and the standalone [RefreshTrigger.DiscoverEnabled] fetch. Backed by the
     * VM's `discoverEnabled` mirror (Seerr enabled AND discover enabled).
     */
    val discoverEnabled: () -> Boolean,
    /**
     * Whether the direct *arr "Recently Grabbed" calendar row fetches — the
     * WHAT gate for the arr fetch group inside [HomeRefresher.fetchOnce]
     * (DIRECT_ARR_INTEGRATION experimental flag). Backed by the VM's
     * `directArrEnabled` mirror.
     */
    val directArrEnabled: () -> Boolean,
    /**
     * Whether a Continue-Watching change also refreshes the Android TV
     * "Watch Next" OS row — read AFTER the fetch lands (inside the deferred
     * CW side-effect), so the pref flip applies to the next fetch's side
     * effect without re-running it. Backed by the VM's
     * `androidTvWatchNextEnabled` mirror.
     */
    val androidTvWatchNextEnabled: () -> Boolean,
)
