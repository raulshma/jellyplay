package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.RememberedTrack
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.isLanguageMatch

/**
 * Side-loaded streaming subtitle id: `"external:{server stream index}"`.
 * Stamped by `PlayerSessionManager.buildExternalSubtitles` onto each
 * `SubtitleSource` and matched by [TrackSelectionPolicy.resolveByStreamIndex].
 * Single source so constructor and matcher can't drift apart.
 */
internal fun externalSubtitleTrackId(streamIndex: Int): String = "external:$streamIndex"

/**
 * Offline side-loaded subtitle id: `"offline:{subtitle manifest index}"`.
 * Stamped by `PlayerSessionManager.loadOfflineSubtitles` and matched by
 * [TrackSelectionPolicy.resolveByOfflineSubtitleId].
 */
internal fun offlineSubtitleTrackId(index: Int): String = "offline:$index"

/**
 * The deep, **pure** home for the track-selection *policy*: given the candidate
 * tracks, the server stream list, the resolved preference, and the cross-episode
 * memory — *which* [TrackOption] plays for this load?
 *
 * Previously this precedence ladder had no type. It existed only as the call
 * sequence inside `TrackSelectionHelper.updateTracksFromEngine()`, so the
 * ordering and fallback rules could not be unit-tested as a unit (the legacy
 * `TrackSelectionLogicTest` re-implemented the logic with local vars rather than
 * calling real code). Extracting it makes the policy first-class:
 *
 *  - **Subtitle**: G5 scoring pre-pass (when not forced-only) → forced-only
 *    stream pick → tiered [SubtitleTrackMatcher] → `null` (caller selects Off).
 *  - **Audio**: G5 scoring pre-pass → audio-description preference →
 *    language match → `null` (caller selects Default).
 *
 * The side-effecting consequences of a decision — `engine.selectTrack()`,
 * latching the held-selection guard, persisting the per-item choice, and
 * mutating/ hydrating the remembered-track memory — remain the helper's job.
 * This module decides; [TrackSelectionHelper] enforces.
 *
 * [TrackScorer] and [SubtitleTrackMatcher] are composed, not merged: they solve
 * genuinely different problems (cross-episode continuity vs in-episode role
 * relaxation), and the win here is *naming the policy*, not collapsing the
 * collaborators (the review's caveat).
 */
internal class TrackSelectionPolicy {

    /**
     * Picks the subtitle [TrackOption] for the current load, or `null` when no
     * same-language track exists (the caller then selects the "Off" placeholder).
     *
     * Ordering — the explicit ladder that used to live only as a call sequence:
     *
     *  1. **G5 scoring pre-pass** — *skipped* under [SubtitleResolutionArgs.forcedOnly],
     *    where forced/hearing-impaired rules must win for accessibility. When the
     *    previous episode's subtitle is [remembered], a confident (≥3) match
     *    carries the specific pick (e.g. "English SDH") forward instead of
     *    dropping to the first same-language track.
     *  2. **Forced-only** — when the preference pins forced subs, pick the
     *    forced track in the resolved language (else any forced track).
     *  3. **Tiered matcher** — [SubtitleTrackMatcher] relaxes forced/SDH roles
     *    in tiers so the user lands on the closest-available subtitle.
     *  4. `null` → caller selects Off.
     */
    fun resolveSubtitle(args: SubtitleResolutionArgs): TrackOption? {
        // G5 scoring pre-pass — non-forced only; forced/hearing-impaired rules
        // take precedence for accessibility.
        val scored = if (!args.forcedOnly) {
            args.remembered?.let { remembered ->
                pickByScoring(args.tracks, remembered)
            }
        } else {
            null
        }
        if (scored != null) return scored

        if (args.forcedOnly) {
            val forcedStream = args.streams
                .firstOrNull { it.type == StreamType.SUBTITLE && it.isForced && isLanguageMatch(it.language, args.lang) }
                ?: args.streams.firstOrNull { it.type == StreamType.SUBTITLE && it.isForced }
            if (forcedStream != null) {
                // Prefer container stream index (robust); fall back to label.
                return resolveByStreamIndex(args.tracks, forcedStream.index, forcedStream)
            }
            return null
        }

        return SubtitleTrackMatcher.match(
            tracks = args.tracks,
            lang = args.lang,
            forced = args.forced,
            hearingImpaired = args.hearingImpaired,
        )
    }

