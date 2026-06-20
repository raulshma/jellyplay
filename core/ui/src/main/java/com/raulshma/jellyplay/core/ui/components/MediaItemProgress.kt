package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.MediaItem

fun MediaItem.progressFraction(): Float? {
    val position = playbackPositionTicks ?: return null
    val runtime = runTimeTicks?.takeIf { it > 0 } ?: return null
    return (position.toFloat() / runtime.toFloat()).coerceIn(0f, 1f)
}
