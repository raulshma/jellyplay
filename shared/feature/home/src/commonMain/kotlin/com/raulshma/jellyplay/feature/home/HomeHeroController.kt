package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.model.CuratedHeroEntry
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.ui.adaptive.AdaptiveHeroHeight
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.components.LocalPerformanceMode
import com.raulshma.jellyplay.core.ui.components.LocalSurpriseOnLaunch
import com.raulshma.jellyplay.core.ui.image.mediaImageDecodeSize
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject

/**
 * The hero backdrop's decode size — MUST mirror the hero `MediaImage`'s
 * `size = CoilSize(1920, 1080)` in HomeHero.kt (performanceModeAware true, so
 * [mediaImageDecodeSize] tiers it to 768² under performance mode exactly as
 * the display request is clamped). The rotation prefetch loads at this size so
 * the display request hits the prefetched memory-cache entry instead of
 * re-fetching and re-decoding at a different key.
 */
private val HeroBackdropDecodeSize = CoilSize(1920, 1080)

/**
 * Holds the ONE in-flight hero-backdrop prefetch (the downloads sidecar's
 * `imageLoader.enqueue` targetless-preload posture). A new [prefetch] call
 * disposes the previous request first — that IS the fast-manual-navigation
 * cancellation: a surprise pick or quick rotation tick that replaces the
 * current hero before the last prefetch finished drops the now-stale transfer
 * instead of letting it burn bandwidth toward a cache entry nobody will read.
 * [cancel] runs at composition leave (the hero left Home) so no prefetch
 * outlives the screen that wanted it.
 */
internal class HeroBackdropPrefetcher {
    private var disposable: coil3.request.Disposable? = null

    fun prefetch(context: PlatformContext, url: String, decodeSize: CoilSize) {
        cancel()
        if (url.isBlank()) return
        disposable = SingletonImageLoader.get(context).enqueue(
            ImageRequest.Builder(context)
                .data(url)
                // Same URL + same decode size as the hero's display request →
                // same memory/disk cache keys (Coil's auto-derived memory-cache
                // key embeds the size string). No crossfade/transition: this is
                // a targetless preload, only the caches matter.
                .size(decodeSize)
                .build()
        )
    }

    fun cancel() {
        disposable?.dispose()
        disposable = null
    }
}

/**
 * Selects the hero "featured" candidate list from the home sections, in a single
 * pass (a multi-pass filter/flatMap walk would traverse the sections up to 3
 * times).
 *
 * Preference order: up to 3 movies/series total, taken from the LATEST_MEDIA
 * sections in order; if that yields nothing, every movie/series across all
 * sections; if that still yields nothing, every item. Computed in one traversal
 * with a single fallback sweep.
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
 *
 * The plugin-curated billboards ride AHEAD of the local pool in the
 * online branch ([mergeCuratedHeroCandidates] — server order, dedupe by item
 * id, curated wins). Offline rendering never carries them: curated is
 * server data and the offline hero features downloads only.
 */
internal fun selectHomeHeroCandidates(
    renderingOffline: Boolean,
    homeMode: HomeMode,
    onlineSections: List<HomeSection>,
    offlineSections: List<HomeSection>,
    curatedHero: List<CuratedHeroEntry> = emptyList(),
): List<MediaItem> = when {
    renderingOffline && homeMode != HomeMode.MUSIC -> selectFeaturedCandidates(offlineSections)
    renderingOffline -> emptyList()
    else -> mergeCuratedHeroCandidates(curatedHero, selectFeaturedCandidates(onlineSections))
}

/**
 * The curated/local merge — the curated entries' items first (exactly the
 * plugin's serve order; a duplicated curated id keeps its FIRST slot), then
 * the local candidates minus every id the curated list already features
 * (curated wins, no double rotation slot). Pure and synchronously testable.
 */
