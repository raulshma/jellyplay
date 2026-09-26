package com.raulshma.jellyplay.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.raulshma.jellyplay.core.ui.animation.DefaultNavTransitionPolicy
import com.raulshma.jellyplay.core.ui.animation.NavDirection
import com.raulshma.jellyplay.core.ui.animation.NavTransitionContext
import com.raulshma.jellyplay.core.ui.animation.isReducedMotion
import com.raulshma.jellyplay.core.ui.animation.toTransition
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.toNavRouteClass
import com.raulshma.jellyplay.feature.player.live.navigation.livePlayerSection
import com.raulshma.jellyplay.feature.shell.navigation.shellEntryProvider
import com.raulshma.jellyplay.feature.subtitle.tester.navigation.subtitleTesterSection

/**
 * One [ContentTransform] resolver for all three [NavDisplay] transition
 * specs — forward, pop and predictive pop differ only in [NavDirection].
 * The former local `resolveTransition` body (plus its three pasted copies)
 * collapsed into this file-level extension; it only uses the passed
 * [motionScheme]/[reducedMotion], so it is safe to call from the
 * non-composable spec lambdas.
 */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.resolveShellTransition(
    motionScheme: MotionScheme,
    reducedMotion: Boolean,
    direction: NavDirection,
): ContentTransform {
    val targetRoute = targetState.entries.lastOrNull()?.contentKey as? Route
    val initialRoute = initialState.entries.lastOrNull()?.contentKey as? Route
    val context = NavTransitionContext(
        targetClass = targetRoute.toNavRouteClass,
        initialClass = initialRoute.toNavRouteClass,
        direction = direction,
        isReducedMotion = reducedMotion,
    )
    val kind = DefaultNavTransitionPolicy.kind(context)
    val transition = kind.toTransition(motionScheme)
    return transition.enter togetherWith transition.exit
}

@Composable
internal fun MainNavDisplay(
    shellParams: ShellNavParams,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
) {
    val navigationState = shellParams.navigationState
    val navigator = shellParams.navigator
    val onLogout = shellParams.onLogout
    val homeMode = shellParams.homeMode
    val onModeChange = shellParams.onModeChange
    val saveableStateHolder = shellParams.saveableStateHolder
    val entryDecorator = shellParams.entryDecorator
    val onNowPlayingClick = shellParams.onNowPlayingClick
    val onAmbientClick = shellParams.onAmbientClick
    val playOn = shellParams.playOn
    val shellHost = shellParams.shellHost

    val currentBackStack = navigationState.backStacks[navigationState.topLevelRoute.value] ?: return

    val paddingDecorator = remember(innerPadding) {
        NavEntryDecorator<NavKey>(
            decorate = { entry ->
                // Full-screen hosts (players / ambient / onboarding / photo
                // viewer) bypass the mini-player bottom padding — typed
                // membership instead of the former contentKey.toString()
                // string matching. Known delta vs the string checks: Onboarding
                // and PhotoViewer also skip the padding now, which is correct
                // (no mini player is shown under either). SubtitleTester
                // intentionally keeps the padding (it overlays a fullscreen
                // host, and isFullScreen deliberately excludes it).
                val isFullScreenHost = (entry.contentKey as? Route)?.isFullScreen == true

                if (isFullScreenHost) {
                    entry.Content()
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        entry.Content()
                    }
                }
            }
        )
    }

    // Read @Composable values ONCE in the composable body — the transition
    // spec lambdas below are NOT composable scopes and cannot call these.
    val motionScheme = MaterialTheme.motionScheme
    val reducedMotion = isReducedMotion()

    // Remember the entry provider graph so the ~25 section builders aren't
    // re-invoked (allocating fresh lambdas + entry objects) on every
    // MainNavDisplay recomposition. The 20 shared sections are the shell
    // module's appSections (one canonical graph behind both shells); this
    // shell adds only what cannot leave Android — the androidMain-only
    // livePlayer/subtitleTester builders and the inline PlayOnCompanion
    // entry — through shellEntryProvider's extraSections slot, which the
    // shared registration ledger sees too. The hooks arrive already built
    // on [ShellNavParams] (MainContent constructs them from the
    // activity-scoped MainViewModel it owns), so the admin/update seams
    // never touch this file.
    val shellSections = remember(navigator, shellHost) {
        shellEntryProvider(navigator = navigator, host = shellHost) {
            livePlayerSection(navigator)
            subtitleTesterSection(navigator)
            // Play On companion — full-screen remote-control surface reached by
            // tapping the persistent PlayOnMiniBar. The controller arrives as
            // an explicit parameter: the same single instance MainContent
            // constructs and the mini bar renders. The former koinViewModel()
            // self-resolution here was identity-by-convention — it held only
            // while both sites sat under MainActivity's ViewModelStoreOwner.
            entry<Route.PlayOnCompanion> {
                com.raulshma.jellyplay.components.PlayOnCompanionScreen(
                    onBack = { navigator.goBack() },
                    playOn = playOn,
                )
            }
        }
    }

    NavDisplay(
        backStack = currentBackStack,
        onBack = { navigator.goBack() },
        entryDecorators = listOf(entryDecorator, paddingDecorator),
        transitionSpec = {
            resolveShellTransition(motionScheme, reducedMotion, NavDirection.FORWARD)
        },
        popTransitionSpec = {
            resolveShellTransition(motionScheme, reducedMotion, NavDirection.POP)
        },
        predictivePopTransitionSpec = { _ ->
            resolveShellTransition(motionScheme, reducedMotion, NavDirection.PREDICTIVE_POP)
        },
        entryProvider = shellSections.entryProvider,
        modifier = modifier,
    )
}
