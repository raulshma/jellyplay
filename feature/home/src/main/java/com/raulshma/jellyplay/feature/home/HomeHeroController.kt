package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.ui.adaptive.AdaptiveHeroHeight
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.components.LocalSurpriseOnLaunch
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.isTv
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Selects the hero "featured" candidate list from the home sections, in a single
 * pass. Previously this traversed all sections up to 3 times (filter+flatMap of
 * LATEST_MEDIA, then a full flatMap+filter fallback, then another bare flatMap).
 *
 * Preference order: up to 3 movies/series total, taken from the LATEST_MEDIA
 * sections in order; if that yields nothing, every movie/series across all
 * sections; if that still yields nothing, every item. The result is identical
 * to the old multi-pass version but computed in one traversal with a single
 * fallback sweep.
 */
internal fun selectFeaturedCandidates(sections: List<HomeSection>): List<MediaItem> =
    buildList {
        for (section in sections) {
            if (section.type != com.raulshma.jellyplay.core.model.HomeSectionType.LATEST_MEDIA) continue
            for (item in section.items) {
                if (item.mediaType == MediaType.MOVIE || item.mediaType == MediaType.SERIES) {
                    add(item)
                    if (size >= 3) return@buildList
                }
            }
        }
    }.ifEmpty {
        buildList {
            for (section in sections) {
                for (item in section.items) {
                    if (item.mediaType == MediaType.MOVIE || item.mediaType == MediaType.SERIES) add(item)
                }
            }
        }
    }.ifEmpty { sections.flatMap { it.items } }

/**
 * Resolves the hero height for the current form factor.
 */
@Composable
internal fun rememberHeroHeight(): Dp {
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    return remember(isTv, adaptiveInfo.isLandscape, adaptiveInfo.windowSizeClass) {
        when {
            isTv -> AdaptiveHeroHeight.Tv
            adaptiveInfo.isLandscape && adaptiveInfo.windowSizeClass != WindowSizeClass.Compact ->
                AdaptiveHeroHeight.LandscapeMedium
            else -> AdaptiveHeroHeight.PortraitCompact
        }
    }
}

/**
 * Owns the hero rotation state and its effects: the featured-item index, the
 * "Surprise Me" pick, auto-rotation while idle, and snapping the list back to
 * the top when the hero receives focus (TV). The [HeroController] class holds
 * all state and transitions; this composable only constructs it once and
 * dispatches the effects (candidate updates, the launch-shortcut arm, the idle
 * rotation cadences, and the TV snap-to-top). All cadences (8s idle / 2s while
 * scrolling) and the `isAtLeast(RESUMED)` gate are preserved from the inline
 * implementation previously in `MainHomeContent`.
 */
