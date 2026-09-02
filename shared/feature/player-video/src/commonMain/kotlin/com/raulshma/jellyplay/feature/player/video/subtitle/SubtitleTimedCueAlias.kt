package com.raulshma.jellyplay.feature.player.video.subtitle

/**
 * Source-compatibility alias: TimedCue's canonical home is the engine package
 * in :shared:core:player-contract (MediaEngine exposes it as `currentCues`).
 * Existing references to `subtitle.TimedCue` keep compiling.
 *
 * The alias used to live at the top of SubtitleParserHelper.kt; it moved to
 * this commonMain file with the wave 7C KMP migration because
 * VideoPlayerUiState (commonMain) and the subtitle sheets reference it while
 * SubtitleParserHelper itself is media3-typed and stays in androidMain.
 */
typealias TimedCue = com.raulshma.jellyplay.feature.player.video.engine.TimedCue
