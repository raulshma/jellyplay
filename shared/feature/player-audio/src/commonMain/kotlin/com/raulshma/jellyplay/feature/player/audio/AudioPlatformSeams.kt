package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.runtime.Composable

/**
 * Locale 24-hour clock preference for the audio sleep-timer sheet's
 * projected "Stops at" label — the audio twin of player-video's
 * `PlatformWindowSeam.rememberIs24HourFormat` (player-audio does not depend
 * on player-video). Android reads the system setting; desktop derives it
 * from the default JDK time format pattern.
 */
@Composable
internal expect fun rememberIs24HourFormat(): Boolean
