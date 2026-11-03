package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.ResolvedPerformanceProfile

/**
 * The seed strategy over [PLAYER_PREF_LEAVES]: the load-time, UNGUARDED
 * projection that folds every row's write into one uiState transform when a
 * session load starts — [seededProjection] folds a [VideoPlayerAggregate]
 * (plus the network slice's `adaptiveBitrateEnabled` and the resolved
 * performance profile, passed in so this module stays pure and store-free)
 * into the extension-lambda shape [SessionLoadOutputs.onPrefsProjected]
 * applies to the residual uiState.
 *
 * The pref→leaf vocabulary lives in the shared table; this class owns only
 * the apply strategy. Hand-written here because they are not table rows:
 * the `adaptiveBitrateEnabled` leaf (its source is the network slice
 * parameter, not the aggregate) and the two resolver-sourced performance
 * leaves (issue #186 §5.8 — `detectedPerformanceTier` / `performanceTuning`
 * come from the RESOLVER, a runtime seam the aggregate never carries; the
 * `videoPerformanceProfile` pref itself IS aggregate-sourced and rides its
 * own leaf row like every other pref). Every other leaf comes from the
 * table, including the aspect-ratio parse and the segment-behaviors fold,
 * which live beside their rows.
 *
 * Every non-table leaf is untouched: the transform is a plain `copy` of the
 * receiver, so session state (position, tracks, errors, controllers' slices)
 * survives the seed.
 *
 * The *change-time* counterpart strategies are [SettingsProjector] (guarded
 * diff over the same rows) and [PlayerPrefsFanout] (the reaction triggers).
 *
 * Extracted verbatim from [SessionLoadPipeline]'s inline `onPrefsProjected {
 * copy(...) }` block; the pipeline keeps the stage order and only loses the
 * copy. Pinned by [PlayerPrefsSeedTest].
 */
internal object PlayerPrefsSeed {

    /**
     * Builds the prefs-derived uiState transform for one session load. Pure:
     * the returned lambda reads only [agg] / [adaptiveBitrateEnabled] /
     * [performance] and its receiver, so callers may build it before or
     * after other stages without changing the result.
     *
     * [performance] is the resolver's resolution for this load (§5.8: the
     * pipeline resolves it with a bounded wait before seeding). `null`
     * leaves the three performance leaves untouched — no resolution, no
     * seed write (the residual state keeps whatever an earlier load seeded).
     */
    fun seededProjection(
        agg: VideoPlayerAggregate,
        adaptiveBitrateEnabled: Boolean,
        performance: ResolvedPerformanceProfile? = null,
    ): VideoPlayerUiState.() -> VideoPlayerUiState = {
        var seeded = this
        for (leaf in PLAYER_PREF_LEAVES) {
            val write = leaf.writeAny
            seeded = seeded.write(leaf.readAggregateAny(agg))
        }
        // The non-table leaves: their sources are the passed-in parameters
        // or the resolver, not the aggregate (see the class KDoc). The
        // profile pref itself seeds through its aggregate-sourced leaf row
        // above — one write per leaf.
        seeded.copy(uiPrefs = seeded.uiPrefs.copy(
            adaptiveBitrateEnabled = adaptiveBitrateEnabled,
            detectedPerformanceTier = performance?.tier
                ?: seeded.uiPrefs.detectedPerformanceTier,
            performanceTuning = performance?.tuning
                ?: seeded.uiPrefs.performanceTuning,
        ))
    }
}
