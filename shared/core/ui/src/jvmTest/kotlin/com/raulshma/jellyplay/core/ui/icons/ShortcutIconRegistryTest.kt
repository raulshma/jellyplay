package com.raulshma.jellyplay.core.ui.icons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Registry contract: unique persisted ids, resolvable vectors, named entries. */
class ShortcutIconRegistryTest {

    @Test
    fun `every entry has a unique non-blank id and name`() {
        val ids = ShortcutIconRegistry.ALL.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "duplicate icon ids")
        assertTrue(ShortcutIconRegistry.ALL.size >= 60, "curated set should stay browsable-wide")
        ShortcutIconRegistry.ALL.forEach { entry ->
            assertTrue(entry.id.isNotBlank())
            assertTrue(entry.displayName.isNotBlank(), "entry ${entry.id} needs a display name")
        }
    }

    @Test
    fun `resolve returns the entry vector and null for unknown ids`() {
        ShortcutIconRegistry.ALL.forEach { entry ->
            assertEquals(entry.icon, ShortcutIconRegistry.resolve(entry.id))
            assertNotNull(ShortcutIconRegistry.displayName(entry.id))
        }
        assertNull(ShortcutIconRegistry.resolve("not-a-real-icon"))
        assertNull(ShortcutIconRegistry.displayName("not-a-real-icon"))
    }
}
