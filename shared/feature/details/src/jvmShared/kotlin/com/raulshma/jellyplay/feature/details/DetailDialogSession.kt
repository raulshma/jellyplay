package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The dialog/sheet SESSION half of the media-detail screen: the seven
 * screen-local sheet-visibility flags (what used to be seven hand-synced
 * `var showX by remember` booleans) plus the open/dismiss CASCADES around
 * them — the home feature's HomeDialogSession shape: a plain class whose
 * sequences are jvmTest-pinned ([DetailDialogSessionTest]); the hosting
 * `if`s stay in the screen (Compose needs them there).
 *
 * Every cascade here was previously duplicated per call site — the series
 * download sheet alone repeated its reset in three places (deep-link open,
 * ⋮-menu open, dismiss AND the confirm callback) — so a call site that
 * forgot one arm would strand the sheet's episode cache on a stale series.
 * Owning flag + cascade as one method makes the "close means reset" and
 * "flag clears BEFORE the command fires" invariants structural, and the
 * cascades' side effects are injected through [Cascades] so the session
 * stays testable without the ViewModel.
 *
 * Deliberately NOT here (each is already a deep module or a value, not a
 * boolean):
 *  - [PendingConfirmation] machines (`pendingDelete` / `pendingDeleteEpisode`)
 *    and the mark-played [ConfirmState] request — confirm machines with
 *    deferred calls, not open/close flags;
 *  - `selectedVersionId` — the version VALUE the play path reads, not a
 *    dialog flag (the picker's open flag is here);
 *  - playlist/collection pickers, identify/merge sheets and the Seerr
 *    request dialog — their open state lives in their VM-side helpers
 *    (`showPicker`, `identifyState.query`, `seerrRequestDialog.item`), which
 *    are the session equivalents on the VM side.
 */
internal class DetailDialogSession(
    private val cascades: Cascades,
) {
    /**
     * The cascade seams: exactly the helper calls the open/dismiss
     * choreography must fire, no more. The screen binds them to
     * `viewModel.downloads` / `viewModel.resync`; tests record the calls.
     */
    interface Cascades {
        /** Series download sheet opened: prefetch downloaded ids + sheet episodes. */
        fun prepareSeriesDownloadSheet()

        /** Series download sheet closed (dismiss OR confirm): reset its caches. */
        fun resetSeriesDownloadSheetState()

        /** Download-details sheet opened: load the on-disk inventory (fresh sizes). */
        fun loadDownloadFileInventory()

        /** Download-details sheet closed: drop the inventory. */
        fun clearDownloadFileInventory()

        /** Resync sheet closed: drop the resync result state. */
        fun clearResyncState()
    }

    /** Series batch-download sheet (quick-action / ⋮ menu / route deep-link). */
    var showSeriesDownloadSheet by mutableStateOf(false)
        private set

    /** "Refresh metadata" mode sheet (⋮ menu → Refresh metadata). */
    var showRefreshMetadataSheet by mutableStateOf(false)
        private set

    /** Version picker sheet (chevron beside Play / ⋮ menu → Version). */
    var showVersionPicker by mutableStateOf(false)
        private set

    /** Split-versions confirm (⋮ menu → Split versions, admin). */
    var showSplitConfirm by mutableStateOf(false)
        private set

    /** Series batch-delete sheet (multi-select downloaded episodes). */
    var showDeleteEpisodesSheet by mutableStateOf(false)
        private set

    /** Resync bottom-sheet (banner tap). */
    var showResyncSheet by mutableStateOf(false)
        private set

    /** Full download-details bottom-sheet (DownloadInfoCard tap). */
    var showDownloadDetailsSheet by mutableStateOf(false)
        private set

    // ── Series download sheet ───────────────────────────────────────────

    /**
     * Opening the sheet ALWAYS prefetches the downloaded-ids + per-season
     * episodes (the sheet re-resolves on every open — all three former open
     * sites repeated this pair).
     */
    fun openSeriesDownloadSheet() {
        showSeriesDownloadSheet = true
        cascades.prepareSeriesDownloadSheet()
    }

    /** Dismissing resets the sheet's caches — "close means reset". */
    fun dismissSeriesDownloadSheet() {
        showSeriesDownloadSheet = false
        cascades.resetSeriesDownloadSheetState()
    }

    /**
     * Confirming a selection closes the sheet, fires [download], THEN resets
     * the sheet caches — the same reset arm a dismiss gets, in the exact
     * order the former callback had (download reads nothing from the sheet
     * state, but the reset must not race it by landing first).
     */
    fun seriesDownloadConfirmed(download: () -> Unit) {
        showSeriesDownloadSheet = false
        download()
        cascades.resetSeriesDownloadSheetState()
    }

    // ── Download-details sheet ──────────────────────────────────────────

    /**
     * Opening loads the on-disk inventory (media + sidecars) BEFORE showing
     * the sheet so sizes are fresh; it re-reads on every open.
     */
    fun openDownloadDetailsSheet() {
        cascades.loadDownloadFileInventory()
        showDownloadDetailsSheet = true
    }

    /** Dismissing drops the inventory with the flag. */
    fun dismissDownloadDetailsSheet() {
        showDownloadDetailsSheet = false
        cascades.clearDownloadFileInventory()
    }

    // ── Resync sheet ────────────────────────────────────────────────────

    fun openResyncSheet() {
        showResyncSheet = true
    }

    /** Dismissing drops the resync result with the flag. */
    fun dismissResyncSheet() {
        showResyncSheet = false
        cascades.clearResyncState()
    }

    // ── Refresh-metadata sheet ──────────────────────────────────────────

    fun openRefreshMetadataSheet() {
        showRefreshMetadataSheet = true
    }

    fun dismissRefreshMetadataSheet() {
        showRefreshMetadataSheet = false
    }

    /**
     * Confirming clears the flag BEFORE firing [refresh] — a sheet must never
     * outlive the command it launched.
     */
    fun refreshMetadataConfirmed(refresh: () -> Unit) {
        showRefreshMetadataSheet = false
        refresh()
    }

    // ── Version picker ──────────────────────────────────────────────────

    fun openVersionPicker() {
        showVersionPicker = true
    }

    fun dismissVersionPicker() {
        showVersionPicker = false
    }

    // ── Split-versions confirm ──────────────────────────────────────────

    fun openSplitConfirm() {
        showSplitConfirm = true
    }

    fun dismissSplitConfirm() {
        showSplitConfirm = false
    }

    /**
     * Confirming clears the flag BEFORE firing [split] (strong-confirm before
     * firing — the dialog must be gone the instant the dissolve starts).
     */
    fun splitConfirmed(split: () -> Unit) {
        showSplitConfirm = false
        split()
    }

    // ── Delete-episodes sheet ───────────────────────────────────────────

    fun openDeleteEpisodesSheet() {
        showDeleteEpisodesSheet = true
    }

    fun dismissDeleteEpisodesSheet() {
        showDeleteEpisodesSheet = false
    }

    /**
     * Navigation escape hatch: every sheet down in one call. The
     * cascade-carrying dismisses fire their cascades unconditionally (each
     * cascade is an idempotent cache reset, so closing a closed sheet is a
     * no-op). No current call site — the screen dies with navigation and
     * takes the session with it; pinned here so the first future caller
     * (e.g. an in-place account switch) reaches for the tested method
     * instead of re-widening the screen with seven flag writes.
     */
    fun closeAll() {
        dismissSeriesDownloadSheet()
        dismissDownloadDetailsSheet()
        dismissResyncSheet()
        showRefreshMetadataSheet = false
        showVersionPicker = false
        showSplitConfirm = false
        showDeleteEpisodesSheet = false
    }
}
