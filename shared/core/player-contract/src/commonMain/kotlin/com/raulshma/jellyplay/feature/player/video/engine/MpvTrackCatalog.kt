package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.TrackType

/**
 * Pure mpv track-list → [MediaTrack] catalog — the mapping half of the two
 * mpv hosts' `buildTracks` (the `MpvStyleMapping` precedent: pure commonMain
 * mapping + thin engine adapters). Android's `MpvPlayerEngine` carried the
 * full choreography (side-loaded id stamping, codec/flag extraction,
 * [TrackLabelFormatter] labels); the desktop `MpvDesktopEngine` re-parsed
 * bare (`"mpv_$id"` synthetic ids, `title ?: lang ?: "Track $id"` labels, no
 * side-loaded registry) — so offline-restore selection
 * (`TrackSelectionPolicy.resolveByOfflineSubtitleId` keys on the caller's
 * `offline:{index}` [SubtitleSource.id]) never resolved on desktop. Both now
 * funnel through this one mapping.
 *
 * Input is the binding-agnostic [MpvTrackEntry] row: each host hands its raw
 * parsed `track-list` rows to [trackEntry] — the ONE normalization both
 * engines run (the class of drift that bit offline-restore ids before) — and
 * delegates the [MediaTrack] construction to [mediaTracks]. The raw row shape
 * is the plain-Kotlin tree both transports already produce (or trivially
 * adapt to): `Map` with `String`/`Boolean`/`Long`/`Double`/`List`/`Any?`
 * values — desktop `MpvLib.readNode`'s output verbatim, Android's `MPVNode`
 * through its engine-side plain-value adapter.
 */
object MpvTrackCatalog {

    /**
     * Normalizes one raw mpv `track-list` row into an [MpvTrackEntry] — the
     * shared parse the two mpv hosts funnel every track through (formerly two
     * per-engine field extractions that could — and did — drift). Keys are
     * mpv's wire names (`type`/`id`/`title`/`lang`/`codec`/`selected`/
     * `external`/`ff-index`/`forced`/`default`/`hearing-impaired`); values
     * are plain Kotlin (numbers coerce through [Number] so either transport's
     * Long/Double lands; the string form of an id still parses, the defensive
     * fallback both former parsers carried). Returns null for non-map rows
     * and rows without a `type`/`id` — exactly the rows both former parsers
     * dropped.
     */
    fun trackEntry(raw: Any?): MpvTrackEntry? {
        val map = raw as? Map<*, *> ?: return null
        val type = map["type"] as? String ?: return null
        val id = (map["id"] as? Number)?.toInt()
            ?: (map["id"] as? String)?.toIntOrNull()
            ?: return null
        return MpvTrackEntry(
            type = type,
            id = id,
            title = map["title"] as? String,
            lang = map["lang"] as? String,
            codec = map["codec"] as? String,
            selected = map["selected"] as? Boolean ?: false,
            // `external` is true for sub-add'd (side-loaded) tracks and absent/
            // false for container-demuxed tracks. Gates the side-loaded id
            // lookup in [mediaTracks] so a demuxed track that happens to share
            // a label with a sidecar never inherits the sidecar's stable id.
            external = map["external"] as? Boolean ?: false,
            // ff-index is the demuxer/container stream index — present for
            // container-demuxed tracks (== the server's MediaStream.index), null
            // for side-loaded (sub-add) tracks. Used as the robust resolution key
            // in TrackSelectionHelper instead of fragile label matching.
            ffIndex = (map["ff-index"] as? Number)?.toInt()
                ?: (map["ff-index"] as? String)?.toIntOrNull(),
            forced = map["forced"] as? Boolean ?: false,
            default = map["default"] as? Boolean ?: false,
            hearingImpaired = map["hearing-impaired"] as? Boolean ?: false,
        )
    }

    /**
     * Labels of the subtitle rows in a parsed track-list — the raw mpv
     * `title`s, i.e. the EXACT `title` arg previously passed to `sub-add` and
     * echoed back verbatim. Both hosts' `existingSubLabels` dedupe key for
     * [MpvSubtitleSideLoadPlan] (the plan matches a pending source's label
     * against these to skip true re-adds). Note this is deliberately NOT the
     * [TrackLabelFormatter] display label: that appends language/codec and
     * strips indexes, so it no longer equals the `sub-add` title — Android's
     * former formatted-label set made its label-based duplicate skip dead
     * code; both engines now dedupe on the raw titles.
     */
    fun existingSubtitleLabels(entries: List<MpvTrackEntry>): Set<String> =
        entries.filter { it.type == "sub" }.mapNotNull { it.title }.toSet()


    /**
     * One parsed mpv `track-list` entry. [type] is mpv's raw discriminator
     * (`"audio"` / `"sub"` produce contract tracks; everything else — video —
     * is dropped, matching both former parsers). [id] is mpv's per-type track
     * id (the positional [MediaTrack.index] the select commands write to
     * `sid`/`aid`).
     */
    data class MpvTrackEntry(
        val type: String,
        val id: Int,
        val title: String? = null,
        val lang: String? = null,
        val codec: String? = null,
        val selected: Boolean = false,
        val external: Boolean = false,
        val ffIndex: Int? = null,
        val forced: Boolean = false,
        val default: Boolean = false,
        val hearingImpaired: Boolean = false,
    )

    /**
     * Builds the contract [MediaTrack] list.
     *
     * [sideLoadedSubtitleIds] is the label → [SubtitleSource.id] registry
     * ([MpvSubtitleSideLoadPlan] maintains it): an EXTERNAL subtitle track
     * whose mpv `title` (the `sub-add` title arg) keys into the registry is
     * stamped with the caller's stable id instead of the synthetic
     * `"mpv_sub_{id}"` — mirroring ExoPlayer's
     * `SubtitleConfiguration.id` → track `format.id` propagation, so a
     * persisted offline/provider/external selection resolves on both mpv
     * hosts. Demuxed (container) tracks never inherit a sidecar id, even when
     * a title collides — the `external` flag gates the lookup.
     */
    fun mediaTracks(
        entries: List<MpvTrackEntry>,
        sideLoadedSubtitleIds: Map<String, String> = emptyMap(),
    ): List<MediaTrack> = entries.mapNotNull { entry ->
        val trackType = when (entry.type) {
            "audio" -> TrackType.AUDIO
            "sub" -> TrackType.SUBTITLE
            else -> return@mapNotNull null   // video tracks aren't in the contract
        }
        val info = TrackLabelInfo(
            title = entry.title,
            language = entry.lang,
            codec = entry.codec,
            isForced = entry.forced,
            isDefault = entry.default,
            isHearingImpaired = entry.hearingImpaired,
        )
        // For side-loaded subtitles, prefer the caller-supplied SubtitleSource.id
        // (looked up by the track's title — the exact `title` arg passed to
        // sub-add) over the synthetic mpv id. Demuxed tracks and side-loaded
        // tracks without a registered id fall through to `"mpv_{t}_{id}"`.
        val resolvedId = if (trackType == TrackType.SUBTITLE && entry.external) {
            entry.title?.let { sideLoadedSubtitleIds[it] }
        } else {
            null
        }
        MediaTrack(
            id = resolvedId ?: "mpv_${entry.type}_${entry.id}",
            index = entry.id,
            label = TrackLabelFormatter.primary(info),
            language = entry.lang,
            isSelected = entry.selected,
            type = trackType,
            streamIndex = entry.ffIndex,
            badges = TrackLabelFormatter.badges(info),
        )
    }
}
