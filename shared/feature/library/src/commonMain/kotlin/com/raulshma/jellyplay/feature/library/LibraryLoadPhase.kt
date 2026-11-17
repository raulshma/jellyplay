package com.raulshma.jellyplay.feature.library

/**
 * Sealed load-phase for the Library's FOLDER fetch lifecycle — the machine
 * [LibraryViewModel] publishes while `loadFolders` runs. Replaces the former
 * `_isLoading: StateFlow<Boolean>` + `_error: StateFlow<String?>` boolean
 * pair (the exact §1 violation in
 * [com.raulshma.jellyplay.core.ui.viewmodel.StateManagementConventions]:
 * the two flags could coexist — a refresh started while a fatal error was on
 * screen raised `isLoading=true` alongside `error != null`, and the error
 * write itself was CONDITIONAL, so the same emission meant "fatal" when the
 * folder list was empty and "non-blocking" when stale folders survived).
 *
 * The three phases and their transition rules (pinned by
 * `LibraryViewModelTest`):
 *
 *  1. initial → [Loading] (the phase's initial value; the former
 *     `_isLoading` started `true`).
 *  2. success → [Ready] — the folder list stays a separate flow beside the
 *     phase (`LibraryViewModel.folders`), unchanged.
 *  3. failure with EMPTY folders → [Failed] — the error-screen path (the
 *     surface dispatch's Error arm).
 *  4. failure with folders ALREADY LOADED → stays [Ready] — the preserved
 *     conditional: a failure only becomes blocking when there is nothing to
 *     browse. The former machine encoded this as "write `error` only when
 *     `_folders.value.isNullOrEmpty()`"; here the same gate picks between
 *     [Failed] and [Ready], so the non-blocking outcome is no longer
 *     "Ready + hidden error" but simply [Ready].
 *
 * [Loading] covers both the initial load and pull-to-refresh alike — the
 * former single `_isLoading` made no distinction and this preserves that
 * exactly.
 */
internal sealed interface LibraryLoadPhase {
    /** A folder fetch is in flight — initial load or refresh alike. */
    data object Loading : LibraryLoadPhase

    /**
     * Folders loaded and browsable (or a failed refresh over already-loaded
     * folders — transition rule 4 above, the non-blocking-failure rule in
     * the type KDoc: the stale list stays up, no error surface).
     */
    data object Ready : LibraryLoadPhase

    /**
     * The fetch failed with nothing loaded — the error-screen path.
     * [message] is the resolved user-facing text, null-capable to mirror the
     * former `error: String?` exposure shape.
     */
    data class Failed(val message: String?) : LibraryLoadPhase
}
