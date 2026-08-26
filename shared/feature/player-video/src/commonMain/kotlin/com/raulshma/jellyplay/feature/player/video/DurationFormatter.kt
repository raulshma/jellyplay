package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.ui.components.formatDurationMs
import kotlin.math.abs

internal fun formatDuration(ms: Long): String {
    return if (ms < 0) {
        "-" + formatDurationMs(abs(ms))
    } else {
        formatDurationMs(ms)
    }
}
