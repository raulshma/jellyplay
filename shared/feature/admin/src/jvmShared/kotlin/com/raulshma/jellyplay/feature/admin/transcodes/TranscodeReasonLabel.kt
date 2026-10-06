package com.raulshma.jellyplay.feature.admin.transcodes

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.ui.player.TranscodeReasonCatalog

/**
 * ONE reason-label seam for the admin transcode surfaces (the transcodes
 * monitor and the analytics session rows): known server tokens resolve
 * through the shared [TranscodeReasonCatalog] — the same localized table the
 * player's meta row reads, so admin does not grow a second, English-only
 * vocabulary. Tokens the catalog does not know yet fall back to
 * [humanizeTranscodeReason], keeping unknown server additions readable.
 */
@Composable
internal fun String.transcodeReasonLabel(): String {
    val strings = TranscodeReasonCatalog.lookup(this)
        ?: return humanizeTranscodeReason()
    return stringResource(strings.explanation)
}

/**
 * The row-level form — one comma-joined label list. Resolution runs in a
 * plain loop, not a `joinToString` transform (whose lambda is not inline, so
 * composable calls cannot ride it).
 */
@Composable
internal fun List<String>.transcodeReasonsLabel(): String {
    val labels = ArrayList<String>(size)
    for (reason in this) labels += reason.transcodeReasonLabel()
    return labels.joinToString(separator = ", ")
}