@Composable
internal fun rememberHeroController(
    featuredCandidates: List<MediaItem>,
    listState: LazyListState,
    heroFocusRequester: androidx.compose.ui.focus.FocusRequester,
    getBackdropUrl: (String) -> String,
): HeroController {
    val context = LocalContext.current
    val isTvForRotation = remember(context) { context.isTv() }
    // Keyed on the lambda so a replaced provider (new VM) can't leave the
    // controller resolving backdrops through a stale capture. The call site
    // remembers the lambda per view model, so this re-runs only on a real VM
    // change — never per recomposition.
    val controller = remember(getBackdropUrl) {
        HeroController(getBackdropUrl, initialAutoRotateEnabled = !isTvForRotation)
    }

    // Sync candidates during composition, not in a LaunchedEffect (which runs
    // post-composition and would render one frame with stale candidates), so
    // HomeScreen reads a fresh featuredItem in the same frame the list changes.
    // INVARIANT: this deliberate snapshot-state write during composition stays
    // safe only while `featuredCandidates` never derives from the controller's
    // own output (candidates/featuredItem) — a derived input would feed the
    // write back into the next composition (the recomposition-loop pattern
    // Compose warns about). Keyed on the controller too, so a rebuilt
    // controller can never sit with empty candidates until the list happens
    // to change.
    remember(getBackdropUrl, featuredCandidates) {
        controller.updateCandidates(featuredCandidates)
        true
    }

    // Honor the "Surprise Me" launcher shortcut when the
    // app-level signal arms, flip the same state the menu item uses, then clear
    // the signal so re-mounting Home doesn't re-fire.
    val surpriseController = LocalSurpriseOnLaunch.current
    val surpriseArmed by surpriseController.armed.collectAsStateWithLifecycle()
    LaunchedEffect(surpriseArmed) {
        if (surpriseArmed) {
            controller.onSurpriseArmed()
            surpriseController.consume()
        }
    }

    val isTv = LocalTvMode.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(featuredCandidates, listState, controller.autoRotateEnabled) {
        if (featuredCandidates.isEmpty() || !controller.autoRotateEnabled || !controller.focusInHero) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { isScrolling ->
                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@collectLatest
                if (!isScrolling) {
                    delay(8000)
                    if (controller.autoRotateEnabled && controller.focusInHero) {
                        controller.rotationTick()
                    }
                } else {
                    delay(2000)
                }
            }
    }

    // When the hero actually receives focus, snap the list back to the top so
    // the full hero is visible. The first emission is skipped so a freshly
    // (re)composed Home doesn't snap before per-row focus restoration runs.
    LaunchedEffect(controller.focusInHero) {
        if (!controller.focusSnapSettled) {
            controller.focusSnapSettled = true
        } else if (controller.focusInHero && isTv) {
            listState.scrollToItem(0, 0)
        }
    }

    return controller
}

@Stable
internal class HeroController(
    private val getBackdropUrl: (String) -> String,
    initialAutoRotateEnabled: Boolean,
) {
    /** The index is deliberately not clamped on updates: a shrunk pool yields a null [featuredItem] until it is valid again. */
    var candidates: List<MediaItem> by mutableStateOf(emptyList())
        private set
    private var featuredIndex by mutableIntStateOf(0)
    var showSurprise: Boolean by mutableStateOf(false)
        private set
    var autoRotateEnabled: Boolean by mutableStateOf(initialAutoRotateEnabled)
        private set
    var focusInHero: Boolean by mutableStateOf(true)
        private set

    /** First-emission skip flag for the composable's snap-to-top effect. */
    internal var focusSnapSettled: Boolean by mutableStateOf(false)

    val featuredItem: MediaItem? get() = candidates.getOrNull(featuredIndex)
    val backdropUrl: String? get() = featuredItem?.let { getBackdropUrl(it.id) }

    fun updateCandidates(items: List<MediaItem>) {
        candidates = items
    }

    /**
     * Surprise-Me toggle: flips state and re-enables rotation when turning off.
     * `was` is the pre-flip value, so after flipping to `!was`, re-enabling
     * rotation when `was` is true means re-enabling when we just turned it off;
     * turning it on instead runs the surprise pick.
     */
    fun toggleSurprise() {
        val was = showSurprise
        showSurprise = !was
        if (was) autoRotateEnabled = true else onSurpriseShown()
    }

    /** Launch-shortcut path: arms surprise mode and runs the same pick as [toggleSurprise] turning on. */
    fun onSurpriseArmed() {
        showSurprise = true
        onSurpriseShown()
    }

    private fun onSurpriseShown() {
        if (candidates.isNotEmpty()) {
            // Exclude the currently-displayed hero from the candidate pool so a
            // "Surprise Me" tap always produces a visible change. With a small
            // pool (e.g. N=3 from LATEST_MEDIA) the naive pick had a 1/N chance
            // of re-selecting the same item, making the feature appear to no-op.
            val previousIndex = featuredIndex
            featuredIndex = if (candidates.size > 1) {
                (0 until candidates.size).filter { it != previousIndex }.random()
            } else {
                0
            }
            autoRotateEnabled = false
        }
    }

    fun rotationTick() {
        if (candidates.isNotEmpty()) featuredIndex = (featuredIndex + 1) % candidates.size
    }

    /** Notifies the controller that hero focus changed (used to drive the snap-to-top). */
    fun onFocusChange(focused: Boolean) {
        focusInHero = focused
    }
}
