package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Card-rendering preferences surfaced to media-card composables
 * (`PosterCard`, `WideMediaCard`, …) so they can honour the user's
 * appearance settings without each call site having to thread the
 * flags manually.
 */
@Immutable
data class CardDisplayPreferences(
    val showUnwatchedBadge: Boolean = true,
    val showWatchedCheckmark: Boolean = true,
    val hideWatchedItems: Boolean = false,
    /** H11: quality badges (4K / HDR / DV / Atmos) on poster + wide cards. */
    val showQualityBadges: Boolean = true,
    /** H12: "NEW" dot for items added since the last home visit. */
    val showNewBadges: Boolean = true,
    /**
     * H15 anti-spoiler home mode — when on, the card render sites force-hide
     * watch-state spoilers (progress bars, "time left", watched checkmark,
     * unwatched count) regardless of the individual flags above.
     */
    val antiSpoilerHome: Boolean = false,
)

val LocalCardDisplayPreferences: ProvidableCompositionLocal<CardDisplayPreferences> =
    compositionLocalOf { CardDisplayPreferences() }

/**
 * The anti-spoiler mode's resolved card-render decisions (H15) — the ONE
 * pure fold every suppression point consumes, so the policy (what counts as
 * a watch-state spoiler) lives in a single testable place instead of being
 * hand-derived at the scaffold, the footers, the poster overlays and the
 * wide-card title.
 *
 * Precedence (documented, pinned by [spoilerSafeCardState]'s tests): when
 * [CardDisplayPreferences.antiSpoilerHome] is ON, the four spoiler surfaces
 * — progress bars, "Xm left" footers, watched checkmark + unwatched count
 * badges, and episode titles on wide resume/next-up cards — are force-hidden
 * regardless of the individual [CardDisplayPreferences.showWatchedCheckmark] /
 * [CardDisplayPreferences.showUnwatchedBadge] flags; [CardDisplayPreferences.hideWatchedItems]
 * row filtering keeps its own semantics (whole-card filtering is a curation
 * choice, not an on-card spoiler), and rating badges are never spoilers.
 */
@Immutable
data class SpoilerSafeCardState(
    /** The card's progress bar (and the play ring's progress arc). */
    val showProgress: Boolean,
    /** The footer's "Xm left" remaining-time segment. */
    val showTimeLeft: Boolean,
    /** The watched checkmark + unwatched-count overlays. */
    val showWatchedBadges: Boolean,
    /** Episode titles on wide (resume/next-up) cards. */
    val showEpisodeTitles: Boolean,
)

/**
 * Folds [prefs] into the suppression decisions above: every surface visible
 * when anti-spoiler is off; every spoiler surface suppressed when it's on.
 * Pure and Compose-free — the HomeBackdrop-decision-test shape.
 */
fun spoilerSafeCardState(prefs: CardDisplayPreferences): SpoilerSafeCardState =
    if (prefs.antiSpoilerHome) {
        SpoilerSafeCardState(
            showProgress = false,
            showTimeLeft = false,
            showWatchedBadges = false,
            showEpisodeTitles = false,
        )
    } else {
        SpoilerSafeCardState(
            showProgress = true,
            showTimeLeft = true,
            showWatchedBadges = true,
            showEpisodeTitles = true,
        )
    }
