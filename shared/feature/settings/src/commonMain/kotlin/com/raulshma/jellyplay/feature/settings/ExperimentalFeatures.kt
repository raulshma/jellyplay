package com.raulshma.jellyplay.feature.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import org.jetbrains.compose.resources.StringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_arr_direct_integration
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_exp_arr_integration_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_exp_card_clipping
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_exp_card_clipping_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_exp_press_hold_preview
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_exp_press_hold_preview_subtitle

/**
 * Presentation metadata for an [ExperimentalFeature]. Keeps UI strings/icons
 * in the settings layer so the `core/model` enum stays a pure identifier.
 *
 * To surface a new experimental feature in the Experimental screen, add a
 * matching [ExperimentalFeatureInfo] entry to [ExperimentalFeatures.all].
 * The screen renders one toggle per entry, in declaration order.
 */
data class ExperimentalFeatureInfo(
    val feature: ExperimentalFeature,
    val titleRes: StringResource,
    val subtitleRes: StringResource,
    val icon: ImageVector,
)

object ExperimentalFeatures {

    val all: List<ExperimentalFeatureInfo> = listOf(
        ExperimentalFeatureInfo(
            feature = ExperimentalFeature.HOME_CARD_CLIPPING,
            titleRes = Res.string.settings_exp_card_clipping,
            subtitleRes = Res.string.settings_exp_card_clipping_subtitle,
            icon = Tabler.Outline.Crop,
        ),
        ExperimentalFeatureInfo(
            feature = ExperimentalFeature.MEDIA_CARD_PEEK,
            titleRes = Res.string.settings_exp_press_hold_preview,
            subtitleRes = Res.string.settings_exp_press_hold_preview_subtitle,
            icon = Tabler.Outline.Eye,
        ),
        ExperimentalFeatureInfo(
            feature = ExperimentalFeature.DIRECT_ARR_INTEGRATION,
            titleRes = Res.string.settings_arr_direct_integration,
            subtitleRes = Res.string.settings_exp_arr_integration_subtitle,
            icon = Tabler.Outline.Download,
        ),
    )

    /**
     * Returns the presentation metadata for a given feature id (used by
     * settings search deep-linking). Returns null for ids without a
     * registered entry so the screen degrades gracefully instead of crashing.
     */
    fun infoFor(id: String): ExperimentalFeatureInfo? =
        all.firstOrNull { it.feature.name == id }
}
