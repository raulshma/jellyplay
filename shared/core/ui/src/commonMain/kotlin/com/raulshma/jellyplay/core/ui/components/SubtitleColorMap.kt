package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.model.SubtitleColor

/**
 * Map a [SubtitleColor] enum to its Compose [Color].
 *
 * Stays in core/ui (unlike its sibling mapping in feature/settings'
 * `ResolvedSubtitleStyleCompose`, which moved next to its only consumer):
 * onboarding's `SubtitlesStep` renders the color pickers and is the live
 * consumer, and features must not depend on each other.
 */
fun subtitleColorToCompose(color: SubtitleColor): Color = when (color) {
    SubtitleColor.WHITE -> Color.White
    SubtitleColor.YELLOW -> Color.Yellow
    SubtitleColor.GREEN -> Color.Green
    SubtitleColor.CYAN -> Color.Cyan
    SubtitleColor.RED -> Color.Red
    SubtitleColor.BLACK -> Color.Black
    SubtitleColor.BLUE -> Color.Blue
}
