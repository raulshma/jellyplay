package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the language screen's ONE overlay slot ([LanguageSettingsDialog]) —
 * the extension that folded the former track-selection trio of parallel
 * composition booleans (`showAudioOrderEditor` / `showSubtitleOrderEditor` /
 * `showRulesEditor`) into the same slot the two subtitle-color sheets already
 * used (SAME SCREEN, TWO ENCODINGS → one). The screen's render `is`-chain is
 * compiler-checked per arm, so this pins the declared arm inventory and the
 * newest-show-wins replacement semantics so the fold stays auditable — the
 * module has no Compose screen harness for this screen.
 *
 * Comment-pinned (composition facts, declared at [LanguageSettingsDialog]'s
 * KDoc and the render site): the editors' render-site move from inside the
 * track-selection group body to the screen's post-scaffold block (window
 * z-order unaffected — each arm is its own modal window), and the
 * `hexPickerTarget` handoff remaining a deliberate separate two-level flow.
 */
class LanguageSettingsDialogSlotTest {

    /**
     * The canonical [LanguageSettingsDialog] arm positions — exhaustive over
     * the sealed class WITHOUT `sealedSubclasses`: this JVM test source set
     * carries no kotlin-reflect, so the reflective scan throws
     * KotlinReflectionNotSupportedError before any assertion runs (the same
     * constraint PlayerSheetRegistryTest documents for player-video). The
     * compile-time exhaustive `when` is the stand-in — a new or removed arm
     * fails this file's compilation until its arm lands here.
     */
    private fun armPosition(dialog: LanguageSettingsDialog): Int = when (dialog) {
        LanguageSettingsDialog.None -> 0
        LanguageSettingsDialog.SubtitleBgColorPicker -> 1
        LanguageSettingsDialog.SubtitleTextColorPicker -> 2
        LanguageSettingsDialog.AudioOrderEditor -> 3
        LanguageSettingsDialog.SubtitleOrderEditor -> 4
        LanguageSettingsDialog.RulesEditor -> 5
    }

    @Test
    fun `slot inventory - the six overlay arms`() {
        val arms = listOf(
            LanguageSettingsDialog.None,
            LanguageSettingsDialog.SubtitleBgColorPicker,
            LanguageSettingsDialog.SubtitleTextColorPicker,
            LanguageSettingsDialog.AudioOrderEditor,
            LanguageSettingsDialog.SubtitleOrderEditor,
            LanguageSettingsDialog.RulesEditor,
        )
        // Declared order preserved: the mapped positions are the full 0..5
        // sequence, and the arm set is exactly the six the screen renders.
        assertEquals(listOf(0, 1, 2, 3, 4, 5), arms.map(::armPosition))
        assertEquals(
            setOf(
                "None",
                "SubtitleBgColorPicker",
                "SubtitleTextColorPicker",
                "AudioOrderEditor",
                "SubtitleOrderEditor",
                "RulesEditor",
            ),
            arms.map { it::class.simpleName }.toSet(),
        )
    }

    @Test
    fun `newest show replaces - the folded editors share one slot`() {
        var slot: LanguageSettingsDialog = LanguageSettingsDialog.None
        slot = LanguageSettingsDialog.AudioOrderEditor
        slot = LanguageSettingsDialog.SubtitleOrderEditor
        assertEquals(LanguageSettingsDialog.SubtitleOrderEditor, slot)
        slot = LanguageSettingsDialog.RulesEditor
        assertEquals(LanguageSettingsDialog.RulesEditor, slot)
        slot = LanguageSettingsDialog.None
        assertEquals(LanguageSettingsDialog.None, slot)
    }
}
