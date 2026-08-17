package com.raulshma.jellyplay.core.ui.viewmodel

import androidx.compose.runtime.FloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LongState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Base class for all JellyPlay ViewModels. Establishes the standardized state
 * management pattern:
 *
 *  - **Compose state** ([composeState], [composeFloatState],
 *    [composeIntState], [composeLongState]) — for UI-only state that does
 *    not need to outlive the screen (e.g. dialog visibility, current tab).
 *    Backed by [mutableStateOf] for minimal recomposition cost.
 *
 *  - **StateFlow** ([stateFlow]) — for state that may be observed by
 *    non-Compose code, needs `.value` access from background coroutines, or
 *    is collected via `collectAsState` from a Composable. Backed by
 *    [MutableStateFlow].
 *
 *  - **Loading state** ([LoadingState]) — for the common
 *    `Idle | Loading | Success | Error` pattern. Subclasses can use the
 *    bundled sealed interface.
 *
 * Subclasses should prefer Compose state for screen-local UI; reserve
 * StateFlow for state that crosses module boundaries or is consumed by
 * multiple Composables.
 *
 * Marked [Stable] so ViewModels passed as composable parameters don't force
 * recomposition. A ViewModel instance is stable for Compose's purposes: it is
 * scoped to the nav entry / activity and never structurally replaced, so any
 * state it publishes must be read through Compose state holders instead.
 */
@Stable
abstract class JellyPlayViewModel : ViewModel() {

    /** Convenience alias for [viewModelScope]. */
    protected val scope: CoroutineScope = viewModelScope

    /**
     * Returns a Compose [State] backed by a [mutableStateOf]. The value can
     * be read and written via property delegation (`var x by composeState(default)`).
     */
    protected fun <T> composeState(initial: T): MutableComposeState<T> =
        MutableComposeState(initial)

    /**
     * Compose state holder backed by [mutableFloatStateOf]. Use for slider
     * and seek-bar positions to avoid autoboxing. Supports `var x by composeFloatState(0f)`.
     */
    protected fun composeFloatState(initial: Float): MutableComposeFloatState =
        MutableComposeFloatState(mutableFloatStateOf(initial))

    /**
     * Compose state holder backed by [mutableIntStateOf]. Use for integer UI
     * state (pager index, list size) to avoid autoboxing. Supports
     * `var x by composeIntState(0)`.
     */
    protected fun composeIntState(initial: Int): MutableComposeIntState =
        MutableComposeIntState(mutableIntStateOf(initial))

    /**
     * Compose state holder backed by [mutableLongStateOf]. Use for large or
     * monotonic counters (timestamps, byte counters) to avoid autoboxing.
     * Supports `var x by composeLongState(0L)`.
     */
    protected fun composeLongState(initial: Long): MutableComposeLongState =
        MutableComposeLongState(mutableLongStateOf(initial))

    /**
     * Returns a read-only [StateFlow] backed by a [MutableStateFlow]. The
     * value can be updated via the returned [StateFlowHandle] reference.
     */
    protected fun <T> stateFlow(initial: T): StateFlowHandle<T> =
        StateFlowHandle(MutableStateFlow(initial))

    /**
     * Returns a [StateFlow] derived from an upstream [flow] using
     * [stateIn] with [SharingStarted.WhileSubscribed] (5s timeout). Use
     * this to share expensive upstream work across multiple collectors.
     */
    protected fun <T> stateIn(
        initial: T,
        started: SharingStarted = SharingStarted.WhileSubscribed(5_000),
        flow: kotlinx.coroutines.flow.Flow<T>,
    ): StateFlow<T> = flow.stateIn(scope, started, initial)

    /**
     * Launch a coroutine in [scope] (alias for `viewModelScope.launch`).
     */
    protected fun launch(
        context: kotlin.coroutines.CoroutineContext = kotlin.coroutines.EmptyCoroutineContext,
        start: kotlinx.coroutines.CoroutineStart = kotlinx.coroutines.CoroutineStart.DEFAULT,
        block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit,
    ) = scope.launch(context, start, block)

    /**
     * Common loading/result state. Use [LoadingState.Idle] before any work,
     * [LoadingState.Loading] while a coroutine runs, [LoadingState.Success]
     * to wrap a result, and [LoadingState.Error] to surface failures.
     */
    sealed interface LoadingState<out T> {
        data object Idle : LoadingState<Nothing>
        data object Loading : LoadingState<Nothing>
        data class Success<T>(val value: T) : LoadingState<T>
        data class Error(val message: String, val cause: Throwable? = null) : LoadingState<Nothing>
    }
}

/**
 * Compose state holder returned by [JellyPlayViewModel.composeState].
 * Supports the standard Kotlin property delegate pattern so subclasses can
 * declare `var x by composeState(default)`.
 */
class MutableComposeState<T>(initial: T) {
    private val state = mutableStateOf(initial)
    var value: T
        get() = state.value
        set(newValue) { state.value = newValue }

    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = state.value
    operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
        state.value = value
    }

    fun asState(): State<T> = state
}

/**
 * Float Compose state holder returned by [JellyPlayViewModel.composeFloatState].
 * Backed by [MutableFloatState] to avoid autoboxing. Supports
 * `var sliderPosition by composeFloatState(0f)`.
 */
class MutableComposeFloatState(private val backing: MutableFloatState) {
    var value: Float
        get() = backing.floatValue
        set(newValue) { backing.floatValue = newValue }

    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Float =
        backing.floatValue
    operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Float) {
        backing.floatValue = value
    }

    fun asState(): FloatState = backing
}

/**
 * Int Compose state holder returned by [JellyPlayViewModel.composeIntState].
 * Backed by [MutableIntState] to avoid autoboxing. Supports
 * `var selectedIndex by composeIntState(0)`.
 */
class MutableComposeIntState(private val backing: MutableIntState) {
    var value: Int
        get() = backing.intValue
        set(newValue) { backing.intValue = newValue }

    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Int =
        backing.intValue
    operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Int) {
        backing.intValue = value
    }

    fun asState(): IntState = backing
}

/**
 * Long Compose state holder returned by [JellyPlayViewModel.composeLongState].
 * Backed by [MutableLongState] to avoid autoboxing. Supports
 * `var bytesRead by composeLongState(0L)`.
 */
class MutableComposeLongState(private val backing: MutableLongState) {
    var value: Long
        get() = backing.longValue
        set(newValue) { backing.longValue = newValue }

    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Long =
        backing.longValue
    operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Long) {
        backing.longValue = value
    }

    fun asState(): LongState = backing
}

/**
 * StateFlow handle returned by [JellyPlayViewModel.stateFlow]. The
 * read-only [StateFlow] is exposed via [flow]; the underlying
 * [MutableStateFlow] is accessible via [update] / [set] / [value] for updates.
 */
class StateFlowHandle<T>(private val backing: MutableStateFlow<T>) {
    val flow: StateFlow<T> = backing.asStateFlow()

    fun update(transform: (T) -> T) {
        backing.update(transform)
    }

    fun set(value: T) {
        backing.value = value
    }

    val value: T get() = backing.value
}
