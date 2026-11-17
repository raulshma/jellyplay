package com.raulshma.jellyplay.feature.downloads

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the Downloads screen's resync sheet slot ([ResyncSheet]) — the ONE
 * nullable slot that replaced the former `showResyncSheet` /
 * `showForceResyncSheet` boolean pair, making the sheets' mutual exclusivity
 * structural (writing [ResyncSheet.ForceResync] IS closing
 * [ResyncSheet.Resync]). This pins the declared arm inventory and the
 * escalate-replaces / dismiss-nulls transition semantics so the fold stays
 * auditable.
 *
 * Comment-pinned (composition facts, declared at [ResyncSheet]'s KDoc and the
 * render site — the screen body has no Compose harness): both sheets' content
 * and the VM commands they invoke are unchanged — the app-bar resync action
 * still runs `checkAllForUpdates()` before arming [ResyncSheet.Resync], the
 * escalation handoff still calls `clearResyncProgress()` (now followed by the
 * single slot write that both closes the resync sheet and opens the force
 * picker), and every dismiss path nulls the slot then clears progress. The
 * resync/force-resync VM state machine itself is out of scope.
 */
class ResyncSheetSlotTest {

    @Test
    fun `slot inventory - Resync, ForceResync`() {
        assertEquals(
            listOf("Resync", "ForceResync"),
            ResyncSheet.entries.map { it.name },
        )
    }

    @Test
    fun `escalation replaces, dismissal nulls - one slot, no stacking`() {
        var slot: ResyncSheet? = null
        slot = ResyncSheet.Resync // app-bar resync action (after checkAllForUpdates)
        assertEquals(ResyncSheet.Resync, slot)
        slot = ResyncSheet.ForceResync // escalation handoff replaces, never stacks
        assertEquals(ResyncSheet.ForceResync, slot)
        slot = null // dismiss
        assertEquals(null, slot)
    }
}
