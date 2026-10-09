package com.raulshma.jellyplay.feature.player.live.engine

import com.raulshma.jellyplay.core.ui.player.TranscodeReasonCatalog
import com.raulshma.jellyplay.core.ui.player.distinctTranscodeReasons
import org.jetbrains.compose.resources.getString

/**
 * Localizes the server-reported transcode-reason tokens for the error
 * overlay's detail block (player-live conveyor seam). Returns the
 * per-reason rendered lines (explanation + optional hint); the session joins
 * them with `\n`.
 *
 * The ONE implementation resolves through core:ui's commonMain
 * [TranscodeReasonCatalog] via compose resources — the same table the video
 * stats overlay and the admin transcodes monitor read, so every surface
 * renders identical text (including the unknown-token fallback, which
 * interpolates the raw server token). Resolution is suspend because the
 * session renders off composition (the same compose-resources
 * `getString` path PlayerSessionManager/PlayerWiring already use).
 */
fun interface TranscodeReasonsRenderer {
    suspend fun render(rawReasons: List<String>): List<String>
}

/** The catalog-backed renderer — the seam's only production binding. */
object CatalogTranscodeReasonsRenderer : TranscodeReasonsRenderer {
    override suspend fun render(rawReasons: List<String>): List<String> =
        rawReasons.distinctTranscodeReasons().map { raw ->
            val strings = TranscodeReasonCatalog.lookup(raw)
            val explanation = strings?.let { getString(it.explanation) }
                ?: getString(TranscodeReasonCatalog.unknownTemplate, raw)
            val hint = strings?.hint?.let { getString(it) }
            TranscodeReasonCatalog.renderedLine(explanation, hint)
        }
}
