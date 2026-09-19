package com.raulshma.jellyplay.feature.player.video.engine

import androidx.media3.common.PlaybackException

/**
 * Thin Media3 adapter over the shared [ExoErrorTaxonomy] table (commonMain):
 * reads the exception's stable `errorCode` int + message + cause and delegates
 * — the bucket table itself is jvmTest-pinned there, the `MpvErrorTaxonomy`
 * pattern. Kept as an extension so any Media3-based engine — ExoPlayer today,
 * the live engine when it adopts structured errors — maps codes through one
 * table instead of forking it.
 */
fun PlaybackException.toEngineError(): EngineError =
    ExoErrorTaxonomy.fromCode(errorCode, message, cause)
