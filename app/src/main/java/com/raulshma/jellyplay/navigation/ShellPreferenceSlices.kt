package com.raulshma.jellyplay.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.LayoutMode
import com.raulshma.jellyplay.core.model.MainPreferences
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.TvOverscan
import com.raulshma.jellyplay.core.model.isExperimentalEnabled
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Topic-derived preference slices for the app shell (report 8.1 — the
 * monolithic preference state collected at the application root).
 *
 * The merged [MainPreferences] pipeline (`MainViewModel.preferences`, ~60
 * fields) used to be collected WHOLE at the app root: every preference write
 * emitted a new state object and invalidated [JellyPlayApp] → [MainContent]
 * → the [rememberShellEnvironmentHost] remember, even for fields nothing in
 * the shell path reads. These slices invert that: each shell site collects
 * exactly the fields it consumes, mapped per emission and de-duplicated, so
 * an unrelated write (theme, subtitles, security, ...) produces an EQUAL
 * slice value and stops recomposing shell scopes it never affected.
 *
 * Deliberately NOT ViewModel members: MainViewModel's public surface is
 * pinned by its ownership ratchet (new shell state belongs in a coordinator
 * or the existing seams), and the slices are presentation-side folds anyway —
 * they derive from the same single source-of-truth flow via [map] +
 * [distinctUntilChanged], so the values and emission timing are identical to
 * reading the fields off `MainPreferences` directly. Each collector's
 * initial value maps the source StateFlow's CURRENT value, so the first
 * composition sees the same values the former whole-flow collection did.
 */

/**
 * The navigation-chrome preferences [MainContent] consumes: the home mode +
 * start tab + preferred player that seed the navigation state, the nav
 * customization pair behind `visibleTopLevelRoutes`, the TV overscan and the
 * phone bottom-nav styling flags. Nothing else from `MainPreferences` — a
 * write outside these fields no longer reaches MainContent.
 */
@Immutable
internal data class ShellNavPrefs(
    val homeMode: HomeMode,
    val startTab: String,
    val preferredPlayer: PlayerType,
    val hiddenNavItems: Set<String>,
    val navItemOrder: List<String>,
    val timeUse24h: Boolean,
    val tvOverscan: TvOverscan,
    val hideBottomNavOnScroll: Boolean,
    val navBarShowLabels: Boolean,
)

/**
 * The shell-environment preferences [rememberShellEnvironmentHost] consumes:
 * the adaptive layout override, the haptics master switch, the media-card
 * peek opt-in (the `isExperimentalEnabled(MEDIA_CARD_PEEK)` fold, resolved
 * once here at derivation time) and the seven card-display toggles the
 * environment's CompositionLocals publish. Nothing else from
 * `MainPreferences` — a write outside these fields no longer rebuilds the
 * environment holder or re-fires its `provides` equality checks.
 */
@Immutable
internal data class ShellEnvironmentPrefs(
    val layoutMode: LayoutMode,
    val hapticsEnabled: Boolean,
    val mediaCardPeekEnabled: Boolean,
    val showUnwatchedBadge: Boolean,
    val showWatchedCheckmark: Boolean,
    val hideWatchedItems: Boolean,
    val showQualityBadges: Boolean,
    val showNewBadges: Boolean,
    val antiSpoilerHome: Boolean,
    val cardPressAnimations: Boolean,
)

/** Folds the navigation-chrome fields off the merged pipeline. */
internal fun MainPreferences.toShellNavPrefs(): ShellNavPrefs = ShellNavPrefs(
    homeMode = homeMode,
    startTab = startTab,
    preferredPlayer = preferredPlayer,
    hiddenNavItems = hiddenNavItems,
    navItemOrder = navItemOrder,
    timeUse24h = timeUse24h,
    tvOverscan = tvOverscan,
    hideBottomNavOnScroll = hideBottomNavOnScroll,
    navBarShowLabels = navBarShowLabels,
)

/** Folds the shell-environment fields off the merged pipeline. */
internal fun MainPreferences.toShellEnvironmentPrefs(): ShellEnvironmentPrefs =
    ShellEnvironmentPrefs(
        layoutMode = layoutMode,
        hapticsEnabled = hapticsEnabled,
        mediaCardPeekEnabled = isExperimentalEnabled(ExperimentalFeature.MEDIA_CARD_PEEK),
        showUnwatchedBadge = showUnwatchedBadge,
        showWatchedCheckmark = showWatchedCheckmark,
        hideWatchedItems = hideWatchedItems,
        showQualityBadges = showQualityBadges,
        showNewBadges = showNewBadges,
        antiSpoilerHome = antiSpoilerHome,
        cardPressAnimations = cardPressAnimations,
    )

/**
 * Collects the onboarding-completion flag off the merged preference pipeline
 * ([com.raulshma.jellyplay.navigation.ShellGateModel.preferences]) — the one
 * field the root gate branch reads ([JellyPlayApp]'s TV auto-complete and
 * the shared `onboardingGateRoute` decision). A [Boolean] slice, so a
 * completion flip is the only write that invalidates the gate scope.
 */
@Composable
internal fun rememberOnboardingCompleted(
    preferences: StateFlow<MainPreferences>,
): Boolean =
    preferences.map { it.onboardingCompleted }
        .distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = preferences.value.onboardingCompleted)
        .value

/**
 * Collects the [ShellNavPrefs] slice off the merged preference pipeline —
 * [MainContent]'s ONLY preference input. The initial value maps the source
 * StateFlow's current value, keeping the first composition identical to the
 * former whole-`MainPreferences` collection.
 */
@Composable
internal fun rememberShellNavPrefs(
    preferences: StateFlow<MainPreferences>,
): ShellNavPrefs =
    preferences.map { it.toShellNavPrefs() }
        .distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = preferences.value.toShellNavPrefs())
        .value

/**
 * Collects the [ShellEnvironmentPrefs] slice off the merged preference
 * pipeline — [rememberShellEnvironmentHost]'s ONLY preference input (and
 * therefore its remember key). The initial value maps the source StateFlow's
 * current value, keeping the first composition identical to the former
 * whole-`MainPreferences` collection.
 */
@Composable
internal fun rememberShellEnvironmentPrefs(
    preferences: StateFlow<MainPreferences>,
): ShellEnvironmentPrefs =
    preferences.map { it.toShellEnvironmentPrefs() }
        .distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = preferences.value.toShellEnvironmentPrefs())
        .value