internal fun mergeCuratedHeroCandidates(
    curated: List<CuratedHeroEntry>,
    local: List<MediaItem>,
): List<MediaItem> {
    if (curated.isEmpty()) return local
    val curatedIds = HashSet<String>()
    val curatedItems = curated.mapNotNull { entry -> entry.item.takeIf { curatedIds.add(it.id) } }
    return curatedItems + local.filter { it.id !in curatedIds }
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
 * The hero video's user gates read AT the hero — the pref mirror
 * (`home_hero_video_enabled` touch/default, `home_hero_video_tv_enabled` TV;
 * the additive koinInject mirror, the HomeTrickplayPeek repository precedent,
 * because the hero composable sits below the screen orchestrator that carries
 * the HomeUiState pref mirrors) and performance mode (the app-provided
 * [com.raulshma.jellyplay.core.ui.components.LocalPerformanceMode]).
 */
internal class HeroVideoGates(
    /** The per-form-factor pref: the feature master gate. Default off. */
    val prefEnabled: Boolean,
    /** Performance mode hard-gates the clip alongside the reduced-motion gate. */
    val performanceMode: Boolean,
)

@Composable
internal fun rememberHeroVideoGates(): HeroVideoGates {
    val isTv = LocalTvMode.current
    val homeDiscovery = koinInject<HomeDiscoveryStore>()
    val discoverySlice by homeDiscovery.homeDiscovery.collectAsStateWithLifecycle()
    val performanceMode = LocalPerformanceMode.current
    // Only the two hero-video flags are consumed — map them out and key
    // on them, not the whole slice, so an unrelated home-pref change (row
    // order, banners, stats) doesn't invalidate the gates and their readers.
    val videoEnabled = discoverySlice.homeHeroVideoEnabled
    val videoTvEnabled = discoverySlice.homeHeroVideoTvEnabled
    return remember(isTv, videoEnabled, videoTvEnabled, performanceMode) {
        HeroVideoGates(
            prefEnabled = if (isTv) videoTvEnabled else videoEnabled,
            performanceMode = performanceMode,
        )
    }
}

/**
 * The start gate, in its pure form (the [HeroController.rotationCadence]
 * family of Compose-free decisions): the ambient clip plays only when the
 * pref is on AND the platform can render it (desktop: static hero), the
 * user has not paused it for the session, neither reduced-motion nor
 * performance mode holds, the hero is visible AND prominent AND still holds
 * focus (TV), the lifecycle is at least RESUMED, and the list is not
 * scrolling. Any leg failing means the static image stays — the clip's
 * absence must never be observable work.
 */
internal fun heroVideoShouldPlay(
    prefEnabled: Boolean,
    sessionPaused: Boolean,
    reducedMotion: Boolean,
    performanceMode: Boolean,
    isVisible: Boolean,
    isProminent: Boolean,
    heroFocusHeld: Boolean,
    lifecycleResumed: Boolean,
    isScrolling: Boolean,
    platformCapable: Boolean,
): Boolean =
    prefEnabled &&
        platformCapable &&
        !sessionPaused &&
        !reducedMotion &&
        !performanceMode &&
        isVisible &&
        isProminent &&
        heroFocusHeld &&
        lifecycleResumed &&
        !isScrolling

/**
 * Reactive RESUMED read for the hero's clip gate (the rotation cadence reads
 * the same lifecycle state, but non-reactively inside its snapshotFlow
 * collector; the clip gate needs it as composition state so an app
 * backgrounding unmounts the surface).
 */
@Composable
internal fun rememberIsLifecycleResumed(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        try {
            awaitCancellation()
        } finally {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return resumed
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
 * gate passed as an argument), and the focus-keyed snap effect.
 */
@Composable
internal fun rememberHeroController(
    featuredCandidates: List<MediaItem>,
    listState: LazyListState,
    getBackdropUrl: (String) -> String,
): HeroController {
    // Conveyor transform: Context.isTv() (UI_MODE check on
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
    // to change. The epoch rides alongside: candidates identity churns
    // per refresh emission, so the cadence effect keys on this epoch instead
    // of the list — it advances only when a write crosses the pool's
    // availability boundary (empty ↔ non-empty), the one transition the
    // collector must re-run on; content churn leaves a pending idle wait
    // alone. The epoch bump itself is a SideEffect, not an in-composition
    // write: the pending flag is a plain local (a composition-phase snapshot
    // write costs a guaranteed extra recomposition), and the one-frame lag
    // before the cadence effect sees the new key is irrelevant at an 8 s/2 s
    // cadence.
    var candidatesEpoch by remember { mutableIntStateOf(0) }
    var epochBumpPending = false
    remember(getBackdropUrl, featuredCandidates) {
        val wasEmpty = controller.candidates.isEmpty()
        controller.updateCandidates(featuredCandidates)
        epochBumpPending = wasEmpty != featuredCandidates.isEmpty()
        true
    }
    SideEffect {
        if (epochBumpPending) candidatesEpoch++
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
    // scroll-state change. The effect keys are [listState] plus the
    // candidates epoch, and autoRotateEnabled is read inside the
    // snapshotFlow instead — a pref or "Surprise Me" flip still re-evaluates
    // the cadence immediately, while per-emission candidates churn does not
    // cancel a pending 8 s idle wait.
    LaunchedEffect(listState, candidatesEpoch) {
        snapshotFlow { listState.isScrollInProgress to controller.autoRotateEnabled }
            .collectLatest { (isScrolling, _) ->
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

    // Hero backdrop prefetch: the moment an item becomes current, preload the
    // NEXT rotation successor's backdrop (same URL builder the hero display
    // uses, [HeroBackdropDecodeSize]'s size) so a rotation swaps straight to a
    // decoded bitmap instead of holding the blurHash placeholder through a
    // full network fetch + decode of a ~1920px JPEG. autoRotateEnabled rides
    // in the flow so a rotation-off state (TV default, Surprise Me) prefetches
    // nothing; flipping it re-evaluates immediately. Every featured change
    // REPLACES the in-flight prefetch ([HeroBackdropPrefetcher.prefetch]
    // disposes the previous request first) and leaving the composition cancels
    // it — a fast manual navigation never leaves stale transfers running.
    val prefetchContext = coil3.compose.LocalPlatformContext.current
    val prefetchPerformanceMode = LocalPerformanceMode.current
    val backdropPrefetcher = remember { HeroBackdropPrefetcher() }
    DisposableEffect(backdropPrefetcher) {
        onDispose { backdropPrefetcher.cancel() }
    }
    LaunchedEffect(controller, backdropPrefetcher, prefetchContext, prefetchPerformanceMode) {
        snapshotFlow {
            Triple(controller.featuredItem, controller.nextCandidate, controller.autoRotateEnabled)
        }.collect { (current, next, autoRotate) ->
            if (!autoRotate) return@collect
            val candidate = next ?: return@collect
            if (candidate.id == current?.id) return@collect
            val url = getBackdropUrl(candidate.id)
            if (url.isBlank()) return@collect
            backdropPrefetcher.prefetch(
                context = prefetchContext,
                url = url,
                // The clamp must produce the display request's exact decode
                // size (the hero keeps performanceModeAware true), or the
                // display path would miss the prefetched memory-cache entry.
                decodeSize = mediaImageDecodeSize(HeroBackdropDecodeSize, prefetchPerformanceMode),
            )
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

    /**
     * The rotation successor of [featuredItem] (featuredIndex + 1, wrapping) —
     * the candidate the next auto-rotation tick features, known ahead of time
     * so its backdrop can be prefetched while the current item displays. Null
     * on an empty pool; equals the current item on a single-entry pool.
     */
    val nextCandidate: MediaItem?
        get() {
            val pool = candidates
            if (pool.isEmpty()) return null
            return pool[(featuredIndex + 1) % pool.size]
        }

    /**
     * Memoized per (candidates, featuredIndex) read: [featuredItem]'s
     * backdrop string is resolved once per actual featured-item change instead
     * of on every read. [derivedStateOf] tracks both state reads, so the
     * value is identical to the old per-read resolution.
     */
    val backdropUrl: String? by derivedStateOf { featuredItem?.let { getBackdropUrl(it.id) } }

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

    /** Notifies the controller that hero focus changed (drives the snap-to-top). */
    fun onFocusChange(focused: Boolean) {
        focusInHero = focused
    }
}