    /**
     * Picks the audio [TrackOption] for the current load, or `null` (the caller
     * then selects the "Default" placeholder).
     *
     * Ordering:
     *
     *  1. **G5 scoring pre-pass** — a confident (≥3) match against the
     *    [remembered] previous-episode audio track (e.g. "English · 5.1 · DTS")
     *    carries forward; otherwise falls through.
     *  2. **Audio-description preference** — when [preferAudioDescription] is
     *    set, descriptive tracks (matched via title/label keywords) win over the
     *    language match so visually-impaired users get narration by default.
     *  3. **Language match** — first track in the resolved language.
     *  4. `null` → caller selects Default.
     */
    fun resolveAudio(args: AudioResolutionArgs): TrackOption? {
        val scored = args.remembered?.let { remembered ->
            pickByScoring(args.tracks, remembered)
        }
        if (scored != null) return scored
        return pickPreferredAudioTrack(
            audioTracks = args.tracks,
            streams = args.streams,
            resolvedLang = args.resolvedLang,
            preferAudioDescription = args.preferAudioDescription,
        )
    }

    /**
     * Resolves a stored/pending server [MediaStream.index] to an engine
     * [TrackOption]. Prefers the engine track's container stream index (mpv
     * `ff-index`, which equals the server index for demuxed tracks) — robust
     * against blank/duplicate/translated titles — then the side-loaded
     * subtitle id (see below), and finally falls back to matching
     * [targetStream]'s label for engines or tracks that expose neither key.
     * Returns null if nothing matches (the caller then handles the
     * offline-positional-index case).
     */
    fun resolveByStreamIndex(
        tracks: List<TrackOption>,
        streamIndex: Int,
        targetStream: MediaStream?,
    ): TrackOption? {
        val byStreamIndex = tracks.firstOrNull { it.index >= 0 && it.streamIndex == streamIndex }
        if (byStreamIndex != null) return byStreamIndex
        // Streaming side-load contract: on a transcode the server's text subs
        // are delivered as side-loaded files and
        // PlayerSessionManager.buildExternalSubtitles stamps
        // [externalSubtitleTrackId] onto each SubtitleSource — propagated into
        // TrackOption.id by both engines (the streaming mirror of the
        // "offline:{index}" contract below). The id is exact, so it outranks
        // the label fallback: enrichment can miss (blank or duplicate
        // languages) and duplicate-language subs make labels ambiguous.
        val byExternalId = tracks.firstOrNull { it.index >= 0 && it.id == externalSubtitleTrackId(streamIndex) }
        if (byExternalId != null) return byExternalId
        val targetLabel = targetStream?.displayTitle ?: targetStream?.title ?: targetStream?.language
            ?: return null
        return tracks.firstOrNull { it.index >= 0 && it.label == targetLabel }
    }

    /**
     * Resolves a stored/pending server stream [index] to the offline side-loaded
     * subtitle whose [TrackOption.id] == `"offline:${index}"`.
     *
     * Offline playback carries no server [MediaStream]s, so the only stable
     * handle linking the detail screen's persisted selection (the original server
     * stream index) to an engine track is the `"offline:${index}"` id that
     * `PlayerSessionManager.loadOfflineSubtitles` stamps onto every
     * `SubtitleSource` and the engines propagate into [TrackOption.id]:
     * ExoPlayer via the `MediaItem.SubtitleConfiguration.id` → track `format.id`,
     * and mpv via its side-loaded-subtitle id registry. Returns `null` when no
     * track carries that id (e.g. the sub failed to side-load, or an engine that
     * doesn't propagate the id), so the caller can fall back to its legacy
     * positional-index match.
     *
     * The `offline:${index}` contract is established in
     * `PlayerSessionManager.loadOfflineSubtitles` and matched by the detail
     * screen's local-subtitle selector, which writes the chosen
     * `OfflineSubtitleEntry.index` into the per-item `subtitleStreamIndex`.
     */
    fun resolveByOfflineSubtitleId(
        tracks: List<TrackOption>,
        index: Int,
    ): TrackOption? =
        tracks.firstOrNull { it.index >= 0 && it.id == offlineSubtitleTrackId(index) }

