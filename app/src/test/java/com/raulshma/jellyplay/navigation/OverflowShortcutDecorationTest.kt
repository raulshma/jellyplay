package com.raulshma.jellyplay.navigation

import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.ShortcutAction
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalogTarget
import com.raulshma.jellyplay.core.ui.navigation.navKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins [overflowShortcutDecoration] — the overflow menu's per-entry
 * decoration table. The action×offline-state matrix below IS the contract;
 * a change here is a deliberate visual change.
 */
class OverflowShortcutDecorationTest {

    private val screen = ShortcutCatalogTarget.Screen(Route.Downloads, Route.Downloads.navKey)
    private val syncPlay = ShortcutCatalogTarget.Screen(Route.SyncPlay, Route.SyncPlay.navKey)
    private val surprise = ShortcutCatalogTarget.Action(ShortcutAction.SURPRISE_ME)
    private val playOn = ShortcutCatalogTarget.Action(ShortcutAction.PLAY_ON)
    private val toggleOffline = ShortcutCatalogTarget.Action(ShortcutAction.TOGGLE_OFFLINE)

    private fun decorate(
        target: ShortcutCatalogTarget,
        offline: Boolean = false,
        goingOnline: Boolean = false,
        downloads: Int = 0,
    ) = overflowShortcutDecoration(
        target = target,
        isOfflineActive = offline,
        isGoingOnline = goingOnline,
        downloadCount = downloads,
    )

    @Test
    fun `plain screen rows carry no dynamic decoration and close the menu`() {
        val decoration = decorate(syncPlay)
        assertNull(decoration.action)
        assertNull(decoration.iconSlot)
        assertNull(decoration.labelOverrideRes)
        assertEquals(0, decoration.badgeCount)
        assertEquals(false, decoration.isHighlighted)
        assertEquals(false, decoration.isProgressing)
        assertEquals(true, decoration.closesMenu)
    }

    @Test
    fun `downloads row badges the in-flight count`() {
        assertEquals(7, decorate(screen, downloads = 7).badgeCount)
        assertEquals(0, decorate(syncPlay, downloads = 7).badgeCount)
    }

    @Test
    fun `surprise me highlights and closes the menu`() {
        val decoration = decorate(surprise)
        assertEquals(ShortcutAction.SURPRISE_ME, decoration.action)
        assertEquals(true, decoration.isHighlighted)
        assertEquals(true, decoration.closesMenu)
        assertNull(decoration.iconSlot)
        assertNull(decoration.labelOverrideRes)
    }

    @Test
    fun `play on keeps the menu open and carries no dynamic decoration`() {
        val decoration = decorate(playOn)
        assertEquals(ShortcutAction.PLAY_ON, decoration.action)
        assertEquals(false, decoration.closesMenu)
        assertNull(decoration.iconSlot)
        assertNull(decoration.labelOverrideRes)
        assertEquals(false, decoration.isHighlighted)
        assertEquals(false, decoration.isProgressing)
    }

    // ── the offline toggle × offline-state matrix ────────────────────

    @Test
    fun `offline toggle online shows go_offline, unhighlighted`() {
        val decoration = decorate(toggleOffline, offline = false)
        assertEquals(OverflowOfflineIcon.WifiOff, decoration.iconSlot)
        assertEquals(R.string.menu_go_offline, decoration.labelOverrideRes)
        assertEquals(false, decoration.isHighlighted)
        assertEquals(false, decoration.isProgressing)
        assertEquals(false, decoration.closesMenu)
    }

    @Test
    fun `offline toggle offline shows go_online, highlighted`() {
        val decoration = decorate(toggleOffline, offline = true)
        assertEquals(OverflowOfflineIcon.Wifi, decoration.iconSlot)
        assertEquals(R.string.menu_go_online, decoration.labelOverrideRes)
        assertEquals(true, decoration.isHighlighted)
        assertEquals(false, decoration.isProgressing)
    }

    @Test
    fun `offline toggle offline and going online shows the transition`() {
        val decoration = decorate(toggleOffline, offline = true, goingOnline = true)
        assertEquals(OverflowOfflineIcon.Wifi, decoration.iconSlot)
        assertEquals(R.string.menu_going_online, decoration.labelOverrideRes)
        assertEquals(true, decoration.isHighlighted)
        assertEquals(true, decoration.isProgressing)
    }

    @Test
    fun `going online while still online keys the label but not the highlight`() {
        // A state the toggle normally can't reach (the manager flips the mode
        // before the spinner arms) — pinned so the label precedence
        // (going_online outranks the pair) and the mode-keyed icon/highlight
        // can't drift apart.
        val decoration = decorate(toggleOffline, offline = false, goingOnline = true)
        assertEquals(OverflowOfflineIcon.WifiOff, decoration.iconSlot)
        assertEquals(R.string.menu_going_online, decoration.labelOverrideRes)
        assertEquals(false, decoration.isHighlighted)
        assertEquals(true, decoration.isProgressing)
    }
}
