package com.raulshma.jellyplay.core.ui.viewmodel

/**
 * The codified state-management convention for JellyPlay ViewModels and
 * screens, published as [PREFERRED_PATTERN] (doc-rendered; the name and the
 * const are a stable artifact — do not rename).
 *
 * REWRITTEN (v0.11.9): this file previously prescribed the
 * `data class UiState(isLoading, error)` boolean-flag shape. The codebase
 * outgrew it: the sealed migration landed via `DetailUiLoadState`
 * (shared/feature/details/src/commonMain/kotlin/com/raulshma/jellyplay/feature/details/DetailUiState.kt),
 * whose KDoc records that it "collapses the former isLoading/isRefreshing/
 * error/isAccessDenied field soup", with the same shape landing in
 * `QuickConnectUiState`, `UpdateState` and `LiveFallbackPhase`. This
 * convention now codifies sealed phase models as the target shape for all
 * NEW state — load lifecycles, one-shot events, and infra/lifecycle
 * machines — and demotes the boolean-flag choreography (`loadInto`,
 * LoadInto.kt) to transitional status for screens whose migration has not
 * landed yet. The per-feature catalog of these machines lives in the
 * "State machines" section of CONTEXT.md.
 */
object StateManagementConventions {

    const val PREFERRED_PATTERN = """
    # JellyPlay State Management Conventions

    ## 1. Sealed load-phase for load lifecycles (PREFERRED)

    One sealed interface per screen lifecycle, with exactly the variants that
    screen needs (Idle/Loading/Refreshing/Loaded/Error are the usual
    suspects, not a mandatory set). One owner StateFlow, an exhaustive
    `when` at the render site. Exemplar: `DetailUiLoadState` in
    shared/feature/details/src/commonMain/kotlin/com/raulshma/jellyplay/feature/details/DetailUiState.kt —
    its KDoc records the migration from the former isLoading/isRefreshing/
    error/isAccessDenied field soup to one mutually-exclusive state so the
    screen renders exactly one overlay treatment. For the plain
    Idle/Loading/Success/Error ladder with no screen-specific variants,
    reuse the generic `JellyPlayViewModel.LoadingState` sealed interface
    (shared/core/ui/src/commonMain/kotlin/com/raulshma/jellyplay/core/ui/viewmodel/JellyPlayViewModel.kt)
    instead of minting a near-identical one.

    ```kotlin
    sealed interface LibraryLoadState {
        data object Idle : LibraryLoadState
        data object Loading : LibraryLoadState
        data object Refreshing : LibraryLoadState   // content stays visible underneath
        data class Loaded(val items: List<Item>) : LibraryLoadState
        data class Error(val message: String) : LibraryLoadState
    }

    class LibraryViewModel(...) : JellyPlayViewModel() {
        private val _loadState = stateFlow<LibraryLoadState>(LibraryLoadState.Idle)
        val loadState = _loadState.flow

        fun load() {
            launch {
                _loadState.set(LibraryLoadState.Loading)
                repository.getItems()
                    .onSuccess { _loadState.set(LibraryLoadState.Loaded(it)) }
                    .onFailure { _loadState.set(LibraryLoadState.Error(it.message ?: "Load failed")) }
            }
        }
    }
    ```

    Render with an exhaustive `when` — the compiler, not a comment, enforces
    that every phase gets exactly one overlay treatment and payloads
    smart-cast in their arm:

    ```kotlin
    val state by viewModel.loadState.collectAsStateWithLifecycle()
    when (state) {
        LibraryLoadState.Idle -> Unit
        LibraryLoadState.Loading -> FullScreenSpinner()
        LibraryLoadState.Refreshing -> PullRefreshIndicator()
        is LibraryLoadState.Loaded -> ItemList(state.items)
        is LibraryLoadState.Error -> ErrorBanner(state.message)
    }
    ```

    NEVER mint parallel booleans (`isLoading` + `isRefreshing` + `error`):
    nothing prevents `isLoading=true` alongside `error != null`, and every
    render site must re-derive which combination wins. If two of your flags
    can both be true at once, that is the wrong shape — see section 3.

    ## 2. One-shot events via Channel

    Events that fire once — navigation, snackbars, toasts, "close the
    player" — ride a `Channel` exposed as `receiveAsFlow()` and are consumed
    exactly once by the screen (typically in a `LaunchedEffect` collector):

    ```kotlin
    sealed interface EditorUiEvent {
        data class OpenItem(val itemId: String) : EditorUiEvent
        data class ShowMessage(val text: String) : EditorUiEvent
    }

    private val _events = Channel<EditorUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    ```

    NEVER park a one-shot result as a nullable uiState field plus a
    clear-it-after-handling call: a StateFlow replays its current value to
    every new collector, so the event re-fires on state restoration, process
    recreation, or a re-subscribing collector. Live exemplars:
    `VideoPlayerViewModel.closePlayer`,
    `ChannelDetailViewModel.messages`, `DownloadsViewModel.messages`; for
    undoable-action snackbars use the shared `undoActionChannel()` factory
    (shared/core/ui/src/commonMain/kotlin/com/raulshma/jellyplay/core/ui/components/UndoableAction.kt).

    ## 3. Infra/lifecycle machines: sealed phase + pure decision object

    Non-load machinery (self-update, QuickConnect handshake, live fallback)
    uses the same shape with one addition: a PURE decision/transition object
    beside the phase type, so the rules are unit-testable without the
    ViewModel's scopes, stores, or I/O. Exemplars:
    `UpdateState` + `AppUpdateDecision`
    (app/src/store/java/com/raulshma/jellyplay/update/),
    `QuickConnectUiState` (shared/feature/auth/src/commonMain/kotlin/com/raulshma/jellyplay/feature/auth/AuthViewModel.kt),
    and `LiveFallbackPhase`
    (shared/feature/player-live/src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/live/LiveFallbackPhase.kt),
    which was extracted from the former `errorTerminal` + `fallbackInvoked`
    booleans precisely because those two flags could both be true and the
    engine drifted on which one won.

    The invariant: impossible combinations must be UNREPRESENTABLE. A sealed
    phase makes the compiler enforce what boolean comments only hoped for —
    `WaitingForApproval` and `Success` cannot coexist; `errorTerminal` and
    `fallbackInvoked` can. If two fields of your state can both be true at
    once (`isChecking` + `isDownloading`), it is the wrong shape: make it a
    phase. Transition rules live in the pure decision object and are pinned
    by tests (see `LiveFallbackPhaseTest`, `AppUpdateDecisionTest`).

    ## 4. `loadInto` is the TRANSITIONAL choreography

    `loadInto` (shared/core/ui/src/commonMain/kotlin/com/raulshma/jellyplay/core/ui/viewmodel/LoadInto.kt)
    is the ONE load ladder for NOT-YET-MIGRATED boolean-flag states: raise
    the caller's start flag(s) and clear the error before the fetch, then
    dispatch the Result to exactly one arm. Keep using it where a screen
    still carries boolean soup and the sealed migration has not landed. It
    is NOT the target shape for new code: a sealed load phase (section 1)
    replaces the flag pair outright — there is no flag left to raise or
    clear, only a phase to set.

    ## Collect in Screens

    ```kotlin
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ```

    Lifecycle-aware collection stops updates while the screen is STOPPED.
    (StateFlow needs no initial value; a plain `Flow` passes one.)

    ## Why a single-owner StateFlow (sealed phase or data class)

    - Atomic state snapshots — no intermediate inconsistent states
    - Easier to test — one object to assert against
    - Better for state restoration — one object to serialize
    - Clearer state transitions in debug tools
    - `.update {}` / `StateFlowHandle.set` for thread-safe mutation
    """
}
