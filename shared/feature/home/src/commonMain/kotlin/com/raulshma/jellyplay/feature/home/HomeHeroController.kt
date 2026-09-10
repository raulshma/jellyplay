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
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.ui.adaptive.AdaptiveHeroHeight
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.components.LocalSurpriseOnLaunch
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
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
 * Picks the hero candidate pool for the current render source. While offline
 * (or implicit-offline: server fetch failed with downloads present) the hero
 * features downloaded media, so candidates come from the offline-derived
 * sections. The music home is excluded to match the online music home, which
 * never renders a hero — and stale online sections painted by the user-switch
 * SWR path must not leak into it. Online rendering uses the server sections.
 */
internal fun selectHomeHeroCandidates(
    renderingOffline: Boolean,
    homeMode: HomeMode,
    onlineSections: List<HomeSection>,
    offlineSections: List<HomeSection>,
): List<MediaItem> = when {
    renderingOffline && homeMode != HomeMode.MUSIC -> selectFeaturedCandidates(offlineSections)
    renderingOffline -> emptyList()
    else -> selectFeaturedCandidates(onlineSections)
}

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
 * the top when the hero receives focus (TV). All policy lives on
 * [HeroController] — [HeroController.rotationCadence] (cadence + gates),
 * [HeroController.shouldTickNow] (post-delay re-check) and
 * [HeroController.onFocusEffect] (first-emission snap skip) are Compose-free
 * and synchronously testable — so this composable only constructs the
 * controller once and runs the dumb collector shells: the candidate
 * composition-write, the launch-shortcut arm, the snapshotFlow/collectLatest
 * cadence collector (8s idle / 2s while scrolling, the `isAtLeast(RESUMED)`
 * gate passed as an argument, keys preserved from the inline implementation
 * previously in `MainHomeContent`), and the focus-keyed snap effect.
 */
@Composable
internal fun rememberHeroController(
    featuredCandidates: List<MediaItem>,
    listState: LazyListState,
    getBackdropUrl: (String) -> String,
): HeroController {
    // Conveyor transform: the legacy Context.isTv() (UI_MODE check on
    // LocalContext) is Android-only; LocalTvMode is the shared core:ui
    // equivalent and is provided from the same device-class detection at the
    // composition root, so the value is identical wherever this runs.
    val isTvForRotation = LocalTvMode.current
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
    // Cadence shell: every decision (gates, delays, post-delay re-check) is the
    // controller's [HeroController.rotationCadence]/[shouldTickNow]; this is
    // just the collector — collectLatest cancels a pending delay on any
    // scroll-state change. Effect keys preserved from the inline implementation.
    LaunchedEffect(featuredCandidates, listState, controller.autoRotateEnabled) {
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { isScrolling ->
                when (
                    val cadence = controller.rotationCadence(
                        isScrolling = isScrolling,
                        lifecycleResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                    )
                ) {
                    RotationCadence.None -> Unit
                    is RotationCadence.RecheckAfter -> delay(cadence.delayMs)
                    is RotationCadence.TickAfter -> {
                        delay(cadence.delayMs)
                        if (controller.shouldTickNow()) {
                            controller.rotationTick()
                        }
                    }
                }
            }
    }

    // TV snap-to-top shell: the first-emission skip and the focused+TV decision
    // live in [HeroController.onFocusEffect]; key and firing occasions preserved.
    LaunchedEffect(controller.focusInHero) {
        if (controller.onFocusEffect(controller.focusInHero, isTv)) {
            listState.scrollToItem(0, 0)
        }
    }

    return controller
}

/**
 * What the cadence collector should do after [HeroController.rotationCadence]
 * decides. Carrying the decision kind (not a bare delay) keeps the collector
 * from re-deriving the branch by comparing delay constants.
 */
internal sealed interface RotationCadence {
    /** No delay may be scheduled; the collector does nothing this emission. */
    data object None : RotationCadence

    /** A pure re-check wait: `collectLatest` restarts it on the next scroll-state change. */
    data class RecheckAfter(val delayMs: Long) : RotationCadence

    /** Wait, then tick the featured index if [HeroController.shouldTickNow] still passes. */
    data class TickAfter(val delayMs: Long) : RotationCadence
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

    /** First-emission skip flag, consumed once by [onFocusEffect]. */
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

    /**
     * The rotation-cadence decision behind the composable's snapshotFlow
     * collector — the whole policy, Compose-free and synchronously testable.
     * Returns [RotationCadence.None] when no delay may be scheduled (no
     * candidates, rotation disabled, focus outside the hero, or lifecycle
     * below RESUMED — the same gates the inline implementation applied, here
     * re-evaluated on every scroll emission); [RotationCadence.RecheckAfter]
     * while scrolling (a pure re-check wait: `collectLatest` cancels it on the
     * next scroll-state change); or [RotationCadence.TickAfter] when idle —
     * the tick decision, whose completion the caller gates on
     * [shouldTickNow], mirroring the inline implementation's post-delay
     * re-check. The decision carries its kind, so the collector dispatches on
     * the sealed type instead of comparing raw delay constants.
     */
    internal fun rotationCadence(isScrolling: Boolean, lifecycleResumed: Boolean): RotationCadence {
        if (candidates.isEmpty() || !autoRotateEnabled || !focusInHero || !lifecycleResumed) return RotationCadence.None
        return if (isScrolling) {
            RotationCadence.RecheckAfter(SCROLL_DEFER_DELAY_MS)
        } else {
            RotationCadence.TickAfter(IDLE_ROTATION_DELAY_MS)
        }
    }

    /**
     * Post-delay re-check for the idle cadence branch, faithful to the inline
     * implementation's guard right before the tick: state flipped while the
     * delay ran (e.g. "Surprise Me" disabled rotation, or focus left the hero)
     * suppresses the tick.
     */
    internal fun shouldTickNow(): Boolean = autoRotateEnabled && focusInHero

    /**
     * The TV snap-to-top decision behind the composable's focus effect,
     * absorbing the first-emission skip: the first invocation ever (per
     * controller instance, regardless of arguments) only advances
     * [focusSnapSettled] and returns `false` — a freshly (re)composed Home
     * must not snap before per-row focus restoration runs. Every later
     * invocation returns whether to scroll the list back to the top now: only
     * when the hero actually has focus and we are on TV. Compose-free; the
     * composable stays a dumb collector.
     */
    internal fun onFocusEffect(focused: Boolean, isTv: Boolean): Boolean {
        if (!focusSnapSettled) {
            focusSnapSettled = true
            return false
        }
        return focused && isTv
    }

    internal companion object {
        /** Idle auto-rotation cadence: idle time before a featured-index tick. */
        internal const val IDLE_ROTATION_DELAY_MS = 8_000L

        /** Scroll-defer cadence: re-check wait while the list is scrolling. */
        internal const val SCROLL_DEFER_DELAY_MS = 2_000L
    }

    /** Notifies the controller that hero focus changed (used to drive the snap-to-top). */
    fun onFocusChange(focused: Boolean) {
        focusInHero = focused
    }
}
