package com.raulshma.jellyplay.core.model.remote

/**
 * The remote-navigation vocabulary the receiver's General-command ladder
 * emits and every shell's collector folds onto its own navigator. Pure value
 * types: the flow plumbing that carries them ([com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge])
 * stays in core:data — same split as every other model/runtime pair.
 *
 * This enum is the focus-direction vocabulary of that ladder
 * (`MoveUp`/`MoveDown`/`MoveLeft`/`MoveRight` general commands). Each host
 * maps it onto its platform's focus/key synthesis (Android keycodes, desktop
 * `FocusManager` moves).
 */
enum class RemoteFocusDirection { UP, DOWN, LEFT, RIGHT }

/**
 * The remote "go to a top-level destination" vocabulary (`GoHome` /
 * `GoToSettings` / `GoToSearch` general commands). Enum, not a Route: this
 * module cannot depend on core/ui's navigation model; each host folds the
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
