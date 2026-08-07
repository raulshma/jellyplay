package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The scope a [ItemPlaybackPreference] applies to.
 *
 * - [ITEM]: keyed by a single media-item id (e.g. one movie or one episode).
 * - [SERIES]: keyed by a series id, applying to every episode of that series
 *   (e.g. "always play Dark in German with English subs").
 */
@Immutable
@Serializable
enum class PlaybackPrefScope {
    ITEM,
    SERIES,
}

/**
 * The cross-episode track-scoring memory for one track type (G5): the display
 * [label] (codec folded in), its [language], and its positional
 * [indexWithinLanguage] for layout stability. Persisted per series so a specific
 * pick (e.g. "English · 5.1") survives an app restart and carries to the next
 * episode instead of being remembered only in-process. `null` index means
 * "unknown / not comparable".
 */
@Immutable
@Serializable
data class RememberedTrack(
    val label: String,
    val language: String?,
    val indexWithinLanguage: Int = -1,
)

/**
 * A per-item / per-series playback-language preference.
 *
 * Each language field is nullable: a `null` value means "not set / inherit",
 * so a rule can pin only the audio language, only the subtitle language, or
 * both. Resolution precedence at playback load (in `TrackSelectionHelper`) is:
 *
 * pending nav stream index → per-item stream-index override (DataStore) →
 * **per-item language rule → per-series language rule (this model)** →
 * global `preferredAudioLanguage` / `preferredSubtitleLanguage`.
 *
 * The [dialogueBoostStrength] field extends the per-item model to audio effects
 *: a `null` value means "no per-item rule" (resolve to the
 * effective default of [EffectStrength.NONE] — dialogue boost does NOT carry
 * across items unless explicitly pinned per-item or per-series). A non-null
 * value pins the effect for this item/series.
 *
 * @param scope whether this rule is keyed by item id or series id.
 * @param key the item id (when [PlaybackPrefScope.ITEM]) or series id
 *   (when [PlaybackPrefScope.SERIES]).
 * @param audioLanguage preferred audio language code (ISO-639), or null to inherit.
 * @param subtitleLanguage preferred subtitle language code (ISO-639), or null to inherit.
 *   Mutually exclusive with [subtitleDisabled]: when one is set the other must be null.
 * @param subtitleDisabled explicit "subtitles off" intent, or null to inherit. When `true`
 *   the resolver forces subtitles off for this item/series instead of resolving a track.
 *   Mutually exclusive with [subtitleLanguage] (and its role fields); the repository keeps
 *   them consistent on write. Added in migration 41→42.
 * @param subtitleForced whether the preferred subtitle should be a forced-narrative
 *   track, or null to not care. Lets a series pin "English Forced" so the
 *   restore matcher (in `TrackSelectionHelper`) prefers forced over plain for
 *   every episode, relaxing only when absent.
 * @param subtitleHearingImpaired whether the preferred subtitle should be an SDH /
 *   hearing-impaired track, or null to not care. Pairs with [subtitleLanguage]
 *   so a series can pin "English SDH" and have it carried episode to episode.
 * @param dialogueBoostStrength per-item dialogue-boost strength, or null to use the
 *   effective default ([EffectStrength.NONE]).
 * @param rememberedAudioTrack the last-selected audio track to carry to the next
 *   episode via track scoring, or null to not remember one.
 * @param rememberedSubtitleTrack the last-selected subtitle track to carry to the
 *   next episode via track scoring, or null to not remember one.
 * @param updatedAt epoch millis of the last write.
 */
@Immutable
@Serializable
data class ItemPlaybackPreference(
    val scope: PlaybackPrefScope,
    val key: String,
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val subtitleDisabled: Boolean? = null,
    val subtitleForced: Boolean? = null,
    val subtitleHearingImpaired: Boolean? = null,
    val dialogueBoostStrength: EffectStrength? = null,
    val rememberedAudioTrack: RememberedTrack? = null,
    val rememberedSubtitleTrack: RememberedTrack? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
