package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.Test

class CacheIdentityTest {

    @Test
    fun of_encodesServerAndUser() {
        val identity = CacheIdentity.of("server-1", "user-A")
        assertEquals(
identity.encoded,
"server-1/user-A",
)
    }

    @Test
    fun of_sameInputs_areEqual() {
        assertEquals(CacheIdentity.of("s", "u"), CacheIdentity.of("s", "u"))
    }

    @Test
    fun of_differentServer_notEqual() {
        assertNotEquals(CacheIdentity.of("server-1", "u"), CacheIdentity.of("server-2", "u"))
    }

    @Test
    fun of_differentUser_notEqual() {
        assertNotEquals(CacheIdentity.of("s", "user-A"), CacheIdentity.of("s", "user-B"))
    }

    @Test
    fun unknown_isStableSingleton() {
        assertEquals(CacheIdentity.UNKNOWN, CacheIdentity.UNKNOWN)
        assertEquals(
CacheIdentity.UNKNOWN.encoded,
"__unknown__",
)
    }

    @Test
    fun unknown_neverCollidesWithRealIdentity() {
        // A real identity can never produce the UNKNOWN sentinel: of() always
        // joins with a single "/", so even pathological inputs don't collide.
        assertNotEquals(CacheIdentity.UNKNOWN, CacheIdentity.of("__unknown__", ""))
        assertNotEquals(CacheIdentity.UNKNOWN, CacheIdentity.of("", "__unknown__"))
    }

    @Test
    fun ofOrNull_nullServerOrUser_returnsUnknown() {
        // Both call sites (MediaRepositoryImpl mirror + LibraryApiClientImpl
        // StateFlow reads) hand-rolled this null-guard; ofOrNull is the single
        // source. Null on either side collapses to UNKNOWN.
        assertEquals(CacheIdentity.UNKNOWN, CacheIdentity.ofOrNull(null, "u"))
        assertEquals(CacheIdentity.UNKNOWN, CacheIdentity.ofOrNull("s", null))
        assertEquals(CacheIdentity.UNKNOWN, CacheIdentity.ofOrNull(null, null))
    }

    @Test
    fun ofOrNull_bothPresent_matchesOf() {
        assertEquals(CacheIdentity.of("s", "u"), CacheIdentity.ofOrNull("s", "u"))
    }
}
