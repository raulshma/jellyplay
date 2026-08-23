package com.raulshma.jellyplay.feature.player.video.engine

/**
 * A single subtitle cue bounded by start/end media-time microseconds, with its
 * rendered text. The shared value type for both subtitle-parsing paths:
 *  - [com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleParserHelper]
 *    produces a full [List]<[TimedCue]> from an external text subtitle file.
 *  - [MediaEngine.currentCues] accumulates them live from an engine's rendered
 *    output (e.g. ExoPlayer's `onCues`) for embedded subs that can't be
 *    re-fetched as bytes.
 *
 * Lives in the player contract module so the [MediaEngine] interface (which is
 * the reactive surface consumed by the player feature) can expose it. The
 * original `com.raulshma.jellyplay.feature.player.video.subtitle.TimedCue`
 * symbol is retained as a `typealias` for source compatibility. The media3
 * `@UnstableApi` opt-in annotation was dropped in the Phase V2 common-ization —
 * the data class itself is pure.
 */
data class TimedCue(
    val startTimeUs: Long,
    val endTimeUs: Long,
    val text: CharSequence,
)
