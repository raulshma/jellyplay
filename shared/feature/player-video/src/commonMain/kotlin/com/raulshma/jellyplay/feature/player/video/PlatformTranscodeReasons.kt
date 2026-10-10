package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.ui.player.TranscodeReasonCatalog
import com.raulshma.jellyplay.core.ui.player.distinctTranscodeReasons
import org.jetbrains.compose.resources.stringResource

/**
 * Transcode-reason value: one localized "why is this transcoding" row for
 * the stats overlay and playback-error dialog. Resolved through core:ui's
 * ONE [TranscodeReasonCatalog] (compose resources localize on both
 * platforms); unknown tokens embed their raw server text.
 */
class PlatformTranscodeReason(
    val raw: String,
    /** Pre-localized explanation; unknown tokens embed their raw server text. */
    val explanation: String,
    /** In-app remedy hint, `null` when no user action applies. */
    val hint: String?,
) {
    /** Canonical multi-line render: explanation, then hint on a second line. */
    val renderedText: String
        get() = TranscodeReasonCatalog.renderedLine(explanation, hint)
}

/**
 * Localize the session's raw transcode-reason tokens once per distinct list
 * (and per locale, so a locale change re-formats) through the shared
 * commonMain catalog. Unknown tokens keep the raw server text, same as every
 * other catalog consumer.
 */
@Composable
internal fun rememberFormattedTranscodeReasons(
    rawReasons: List<String>,
): List<PlatformTranscodeReason> {
    val rows = remember(rawReasons) {
        rawReasons.distinctTranscodeReasons()
            .map { raw -> raw to TranscodeReasonCatalog.lookup(raw) }
    }
    return rows.map { (raw, strings) ->
        PlatformTranscodeReason(
            raw = raw,
            explanation = strings?.let { stringResource(it.explanation) }
                ?: stringResource(TranscodeReasonCatalog.unknownTemplate, raw),
            hint = strings?.hint?.let { stringResource(it) },
        )
    }
}
