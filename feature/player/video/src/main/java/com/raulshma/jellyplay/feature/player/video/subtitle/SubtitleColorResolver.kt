package com.raulshma.jellyplay.feature.player.video.subtitle

import com.raulshma.jellyplay.core.model.SubtitleStyle

/**
 * Resolves the nullable ARGB fields on [SubtitleStyle] over the legacy
 * [com.raulshma.jellyplay.core.model.SubtitleColor] enum values. ARGB wins
 * when present; null falls back to the enum so old DataStore entries and the
 * preset color chips behave exactly as before the free-form picker shipped.
 */
internal object SubtitleColorResolver {
    fun resolveTextColor(style: SubtitleStyle): Int =
        style.fontColorArgb ?: style.fontColor.value

    fun resolveBackgroundColor(style: SubtitleStyle): Int =
        style.backgroundColorArgb ?: style.backgroundColor.value

    fun resolveEdgeColor(style: SubtitleStyle): Int =
        style.edgeColorArgb ?: style.edgeColor.value
}
