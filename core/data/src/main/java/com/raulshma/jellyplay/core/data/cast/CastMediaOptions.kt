package com.raulshma.jellyplay.core.data.cast

import androidx.compose.runtime.Immutable

/**
 * Carries the locally-selected playback intent that should survive a cast
 * handoff: which audio/subtitle stream is active, the requested quality
 * ceiling, and the media source to target.
 *
 * Threading these through [com.raulshma.jellyplay.core.data.cast.CastManager.loadMedia]
 * ensures that casting a session does not silently drop
 * the user's track and quality selections — each strategy applies the values
 * in the way its transport supports (URL query params for DLNA / Google Cast,
 * admin play-command fields for Jellyfin Remote Play).
 *
 * All fields are optional; `null` means "no override / leave the server
 * default" so existing callers behave exactly as before.
 */
@Immutable
data class CastMediaOptions(
    val mediaSourceId: String? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val maxVideoBitrate: Int? = null,
)
