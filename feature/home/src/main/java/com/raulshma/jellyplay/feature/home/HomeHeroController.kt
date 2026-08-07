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
 * Preference order: up to 3 movies/series from each LATEST_MEDIA section; if
 * that yields nothing, every movie/series across all sections; if that still
 * yields nothing, every item. The result is identical to the old multi-pass
 * version but computed in one traversal with a single fallback sweep.
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
 * the top when the hero receives focus (TV). All cadences (8s idle / 2s while
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
    var showSurprise by remember { mutableStateOf(false) }
    var featuredIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val isTvForRotation = remember(context) { context.isTv() }
    var autoRotateEnabled by remember { mutableStateOf(!isTvForRotation) }
    var focusInHero by remember { mutableStateOf(true) }

    // Honor the "Surprise Me" launcher shortcut when the
    // app-level signal arms, flip the same state the menu item uses, then clear
    // the signal so re-mounting Home doesn't re-fire.
    val surpriseController = LocalSurpriseOnLaunch.current
    val surpriseArmed by surpriseController.armed.collectAsStateWithLifecycle()
    LaunchedEffect(surpriseArmed) {
        if (surpriseArmed) {
            showSurprise = true
            surpriseController.consume()
        }
    }

    LaunchedEffect(showSurprise) {
        if (showSurprise && featuredCandidates.isNotEmpty()) {
            // Exclude the currently-displayed hero from the candidate pool so a
            // "Surprise Me" tap always produces a visible change. With a small
            // pool (e.g. N=3 from LATEST_MEDIA) the naive pick had a 1/N chance
            // of re-selecting the same item, making the feature appear to no-op.
            val previousIndex = featuredIndex
            featuredIndex = if (featuredCandidates.size > 1) {
                (0 until featuredCandidates.size).filter { it != previousIndex }.random()
            } else {
                0
            }
            autoRotateEnabled = false
        }
    }

    val featuredItem = remember(featuredCandidates, featuredIndex) {
        featuredCandidates.getOrNull(featuredIndex)
    }
    val backdropUrl = remember(featuredItem?.id) { featuredItem?.let { getBackdropUrl(it.id) } }

    val isTv = LocalTvMode.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(featuredCandidates, listState, autoRotateEnabled) {
        if (featuredCandidates.isEmpty() || !autoRotateEnabled || !focusInHero) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { isScrolling ->
                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@collectLatest
                if (!isScrolling) {
                    delay(8000)
                    if (autoRotateEnabled && focusInHero) {
                        featuredIndex = (featuredIndex + 1) % featuredCandidates.size
                    }
                } else {
                    delay(2000)
                }
            }
    }

    // When the hero actually receives focus, snap the list back to the top so
    // the full hero is visible. The first emission is skipped so a freshly
    // (re)composed Home doesn't snap before per-row focus restoration runs.
    var heroFocusScrollSettled by remember { mutableStateOf(false) }
    LaunchedEffect(focusInHero) {
        if (!heroFocusScrollSettled) {
            heroFocusScrollSettled = true
        } else if (focusInHero && isTv) {
            listState.scrollToItem(0, 0)
        }
    }

    return HeroController(
        featuredItem = featuredItem,
        backdropUrl = backdropUrl,
        showSurprise = showSurprise,
        setShowSurprise = { showSurprise = it },
        autoRotateEnabled = autoRotateEnabled,
        setAutoRotateEnabled = { autoRotateEnabled = it },
        focusInHero = focusInHero,
        setFocusInHero = { focusInHero = it },
    )
}

@Stable
internal class HeroController(
    val featuredItem: MediaItem?,
    val backdropUrl: String?,
    private val showSurprise: Boolean,
    private val setShowSurprise: (Boolean) -> Unit,
    val autoRotateEnabled: Boolean,
    private val setAutoRotateEnabled: (Boolean) -> Unit,
    val focusInHero: Boolean,
    val setFocusInHero: (Boolean) -> Unit,
) {
    /**
     * Surprise-Me toggle: flips state and re-enables rotation when turning off.
     * `showSurprise` here is the pre-flip value, so after flipping to
     * `!showSurprise`, re-enabling rotation when that new value is false means
     * re-enabling when the old value was true (i.e. we just turned it off).
     */
    fun toggleSurprise() {
        setShowSurprise(!showSurprise)
        if (showSurprise) setAutoRotateEnabled(true)
    }
    /** Notifies the controller that hero focus changed (used to drive the snap-to-top). */
    fun onFocusChange(focused: Boolean) = setFocusInHero(focused)
}
