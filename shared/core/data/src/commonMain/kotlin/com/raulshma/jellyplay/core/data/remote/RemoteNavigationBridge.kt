package com.raulshma.jellyplay.core.data.remote

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The focus-direction vocabulary of the remote navigation ladder
 * (`MoveUp`/`MoveDown`/`MoveLeft`/`MoveRight` general commands). Defined
 * here (not in core/ui) because the bridge is a core:data type — hosts map
 * it onto their platform's focus/key synthesis.
 */
enum class RemoteFocusDirection { UP, DOWN, LEFT, RIGHT }

/**
 * The remote "go to a top-level destination" vocabulary (`GoHome` /
 * `GoToSettings` / `GoToSearch` general commands). Enum, not a Route: the
 * bridge cannot depend on core/ui's navigation model; each host folds the
 * destination onto its own top-level/tab routing.
 */
enum class RemoteTopLevelDestination { HOME, SEARCH, SETTINGS }

sealed class NavigationTarget {
    data class OpenVideoPlayer(
        val itemId: String,
        val mediaSourceId: String? = null,
        val startPositionTicks: Long = 0L,
        val audioStreamIndex: Int? = null,
        val subtitleStreamIndex: Int? = null,
    ) : NavigationTarget()

    data class OpenAudioPlayer(val itemId: String) : NavigationTarget()

    data class OpenMediaDetail(val itemId: String) : NavigationTarget()

    // ── Navigation ladder: UI-level targets emitted by the remote
    // receiver's d-pad / destination commands. Each host executes them
    // against its own navigator / key-input chain; none is a pushed Route.

    /** Pop one entry off the current back stack (remote "Back"). */
    data object GoBack : NavigationTarget()

    /** Move the UI focus one step in [direction] (remote d-pad). */
    data class MoveFocus(val direction: RemoteFocusDirection) : NavigationTarget()

    /** Activate the focused element (remote "Select" / D-pad center). */
    data object InvokeSelect : NavigationTarget()

    /** Switch to a top-level destination (remote GoHome/GoToSettings/GoToSearch). */
    data class GoToTopLevel(val destination: RemoteTopLevelDestination) : NavigationTarget()

    /**
     * Open the context menu of the focused element. Hosts without a
     * context-menu affordance surface the standard fallback message.
     */
    data object OpenContextMenu : NavigationTarget()

    data object ClosePlayer : NavigationTarget()
}

class RemoteNavigationBridge() {
    private val _targets = MutableSharedFlow<NavigationTarget>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val targets: SharedFlow<NavigationTarget> = _targets.asSharedFlow()

    fun request(target: NavigationTarget) {
        _targets.tryEmit(target)
    }
}
