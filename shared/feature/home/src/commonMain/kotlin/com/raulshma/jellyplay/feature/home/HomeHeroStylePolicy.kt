package com.raulshma.jellyplay.feature.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.HomeHeroStyle

/**
 * How much render chrome the hero carries over its backdrop.
 *
 * The carrier for the ambient-clip gate too: [MINIMAL] (the SLIDESHOW style)
 * suppresses the hero video billboard entirely — a pure backdrop slideshow —
 * while [STANDARD]/[RICH] leave the clip gates untouched (the pref + platform
 * + visibility gates in [heroVideoShouldPlay] still decide).
 */
enum class HomeHeroChromeVisibility {
    /**
     * Metadata overlay hidden until the user taps (touch) or focuses (TV) the
     * hero; no ambient video clip. The SLIDESHOW presentation.
     */
    MINIMAL,

    /**
     * The shipped CAROUSEL chrome: title, info chips, two-line synopsis and
     * the Play/Details actions — always visible. Render-identical to the
     * pre-style hero; the regression bar for this whole module.
     */
    STANDARD,

    /**
     * The DETAILED spotlight chrome: everything [STANDARD] renders, with the
     * synopsis expanded (five lines) and a taller hero (see
     * [DetailedHeroHeightExtra]).
     */
    RICH,
}

/**
 * The hero presentation decision for one style — the four render flags the
 * screen and the hero composables branch on. Pure data: Compose-free, so the
 * fold is synchronously testable and the render sites decide nothing.
 *
 * The zero-argument defaults ARE the CAROUSEL output — the shipped behavior —
 * so a [HomeHeroStyleDecision] default instance renders exactly the old hero.
 */
data class HomeHeroStyleDecision(
    /**
     * True: the backdrop spans the full content width with the shipped
     * dissolve-into-background treatment. False (DETAILED): the hero renders
     * as a contained spotlight card — inset by the screen content padding and
     * clipped to rounded corners.
     */
    val fullBleed: Boolean = true,
    val chromeVisibility: HomeHeroChromeVisibility = HomeHeroChromeVisibility.STANDARD,
    /**
     * The SLIDESHOW timed-advance interval in ms (the
     * `home_hero_slideshow_interval_sec` pref, clamped to the store's 3–60 s
     * write range). Null for every other style: CAROUSEL/DETAILED keep the
     * controller's shipped 8 s idle cadence, TV_SLIDER never auto-advances.
     * The empty-candidate pool needs no special case here — the cadence
     * collector already returns None on an empty pool.
     */
    val autoAdvanceMs: Long? = null,
    /**
     * True: the hero is a horizontal snap slider (the TV_SLIDER style) — a
     * pager over the rotation candidate list with D-pad left/right paging and
     * page indicators, instead of timed rotation. False everywhere else.
     */
    val isSlider: Boolean = false,
)

/**
 * The home hero's four presentation styles folded to the render flags — the
 * ONE place that knows what a [HomeHeroStyle] means on screen. Compose-free
 * and synchronous; pinned by `HomeHeroStylePolicyTest`.
 *
 * Semantics:
 *  - [HomeHeroStyle.CAROUSEL] — the shipped behavior, byte-for-byte: full
 *    bleed, [HomeHeroChromeVisibility.STANDARD] chrome, controller cadence
 *    (null [HomeHeroStyleDecision.autoAdvanceMs]), no slider. The regression
 *    bar: every other default must not disturb this path.
 *  - [HomeHeroStyle.SLIDESHOW] — full-bleed auto-advance at the
 *    `homeHeroSlideshowIntervalSec` pacing ([HomeHeroStyleDecision.autoAdvanceMs]),
 *    metadata overlay hidden until tapped/focused and no ambient clip
 *    ([HomeHeroChromeVisibility.MINIMAL]). The interval only arms when the
 *    candidate pool can actually advance ([candidateCount] ≥ 2 — a tick over a
 *    one-entry pool re-features the same item), and on TV the controller's
 *    focus-stability policy still holds (TV heroes never auto-rotate), so the
 *    interval effectively arms on touch/desktop form factors only.
 *  - [HomeHeroStyle.DETAILED] — the spotlight: a taller, contained card
 *    (not full-bleed; [DetailedHeroHeightExtra] over the adaptive base)
 *    whose synopsis and rating line are always visible
 *    ([HomeHeroChromeVisibility.RICH]). Rotation keeps the shipped cadence.
 *  - [HomeHeroStyle.TV_SLIDER] — on TV with a pageable pool
 *    ([candidateCount] ≥ 2), the horizontal snap slider
 *    ([HomeHeroStyleDecision.isSlider]) over the rotation candidate list;
 *    anything else (touch/desktop, or a pool too small to page) folds back
 *    to the CAROUSEL flags rather than inventing a degenerate slider.
 */
fun homeHeroStyleDecision(
    style: HomeHeroStyle,
    isTv: Boolean,
    slideshowIntervalSec: Int,
    candidateCount: Int,
): HomeHeroStyleDecision = when (style) {
    HomeHeroStyle.CAROUSEL -> HomeHeroStyleDecision()
    HomeHeroStyle.SLIDESHOW -> HomeHeroStyleDecision(
        fullBleed = true,
        chromeVisibility = HomeHeroChromeVisibility.MINIMAL,
        autoAdvanceMs = if (candidateCount >= 2) {
            slideshowIntervalSec.coerceIn(MIN_SLIDESHOW_INTERVAL_SEC, MAX_SLIDESHOW_INTERVAL_SEC) * 1_000L
        } else {
            null
        },
        isSlider = false,
    )
    HomeHeroStyle.DETAILED -> HomeHeroStyleDecision(
        fullBleed = false,
        chromeVisibility = HomeHeroChromeVisibility.RICH,
        autoAdvanceMs = null,
        isSlider = false,
    )
    HomeHeroStyle.TV_SLIDER ->
        if (isTv && candidateCount >= 2) {
            HomeHeroStyleDecision(
                fullBleed = true,
                chromeVisibility = HomeHeroChromeVisibility.STANDARD,
                autoAdvanceMs = null,
                isSlider = true,
            )
        } else {
            HomeHeroStyleDecision()
        }
}

/** The store coerces the slideshow pref to this range on write (3–60 s); the policy re-clamps reads. */
internal const val MIN_SLIDESHOW_INTERVAL_SEC = 3
internal const val MAX_SLIDESHOW_INTERVAL_SEC = 60

/**
 * The DETAILED spotlight's extra height over the adaptive base
 * ([rememberHeroHeight]) — the taller hero the style promises. The screen adds
 * it to `headerHeight` only when the decision's chrome is [HomeHeroChromeVisibility.RICH].
 */
internal val DetailedHeroHeightExtra: Dp = 120.dp
