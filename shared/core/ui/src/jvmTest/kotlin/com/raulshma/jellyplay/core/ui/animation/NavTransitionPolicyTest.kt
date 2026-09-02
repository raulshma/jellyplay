package com.raulshma.jellyplay.core.ui.animation

import kotlin.test.assertEquals
import kotlin.test.Test

class NavTransitionPolicyTest {

    private val policy = DefaultNavTransitionPolicy

    private fun ctx(
        target: NavRouteClass,
        initial: NavRouteClass,
        direction: NavDirection = NavDirection.FORWARD,
        reduced: Boolean = false,
    ) = NavTransitionContext(target, initial, direction, reduced)

    // ---- Reduce-motion contract: everything flattens to INSTANT ----

    @Test
    fun reducedMotion_alwaysInstant_regardlessOfRoute() {
        NavRouteClass.values().forEach { target ->
            NavRouteClass.values().forEach { initial ->
                NavDirection.values().forEach { dir ->
                    val kind = policy.kind(ctx(target, initial, dir, reduced = true))
                    assertEquals(NavTransitionKind.INSTANT, kind, "Reduced motion should be INSTANT for $target/$initial/$dir")
                }
            }
        }
    }

    // ---- Ambient & fullscreen always fade ----

    @Test
    fun ambientTarget_fades() {
        assertEquals(
            NavTransitionKind.FADE,
            policy.kind(ctx(NavRouteClass.AMBIENT, NavRouteClass.DEFAULT)),
        )
    }

    @Test
    fun ambientInitial_fades() {
        assertEquals(
            NavTransitionKind.FADE,
            policy.kind(ctx(NavRouteClass.DEFAULT, NavRouteClass.AMBIENT)),
        )
    }

    @Test
    fun fullscreenTarget_fades() {
        assertEquals(
            NavTransitionKind.FADE,
            policy.kind(ctx(NavRouteClass.FULLSCREEN, NavRouteClass.DEFAULT)),
        )
    }

    // ---- Forward direction ----

    @Test
    fun forward_modalTarget_isModalPush() {
        assertEquals(
            NavTransitionKind.MODAL_PUSH,
            policy.kind(ctx(NavRouteClass.MODAL, NavRouteClass.DEFAULT)),
        )
    }

    @Test
    fun forward_modalTarget_overDetail_isModalPush() {
        // e.g. opening the Requests modal from a PersonDetail screen: the modal
        // target wins over the detail initial route. Modal membership now
        // comes from per-route member metadata, so this pins the bucket
        // interplay the old predicate lists used to own.
        assertEquals(
            NavTransitionKind.MODAL_PUSH,
            policy.kind(ctx(NavRouteClass.MODAL, NavRouteClass.DETAIL)),
        )
    }

    @Test
    fun forward_settingsModal_to_settingsModal_isModalPush() {
        // e.g. Integrations → ArrSettings (a modal opening another modal).
        assertEquals(
            NavTransitionKind.MODAL_PUSH,
            policy.kind(ctx(NavRouteClass.MODAL, NavRouteClass.MODAL)),
        )
    }

    @Test
    fun forward_tabToTab_isTabSwitch() {
        assertEquals(
            NavTransitionKind.TAB_SWITCH,
            policy.kind(ctx(NavRouteClass.TOP_LEVEL_TAB, NavRouteClass.TOP_LEVEL_TAB)),
        )
    }

    @Test
    fun forward_detailTarget_isDetailPush() {
        assertEquals(
            NavTransitionKind.DETAIL_PUSH,
            policy.kind(ctx(NavRouteClass.DETAIL, NavRouteClass.DEFAULT)),
        )
    }

    @Test
    fun forward_default_isDefaultPush() {
        assertEquals(
            NavTransitionKind.DEFAULT_PUSH,
            policy.kind(ctx(NavRouteClass.DEFAULT, NavRouteClass.DEFAULT)),
        )
    }

    // ---- Pop direction ----

    @Test
    fun pop_fromModal_isModalPop() {
        assertEquals(
            NavTransitionKind.MODAL_POP,
            policy.kind(
                ctx(NavRouteClass.DEFAULT, NavRouteClass.MODAL, NavDirection.POP),
            ),
        )
    }

    @Test
    fun pop_fromDetail_isDetailPop() {
        assertEquals(
            NavTransitionKind.DETAIL_POP,
            policy.kind(
                ctx(NavRouteClass.DEFAULT, NavRouteClass.DETAIL, NavDirection.POP),
            ),
        )
    }

    @Test
    fun pop_default_isDefaultPop() {
        assertEquals(
            NavTransitionKind.DEFAULT_POP,
            policy.kind(
                ctx(NavRouteClass.DEFAULT, NavRouteClass.DEFAULT, NavDirection.POP),
            ),
        )
    }

    // ---- Predictive pop ----

    @Test
    fun predictivePop_fromDetail_isDetailPop() {
        assertEquals(
            NavTransitionKind.DETAIL_POP,
            policy.kind(
                ctx(NavRouteClass.DEFAULT, NavRouteClass.DETAIL, NavDirection.PREDICTIVE_POP),
            ),
        )
    }

    @Test
    fun predictivePop_default_isDefaultPop() {
        assertEquals(
            NavTransitionKind.DEFAULT_POP,
            policy.kind(
                ctx(NavRouteClass.DEFAULT, NavRouteClass.DEFAULT, NavDirection.PREDICTIVE_POP),
            ),
        )
    }
}
