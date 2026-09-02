package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.engine.TrackLabelFormatter
import com.raulshma.jellyplay.feature.player.video.engine.TrackLabelInfo

/**
 * Pure, engine-agnostic enrichment of engine track options with Jellyfin server
 * [MediaStream] data. Extracted from [TrackSelectionHelper] so the matching
 * algorithm is unit-testable without instantiating the helper (which needs
 * coroutines + an engine).
 *
 * See [TrackSelectionHelper.enrichFromServer] for the full rationale: this is
 * what fixes Direct-Play, where engine labels are crude (e.g. ExoPlayer's
 * `English · application/x-media3-cues`) and the server stream list was
 * previously ignored. Matching yields real titles + codecs + role badges.
 */
internal object TrackEnrichmentResolver {

    /**
     * Upgrades each engine track option with the richer server stream when a
     * match is found. Tracks with no match pass through unchanged.
     *
     * Matching priority (each server stream consumed at most once):
     *  1. Container stream index ([TrackOption.streamIndex] == server `index`).
     *  2. Same language, positional order within that language.
     *  3. Exact title/label match (case-insensitive).
     */
    fun enrich(
        engineOptions: List<TrackOption>,
        streams: List<MediaStream>,
        type: StreamType,
    ): List<TrackOption> {
        if (streams.isEmpty()) return engineOptions
        val typedStreams = streams.filter { it.type == type }
        if (typedStreams.isEmpty()) return engineOptions

        val byStreamIndex = typedStreams.associateBy { it.index }
        val byLanguage = typedStreams.groupBy { it.language?.lowercase().orEmpty() }
        val consumed = mutableSetOf<Int>()

        return engineOptions.map { option ->
            // 1. Exact container-index match.
            byStreamIndex[option.streamIndex]
                ?.takeIf { consumed.add(it.index) }
                ?.let { return@map rebuild(option, it) }

            // 2. Same-language positional match.
            val langKey = option.language?.lowercase().orEmpty()
            byLanguage[langKey]?.firstOrNull { it.index !in consumed }
                ?.takeIf { consumed.add(it.index) }
                ?.let { return@map rebuild(option, it) }

            // 3. Title/label exact match.
            typedStreams.firstOrNull {
                it.index !in consumed &&
                    option.label.equals(it.displayTitle ?: it.title, ignoreCase = true)
            }?.takeIf { consumed.add(it.index) }
                ?.let { return@map rebuild(option, it) }

            option
        }
    }

    private fun rebuild(option: TrackOption, stream: MediaStream): TrackOption {
        val info = stream.toLabelInfo()
        // Prefer the server's canonical displayTitle (what Jellyfin web/Wholphin
        // show) when present; only assemble from parts when the server didn't
        // provide one (e.g. offline/side-loaded subs).
        val label = stream.displayTitle?.takeIf { it.isNotBlank() }
            ?: TrackLabelFormatter.primary(info)
        return option.copy(
            label = label,
            language = stream.language ?: option.language,
            badges = TrackLabelFormatter.badges(info),
        )
    }

    private fun MediaStream.toLabelInfo(): TrackLabelInfo {
        // Role badges come first from the server's explicit flags (isForced /
        // isHearingImpaired); TrackLabelFormatter.badges() additionally inspects
        // the title/displayTitle text for markers as a fallback for tracks whose
        // only role signal is textual (e.g. "English (SDH)", "Forced"). The
        // server `title` is often blank for subtitle streams while
        // `displayTitle` carries those markers, so fall back to it here —
        // otherwise such a track ends up with no badges and can't be pinned by
        // the "remember subtitle for series" matcher.
        val titleForBadges = title?.takeIf { it.isNotBlank() }
            ?: displayTitle?.takeIf { it.isNotBlank() }
        return TrackLabelInfo(
            title = titleForBadges,
            language = language,
            codec = codec,
            channels = channels,
            isForced = isForced,
            isDefault = isDefault,
            isHearingImpaired = isHearingImpaired,
        )
    }
}
