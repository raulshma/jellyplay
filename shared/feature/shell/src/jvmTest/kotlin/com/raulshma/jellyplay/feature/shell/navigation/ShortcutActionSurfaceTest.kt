package com.raulshma.jellyplay.feature.shell.navigation

import com.raulshma.jellyplay.core.ui.navigation.ShortcutAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the shared shortcut-action execution surface: the capability verdict
 * derived from the armed arms, the navigate-home-before-arm-surprise ordering
 * invariant (the reason the fold is a module and not a lambda), the
 * unsupported arm routing, and the supported arms' pass-through.
 */
class ShortcutActionSurfaceTest {

    // ── recording shell ────────────────────────────────────────────────

    private class RecordingShell(
        armSurprise: (() -> Unit)?,
        requestPlayOn: (() -> Unit)?,
        toggleOffline: (() -> Unit)?,
    ) {
        val events = mutableListOf<String>()
        val unsupported = mutableListOf<ShortcutAction>()
        val surface = ShortcutActionSurface(
            focusHome = { events += "focusHome" },
            armSurprise = armSurprise?.let { arm -> { events += "armSurprise"; arm() } },
            requestPlayOn = requestPlayOn?.let { arm -> { events += "requestPlayOn"; arm() } },
            toggleOffline = toggleOffline?.let { arm -> { events += "toggleOffline"; arm() } },
            onUnsupported = { action ->
                events += "onUnsupported"
                unsupported += action
            },
        )
    }

    // ── capability verdict ─────────────────────────────────────────────

    @Test
    fun `supportedActions derives from the armed arms`() {
        val full = RecordingShell(armSurprise = {}, requestPlayOn = {}, toggleOffline = {})
        assertEquals(ShortcutAction.entries.toSet(), full.surface.supportedActions)
        assertTrue(ShortcutAction.entries.all(full.surface::supports))

        val playOnOnly = RecordingShell(armSurprise = null, requestPlayOn = {}, toggleOffline = null)
        assertEquals(setOf(ShortcutAction.PLAY_ON), playOnOnly.surface.supportedActions)
        assertTrue(playOnOnly.surface.supports(ShortcutAction.PLAY_ON))
        assertFalse(playOnOnly.surface.supports(ShortcutAction.SURPRISE_ME))
        assertFalse(playOnOnly.surface.supports(ShortcutAction.TOGGLE_OFFLINE))

        val none = RecordingShell(armSurprise = null, requestPlayOn = null, toggleOffline = null)
        assertTrue(none.surface.supportedActions.isEmpty())
        ShortcutAction.entries.forEach { assertFalse(none.surface.supports(it)) }
    }

    // ── the surprise ordering invariant ────────────────────────────────

    @Test
    fun `surprise focuses home before arming`() {
        val shell = RecordingShell(armSurprise = {}, requestPlayOn = {}, toggleOffline = {})
        shell.surface.execute(ShortcutAction.SURPRISE_ME)
        // The Home hero controller must be composed to collect the surprise
        // signal — the arm runs strictly after the focus, and only for this
        // action.
        assertEquals(listOf("focusHome", "armSurprise"), shell.events)
    }

    // ── supported arms pass through, unsupported routes loudly ─────────

    @Test
    fun `supported actions run their arm and nothing else`() {
        val shell = RecordingShell(armSurprise = {}, requestPlayOn = {}, toggleOffline = {})
        shell.surface.execute(ShortcutAction.PLAY_ON)
        shell.surface.execute(ShortcutAction.TOGGLE_OFFLINE)
        assertEquals(listOf("requestPlayOn", "toggleOffline"), shell.events)
    }

    @Test
    fun `unsupported action routes to onUnsupported, never a silent no-op`() {
        val shell = RecordingShell(armSurprise = null, requestPlayOn = null, toggleOffline = {})
        shell.surface.execute(ShortcutAction.SURPRISE_ME)
        shell.surface.execute(ShortcutAction.PLAY_ON)
        shell.surface.execute(ShortcutAction.TOGGLE_OFFLINE)
        assertEquals(
            listOf("onUnsupported", "onUnsupported", "toggleOffline"),
            shell.events,
        )
        assertEquals(
            listOf(ShortcutAction.SURPRISE_ME, ShortcutAction.PLAY_ON),
            shell.unsupported,
        )
    }
}
