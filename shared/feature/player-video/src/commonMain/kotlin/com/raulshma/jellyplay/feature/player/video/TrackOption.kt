package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.feature.player.video.engine.TrackBadge

/**
 * One selectable track row in the audio/subtitle picker. The I/O contract type
 * for [TrackSelectionPolicy] and the picker UI alike — engine-agnostic, so the
 * pure resolution ladders operate on it without touching a [com.raulshma.jellyplay.feature.player.video.engine.MediaEngine].
 */
@Immutable
data class TrackOption(
    val index: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
    /**
     * The engine track's container stream index (mpv `ff-index`), when exposed.
     * Equals the server's `MediaStream.index` for demuxed tracks, so it is the
     * robust key for resolving a stored Jellyfin stream selection. Null for
     * side-loaded tracks / engines that don't expose it (matched by label).
     */
    val streamIndex: Int? = null,
    /**
     * Ordered role badges (Forced/Default/SDH) rendered beside the label by the
     * picker. Mirrors [com.raulshma.jellyplay.feature.player.video.engine.MediaTrack.badges].
     */
    val badges: List<TrackBadge> = emptyList(),
    /**
     * The engine track's identifier, when one aligns with a
     * [com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource.id]
     * (ExoPlayer propagates the side-loaded subtitle's id into the track
     * format). Used by the subtitle-sync preview to resolve the active external
     * source exactly instead of guessing by label. Null for server-origin
     * (pre-reload) and side-loaded mpv/libVLC tracks — those fall back to label
     * matching.
     */
    val id: String? = null,
)
