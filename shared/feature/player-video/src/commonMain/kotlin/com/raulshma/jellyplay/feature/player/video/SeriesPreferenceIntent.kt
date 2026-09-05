package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.engine.TrackBadge

/**
 * Pure intent derivations for the per-series track-preference footers (the
 * `RememberPreferenceToggle` rows in the audio picker and the subtitle hub's
 * Tracks tab). The footers used to inline this logic inside their
 * `onToggle` lambdas, where no JVM test could reach it; the composables now
 * reduce to a match + writer dispatch ([VideoPlayerViewModel] /
 * [ItemPlaybackPreferenceWriter]) and every decision — which track counts as
 * selected, which language/role fields a remembered track pins, whether the
 * toggle means "off" or "language" — lives here. No Compose types.
 */

/**
 * The audio footer's intent: remembering pins the currently-selected audio
 * track's language (a real engine track, hence the `index >= 0` guard — the
 * picker's "Off" row carries `index < 0` and has no language to remember);
 * unremembering forgets (null).
 */
internal fun seriesAudioPreferenceIntent(audioTracks: List<TrackOption>, remember: Boolean): String? {
    if (!remember) return null
    return audioTracks.firstOrNull { it.isSelected && it.index >= 0 }?.language
}

/**
 * The subtitle footer's intent. The row the toggle acts on is whichever
 * subtitle track is selected — including the picker's "Off" row
 * (`index < 0`), which turns the toggle into the "subtitles off for this
 * series" intent instead of a language pin.
 */
internal sealed interface SeriesSubtitlePrefIntent {

    /**
     * The "Off" row is selected: the toggle writes the series' subtitles-off
     * intent ([disabled] mirrors the toggle state).
     */
    data class Off(val disabled: Boolean) : SeriesSubtitlePrefIntent

    /**
     * A real track (or nothing) is selected: remember pins its language plus
     * the role badges worth restoring episode-to-episode. A null [language]
     * with `remember` and no selected track degrades to a forget at the
     * writer (its null-language convention), matching the former footer.
     */
    data class Track(
        val language: String?,
        val forced: Boolean?,
        val hearingImpaired: Boolean?,
    ) : SeriesSubtitlePrefIntent

    /** Toggled off with a track (or nothing) selected: forget the language intent. */
    data object Forget : SeriesSubtitlePrefIntent
}

internal fun seriesSubtitlePreferenceIntent(
    subtitleTracks: List<TrackOption>,
    remember: Boolean,
): SeriesSubtitlePrefIntent {
    val selected = subtitleTracks.firstOrNull { it.isSelected }
    return when {
        // "Off" row selected: the off-intent is the only thing to write.
        selected != null && selected.index < 0 -> SeriesSubtitlePrefIntent.Off(disabled = remember)
        remember -> SeriesSubtitlePrefIntent.Track(
            language = selected?.language,
            forced = selected?.badges?.contains(TrackBadge.FORCED)?.takeIf { it },
            hearingImpaired = selected?.badges?.contains(TrackBadge.SDH)?.takeIf { it },
        )
        else -> SeriesSubtitlePrefIntent.Forget
    }
}

/**
 * The subtitle footer's label choice: the toggle reads "remember subtitles
 * off" while the Off row is selected OR the series already carries an
 * off-intent (so the user sees what un-toggling will clear), otherwise
 * "remember subtitle language".
 */
internal fun seriesSubtitlePrefersOffLabel(
    subtitleTracks: List<TrackOption>,
    hasSeriesSubtitleOffPref: Boolean,
): Boolean =
    hasSeriesSubtitleOffPref || subtitleTracks.any { it.isSelected && it.index < 0 }
