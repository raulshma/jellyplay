package com.raulshma.jellyplay.core.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the delegation contract of [SelfSignedTrustRepositoryImpl] — the
 * module-boundary view of `core:network`'s `SelfSignedTrustMatcher` that the
 * Server Management screen reaches through [SelfSignedTrustRepository]:
 * whatever a TLS handshake honors, this seam must answer identically (the
 * matcher's any-port rule for portless grants, host-strict matching, and
 * fail-closed parsing), and addresses must normalize exactly the way
 * `connectToServer` normalizes before probing so stored grants and display
 * reads agree byte-for-byte.
 */
class SelfSignedTrustRepositoryImplTest {

    private val repository = SelfSignedTrustRepositoryImpl()

    // ------------------------------------------------- isSelfSignedTrustGranted

    @Test
    fun `portless grant covers any port of its host - the handshake's any-port rule`() {
        val grants = setOf("https://media.example.com")
        assertTrue(repository.isSelfSignedTrustGranted(grants, "https://media.example.com:8920"))
        assertTrue(repository.isSelfSignedTrustGranted(grants, "https://media.example.com:8921"))
        assertTrue(repository.isSelfSignedTrustGranted(grants, "https://media.example.com"))
    }

    @Test
    fun `exact-address grant matches itself, never a sibling port or another host`() {
        val grants = setOf("https://media.example.com:8920")
        assertTrue(repository.isSelfSignedTrustGranted(grants, "https://media.example.com:8920"))
        assertFalse(repository.isSelfSignedTrustGranted(grants, "https://media.example.com:8921"))
        assertFalse(repository.isSelfSignedTrustGranted(grants, "https://other.example.com:8920"))
    }

    @Test
    fun `stored server addresses normalize before matching - scheme default and trailing slash`() {
        assertTrue(
            repository.isSelfSignedTrustGranted(setOf("https://media.example.com:8920"), " media.example.com:8920/"),
            "a raw stored address form must match its canonical grant",
        )
        assertTrue(
            repository.isSelfSignedTrustGranted(setOf("https://media.example.com"), "media.example.com"),
        )
    }

    @Test
    fun `unparseable grant fails closed`() {
        assertFalse(repository.isSelfSignedTrustGranted(setOf("http://[::1"), "https://media.example.com"))
        assertFalse(repository.isSelfSignedTrustGranted(emptySet(), "https://media.example.com"))
    }

    // --------------------------------------------- selfSignedTrustGrantsCovering

    @Test
    fun `covering returns the grants to revoke - never the addresses`() {
        val grants = setOf("https://media.example.com", "https://other.example.com")
        val covering = repository.selfSignedTrustGrantsCovering(
            grants,
            addresses = listOf(
                "https://media.example.com:8920", // covered by the portless grant
                "https://unrelated.example.com",  // covered by nothing
            ),
        )
        assertEquals(setOf("https://media.example.com"), covering)
    }

    @Test
    fun `covering matches raw stored address forms and alternates too`() {
        val covering = repository.selfSignedTrustGrantsCovering(
            setOf("https://media.example.com:8920", "https://alt.example.com"),
            addresses = listOf("https://media.example.com:8921", " media.example.com:8920/"),
        )
        assertEquals(setOf("https://media.example.com:8920"), covering)
    }

    @Test
    fun `covering is empty for no grants or no match - everything survives the prune`() {
        assertEquals(emptySet(), repository.selfSignedTrustGrantsCovering(emptySet(), listOf("https://a.example.com")))
        assertEquals(
            emptySet(),
            repository.selfSignedTrustGrantsCovering(
                setOf("https://other.example.com"),
                listOf("https://media.example.com"),
            ),
        )
    }

    @Test
    fun `toggle member agrees with the covering member`() {
        val grants = setOf("https://media.example.com")
        val address = "https://media.example.com:8920"
        assertEquals(
            repository.selfSignedTrustGrantsCovering(grants, listOf(address)).isNotEmpty(),
            repository.isSelfSignedTrustGranted(grants, address),
        )
    }
}
