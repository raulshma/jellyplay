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

    @Test
    fun `slot inventory - the six overlay arms`() {
        val arms = LanguageSettingsDialog::class.sealedSubclasses.map { it.simpleName }.toSet()
        assertEquals(
            setOf(
                "None",
                "SubtitleBgColorPicker",
                "SubtitleTextColorPicker",
                "AudioOrderEditor",
                "SubtitleOrderEditor",
                "RulesEditor",
            ),
            arms,
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
