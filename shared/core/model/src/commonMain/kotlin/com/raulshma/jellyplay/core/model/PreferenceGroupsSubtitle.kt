package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The subtitle/language preference aggregates: the logical subtitle domain
 * and the LanguageSettingsScreen slice.
 */

@Immutable
@Serializable
data class SubtitlePreferences(
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val preferredSubtitleLanguage: String? = null,
    val preferredAudioLanguage: String? = null,
)

/** Fields read by `LanguageSettingsScreen`. */
@Immutable
@Serializable
data class LanguagePreferences(
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val preferredSubtitleLanguage: String? = null,
    val preferredAudioLanguage: String? = null,
    val subtitlesForcedOnly: Boolean = false,
    val highContrastSubtitles: Boolean = false,
    val pgsSubtitleDirectPlay: Boolean = false,
    val hdrSubtitleStyleEnabled: Boolean = false,
    val hdrSubtitleStyle: SubtitleStyle = SubtitleStyle(
        fontSize = 28,
        backgroundOpacity = 0.5f,
        edgeType = SubtitleEdgeType.OUTLINE,
    ),
    val appLanguage: String? = null,
    /** The track-language rule set edited by the screen's track-selection group. */
    val languageRules: LanguageRuleSet = LanguageRuleSet(),
)
