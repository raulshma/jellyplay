package com.raulshma.jellyplay.core.model

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
}

/**
 * Convenience helper keeping the "is this experimental feature on?" check
 * readable at call sites.
 */
fun UserPreferences.isExperimentalEnabled(feature: ExperimentalFeature): Boolean =
    feature in enabledExperimentalFeatures
