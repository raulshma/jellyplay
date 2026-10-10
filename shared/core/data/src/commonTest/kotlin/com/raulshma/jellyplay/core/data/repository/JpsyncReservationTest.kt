package com.raulshma.jellyplay.core.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The reservation's byte-compat pin: these strings are persisted in user
 * DataStores across releases, so every factory's output is asserted against
 * the LITERAL shape the field stores hold — a rename here must break this
 * test, not a user's mirror/cursor/identity state.
 */
class JpsyncReservationTest {

    @Test
    fun reservedPrefix_coversTheWholeJpsyncSpace() {
        assertEquals("jpsync.", JpsyncReservation.RESERVED_PREFIX)
        assertTrue(JpsyncReservation.isReserved("jpsync.mirror.theme"))
        assertTrue(JpsyncReservation.isReserved("jpsync.device.id"))
        assertTrue(JpsyncReservation.isReserved("jpsync.cursor.delta.user-1"))
        assertTrue(JpsyncReservation.isReserved("jpsync.ns.enabled.reader"))
        assertFalse(JpsyncReservation.isReserved("theme"))
        assertFalse(JpsyncReservation.isReserved("jpsyncish"))
        // The reservation is a PREFIX match: the exact reserved root itself is reserved.
        assertTrue(JpsyncReservation.isReserved("jpsync."))
    }

    @Test
    fun mirrorKeys_matchTheStoredShape() {
        assertEquals("jpsync.mirror.", JpsyncReservation.MIRROR_ROOT)
        assertEquals("jpsync.mirror.books.", JpsyncReservation.mirrorPrefix("books"))
        assertEquals("jpsync.mirror.cw.", JpsyncReservation.mirrorPrefix("cw"))
        assertEquals("jpsync.mirror.search.", JpsyncReservation.mirrorPrefix("search"))
        assertEquals("jpsync.mirror.reader.", JpsyncReservation.mirrorPrefix("reader"))
        assertEquals("jpsync.mirror.homelayout.", JpsyncReservation.mirrorPrefix("homelayout"))
        // The prefs adapter's mirror rides the root with no namespace segment.
        assertEquals("jpsync.mirror.theme", JpsyncReservation.MIRROR_ROOT + "theme")
        assertEquals(
            "jpsync.mirror.books.book-1/12345",
            JpsyncReservation.mirrorKey("books", "book-1/12345"),
        )
    }

    @Test
    fun wiringKeys_matchTheStoredShape() {
        assertEquals(
            "jpsync.ns.enabled.reader",
            JpsyncReservation.namespaceToggleKey("reader"),
        )
        assertEquals(
            "jpsync.cursor.delta.user-1",
            JpsyncReservation.cursorKey("delta", "user-1"),
        )
        assertEquals(
            "jpsync.cursor.sse.user-2",
            JpsyncReservation.cursorKey("sse", "user-2"),
        )
        assertEquals("jpsync.device.sync_enabled", JpsyncReservation.deviceSyncEnabledKey())
        assertEquals("jpsync.device.id", JpsyncReservation.deviceIdKey())
        assertEquals("jpsync.device.push.endpoint", JpsyncReservation.pushEndpointKey())
    }
}
