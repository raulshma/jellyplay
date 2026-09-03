package com.raulshma.jellyplay.core.ui.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the protected factory surface of the [JellyPlayViewModel] base class
 * itself (the public wrappers are covered by [StateManagementConventionsTest]):
 *
 *  - a concrete subclass declares state through `composeState` /
 *    `composeIntState` / `composeFloatState` / `composeLongState`; every write
 *    is visible through the delegate AND the holder's `asState()` view;
 *  - `stateFlow` exposes a handle whose set/update reach the exposed flow;
 *  - `stateIn` starts at the supplied initial value and mirrors the upstream
 *    while subscribed (WhileSubscribed semantics), running on the
 *    viewModelScope's Main dispatcher (virtualized via setMain);
 *  - the `launch` alias runs on viewModelScope;
 *  - [JellyPlayViewModel.LoadingState] is a sealed set with data semantics:
 *    Idle/Loading are distinct singletons, Success is covariant and
 *    value-equal, Error carries an optional cause.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JellyPlayViewModelTest {

    // Unconfined: viewModelScope (Main.immediate) then runs sharing/launch
    // coroutines eagerly, without needing scheduler handshakes between the
    // runTest scheduler and the Main dispatcher.
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class TestViewModel : JellyPlayViewModel() {
        val textHolder = composeState("initial")
        var text by textHolder

        val indexHolder = composeIntState(3)
        var index by indexHolder

        val sliderHolder = composeFloatState(0.5f)
        var slider by sliderHolder

        val counterHolder = composeLongState(10L)
        var counter by counterHolder

        val handle = stateFlow("v1")

        val derived: StateFlow<Int> = stateIn(
            initial = -1,
            flow = MutableStateFlow(5),
        )

        var launchedFlag = false

        fun doLaunch() {
            launch { launchedFlag = true }
        }
    }

    @Test
    fun composeStateFactory_delegatesReadWriteThroughAsState() {
        val vm = TestViewModel()

        assertEquals("initial", vm.text)
        vm.text = "changed"
        assertEquals("changed", vm.text)
        // Delegate and asState() view the same backing cell.
        assertEquals("changed", vm.textHolder.asState().value)
    }

    @Test
    fun typedComposeStateFactories_roundTripThroughAsState() {
        val vm = TestViewModel()

        assertEquals(3, vm.index)
        vm.index = 9
        assertEquals(9, vm.indexHolder.asState().intValue)

        assertEquals(0.5f, vm.slider)
        vm.slider = 0.25f
        assertEquals(0.25f, vm.sliderHolder.asState().floatValue)

        assertEquals(10L, vm.counter)
        vm.counter = 99_999_999_999L
        assertEquals(99_999_999_999L, vm.counterHolder.asState().longValue)
    }

    @Test
    fun stateFlowHandle_updatesAreVisibleThroughFlow() {
        val vm = TestViewModel()

        assertEquals("v1", vm.handle.flow.value)
        vm.handle.set("v2")
        assertEquals("v2", vm.handle.flow.value)
        vm.handle.update { it + "!" }
        assertEquals("v2!", vm.handle.flow.value)
    }

    @Test
    fun stateIn_holdsInitialValueBeforeSubscription() = runTest {
        assertEquals(-1, TestViewModel().derived.value)
    }

    @Test
    fun stateIn_mirrorsUpstreamWhileSubscribed() = runTest {
        val vm = TestViewModel()
        var last = -1
        val job = backgroundScope.launch { vm.derived.collect { last = it } }
        advanceUntilIdle()

        assertEquals(5, last, "WhileSubscribed must activate the upstream on subscription")
        job.cancel()
    }

    @Test
    fun stateIn_keepsLastValueAfterUnsubscribe() = runTest {
        val vm = TestViewModel()
        val job = backgroundScope.launch { vm.derived.collect { } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(5, vm.derived.value, "WhileSubscribed retains the last value (5s stop timeout)")
    }

    @Test
    fun launchHelper_runsOnViewModelScope() = runTest {
        val vm = TestViewModel()

        vm.doLaunch()
        advanceUntilIdle()

        assertTrue(vm.launchedFlag)
    }

    @Test
    fun loadingState_idleAndLoadingAreDistinctSingletons() {
        val idle: JellyPlayViewModel.LoadingState<Int> = JellyPlayViewModel.LoadingState.Idle
        val loading: JellyPlayViewModel.LoadingState<Int> = JellyPlayViewModel.LoadingState.Loading

        assertSame(JellyPlayViewModel.LoadingState.Idle, idle)
        assertSame(JellyPlayViewModel.LoadingState.Loading, loading)
        assertNotEquals(idle, loading)
    }

    @Test
    fun loadingState_successIsValueEqualAndCovariant() {
        assertEquals(
            JellyPlayViewModel.LoadingState.Success(1),
            JellyPlayViewModel.LoadingState.Success(1),
        )
        assertNotEquals(
            JellyPlayViewModel.LoadingState.Success(1),
            JellyPlayViewModel.LoadingState.Success(2),
        )
        // out T: a Success<Int> is usable where LoadingState<Number> is expected.
        val widened: JellyPlayViewModel.LoadingState<Number> = JellyPlayViewModel.LoadingState.Success(1)
        assertEquals(1, (widened as JellyPlayViewModel.LoadingState.Success<Number>).value)
    }

    @Test
    fun loadingState_errorCarriesMessageAndOptionalCause() {
        val plain = JellyPlayViewModel.LoadingState.Error("boom")
        assertEquals("boom", plain.message)
        assertNull(plain.cause)

        val cause = IllegalStateException("root")
        val rich = JellyPlayViewModel.LoadingState.Error("boom", cause)
        assertSame(cause, rich.cause)
        assertEquals(JellyPlayViewModel.LoadingState.Error("boom", cause), rich)
    }
}
