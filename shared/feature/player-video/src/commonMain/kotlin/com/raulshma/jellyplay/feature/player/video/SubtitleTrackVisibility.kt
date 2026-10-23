package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.subtitle.SubtitleLanguageCodes

/** Filters the picker only; engine tracks and automatic selection remain unchanged. */
internal fun visibleSubtitleTracks(
    tracks: List<TrackOption>,
    showOnlyPreferred: Boolean,
    preferredLanguage: String?,
    orderedLanguages: List<String>,
): List<TrackOption> {
    if (!showOnlyPreferred) return tracks
    val languages = (orderedLanguages + listOfNotNull(preferredLanguage))
        .mapNotNull { SubtitleLanguageCodes.toIso3(it) }
        .filter { it != "und" }
        .toSet()
    if (languages.isEmpty()) return tracks
    return tracks.filter { it.index < 0 || SubtitleLanguageCodes.toIso3(it.language) in languages }
}
