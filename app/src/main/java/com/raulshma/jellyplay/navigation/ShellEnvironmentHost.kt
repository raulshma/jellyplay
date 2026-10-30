package com.raulshma.jellyplay.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.MainPreferences
import com.raulshma.jellyplay.core.model.isExperimentalEnabled
import com.raulshma.jellyplay.core.ui.adaptive.AdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.JellyPlayUiEnvironment
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.adaptive.applyOverride
import com.raulshma.jellyplay.core.ui.adaptive.rememberAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.rememberJellyPlayUiEnvironment
import com.raulshma.jellyplay.core.ui.components.LocalFloatingNavVisibility
import com.raulshma.jellyplay.core.ui.components.LocalPerformanceMode
import com.raulshma.jellyplay.core.ui.preview.LocalMediaPeekEnabled
import com.raulshma.jellyplay.core.ui.preview.LocalMediaPreviewController
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.LocalTvTypography
import com.raulshma.jellyplay.core.ui.tv.isTv
import com.raulshma.jellyplay.core.designsystem.theme.TvTypography

/**
 * The shell ENVIRONMENT half of `MainContent`, extracted into its own holder
 * (the `BottomNavScrollState` / `PeekPreviewState` idiom — a plain holder
 * returned per composition over remembered internals, constructed by one
 * [rememberShellEnvironmentHost] call): the TV detection, the nav-bar color
 * animation the phone layout feeds back through
 * [com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor], the
 * adaptive-info + UI-environment pair (the manual layout override clamped
 * BEFORE anything consumes it), the TV typography, and the floating nav-bar
 * scroll state — plus the ONE [Provide] wrapper that publishes the shell's
 * ~12 app-wide CompositionLocals above the layout branches.
 *
 * NOT here: the shell-HOST arms (Play On controller, admin gate, shortcut
 * surface, hooks graph — the ShellHostArms holder), the peek preview (its own
 * PeekPreviewState holder — passed INTO [Provide] for its two locals), and
 * the layout-branch dispatch itself (MainContent keeps the ordering role).
 *
 * Like its two holder precedents, the holder is returned FRESH per
 * recomposition: [animatedNavBarColor] must re-read the animation's current
 * value every recomposition, exactly as the former inline block did. Only the
 * internals (the color state, the environment, the scroll state) are
 * remembered.
 */
internal class ShellEnvironmentHost(
    private val preferences: MainPreferences,
    /** Whether the device is a TV — drives the typography and [LocalTvMode]. */
    val isTv: Boolean,
    /** The shell's adaptive info — the layout override already applied. */
    val adaptiveInfo: AdaptiveInfo,
    /** The shared UI environment every downstream reader takes via [LocalJellyPlayUi]. */
    val uiEnvironment: JellyPlayUiEnvironment,
    /** TV typography; null off-TV (the local stays unset). */
    val tvTypography: androidx.compose.material3.Typography?,
    /**
     * The nav-bar color sink — screens write the color their content wants
     * behind the nav bar; the animation below resolves it against the
     * surfaceContainer fallback. Provided via
     * [com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor] in
     * MainContent's inner provider.
     */
    val navBarColorState: MutableState<Color?>,
    /** The nav-bar color animation's current value (fresh per recomposition). */
    val animatedNavBarColor: Color,
    /** The floating nav-bar hide-on-scroll state (BottomNavScrollState holder). */
    val bottomNav: BottomNavScrollState,
) {

    /** The peek feature's opt-in state — the same pure fold every peek site reads. */
    val peekEnabled: Boolean
        get() = preferences.isExperimentalEnabled(ExperimentalFeature.MEDIA_CARD_PEEK)

    /**
     * Publishes the shell's app-wide CompositionLocals above [content] (the
     * TV / phone / full-screen dispatch). The two peek locals take
     * [peekPreview] as a parameter because the preview holder is constructed
     * AFTER this environment (it needs [isTv]); the provider call site sits
     * below it, so the hand-off is a plain parameter.
     *
     * Stable values on purpose — `provides` compares by ==, so a fresh lambda
     * per recomposition would force downstream invalidation even when the
     * captured state hasn't changed. MainContent recomposes often (audio
     * metadata, nav color, mini-player), so every member here is a
     * remembered/stable value, never a plain composition local read inside a
     * lambda — those are captured by value from the first composition and go
     * stale (the showMiniPlayer derivedStateOf bug this rule documents).
     */
    @Composable
    fun Provide(peekPreview: PeekPreviewState, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalTvMode provides isTv,
            LocalAdaptiveInfo provides adaptiveInfo,
            LocalJellyPlayUi provides uiEnvironment,
            LocalTvTypography provides tvTypography,
            LocalPerformanceMode provides preferences.performanceMode,
            com.raulshma.jellyplay.core.ui.feedback.LocalHapticsEnabled provides preferences.hapticsEnabled,
            LocalFloatingNavVisibility provides bottomNav.scrollVisibility.visibleState,
            LocalMediaPreviewController provides peekPreview.controller,
            LocalMediaPeekEnabled provides peekEnabled,
            com.raulshma.jellyplay.core.ui.components.LocalCardDisplayPreferences provides com.raulshma.jellyplay.core.ui.components.CardDisplayPreferences(
                showUnwatchedBadge = preferences.showUnwatchedBadge,
                showWatchedCheckmark = preferences.showWatchedCheckmark,
                hideWatchedItems = preferences.hideWatchedItems,
                showQualityBadges = preferences.showQualityBadges,
                showNewBadges = preferences.showNewBadges,
                antiSpoilerHome = preferences.antiSpoilerHome,
            ),
        ) {
            content()
        }
    }
}

/**
 * Constructs the [ShellEnvironmentHost] — the former `MainContent` region
 * from the nav-bar color animation through the floating-nav scroll state,
 * verbatim. The holder itself is fresh per recomposition; only these
 * internals are remembered.
 */
@Composable
internal fun rememberShellEnvironmentHost(preferences: MainPreferences): ShellEnvironmentHost {
    val navBarColorState = remember { mutableStateOf<Color?>(null) }
    val animatedNavBarColor by animateColorAsState(
        targetValue = navBarColorState.value ?: MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "navBarColor",
    )

    val isTv = LocalContext.current.isTv()

    // The manual layout override clamps the measured width class
    // BEFORE anything consumes it: the shell's isExpanded fork in MainContent,
    // the TV/phone DeviceClass tokens, and every downstream LocalAdaptiveInfo
    // reader (rail vs bottom bar, two-pane details, grids) all follow the
    // override from this single site.
    val adaptiveInfo = preferences.layoutMode.applyOverride(rememberAdaptiveInfo())
    val uiEnvironment = rememberJellyPlayUiEnvironment(
        adaptiveInfo = adaptiveInfo,
        isTv = isTv,
    )

    val tvTypography = if (isTv) TvTypography else null

    // The floating nav-bar's hide-on-scroll state (ScrollDirectionVisibility
    // policy + the animated px offset + its stable getter) — constructed by
    // its own holder (BottomNavScrollState.kt); the phone layout's
    // NestedScrollConnection and LocalFloatingNavVisibility/Offset consume it
    // in MainContent's inner provider / phone branch.
    val bottomNav = rememberBottomNavScrollState()

    return ShellEnvironmentHost(
        preferences = preferences,
        isTv = isTv,
        adaptiveInfo = adaptiveInfo,
        uiEnvironment = uiEnvironment,
        tvTypography = tvTypography,
        navBarColorState = navBarColorState,
        animatedNavBarColor = animatedNavBarColor,
        bottomNav = bottomNav,
    )
}
