package com.raulshma.jellyplay.core.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Base class for all JellyPlay ViewModels. Establishes the standardized state
 * management pattern:
 *
 *  - **Compose state** ([composeState]) — for UI-only state that does not
 *    need to outlive the screen (e.g. dialog visibility, current tab).
 *    Backed by [mutableStateOf] for minimal recomposition cost.
 *
 *  - **StateFlow** ([stateFlow]) — for state that may be observed by
 *    non-Compose code, needs `.value` access from background coroutines, or
 *    is collected via [collectAsState] from a Composable. Backed by
 *    [MutableStateFlow].
 *
 *  - **Loading state** ([loadingState]) — for the common
 *    `Idle | Loading | Success | Error` pattern. Subclasses can use the
 *    bundled [Loading] sealed interface.
 *
 * Subclasses should prefer Compose state for screen-local UI; reserve
 * StateFlow for state that crosses module boundaries or is consumed by
 * multiple Composables.
 */
abstract class JellyPlayViewModel : ViewModel() {

    /** Convenience alias for [viewModelScope]. */
    protected val scope: CoroutineScope = viewModelScope

    /**
     * Returns a Compose [State] backed by a [mutableStateOf]. The value can
     * be read and written via property delegation (`var x by state()`).
     */
    protected fun <T> composeState(initial: T): MutableComposeState<T> =
        MutableComposeState(initial)

    /**
     * Returns a read-only [StateFlow] backed by a [MutableStateFlow]. The
     * value can be updated via the returned [MutableStateFlow] reference.
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
 * StateFlow handle returned by [JellyPlayViewModel.stateFlow]. The
 * read-only [StateFlow] is exposed via [flow]; the underlying
 * [MutableStateFlow] is accessible via [mutate] for updates.
 */
class StateFlowHandle<T>(private val backing: MutableStateFlow<T>) {
    val flow: StateFlow<T> = backing.asStateFlow()

    fun update(transform: (T) -> T) {
        backing.value = transform(backing.value)
    }

    fun set(value: T) {
        backing.value = value
    }

    val value: T get() = backing.value
}

/**
 * Helper extension to collect a [StateFlow] as Compose [State] with the
 * standard 5-second subscription timeout. Use this in Composables that need
 * to observe a [StateFlow] produced by a ViewModel.
 */
@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycleSafe(
    initial: T,
): State<T> = collectAsState(initial = initial)
