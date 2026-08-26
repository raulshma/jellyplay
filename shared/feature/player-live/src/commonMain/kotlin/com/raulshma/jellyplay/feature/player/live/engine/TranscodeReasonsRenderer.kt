package com.raulshma.jellyplay.feature.player.live.engine

/**
 * Localizes the server-reported transcode-reason tokens for the error
 * overlay's detail block (player-live conveyor seam). The legacy call was
 * `TranscodeReasonsFormatter.format(context, reasons)` — that formatter is
 * Android-coupled (Context + legacy R) and stays in the :core:ui shim, so
 * the shared ViewModel calls this seam instead and the androidMain Koin
 * wiring (`androidPlayerLiveModule`) delegates to the legacy formatter
 * verbatim. Returns the per-reason rendered lines (explanation + optional
 * hint); the ViewModel joins them with `\n`.
 */
fun interface TranscodeReasonsRenderer {
    fun render(rawReasons: List<String>): List<String>
}
