package com.raulshma.jellyplay.core.model

import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import kotlinx.serialization.Serializable

/**
 * Opt-in experimental features that are disabled by default.
 *
 * Adding a new experimental feature:
 *  1. Add an enum constant here (it becomes the persisted identifier).
 *  2. Provide presentation metadata (title/subtitle/icon) in the settings
 *     feature's `ExperimentalFeatures` registry so the Experimental screen
 *     can render a toggle for it.
 *  3. Read it from `UserPreferences.enabledExperimentalFeatures` at the
 *     call site.
 *
 * The identifier is persisted by [name] so existing entries survive
 * reordering. Do not rename existing constants once released.
 */
@Serializable
enum class ExperimentalFeature {
    /**
     * Clips home-screen horizontal rows and their cards to the row edges /
     * rounded shape (carousel capping + `clipToBounds` + card elevation clip).
     * Off by default so cards and their elevation bleed past the row bounds
     * (the historical rendering without the carousel container effect).
     */
    HOME_CARD_CLIPPING,

    /**
     * Press-and-hold "peek" preview: long-pressing a media card morphs a rich
     * detail card out of it (with the screen sides blurred); lift the finger to
     * dismiss. Off by default while the gesture/animation is still settling.
     * Phone-only regardless of this setting.
     */
    MEDIA_CARD_PEEK,

    /**
     * Direct Radarr / Sonarr integration. When enabled, JellyPlay contacts the
     * configured *arr instances directly (in addition to the existing Seerr
     * proxy) to surface download-queue progress on Requests and a
     * "Recently Grabbed" calendar row on Home — closing the gap between
     * "request approved" and "available in Jellyfin."
     *
     * Server credentials are auto-discovered from Seerr's `/service/` endpoints
     * and may be overridden manually under Settings → *arr. Off by default
     * while the integration stabilizes; never rename (persisted by [name]).
     */
    DIRECT_ARR_INTEGRATION,

    /**
     * Opt-in Material Design 3 Expressive Navigation Bar (Google Photos redesign style).
     * Off by default so the classic floating navigation bar remains the default navigation.
     */
    EXPRESSIVE_NAVIGATION,
}

/**
 * Convenience helper keeping the "is this experimental feature on?" check
 * readable at call sites.
 */
fun UserPreferences.isExperimentalEnabled(feature: ExperimentalFeature): Boolean =
    feature in enabledExperimentalFeatures

/**
 * Slice overload so the Experimental settings screen (which collects only
 * [ExperimentalPreferences]) can perform the same check without the whole
 * [UserPreferences] object.
 */
fun ExperimentalPreferences.isExperimentalEnabled(feature: ExperimentalFeature): Boolean =
    feature in enabledExperimentalFeatures
