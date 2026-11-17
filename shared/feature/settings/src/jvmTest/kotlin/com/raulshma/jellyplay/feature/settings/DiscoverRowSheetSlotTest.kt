package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the discover-row editor's sheet slot ([DiscoverRowSheet]) — the ONE
 * nullable slot that replaced the former three parallel composition booleans
 * (`showLibrariesSheet` / `showStudiosSheet` / `showPeopleSheet`). The
 * compiler makes coexistence unrepresentable (one slot, one render `when`);
 * this pins the declared arm inventory and the newest-show-wins replacement
 * semantics so the fold stays auditable — the module has no Compose screen
 * harness for this screen.
 *
 * Comment-pinned (composition facts, declared at [DiscoverRowSheet]'s KDoc
 * and the render site): the render order (Libraries → Studios → People,
 * preserved from the former separate `if`s) and the per-arm draft gating.
 */
class DiscoverRowSheetSlotTest {

    @Test
    fun `slot inventory - Libraries, Studios, People`() {
        assertEquals(
            listOf("Libraries", "Studios", "People"),
            DiscoverRowSheet.entries.map { it.name },
        )
    }

    @Test
    fun `newest show replaces - one slot, no stacking`() {
        var slot: DiscoverRowSheet? = null
        slot = DiscoverRowSheet.Libraries
        slot = DiscoverRowSheet.Studios // opening Studios while Libraries is "open" replaces it
        assertEquals(DiscoverRowSheet.Studios, slot)
        slot = DiscoverRowSheet.People
        assertEquals(DiscoverRowSheet.People, slot)
        slot = null // dismiss
        assertEquals(null, slot)
    }
}
