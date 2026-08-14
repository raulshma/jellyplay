package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.SubtitleStyle

/**
 * True when [old] and [new] differ in any subtitle-style field *other than*
 * [SubtitleStyle.offsetMs].
 *
 * Subtitle delay (`offsetMs`) is mirrored into both [EngineConfig.subtitleDelayMs]
 * and [EngineConfig.subtitleStyle] by [com.raulshma.jellyplay.feature.player.video.EngineConfigBuilder],
 * so a structural `!=` on the whole [SubtitleStyle] would report a change on a
 * delay-only adjustment. Engines that apply delay live via a runtime setter
 * (libVLC `setSpuDelay`, mpv `sub-delay`) must therefore use this predicate —
 * not a whole-object compare — when deciding whether a reload-requiring style
 * change (font/color/position, which are load-time options) occurred.
 *
 * Comparison is performed by normalising [old]'s delay to [new]'s, so the delay
 * field cancels out and the remaining fields are compared structurally.
 */
internal fun styleChangedExcludingDelay(old: SubtitleStyle, new: SubtitleStyle): Boolean =
    old.copy(offsetMs = new.offsetMs) != new