    /**
     * Maps a selected [TrackOption] back to the server [MediaStream.index] so it
     * can be persisted in the per-item selection row.
     *
     * Offline playback carries no server streams, so there is nothing to match
     * against — the engine track's positional index is the only stable handle
     * for offline sidecar subs (restore-on-reload re-matches by label). Returns
     * the [TrackOption.index] directly in that case. Previously this returned
     * null offline, so the stored selection silently became null and never
     * restored.
     */
    fun resolveMediaStreamIndex(
        streams: List<MediaStream>,
        type: StreamType,
        trackOption: TrackOption,
    ): Int? {
        val typedStreams = streams.filter { it.type == type }
        val trackLabel = trackOption.label
        val trackLanguage = trackOption.language

        if (typedStreams.isEmpty()) return trackOption.index

        val exactMatch = typedStreams.firstOrNull {
            it.displayTitle == trackLabel || it.title == trackLabel || it.language == trackLabel
        }
        if (exactMatch != null) return exactMatch.index

        if (trackLanguage != null) {
            val languageMatches = typedStreams.filter { isLanguageMatch(it.language, trackLanguage) }
            if (languageMatches.isNotEmpty()) {
                if (languageMatches.size == 1) return languageMatches[0].index
                val bestMatch = languageMatches.firstOrNull { stream ->
                    val streamTitle = stream.displayTitle ?: stream.title ?: ""
                    streamTitle.isNotBlank() && (trackLabel.contains(streamTitle, ignoreCase = true) || streamTitle.contains(trackLabel, ignoreCase = true))
                } ?: languageMatches.firstOrNull { it.isDefault } ?: languageMatches.first()
                return bestMatch.index
            }
        }

        return typedStreams.firstOrNull { it.index >= 0 }?.index
    }

    /**
     * Cross-episode scoring pre-pass (G5). Given the candidate [tracks] for a new
     * episode and the previously-selected [remembered] track, returns the best-
     * scoring candidate if one clears the confidence threshold, else null (the
     * caller falls back to the language rule). Used for both audio and subtitle
     * resolution. See [TrackScorer].
     */
    private fun pickByScoring(
        tracks: List<TrackOption>,
        remembered: RememberedTrack,
    ): TrackOption? {
        if (remembered.label.isBlank()) return null
        val selectable = tracks.filter { it.index >= 0 }
        if (selectable.isEmpty()) return null
        val candidates = selectable.mapIndexed { i, opt ->
            TrackScorer.Candidate(
                language = opt.language.orEmpty(),
                label = opt.label,
                indexWithinLanguage = remembered.indexWithinLanguage,
                candidateIndexWithinLanguage = positionalIndexWithinLanguage(selectable, i),
                optionId = i,
            )
        }
        val winner = TrackScorer.bestMatch(remembered.language, remembered.label, candidates = candidates) ?: return null
        return selectable.getOrNull(winner.optionId)
    }

    /**
     * Position of the track at array position [i] within [tracks] among the
     * tracks sharing its language, or -1 if unknown. Feeds the scoring candidate
     * the positional offset into the filtered [tracks] list.
     */
    private fun positionalIndexWithinLanguage(tracks: List<TrackOption>, i: Int): Int {
        val target = tracks.getOrNull(i) ?: return -1
        val lang = target.language?.lowercase()?.trim().orEmpty()
        if (lang.isEmpty()) return -1
        val sameLang = tracks.filter { it.language?.lowercase()?.trim() == lang }
        return sameLang.indexOf(target)
    }

