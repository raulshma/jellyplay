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

internal class AudioPlayerSheetStack {
    var showQueue by mutableStateOf(false)
    var showSpeedPicker by mutableStateOf(false)
    var showEqualizer by mutableStateOf(false)
    var showEffectsSheet by mutableStateOf(false)
    var showLyricsSearch by mutableStateOf(false)
    var showSleepTimer by mutableStateOf(false)
    var showDeleteConfirm by mutableStateOf(false)

    // Declared non-sheet state (see the vocabulary KDoc): a persisted
    // preference and a self-dismissing menu — kept beside the fold so the
    // screen holds no loose sheet flags, excluded from [open].
    var showLyrics by mutableStateOf(false)
    var showMenu by mutableStateOf(false)

    /** Any sheet or dialog holding the screen. */
    val open: Boolean
        get() = showQueue || showSpeedPicker || showEqualizer || showEffectsSheet ||
            showLyricsSearch || showSleepTimer || showDeleteConfirm

    /**
     * Opens exactly [sheet]; every other flag is untouched (overlaps behave
     * as the loose flags did — e.g. the queue can open over the menu).
     */
    fun show(sheet: AudioPlayerSheet) {
        when (sheet) {
            AudioPlayerSheet.Queue -> showQueue = true
            AudioPlayerSheet.SpeedPicker -> showSpeedPicker = true
            AudioPlayerSheet.Equalizer -> showEqualizer = true
            AudioPlayerSheet.Effects -> showEffectsSheet = true
            AudioPlayerSheet.LyricsSearch -> showLyricsSearch = true
            AudioPlayerSheet.SleepTimer -> showSleepTimer = true
            AudioPlayerSheet.DeleteConfirm -> showDeleteConfirm = true
            AudioPlayerSheet.Lyrics -> showLyrics = true
        }
    }

    /** Closes exactly [sheet]. */
    fun hide(sheet: AudioPlayerSheet) {
        when (sheet) {
            AudioPlayerSheet.Queue -> showQueue = false
            AudioPlayerSheet.SpeedPicker -> showSpeedPicker = false
            AudioPlayerSheet.Equalizer -> showEqualizer = false
            AudioPlayerSheet.Effects -> showEffectsSheet = false
            AudioPlayerSheet.LyricsSearch -> showLyricsSearch = false
            AudioPlayerSheet.SleepTimer -> showSleepTimer = false
            AudioPlayerSheet.DeleteConfirm -> showDeleteConfirm = false
            AudioPlayerSheet.Lyrics -> showLyrics = false
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
     * THE back ladder: if any sheet or dialog holds the screen, close all
     * seven and consume the back press. Lyrics (persisted preference) and the
     * menu (self-dismissing) are deliberately exempt — a lone `false` result
     * means the caller should navigate away.
     */
    fun consumeBack(): Boolean {
        val consumed = open
        showQueue = false
        showSpeedPicker = false
        showEqualizer = false
        showEffectsSheet = false
        showLyricsSearch = false
        showSleepTimer = false
        showDeleteConfirm = false
        return consumed
    }
}
