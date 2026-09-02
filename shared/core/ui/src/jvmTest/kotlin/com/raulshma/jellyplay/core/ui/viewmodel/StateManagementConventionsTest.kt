package com.raulshma.jellyplay.core.ui.viewmodel

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the two-way binding contract of the public state wrappers declared in
 * JellyPlayViewModel.kt (the abstract [JellyPlayViewModel] base itself is
 * viewModelScope-bound and intentionally not covered): a write made through
 * the wrapper's `value` is visible through the delegate syntax AND through
 * `asState()`, and vice versa — the wrapper is a thin pass-through over one
 * backing Compose state object, never a copy. [StateFlowHandle] likewise
 * forwards `set`/`update` into the flow its `flow` exposes.
 */
class StateManagementConventionsTest {

    @Test
    fun composeState_valueAndDelegatePropagateBothWays() {
        val holder = MutableComposeState("initial")
        var bound by holder

        // Initial value visible through the delegate.
        assertEquals("initial", bound)

        // Write via delegate → wrapper + backing State read it back.
        bound = "via-delegate"
        assertEquals("via-delegate", holder.value)
        assertEquals("via-delegate", holder.asState().value)

        // Write via wrapper.value → delegate reads it back.
        holder.value = "via-holder"
        assertEquals("via-holder", bound)
    }

    @Test
    fun composeFloatState_writesPropagateToBackingState() {
        val holder = MutableComposeFloatState(mutableFloatStateOf(0.25f))

        assertEquals(0.25f, holder.value)
        assertEquals(0.25f, holder.asState().floatValue)

        holder.value = 0.75f
        assertEquals(0.75f, holder.asState().floatValue, "wrapper write must reach the unboxed backing state")
    }

    @Test
    fun composeFloatState_delegateRoundTrip() {
        val holder = MutableComposeFloatState(mutableFloatStateOf(0f))
        var slider by holder

        slider = 0.5f
        assertEquals(0.5f, holder.value)
        holder.value = 1f
        assertEquals(1f, slider)
    }

    @Test
    fun composeIntState_writesPropagateToBackingState() {
        val holder = MutableComposeIntState(mutableIntStateOf(3))

        assertEquals(3, holder.value)
        assertEquals(3, holder.asState().intValue)

        holder.value = 7
        assertEquals(7, holder.asState().intValue)
    }

    @Test
    fun composeLongState_writesPropagateToBackingState() {
        val holder = MutableComposeLongState(mutableLongStateOf(1024L))

        assertEquals(1024L, holder.value)
        assertEquals(1024L, holder.asState().longValue)

        holder.value = 9_000_000_000L
        assertEquals(9_000_000_000L, holder.asState().longValue)
    }

    @Test
    fun stateFlowHandle_setAndUpdateAreVisibleOnTheExposedFlow() {
        val handle = StateFlowHandle(MutableStateFlow("a"))
        assertEquals("a", handle.flow.value)
        assertEquals("a", handle.value)

        handle.set("b")
        assertEquals("b", handle.flow.value)
        assertEquals("b", handle.value)

        handle.update { it + "!" }
        assertEquals("b!", handle.flow.value)
    }

    @Test
    fun stateFlowHandle_flowReplaysCurrentStateToNewCollector() = runTest {
        val handle = StateFlowHandle(MutableStateFlow(0))
        handle.set(42)

        assertEquals(42, handle.flow.first(), "StateFlow semantics: a new collector gets the current value")
    }
}
