package com.raulshma.jellyplay.feature.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.ExperimentalFeature

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
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

object ExperimentalFeatures {

    val all: List<ExperimentalFeatureInfo> = listOf(
        ExperimentalFeatureInfo(
            feature = ExperimentalFeature.HOME_CARD_CLIPPING,
            title = "Card Clipping",
            subtitle = "Clip home-screen cards to their row edges (and rounded shape) while scrolling",
            icon = Tabler.Outline.Crop,
        ),
        ExperimentalFeatureInfo(
            feature = ExperimentalFeature.MEDIA_CARD_PEEK,
            title = "Press-and-Hold Preview",
            subtitle = "Long-press a media card to peek a blurred detail preview; release to dismiss",
            icon = Tabler.Outline.Eye,
        ),
        ExperimentalFeatureInfo(
            feature = ExperimentalFeature.DIRECT_ARR_INTEGRATION,
            title = "Direct *arr Integration",
            subtitle = "Show download-queue progress on Requests and a coming-soon calendar on Home from your Radarr/Sonarr. Server details are auto-discovered from Seerr; manual override in *arr settings",
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
