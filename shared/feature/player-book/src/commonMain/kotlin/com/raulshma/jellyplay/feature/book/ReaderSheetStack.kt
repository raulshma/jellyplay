package com.raulshma.jellyplay.feature.book

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The reader's sheet admission fold — ONE answer to "does a sheet or dialog
 * hold the screen?" for every consumer (the JS-tap navigation gate, the
 * chrome auto-hide suppression, bottom-bar visibility decisions). The flags
 * were previously six loose `remember { mutableStateOf }` vars whose
 * "any open?" predicate was hand-derived per consumer and had drifted: the
 * tap gate skipped the sleep-timer sheet and the note dialog, the auto-hide
 * gate skipped the note dialog — a new sheet meant editing N boolean lists,
 * and a miss was a silent paging-under-a-sheet bug. The settings sheet was
 * the last straggler outside (VM uiState state with screen-side OR guards
 * at every read); it lives here too now, so this holder is the single owner
 * of every sheet.
 *
 * [open] deliberately includes the note dialog ([noteTarget]): a modal over
 * the content suppresses navigation and auto-hide the same way a sheet does.
 *
 * Compose-free tests can construct it directly; the vars are backing
 * `mutableStateOf` so recomposition reads inside composables behave exactly
 * like the loose vars they replaced.
 */
internal class ReaderSheetStack {
    var showSettings by mutableStateOf(false)
    var showToc by mutableStateOf(false)
    var showBookmarks by mutableStateOf(false)
    var showAnnotations by mutableStateOf(false)
    var showSearch by mutableStateOf(false)
    var showSleepTimer by mutableStateOf(false)
    var noteTarget: NoteDialogTarget? by mutableStateOf(null)

    /** Any sheet or dialog holding the screen. */
    val open: Boolean
        get() = showSettings || showToc || showBookmarks || showAnnotations ||
            showSearch || showSleepTimer || noteTarget != null
}
