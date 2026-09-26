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
 * Input is the binding-agnostic [MpvTrackEntry] row: each host translates its
 * own parsed `track-list` node tree (Android `MPVNode` maps, desktop
 * `MpvLib.readNode` Kotlin trees) into entries and delegates the
 * [MediaTrack] construction here.
 */
object MpvTrackCatalog {

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