    /**
     * Picks the best audio [TrackOption] for automatic (no stored override)
     * selection. When [preferAudioDescription] is enabled, descriptive tracks
     * (matched via title/label keywords) are preferred over the default
     * language match so visually-impaired users get narration by default.
     */
    private fun pickPreferredAudioTrack(
        audioTracks: List<TrackOption>,
        streams: List<MediaStream>,
        resolvedLang: String,
        preferAudioDescription: Boolean,
    ): TrackOption? {
        val selectable = audioTracks.filter { it.index >= 0 }
        if (selectable.isEmpty()) return null
        if (preferAudioDescription) {
            val descriptiveStreamIdx = streams
                .firstOrNull { it.type == StreamType.AUDIO && isAudioDescriptionStream(it) }
                ?.index
            if (descriptiveStreamIdx != null) {
                // Engine track indices are positional (0..n) while mediaStream
                // indices come from the server. Match by label as a bridge.
                val targetStream = streams.firstOrNull { it.index == descriptiveStreamIdx }
                val targetLabel = targetStream?.displayTitle ?: targetStream?.title
                val match = selectable.firstOrNull { opt ->
                    opt.label == targetLabel || (targetLabel != null && opt.label.contains(targetLabel, ignoreCase = true))
                } ?: selectable.firstOrNull { opt -> isAudioDescriptionLabel(opt.label) }
                if (match != null) return match
            }
        }
        return selectable.firstOrNull { isLanguageMatch(it.language, resolvedLang) }
    }

    /** Heuristics for detecting audio-description tracks from titles/labels. */
    private fun isAudioDescriptionStream(stream: MediaStream): Boolean {
        val title = stream.displayTitle ?: stream.title ?: return false
        return isAudioDescriptionLabel(title)
    }

    private fun isAudioDescriptionLabel(label: String): Boolean {
        val lower = label.lowercase()
        return lower.contains("description") ||
            lower.contains("descriptive") ||
            lower.contains("narration") ||
            lower.contains(" dvs") ||
            lower.endsWith(" ad")
    }
}

/**
 * Inputs to [TrackSelectionPolicy.resolveSubtitle] — the in-episode decision
 * (forced/SDH role pins) plus the cross-episode memory. A focused struct rather
 * than a union so audio-only fields never ride along.
 *
 * @param tracks the built/enriched/merged picker rows for the current load.
 * @param streams the server [MediaStream] list (empty offline).
 * @param lang ISO-639 language code the preference resolved to (per-item →
 *   per-series → global preferredSubtitleLanguage).
 * @param forcedOnly global `subtitlesForcedOnly` preference — pins forced subs.
 * @param forced per-series forced-narrative pin, or null to not care.
 * @param hearingImpaired per-series SDH pin, or null to not care.
 * @param remembered the previous episode's selected subtitle (G5 memory), or null.
 */
@Immutable
data class SubtitleResolutionArgs(
    val tracks: List<TrackOption>,
    val streams: List<MediaStream>,
    val lang: String,
    val forcedOnly: Boolean,
    val forced: Boolean?,
    val hearingImpaired: Boolean?,
    val remembered: RememberedTrack?,
)

/**
 * Inputs to [TrackSelectionPolicy.resolveAudio].
 *
 * @param resolvedLang the effective audio language (per-item → per-series →
 *   global preferredAudioLanguage) the policy should match against.
 * @param preferAudioDescription global audio-description preference.
 * @param remembered the previous episode's selected audio track (G5 memory), or null.
 */
@Immutable
data class AudioResolutionArgs(
    val tracks: List<TrackOption>,
    val streams: List<MediaStream>,
    val resolvedLang: String,
    val preferAudioDescription: Boolean,
    val remembered: RememberedTrack?,
)
