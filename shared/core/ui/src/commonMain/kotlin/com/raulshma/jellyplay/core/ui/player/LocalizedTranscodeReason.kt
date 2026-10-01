package com.raulshma.jellyplay.core.ui.player

/**
 * Localized transcode-reason value: the "why is this transcoding"
 * rows the video player's stats overlay and playback-error dialog render.
 * Implemented on Android by `FormattedTranscodeReason` (androidMain,
 * localized through TranscodeReasonsFormatter's R.string table) and on
 * desktop by player-video's jvm actual, which resolves the shared
 * commonMain `TranscodeReasonCatalog` through compose resources — both
 * platforms localize.
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
