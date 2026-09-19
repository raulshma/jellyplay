package com.raulshma.jellyplay.core.data.cast

/**
 * Appends the active track / quality selections onto a Jellyfin stream URL as
 * standard query params (`AudioStreamIndex`, `SubtitleStreamIndex`,
 * `MaxVideoBitrate`), used by the DLNA and Google Cast transports which both
 * consume a server URL. Existing params are preserved; only non-null options
 * are added. Used for the cast-handoff fix.
 *
 * Lives in commonMain beside [CastMediaOptions] (CastStateFanout precedent):
 * the decision is pure string shaping over platform-free data. The Android
 * side keeps only thin folds on top — CastManager's `MediaItem.withCastOptions`
 * rewrites the media3 item's URI through this helper, and the DLNA strategy
 * applies it to its plain URL directly.
 *
 * Declared divergence: [CastMediaOptions.mediaSourceId] is deliberately NOT
 * folded into the URL — it targets source selection on the PlaybackInfo /
 * admin play-command side (the Jellyfin Remote Play transport), not the
 * stream URL's query params.
 */
internal fun String.withCastQueryParams(options: CastMediaOptions): String {
    if (options.audioStreamIndex == null &&
        options.subtitleStreamIndex == null &&
        options.maxVideoBitrate == null
    ) {
        return this
    }
    val separator = if ('?' in this) "&" else "?"
    val params = buildList {
        options.audioStreamIndex?.let { add("AudioStreamIndex=${it}") }
        options.subtitleStreamIndex?.let { add("SubtitleStreamIndex=${it}") }
        options.maxVideoBitrate?.let { add("MaxVideoBitrate=${it}") }
    }
    return this + separator + params.joinToString("&")
}
