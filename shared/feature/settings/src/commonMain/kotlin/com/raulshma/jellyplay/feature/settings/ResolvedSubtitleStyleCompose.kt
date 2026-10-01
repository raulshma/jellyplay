package com.raulshma.jellyplay.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.model.ResolvedSubtitleStyle
import com.raulshma.jellyplay.core.model.SubtitleEdgeType

/**
 * Compose mapping for [ResolvedSubtitleStyle], consumed by the settings
 * subtitle preview ([SubtitleStylePreview]) and pinned by its jvmTest suite.
 * Lives in feature/settings, next to its only consumer (not core/model) so it
 * can return Compose `Color`/`Shadow` types — core/model depends only on
 * compose-runtime and stays free of compose-ui.
 *
 * Replaces the preview's hand-rolled `subtitleColorToCompose` `when`, the
 * `FontWeight.SemiBold` literal (now honours `bold`), and the inline shadow
 * constants — one mapping, single consumer. The player overlay resolves
 * colors through core/ui's `SubtitleColorMap` instead.
 */

/** Map a resolved ARGB int to a Compose [Color]. */
internal fun resolvedColor(argb: Int): Color = Color(argb)

/** The Compose font weight for a resolved style: bold ⇒ Bold, else SemiBold (legibility default). */
internal fun resolvedFontWeight(bold: Boolean): FontWeight =
    if (bold) FontWeight.Bold else FontWeight.SemiBold

/**
 * The Compose [Shadow] for a resolved edge type, or null for NONE. The blur/offset
 * magnitudes are the same ones the old preview hand-rolled — one mapping so the
 * preview can't drift from itself; the player overlay resolves its shadow through
 * core/ui's `SubtitleColorMap` instead (NOT through this file).
 */
internal fun resolvedShadow(style: ResolvedSubtitleStyle): Shadow? {
    val edgeColor = Color(style.edgeColorArgb)
    return when (style.edgeType) {
        SubtitleEdgeType.NONE -> null
        SubtitleEdgeType.OUTLINE -> Shadow(color = edgeColor, blurRadius = 4f)
        SubtitleEdgeType.DROP_SHADOW -> Shadow(
            color = edgeColor.copy(alpha = 0.8f),
            blurRadius = 8f,
            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
        )
        SubtitleEdgeType.RAISED -> Shadow(
            color = edgeColor.copy(alpha = 0.7f),
            blurRadius = 2f,
            offset = androidx.compose.ui.geometry.Offset(1.5f, 1.5f),
        )
        SubtitleEdgeType.DEPRESSED -> Shadow(
            color = edgeColor.copy(alpha = 0.7f),
            blurRadius = 2f,
            offset = androidx.compose.ui.geometry.Offset(-1.5f, -1.5f),
        )
    }
}
