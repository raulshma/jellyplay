package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The audio player's sheet vocabulary — the ONE sealed set every open/dismiss
 * site routes through (the `ReaderSheetStack` precedent, feature-local). The
 * screen previously held nine loose `remember { mutableStateOf }` flags whose
 * "any open?" predicate was a hand-derived seven-flag OR in the back handler
 * with two deliberate exemptions carried only by comments.
 *
 * Two arms are DECLARED non-sheet members, not accidents:
 *  - [AudioPlayerSheet.Lyrics] — lyrics visibility is a persisted preference
 *    (seeded from `audioLyricsVisible`, written back on toggle), so back
 *    navigates away WITHOUT hiding them and they never hold the screen;
 *  - the menu (`showMenu`) — it self-dismisses through the top bar's own
 *    toggle and is never back-consumed.
 *
 * The vars are backing `mutableStateOf` so recomposition reads inside the
 * composable behave exactly like the loose vars they replaced; Compose-free
 * tests construct it directly.
 */
internal sealed interface AudioPlayerSheet {
    data object Queue : AudioPlayerSheet
    data object SpeedPicker : AudioPlayerSheet
    data object Equalizer : AudioPlayerSheet
    data object Effects : AudioPlayerSheet
    data object LyricsSearch : AudioPlayerSheet
    data object SleepTimer : AudioPlayerSheet
    data object DeleteConfirm : AudioPlayerSheet

    /**
     * DECLARED non-sheet arm: a persisted preference riding in the vocabulary
     * so the lyrics toggle routes through the same fold as every sheet.
     * Excluded from [AudioPlayerSheetStack.open] and from the back ladder.
     */
    data object Lyrics : AudioPlayerSheet
}

/**
 * The admission fold — ONE `current` slot instead of the seven parallel
 * `showX` booleans it replaced. The booleans were an implicit stack: several
 * could be true at once (the swipe-up queue gesture opened the queue OVER
 * the delete confirmation, z-order decided by declaration order in the
 * screen), and [consumeBack] discarded the whole pile in one press. A slot
 * makes both impossible by construction — exactly one sheet can hold the
 * screen, and back pops it.
 *
 * The confirmation arm is STICKY: while [AudioPlayerSheet.DeleteConfirm] is
 * the open sheet, [show] is a no-op for every sheet arm — a list/browser
 * sheet must not silently replace an open confirmation. Back still pops it
 * ([consumeBack]) and [hide] still closes it by name. The declared non-sheet
 * arms never touch the slot and stay exempt from the sticky gate.
 *
 * Compose-free tests construct it directly (the vars are backing
 * `mutableStateOf`).
 */
internal class AudioPlayerSheetStack {
    /**
     * The one sheet or dialog currently holding the screen, or null when
     * none does. Replaces the seven `showQueue`…`showDeleteConfirm`
     * booleans; the render gates read `current == AudioPlayerSheet.X`.
     */
    var current: AudioPlayerSheet? by mutableStateOf(null)

    // Declared non-sheet state (see the vocabulary KDoc): a persisted
    // preference and a self-dismissing menu — kept beside the fold so the
    // screen holds no loose sheet flags, excluded from [open].
    var showLyrics by mutableStateOf(false)
    var showMenu by mutableStateOf(false)

    /** Any sheet or dialog holding the screen. */
    val open: Boolean
        get() = current != null

    /**
     * Opens [sheet], closing whatever the slot held — one slot, so a second
     * sheet can no longer stack over the first (the loose flags let the
     * queue open over the delete confirmation). EXCEPT while
     * [AudioPlayerSheet.DeleteConfirm] is the open sheet: every sheet arm is
     * a no-op — the confirmation cannot be silently replaced; it must be
     * answered or hidden first. The declared non-sheet arms are exempt:
     * [AudioPlayerSheet.Lyrics] flips the persisted preference and never
     * touches the slot (the loose flags never cross-closed it either). The
     * `when` is exhaustive: a new arm must be routed consciously — a future
     * declared non-sheet arm must be exempted here, not silently swept into
     * the slot by an `else`.
     */
    fun show(sheet: AudioPlayerSheet) {
        when (sheet) {
            AudioPlayerSheet.Lyrics -> showLyrics = true
            AudioPlayerSheet.Queue,
            AudioPlayerSheet.SpeedPicker,
            AudioPlayerSheet.Equalizer,
            AudioPlayerSheet.Effects,
            AudioPlayerSheet.LyricsSearch,
            AudioPlayerSheet.SleepTimer,
            AudioPlayerSheet.DeleteConfirm,
            -> {
                // Sticky confirmation: a sheet arm cannot silently replace it.
                if (current == AudioPlayerSheet.DeleteConfirm) return
                current = sheet
            }
        }
    }

    /**
     * Closes exactly [sheet]: a hide of a sheet that isn't the open one is a
     * no-op (with one slot there is nothing else to close). The lyrics arm
     * clears the persisted preference.
     */
    fun hide(sheet: AudioPlayerSheet) {
        when (sheet) {
            AudioPlayerSheet.Lyrics -> showLyrics = false
            AudioPlayerSheet.Queue,
            AudioPlayerSheet.SpeedPicker,
            AudioPlayerSheet.Equalizer,
            AudioPlayerSheet.Effects,
            AudioPlayerSheet.LyricsSearch,
            AudioPlayerSheet.SleepTimer,
            AudioPlayerSheet.DeleteConfirm,
            -> if (current == sheet) current = null
        }
    }

    /**
     * The menu-item cascade, folded: `showMenu = false; showX = true` — every
     * menu entry that opens a sheet dismisses the dropdown first.
     */
    fun showFromMenu(sheet: AudioPlayerSheet) {
        showMenu = false
        show(sheet)
    }

    /**
     * THE back ladder: pop the slot — close the ONE open sheet and consume
     * the back press. The former ladder closed all seven flags at once (one
     * back discarded the whole implicit stack); with one slot there is
     * nothing else to discard. Lyrics (persisted preference) and the menu
     * (self-dismissing) are deliberately exempt — a lone `false` result
     * means the caller should navigate away.
     */
    fun consumeBack(): Boolean {
        val consumed = current != null
        current = null
        return consumed
    }
}
