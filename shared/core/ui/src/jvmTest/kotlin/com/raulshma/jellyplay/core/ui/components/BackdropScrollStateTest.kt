package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins what [BackdropScrollState] (line ~111 in BackdropScrollState.kt) itself
 * guarantees: it is a passive @Stable holder whose offset/fraction fields are
 * LIVE [androidx.compose.runtime.State] references — a scroll update written
 * into the backing state is visible through the holder without rebuilding it
 * (the draw-phase-only update contract its KDoc documents) — while
 * [BackdropScrollState.scrollCollapsed], [BackdropScrollState.containerColor]
 * and [BackdropScrollState.titleAlpha] are immutable snapshots stored
 * verbatim.
 *
 * SKIP NOTE: the actual offset math (spacer-offset accumulation, the 0..1
 * fraction clamp, the strict `> 0.7f` collapse threshold) lives inside the
 * @Composable `rememberBackdropScrollState`, which is composition-bound
 * (LocalDensity, MaterialTheme motionScheme/colorScheme,
 * animateFloatAsState) and therefore not executable headlessly; the class
 * under test contains no math of its own. The edge snapshots below document
 * the rest/full-scroll field states consumers build on.
 */
class BackdropScrollStateTest {

    private fun holder(
        offset: Float,
        fraction: Float,
        collapsed: Float = fraction,
        titleAlpha: Float = collapsed,
        containerColor: Color = Color.Transparent,
    ): BackdropScrollState {
        val offsetState = mutableFloatStateOf(offset)
        val fractionState = mutableFloatStateOf(fraction)
        return BackdropScrollState(
            scrollOffsetState = offsetState,
            scrollFractionState = fractionState,
            scrollCollapsed = collapsed,
            containerColor = containerColor,
            titleAlpha = titleAlpha,
        )
    }

    @Test
    fun atRest_holderReadsTheZeroEdgeSnapshot() {
        val state = holder(offset = 0f, fraction = 0f, collapsed = 0f, titleAlpha = 0f)

        assertEquals(0f, state.scrollOffsetState.value)
        assertEquals(0f, state.scrollFractionState.value)
        assertEquals(0f, state.scrollCollapsed)
        assertEquals(0f, state.titleAlpha)
        assertEquals(Color.Transparent, state.containerColor)
    }

    @Test
    fun fullyScrolled_holderReadsTheSaturatedEdgeSnapshot() {
        // spacer fully scrolled away: offset == spacer px, fraction clamped
        // at 1, collapse animation at its target, top bar fully tinted.
        val background = Color(red = 0.1f, green = 0.2f, blue = 0.3f, alpha = 0.95f)
        val state = holder(offset = 800f, fraction = 1f, collapsed = 1f, titleAlpha = 1f, containerColor = background)

        assertEquals(800f, state.scrollOffsetState.value)
        assertEquals(1f, state.scrollFractionState.value)
        assertEquals(1f, state.scrollCollapsed)
        assertEquals(1f, state.titleAlpha)
        assertEquals(background, state.containerColor)
    }

    @Test
    fun scrollOffsetState_isALiveReference_updatesFlowThroughWithoutRebuilding() {
        val offsetBacking = mutableFloatStateOf(0f)
        val fractionBacking = mutableFloatStateOf(0f)
        val state = BackdropScrollState(
            scrollOffsetState = offsetBacking,
            scrollFractionState = fractionBacking,
            scrollCollapsed = 0f,
            containerColor = Color.Transparent,
            titleAlpha = 0f,
        )

        // The holder exposes the SAME state object the factory derived: a new
        // scroll position written into the backing state must be observable
        // through the holder (consumers read .value inside draw lambdas).
        offsetBacking.floatValue = 640f
        fractionBacking.floatValue = 0.8f

        assertEquals(640f, state.scrollOffsetState.value)
        assertEquals(0.8f, state.scrollFractionState.value)
    }

    @Test
    fun scalarFields_areImmutableSnapshots_ofTheConstructionInstant() {
        val offsetBacking = mutableFloatStateOf(0f)
        val fractionBacking = mutableFloatStateOf(0f)
        // scrollCollapsed/titleAlpha/containerColor are plain values captured
        // once at construction: later scroll changes must not alter them.
        val state = BackdropScrollState(
            scrollOffsetState = offsetBacking,
            scrollFractionState = fractionBacking,
            scrollCollapsed = 0.42f,
            containerColor = Color.Transparent,
            titleAlpha = 0.42f,
        )

        offsetBacking.floatValue = 999f
        fractionBacking.floatValue = 1f

        assertEquals(0.42f, state.scrollCollapsed)
        assertEquals(0.42f, state.titleAlpha)
    }
}
