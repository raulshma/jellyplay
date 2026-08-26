package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Composable

/**
 * Transcode-reason value seam (wave 9A): the localized "why is this
 * transcoding" rows the stats overlay and playback-error dialog render. The
 * androidMain actual is a typealias to the legacy core:ui
 * `FormattedTranscodeReason` (localized by [rememberFormattedTranscodeReasons]'s
 * android actual through TranscodeReasonsFormatter); the jvmMain actual is a
 * same-shape local class whose formatter echoes the raw server token — the
 * same fallback text Android shows for unknown tokens — until the legacy
 * formatter's string tables migrate to shared core:ui.
 */
expect class PlatformTranscodeReason {
    val raw: String
    /** Pre-localized explanation; unknown tokens embed their raw server text. */
    val explanation: String
    /** In-app remedy hint, `null` when no user action applies. */
    val hint: String?
    /** Canonical multi-line render: explanation, then hint on a second line. */
    val renderedText: String
}

/**
 * Localize the session's raw transcode-reason tokens once per distinct list
 * (and per context, so a locale change re-formats). Android keeps the
 * TranscodeReasonsFormatter path verbatim; desktop maps each token to a raw
 * echo row.
 */
@Composable
internal expect fun rememberFormattedTranscodeReasons(rawReasons: List<String>): List<PlatformTranscodeReason>
