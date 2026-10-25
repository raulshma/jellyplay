package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate

/**
 * The seed strategy over [PLAYER_PREF_LEAVES]: the load-time, UNGUARDED
 * projection that folds every row's write into one uiState transform when a
 * session load starts — [seededProjection] folds a [VideoPlayerAggregate]
 * (plus the network slice's `adaptiveBitrateEnabled`, passed in so this
 * module stays pure and store-free) into the extension-lambda shape
 * [SessionLoadOutputs.onPrefsProjected] applies to the residual uiState.
 *
 * The pref→leaf vocabulary lives in the shared table; this class owns only
 * the apply strategy. Two writes stay hand-written here because they are not
 * table rows: the `adaptiveBitrateEnabled` leaf (its source is the network
 * slice parameter, not the aggregate) and nothing else — every other leaf
 * comes from the table, including the aspect-ratio parse and the
 * segment-behaviors fold, which live beside their rows.
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
     * the returned lambda reads only [agg] / [adaptiveBitrateEnabled] and its
     * receiver, so callers may build it before or after other stages without
     * changing the result.
     */
    fun seededProjection(
        agg: VideoPlayerAggregate,
        adaptiveBitrateEnabled: Boolean,
    ): VideoPlayerUiState.() -> VideoPlayerUiState = {
        var seeded = this
        for (leaf in PLAYER_PREF_LEAVES) {
            val write = leaf.writeAny
            seeded = seeded.write(leaf.readAggregateAny(agg))
        }
        // The one non-table leaf: its source is the network-slice parameter,
        // not the aggregate (see the class KDoc).
        seeded.copy(uiPrefs = seeded.uiPrefs.copy(adaptiveBitrateEnabled = adaptiveBitrateEnabled))
    }
}
