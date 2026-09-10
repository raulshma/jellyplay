package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.platformEngineSupport

/**
 * Shared choreography for the read-time preferred-player clamp
 * (`normalizePreferredPlayer`): tests write an engine and must predict what
 * the read surfaces on whichever platform runs the suite.
 */

/**
 * A write target for "switch to another engine": a shipped engine other than
 * [current] if the platform has one (the honest write), otherwise the
 * unshipped EXO_PLAYER probe that must clamp back to the shipped default
 * (the desktop/mpv-only case).
 */
internal fun alternateShippedEngineOrProbe(current: PlayerType): PlayerType =
    platformEngineSupport.engines.filter { it != current }.firstOrNull() ?: PlayerType.EXO_PLAYER

/** What a `setPreferredPlayer([target])` read must surface after the clamp. */
internal fun clampedPreferredPlayer(target: PlayerType): PlayerType =
    if (target in platformEngineSupport.engines) target else platformEngineSupport.default
