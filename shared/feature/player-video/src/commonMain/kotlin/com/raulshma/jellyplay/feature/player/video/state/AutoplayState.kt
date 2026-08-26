package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable

/**
 * Auto-play-next-episode settings + the user's cancellation of the current countdown.
 */
@Immutable
data class AutoplayState(
    val videoAutoplayNext: Boolean = false,
    val autoPlayCountdownSec: Int = 10,
    val autoplayCancelled: Boolean = false,
)
