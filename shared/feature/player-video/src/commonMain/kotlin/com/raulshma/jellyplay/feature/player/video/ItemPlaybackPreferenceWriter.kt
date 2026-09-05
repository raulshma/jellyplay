package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import com.raulshma.jellyplay.core.model.RememberedTrack
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The write side of the per-item / per-series playback-language preferences —
 * the command twin of [ItemPlaybackPreferenceResolver] (the read side). Every
 * save/clear of an audio/subtitle/dialogue-boost/remembered-track preference
 * routes through here so the write choreography exists exactly once:
 *
 *  1. resolve the write key from the current session state per the command's
 *     explicit [ScopePolicy] (no key ⇒ no-op),
 *  2. launch the repository write — a null value means FORGET and issues the
 *     explicit `clear*` call, because `save()`'s "null ⇒ preserve" convention
 *     would otherwise silently keep the old language forever,
 *  3. fire [onPreferencesChanged] after every write (the resolver refresh), so
 *     the cached resolution read by the track-restore ladder and the sheet
 *     toggle rows is re-read from the repository.
 *
 * [ScopePolicy] is a declared per-command argument rather than an accident of
 * call-site shape: the language/remembered-track commands are SERIES-only
 * (a standalone movie has no series row to remember onto), while dialogue
 * boost deliberately falls back to ITEM scope when no series exists.
 */
internal class ItemPlaybackPreferenceWriter(
    private val repository: ItemPlaybackPreferenceRepository,
    private val getCurrentSeriesId: () -> String?,
    private val getCurrentItemId: () -> String?,
    private val scope: CoroutineScope,
    /** Runs after every completed write — `TrackSelectionHelper.refreshPlaybackPreferences`. */
    private val onPreferencesChanged: () -> Unit,
) {
    /** Where a command resolves its (scope, key) write target from session state. */
    enum class ScopePolicy {
        /** Series id only; no-op when the current item has no series. */
        SERIES_ONLY,

        /** Series id when the item belongs to one, else the per-item id. */
        SERIES_THEN_ITEM,
    }

    /** A resolved write target, or the command no-ops when null. */
    private data class ScopeKey(val scope: PlaybackPrefScope, val key: String)

    private fun resolveKey(policy: ScopePolicy): ScopeKey? {
        val seriesId = getCurrentSeriesId()
        if (seriesId != null) return ScopeKey(PlaybackPrefScope.SERIES, seriesId)
        return when (policy) {
            ScopePolicy.SERIES_ONLY -> null
            ScopePolicy.SERIES_THEN_ITEM -> getCurrentItemId()?.let { ScopeKey(PlaybackPrefScope.ITEM, it) }
        }
    }

    /**
     * Saves/clears the per-series preferred audio language. [language] null
     * means "forget" — the explicit clear runs, never a value-preserving save.
     */
    fun setSeriesAudioLanguage(language: String?) {
        val key = resolveKey(ScopePolicy.SERIES_ONLY) ?: return
        scope.launch {
            if (language == null) {
                repository.clearAudioLanguage(key.scope, key.key)
            } else {
                repository.save(scope = key.scope, key = key.key, audioLanguage = language)
            }
            onPreferencesChanged()
        }
    }

    /**
     * Saves/clears the per-series preferred subtitle descriptor. [language]
     * null means "forget" (explicit clear, role fields cleared with it).
     */
    fun setSeriesSubtitlePreference(
        language: String?,
        forced: Boolean?,
        hearingImpaired: Boolean?,
    ) {
        val key = resolveKey(ScopePolicy.SERIES_ONLY) ?: return
        scope.launch {
            if (language == null) {
                repository.clearSubtitleLanguage(key.scope, key.key)
            } else {
                repository.save(
                    scope = key.scope,
                    key = key.key,
                    subtitleLanguage = language,
                    subtitleForced = forced,
                    subtitleHearingImpaired = hearingImpaired,
                )
            }
            onPreferencesChanged()
        }
    }

    /** Saves/clears the per-series "subtitles off" intent. */
    fun setSeriesSubtitleDisabled(disabled: Boolean) {
        val key = resolveKey(ScopePolicy.SERIES_ONLY) ?: return
        scope.launch {
            repository.setSubtitleDisabled(key.scope, key.key, disabled)
            onPreferencesChanged()
        }
    }

    /**
     * Saves/clears the dialogue-boost strength under [ScopePolicy.SERIES_THEN_ITEM]
     * (SERIES applies to all episodes, ITEM pins the standalone movie).
     * [EffectStrength.NONE] means "forget" — the explicit clear runs.
     */
    fun setDialogueBoostStrength(strength: EffectStrength) {
        val key = resolveKey(ScopePolicy.SERIES_THEN_ITEM) ?: return
        scope.launch {
            if (strength == EffectStrength.NONE) {
                repository.clearDialogueBoostStrength(key.scope, key.key)
            } else {
                repository.save(scope = key.scope, key = key.key, dialogueBoostStrength = strength)
            }
            onPreferencesChanged()
        }
    }

    /**
     * Persists the last-selected track of [type] for the current series (G5
     * cross-episode scoring memory). SERIES-only: nothing is remembered for a
     * standalone movie.
     */
    fun rememberTrack(type: TrackType, track: RememberedTrack?) {
        val key = resolveKey(ScopePolicy.SERIES_ONLY) ?: return
        scope.launch {
            repository.saveRememberedTrack(scope = key.scope, key = key.key, type = type, track = track)
            onPreferencesChanged()
        }
    }
}
