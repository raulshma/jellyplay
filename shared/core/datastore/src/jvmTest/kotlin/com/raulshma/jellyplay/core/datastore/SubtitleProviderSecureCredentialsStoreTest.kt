package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the [SubtitleProviderSecureCredentialsStore] contract: per-provider
 * storage keys (`creds_<kind>`), polymorphic round-trips of both credential
 * types (Wyzie api key; OpenSubtitles username/password + cached JWT pair),
 * cross-provider isolation of [clearCredentials], and the corrupt-blob /
 * unknown-type degrade to null instead of throwing.
 */
class SubtitleProviderSecureCredentialsStoreTest {

    private lateinit var storage: FakeSecureKeyValueStorage
    private lateinit var store: SubtitleProviderSecureCredentialsStore

    @BeforeTest
    fun setup() {
        storage = FakeSecureKeyValueStorage()
        store = SubtitleProviderSecureCredentialsStore(storage)
    }

    @Test
    fun `absent credentials read as null for every kind`() = runTest {
        assertNull(store.getCredentials(SubtitleProviderKind.WYZIE))
        assertNull(store.getCredentials(SubtitleProviderKind.OPENSUBTITLES))
        assertNull(store.getCredentials(SubtitleProviderKind.JELLYFIN))
    }

    @Test
    fun `wyzie credentials round-trip`() = runTest {
        val creds = SubtitleProviderCredentials.Wyzie(apiKey = "wyzie-key")

        store.setCredentials(SubtitleProviderKind.WYZIE, creds)

        assertEquals(creds, store.getCredentials(SubtitleProviderKind.WYZIE))
    }

    @Test
    fun `opensubtitles credentials round-trip including the cached jwt`() = runTest {
        val creds = SubtitleProviderCredentials.OpenSubtitles(
            username = "user",
            password = "pass",
            jwt = "jwt-token",
            jwtExpiresAt = 1_725_000_000_000,
        )

        store.setCredentials(SubtitleProviderKind.OPENSUBTITLES, creds)

        val restored = store.getCredentials(SubtitleProviderKind.OPENSUBTITLES)
        assertNotNull(restored)
        assertTrue(restored is SubtitleProviderCredentials.OpenSubtitles)
        assertEquals("user", restored.username)
        assertEquals("pass", restored.password)
        assertEquals("jwt-token", restored.jwt)
        assertEquals(1_725_000_000_000, restored.jwtExpiresAt)
    }

    @Test
    fun `providers are isolated under their own storage keys`() = runTest {
        val wyzie = SubtitleProviderCredentials.Wyzie(apiKey = "wyzie-key")
        val openSubs = SubtitleProviderCredentials.OpenSubtitles(username = "u", password = "p")
        store.setCredentials(SubtitleProviderKind.WYZIE, wyzie)
        store.setCredentials(SubtitleProviderKind.OPENSUBTITLES, openSubs)

        store.clearCredentials(SubtitleProviderKind.WYZIE)

        assertNull(store.getCredentials(SubtitleProviderKind.WYZIE))
        assertEquals(openSubs, store.getCredentials(SubtitleProviderKind.OPENSUBTITLES))
        assertNull(storage.raw["creds_wyzie"])
        assertNotNull(storage.raw["creds_opensubtitles"])
    }

    @Test
    fun `overwriting replaces the previous credentials for the kind`() = runTest {
        store.setCredentials(SubtitleProviderKind.WYZIE, SubtitleProviderCredentials.Wyzie(apiKey = "old"))
        val next = SubtitleProviderCredentials.Wyzie(apiKey = "new")

        store.setCredentials(SubtitleProviderKind.WYZIE, next)

        assertEquals(next, store.getCredentials(SubtitleProviderKind.WYZIE))
    }

    @Test
    fun `corrupt blob degrades to null instead of throwing`() = runTest {
        storage.raw["creds_wyzie"] = "{definitely not json"

        assertNull(store.getCredentials(SubtitleProviderKind.WYZIE))
    }

    @Test
    fun `unknown type discriminator degrades to null`() = runTest {
        // A newer build introduced a credential type this build doesn't know.
        // decodeOrNull semantics: unreadable entry → null, user re-enters.
        storage.raw["creds_wyzie"] = """{"type":"someFutureProvider","apiKey":"x"}"""

        assertNull(store.getCredentials(SubtitleProviderKind.WYZIE))
    }

    @Test
    fun `clearCredentials on an absent key is a no-op`() = runTest {
        store.clearCredentials(SubtitleProviderKind.JELLYFIN)

        assertTrue(storage.raw.isEmpty())
    }
}
