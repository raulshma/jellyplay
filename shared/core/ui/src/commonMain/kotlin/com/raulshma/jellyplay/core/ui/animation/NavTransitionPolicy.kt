package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.MotionScheme
import com.raulshma.jellyplay.core.designsystem.theme.expressiveSlideSpec
import com.raulshma.jellyplay.core.designsystem.theme.modalSpringSpec

/**
 * Coarse classification of a destination route, used purely to pick a transition.
 * Derived from the [com.raulshma.jellyplay.core.ui.navigation.Route] classification
 * members (per-route metadata declared on each route) by
 * [com.raulshma.jellyplay.core.ui.navigation.toNavRouteClass].
 */
enum class NavRouteClass {
    AMBIENT,
    FULLSCREEN,
    MODAL,
    DETAIL,
    TOP_LEVEL_TAB,
    DEFAULT,
}

/** Which direction the navigation is flowing. */
enum class NavDirection {
    FORWARD,
    POP,
    PREDICTIVE_POP,
}

/**
 * The intended transition for a navigation event. Pure enum so the decision logic
 * ([NavTransitionPolicy.kind]) is unit-testable without constructing Compose
 * [EnterTransition]/[ExitTransition] objects or loading the Route hierarchy.
 * [kindToTransition] maps each kind to concrete transition pair.
 */
enum class NavTransitionKind {
    /** Fade in/out — ambient & fullscreen (immersive, no spatial distraction). */
    FADE,
    /** Detail push: fade + slide-in 8% from the right. */
    DETAIL_PUSH,
    /** Detail pop: fade + slide-out to the right. */
    DETAIL_POP,
    /** Modal push: fade + slide up 15%. */
    MODAL_PUSH,
    /** Modal pop: fade + slide down. */
    MODAL_POP,
    /** Tab switch: fast fade (instant-feeling). */
    TAB_SWITCH,
    /** Default forward push: horizontal slide. */
    DEFAULT_PUSH,
    /** Default pop: reverse horizontal slide. */
    DEFAULT_POP,
    /** Reduced motion: instant, no animation. */
    INSTANT,
}

/**
 * All inputs the transition decision needs, with Compose/Route types already
 * flattened to plain enums/booleans. This keeps [NavTransitionPolicy] pure and
 * unit-testable.
 */
data class NavTransitionContext(
    val targetClass: NavRouteClass,
    val initialClass: NavRouteClass,
    val direction: NavDirection,
    val isReducedMotion: Boolean,
)

/** A resolved enter/exit pair for a NavDisplay transition spec. */
data class NavTransition(
    val enter: EnterTransition,
    val exit: ExitTransition,
)

/**
 * Maps navigation context to a [NavTransitionKind]. Pure — no Compose, no side
 * effects. The single decision function all three NavDisplay spec lambdas
 * funnel through, so reduce-motion and route classification are enforced in one
 * place.
 */
interface NavTransitionPolicy {
    fun kind(context: NavTransitionContext): NavTransitionKind
}

/**
 * The default (and currently only) policy. Shared by phone and TV since both
 * use the same [MainNavDisplay]. Branch mirrors the intent of the original
 * inline transitionSpec logic but is centralized and testable.
 */
object DefaultNavTransitionPolicy : NavTransitionPolicy {

    override fun kind(context: NavTransitionContext): NavTransitionKind {
        // Reduce-motion flattens everything to instant, regardless of route.
        if (context.isReducedMotion) return NavTransitionKind.INSTANT

        val target = context.targetClass
        val initial = context.initialClass

        // Ambient always fades — immersive, no spatial motion.
        if (target == NavRouteClass.AMBIENT || initial == NavRouteClass.AMBIENT) {
            return NavTransitionKind.FADE
        }

        // Fullscreen (player/onboarding/photo) always fades.
        if (target == NavRouteClass.FULLSCREEN) {
            return NavTransitionKind.FADE
        }

        return when (context.direction) {
            NavDirection.FORWARD -> forwardKind(target, initial)
            NavDirection.POP -> popKind(target, initial)
            NavDirection.PREDICTIVE_POP -> predictivePopKind(target, initial)
        }
    }

    private fun forwardKind(target: NavRouteClass, initial: NavRouteClass): NavTransitionKind = when {
        target == NavRouteClass.MODAL -> NavTransitionKind.MODAL_PUSH
        // Tab switch: both endpoints are top-level tabs.
        target == NavRouteClass.TOP_LEVEL_TAB && initial == NavRouteClass.TOP_LEVEL_TAB ->
            NavTransitionKind.TAB_SWITCH
        target == NavRouteClass.DETAIL || initial == NavRouteClass.DETAIL ->
            NavTransitionKind.DETAIL_PUSH
        else -> NavTransitionKind.DEFAULT_PUSH
    }

