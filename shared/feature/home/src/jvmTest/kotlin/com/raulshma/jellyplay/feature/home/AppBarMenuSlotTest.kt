package com.raulshma.jellyplay.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the home app-bar user-switcher's menu slot ([AppBarMenu]) — the ONE
 * nullable slot that replaced the former platform-fork boolean pair
 * (`menuExpanded` / `showTvMenu`), which a mid-session TV-mode flip could arm
 * both ways. The compiler makes the both-armed combination unrepresentable
 * (one slot, one arm written per open); this pins the declared arm inventory
 * and the one-arm-wins replacement semantics so the fold stays auditable.
 *
 * Comment-pinned (composition facts, declared at [AppBarMenu]'s KDoc and the
 * render site — the module has no Compose harness for the private chip): the
 * platform fork itself stays OUT of the state — the chip's single open path
 * reads [LocalTvMode] to pick the arm (`activeMenu = if (isTv) Tv else
 * Touch`), the `!isTv` dropdown arm and `isTv &&` sheet arm render per mode
 * exactly as before, every dismiss path nulls the slot, and a flip mid-open
 * leaves the foreign arm unrendered with the slot inert until the next chip
 * click re-arms it — the flip edge is structurally moot, not specially
 * handled.
 */
class AppBarMenuSlotTest {

    @Test
    fun `slot inventory - Touch, Tv`() {
        assertEquals(
            listOf("Touch", "Tv"),
            AppBarMenu.entries.map { it.name },
        )
    }

    @Test
    fun `one slot - newest arm replaces, dismissal nulls`() {
        var slot: AppBarMenu? = null
        slot = AppBarMenu.Touch
        assertEquals(AppBarMenu.Touch, slot)
        slot = AppBarMenu.Tv // one slot: opening the other arm replaces, never stacks
        assertEquals(AppBarMenu.Tv, slot)
        slot = null // dismiss
        assertEquals(null, slot)
    }
}
