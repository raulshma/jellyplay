package com.raulshma.jellyplay.core.ui.player

/**
 * Localized transcode-reason value (wave 9A): the "why is this transcoding"
 * rows the video player's stats overlay and playback-error dialog render.
 * Implemented on Android by the legacy core:ui `FormattedTranscodeReason`
 * (localized through TranscodeReasonsFormatter); desktop builds same-shape
 * values with a raw-token echo until the formatter's string tables migrate
 * into this module.
 */
interface LocalizedTranscodeReason {
    /** The raw server token this row was resolved from. */
    val raw: String

    /** Pre-localized explanation; unknown tokens embed their raw server text. */
    val explanation: String

    /** Remedy pointing at an in-app control, `null` when none applies. */
    val hint: String?

    /** Canonical multi-line render: explanation, then hint on a second line when present. */
    val renderedText: String
}