    private fun popKind(target: NavRouteClass, initial: NavRouteClass): NavTransitionKind = when {
        // Popping away from a modal: modal slides down.
        initial == NavRouteClass.MODAL -> NavTransitionKind.MODAL_POP
        target == NavRouteClass.DETAIL || initial == NavRouteClass.DETAIL ->
            NavTransitionKind.DETAIL_POP
        else -> NavTransitionKind.DEFAULT_POP
    }

    private fun predictivePopKind(target: NavRouteClass, initial: NavRouteClass): NavTransitionKind = when {
        target == NavRouteClass.DETAIL || initial == NavRouteClass.DETAIL ->
            NavTransitionKind.DETAIL_POP
        else -> NavTransitionKind.DEFAULT_POP
    }
}

// ---------------------------------------------------------------------------
// Expressive spec helpers (used by the mapper below).
//
// The slide/modal specs live in the designsystem theme package
// (`expressiveSlideSpec()` / `modalSpringSpec()`) so they can be reused by
// other mappers (e.g. shared-element bounds). MotionScheme is a fixed Material 3
// interface and cannot be extended with new methods. Under reduced motion the
// policy returns INSTANT, so these expressive specs never run in that mode.
// ---------------------------------------------------------------------------

/** Fast fade for tab switches — should feel near-instant. */
private const val TAB_FADE_MS = 200

/**
 * Converts a [NavTransitionKind] to a concrete [NavTransition] using the current
 * [motionScheme] for fade durations + the expressive specs above for spatial
 * motion. Thin and deterministic — all decision logic lives in the policy.
 */
fun NavTransitionKind.toTransition(
    motionScheme: MotionScheme,
): NavTransition {
    val defaultFade = motionScheme.defaultEffectsSpec<Float>()
    val fastFade = motionScheme.fastEffectsSpec<Float>()

    return when (this) {
        NavTransitionKind.INSTANT -> NavTransition(
            enter = fadeIn(tween(durationMillis = 0)),
            exit = fadeOut(tween(durationMillis = 0)),
        )
        NavTransitionKind.FADE -> NavTransition(
            enter = fadeIn(defaultFade),
            exit = fadeOut(fastFade),
        )
        NavTransitionKind.TAB_SWITCH -> NavTransition(
            enter = fadeIn(tween(durationMillis = TAB_FADE_MS)),
            exit = fadeOut(tween(durationMillis = TAB_FADE_MS)),
        )
        NavTransitionKind.DETAIL_PUSH -> NavTransition(
            enter = fadeIn(defaultFade) + slideInHorizontally(
                initialOffsetX = { it / 12 },
                animationSpec = expressiveSlideSpec(),
            ),
            exit = fadeOut(fastFade),
        )
        NavTransitionKind.DETAIL_POP -> NavTransition(
            enter = fadeIn(defaultFade),
            exit = fadeOut(fastFade) + slideOutHorizontally(
                targetOffsetX = { it / 12 },
                animationSpec = expressiveSlideSpec(),
            ),
        )
        NavTransitionKind.MODAL_PUSH -> NavTransition(
            enter = fadeIn(defaultFade) + slideInVertically(
                initialOffsetY = { it / 6 },
                animationSpec = modalSpringSpec(),
            ),
            exit = fadeOut(fastFade),
        )
        NavTransitionKind.MODAL_POP -> NavTransition(
            enter = fadeIn(fastFade),
            exit = fadeOut(fastFade) + slideOutVertically(
                targetOffsetY = { it / 6 },
                animationSpec = modalSpringSpec(),
            ),
        )
        NavTransitionKind.DEFAULT_PUSH -> NavTransition(
            enter = fadeIn(defaultFade) + slideInHorizontally(
                initialOffsetX = { it / 8 },
                animationSpec = expressiveSlideSpec(),
            ),
            exit = fadeOut(fastFade) + slideOutHorizontally(
                targetOffsetX = { -it / 18 },
                animationSpec = expressiveSlideSpec(),
            ),
        )
        NavTransitionKind.DEFAULT_POP -> NavTransition(
            enter = fadeIn(defaultFade) + slideInHorizontally(
                initialOffsetX = { -it / 12 },
                animationSpec = expressiveSlideSpec(),
            ),
            exit = fadeOut(fastFade) + slideOutHorizontally(
                targetOffsetX = { it / 10 },
                animationSpec = expressiveSlideSpec(),
            ),
        )
    }
}
