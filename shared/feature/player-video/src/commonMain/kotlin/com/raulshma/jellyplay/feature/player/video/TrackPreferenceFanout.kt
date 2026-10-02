package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.focus.FocusClaimState
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ItemPlaybackPreference

/**
 * Folds one resolved per-item/series playback preference
 * ([ItemPlaybackPreferenceResolver.resolved]'s emission) into the session's
 * dependent slices — the arm-phase collector body the [PlayerWiring] builder
 * used to own inline (the [PlayerPrefsFanout] shape: plain values in, named
 * single-purpose lambdas out, jvmTest-pinned with fakes). In emission order:
 *
 *  1. the dialogue-boost default fold: a stored rule pins the strength;
 *     otherwise the effective default is OFF (NONE), so the effect never
 *     silently carries across items. The global setting is intentionally NOT
 *     used as the auto fallback here. The mirror write carries BOTH the
 *     strength and its `enabled != NONE` twin — one uiState copy;
 *  2. the track-selection helper's series-preference reflection (the sheets'
 *     series-pref toggle rows);
 *  3. the engine-config rebuild trigger (the boost fold above must reach the
 *     engine);
 *  4. the language-preference RE-APPLY ([hasLanguagePreference]-gated
 *     `updateTracksFromEngine`). The DAO read in ItemPlaybackPreferenceResolver
 *     is async; on next-episode autoplay the engine often publishes its track
 *     list (triggering updateTracksFromEngine) before the preference lands.
 *     Without re-running here, the preference never gets applied for that
 *     load. Only a language preference actually existing triggers the re-run,
 *     so null resolutions (and boost-only rules) don't churn (no engine yet ⇒
 *     no-op either way).
 */
internal class TrackPreferenceFanout(
    /** The uiState mirror write: strength + its enabled twin, one copy. */
    private val applyDialogueBoostResolution: (EffectStrength) -> Unit,
    private val onSeriesPreferenceResolved: (ItemPlaybackPreference?) -> Unit,
    private val rebuildEngineConfig: () -> Unit,
    private val reapplyTracksFromEngine: () -> Unit,
) {

    fun onPreferenceResolved(pref: ItemPlaybackPreference?) {
        val resolvedBoost = pref?.dialogueBoostStrength ?: EffectStrength.NONE
        applyDialogueBoostResolution(resolvedBoost)
        onSeriesPreferenceResolved(pref)
        rebuildEngineConfig()
        if (hasLanguagePreference(pref)) {
            reapplyTracksFromEngine()
        }
    }

    companion object {
        /**
         * True when [pref] carries ANY language directive (an audio language,
         * a subtitle language, or an explicit subtitle-off) — the re-run
         * condition of step 4. A boost-only or null resolution re-runs
         * nothing.
         */
        fun hasLanguagePreference(pref: ItemPlaybackPreference?): Boolean =
            pref?.audioLanguage != null || pref?.subtitleLanguage != null ||
                pref?.subtitleDisabled == true
    }
}

/**
 * The displaced-holder self-pause decision for the focus claim flow — the
 * arm-phase collector body the [PlayerWiring] builder used to own inline
 * (the reader's observation pattern): skip ONLY while the floor is HELD by
 * us — every other state falls through and pauses an engine that is somehow
 * still playing. That includes our OWN Suspended(VIDEO) claims: a user pause
 * (engine idle, the pause is a no-op) and a permanent-loss ruling (redundant
 * with suspendHolder's command, harmless as a belt).
 *
 * Load-bearing on desktop — the desktop focus binding registers only the
 * music surface, so a MUSIC eviction of a held VIDEO claim arrives as no
 * command; on Android the surface command path already paused and this is an
 * idempotent belt. The duck row never lands here (the claim stays Held while
 * ducked).
 */
internal class DisplacedHolderSelfPause(
    /** The live engine's playing state (null engine ⇒ not playing ⇒ no-op). */
    private val isEnginePlaying: () -> Boolean,
    private val pauseEngine: () -> Unit,
) {

    fun onClaimStateChanged(state: FocusClaimState) {
        if (!shouldPauseFor(state)) return
        if (isEnginePlaying()) {
            pauseEngine()
        }
    }

    companion object {
        /** True for every claim state except OUR held VIDEO floor. */
        fun shouldPauseFor(state: FocusClaimState): Boolean =
            !(state is FocusClaimState.Held && state.holder == PlaybackSurfaceId.VIDEO)
    }
}
