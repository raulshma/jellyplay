package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.progressFraction

/**
 * [progressFraction] memoized on the item's identity and its playback
 * position/runtime ticks, so the division is not recomputed on every
 * recomposition of a card.
 */
@Composable
fun MediaItem.rememberProgressFraction(): Float? =
    remember(id, playbackPositionTicks, runTimeTicks) { progressFraction() }
