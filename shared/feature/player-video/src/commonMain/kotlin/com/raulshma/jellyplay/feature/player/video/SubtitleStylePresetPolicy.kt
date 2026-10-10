package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.SubtitleStylePreset

/**
 * Pure policy for the subtitle style presets: the built-in quartet, the
 * user-preset list bookkeeping (save with same-name replace + oldest eviction
 * under a hard cap, delete), and the preset→style application fold. Pure data
 * over the existing [SubtitleStyleController.setStyle] path — no engine, no
 * store, no Compose — so the whole surface is JVM-testable
 * ([SubtitleStylePresetPolicyTest]).
 *
 * Built-ins are CODE (stable ids, style values here), never data: only the
 * user-preset list persists (on the subtitle slice, riding the settings
 * backup). Labels localize at the UI layer off the stable [id]s.
 */
internal object SubtitleStylePresetPolicy {

    /** Stable id of a built-in preset; the UI maps it to a localized label. */
    internal enum class BuiltInPreset(val id: String, val style: SubtitleStyle) {

        /** Small, white, shadow-free — disappears until you need it. */
        SUBTLE(
            "subtle",
            SubtitleStyle(
                applyCustomStyle = true,
                fontSize = 22,
                fontColor = SubtitleColor.WHITE,
                backgroundColor = SubtitleColor.BLACK,
                backgroundOpacity = 0f,
                edgeType = SubtitleEdgeType.DROP_SHADOW,
                edgeColor = SubtitleColor.BLACK,
                borderWidth = 1.5f,
                shadowOffset = 1f,
                bold = false,
                italic = false,
            ),
        ),

        /** Large, bold, thick black outline — readable across the room. */
        BIG_BOLD(
            "big_bold",
            SubtitleStyle(
                applyCustomStyle = true,
                fontSize = 40,
                fontColor = SubtitleColor.WHITE,
                backgroundColor = SubtitleColor.BLACK,
                backgroundOpacity = 0f,
                edgeType = SubtitleEdgeType.OUTLINE,
                edgeColor = SubtitleColor.BLACK,
                borderWidth = 3.5f,
                shadowOffset = 1.5f,
                bold = true,
                italic = false,
            ),
        ),

        /** Yellow-on-black-outline, the classic hardsub look. */
        CLASSIC_YELLOW(
            "classic_yellow",
            SubtitleStyle(
                applyCustomStyle = true,
                fontSize = 26,
                fontColor = SubtitleColor.YELLOW,
                backgroundColor = SubtitleColor.BLACK,
                backgroundOpacity = 0f,
                edgeType = SubtitleEdgeType.OUTLINE,
                edgeColor = SubtitleColor.BLACK,
                borderWidth = 2.5f,
                shadowOffset = 1f,
                bold = false,
                italic = false,
            ),
        ),

        /** White text over a semi-transparent black band, Netflix-style. */
        NETFLIXISH(
            "netflixish",
            SubtitleStyle(
                applyCustomStyle = true,
                fontSize = 26,
                fontColor = SubtitleColor.WHITE,
                backgroundColor = SubtitleColor.BLACK,
                backgroundOpacity = 0.4f,
                edgeType = SubtitleEdgeType.DROP_SHADOW,
                edgeColor = SubtitleColor.BLACK,
                borderWidth = 2f,
                shadowOffset = 1f,
                bold = false,
                italic = false,
            ),
        ),
    }

    /** All four built-ins in display order. */
    val builtIns: List<BuiltInPreset> = BuiltInPreset.entries.toList()

    /** Hard cap on persisted user presets; the OLDEST entry beyond it evicts. */
    const val MAX_USER_PRESETS = 8

    /**
     * Saves [style] under [name] into [presets]: a blank name saves nothing,
     * an existing name REPLACES in place (it keeps its position — the user is
     * updating that preset, not re-adding it), otherwise the new preset
     * appends and — past [MAX_USER_PRESETS] — the oldest (first) entry evicts.
     */
    fun saveUser(presets: List<SubtitleStylePreset>, name: String, style: SubtitleStyle): List<SubtitleStylePreset> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return presets
        val preset = SubtitleStylePreset(name = trimmed, style = style)
        val existingIndex = presets.indexOfFirst { it.name.equals(trimmed, ignoreCase = true) }
        if (existingIndex >= 0) {
            return presets.toMutableList().apply { this[existingIndex] = preset }
        }
        val appended = presets + preset
        return if (appended.size > MAX_USER_PRESETS) appended.drop(appended.size - MAX_USER_PRESETS) else appended
    }

    /** Removes the preset named [name] (case-insensitive); unknown names no-op. */
    fun delete(presets: List<SubtitleStylePreset>, name: String): List<SubtitleStylePreset> =
        presets.filterNot { it.name.equals(name.trim(), ignoreCase = true) }

    /**
     * The style a preset APPLIES: the preset's look forced authoritative
     * ([SubtitleStyle.applyCustomStyle] — a preset must activate custom
     * styling, including the "saved while override was off" case) while the
     * current item's resolved sync [SubtitleStyle.offsetMs] carries over —
     * the delay is per-item state, never part of a look.
     */
    fun appliedStyle(preset: SubtitleStylePreset, currentStyle: SubtitleStyle): SubtitleStyle =
        preset.style.copy(applyCustomStyle = true, offsetMs = currentStyle.offsetMs)
}
