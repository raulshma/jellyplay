package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.ServerBackupMetadata
import com.raulshma.jellyplay.core.data.repository.ServerBackupMetadataStore
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.BackupSecrets
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.SeerrSecrets
import com.raulshma.jellyplay.core.datastore.ServerEntrySecret
import com.raulshma.jellyplay.core.datastore.SubtitleCredentialEntry
import com.raulshma.jellyplay.core.datastore.SubtitleProviderSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.toSecret
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Wave-3 secrets gather/apply seam: [SecretsBackupAssembler] fans the
 * three secure credential stores + the token-free server list into a
 * [BackupSecrets] payload and applies a decrypted one back with MERGE-by-id
 * semantics (never wholesale-adopt — restoring must not erase locally-
 * configured credentials the backup knows nothing about). The Jellyfin
 * subtitle provider never appears (it rides the session, no credentials).
 */
class SecretsBackupAssemblerTest {

    // Relaxed: the store writes are the assertions' capture targets, not
    // stubbed answers.
    private val arrStore: ArrSecureCredentialsStore = mockk(relaxed = true)
    private val seerrStore: SeerrSecureCredentialsStore = mockk(relaxed = true)
    private val subtitleStore: SubtitleProviderSecureCredentialsStore = mockk(relaxed = true)
    private val serverMetadataStore: ServerBackupMetadataStore = mockk(relaxed = true)

    private val assembler = SecretsBackupAssembler(arrStore, seerrStore, subtitleStore, serverMetadataStore)

    // ------------------------------------------------------------ gather

    @Test
    fun `gather fans the three stores plus the server list into the payload`() = runTest {
        every { arrStore.getManualServers() } returns listOf(
            ArrServerConfig(id = "arr-1", baseUrl = "https://arr.local", apiKey = "key-1", name = "Arr", kind = ArrServiceKind.RADARR, isManual = true),
        )
        every { subtitleStore.getCredentials(SubtitleProviderKind.WYZIE) } returns SubtitleProviderCredentials.Wyzie("wyzie-key")
        every { subtitleStore.getCredentials(SubtitleProviderKind.OPENSUBTITLES) } returns null
        every { seerrStore.getApiKey() } returns "seerr-key"
        every { seerrStore.getPassword() } returns ""
        every { seerrStore.getSessionCookie() } returns "cookie"
        coEvery { serverMetadataStore.gatherServers() } returns listOf(
            ServerBackupMetadata(id = "srv-1", name = "Home", address = "https://home.local", alternateAddresses = listOf("https://alt"), userNames = listOf("alice")),
        )

        val gathered = assembler.gather()

        assertEquals(1, gathered.arrServers.size)
        assertEquals("key-1", gathered.arrServers.single().apiKey)
        assertTrue(gathered.arrServers.single().isManual)
        assertEquals(1, gathered.subtitleCredentials.size)
        assertEquals(SubtitleProviderKind.WYZIE, gathered.subtitleCredentials.single().kind)
        assertEquals(SeerrSecrets(apiKey = "seerr-key", password = null, sessionCookie = "cookie"), gathered.seerr)
        assertEquals(1, gathered.servers.size)
        assertEquals(listOf("alice"), gathered.servers.single().userNames)
    }

    @Test
    fun `gather omits the Jellyfin subtitle provider and an all-blank Seerr`() = runTest {
        every { arrStore.getManualServers() } returns emptyList()
        every { subtitleStore.getCredentials(any()) } returns null
        every { seerrStore.getApiKey() } returns ""
        every { seerrStore.getPassword() } returns ""
        every { seerrStore.getSessionCookie() } returns ""
        coEvery { serverMetadataStore.gatherServers() } returns emptyList()

        val gathered = assembler.gather()

        assertTrue(gathered.arrServers.isEmpty())
        assertTrue(gathered.subtitleCredentials.isEmpty(), "JELLYFIN carries no credentials by design")
        assertNull(gathered.seerr, "an all-blank Seerr must not produce an empty secrets block")
        assertTrue(gathered.servers.isEmpty())
        coVerify(exactly = 0) { subtitleStore.getCredentials(SubtitleProviderKind.JELLYFIN) }
    }

    // ------------------------------------------------------------ summarize

    @Test
    fun `summarize reports the preview counts`() {
        val summary = assembler.summarize(
            BackupSecrets(
                arrServers = listOf(
                    com.raulshma.jellyplay.core.datastore.ArrServerSecret("a", "https://a", "k", "A", ArrServiceKind.RADARR),
                ),
                subtitleCredentials = listOf(
                    SubtitleCredentialEntry(SubtitleProviderKind.WYZIE, SubtitleProviderCredentials.Wyzie("k")),
                    SubtitleCredentialEntry(SubtitleProviderKind.OPENSUBTITLES, SubtitleProviderCredentials.OpenSubtitles()),
                ),
                seerr = SeerrSecrets(apiKey = "k"),
                servers = listOf(
                    ServerEntrySecret("s1", "One", "https://one"),
                    ServerEntrySecret("s2", "Two", "https://two"),
                ),
            ),
        )

        assertEquals(2, summary.servers)
        assertEquals(1, summary.arrServers)
        assertEquals(2, summary.subtitleProviders)
        assertTrue(summary.hasSeerr)
    }

    // ------------------------------------------------------------ apply

    @Test
    fun `apply merges arr servers by id and keeps locally-configured others`() = runTest {
        val local = ArrServerConfig(id = "local-1", baseUrl = "https://local", apiKey = "local-key", name = "Local", kind = ArrServiceKind.RADARR, isManual = true)
        val incoming = ArrServerConfig(id = "incoming-1", baseUrl = "https://incoming", apiKey = "incoming-key", name = "Incoming", kind = ArrServiceKind.SONARR, isManual = true)
        val replacing = ArrServerConfig(id = "local-1", baseUrl = "https://replaced", apiKey = "new-key", name = "Local", kind = ArrServiceKind.RADARR, isManual = true)
        every { arrStore.getManualServers() } returns listOf(local)
        coEvery { serverMetadataStore.restoreServerMetadata(any()) } returns 0

        val result = assembler.apply(
            BackupSecrets(
                arrServers = listOf(incoming.toSecret(), replacing.toSecret()),
            ),
        )

        assertEquals(2, result.arrServersApplied)
        val written = slot<List<ArrServerConfig>>()
        verify(exactly = 1) { arrStore.setManualServers(capture(written)) }
        val merged = written.captured
        assertEquals(2, merged.size, "local-1 is replaced by the incoming entry, incoming-1 added alongside")
        assertTrue(merged.none { it.apiKey == "local-key" }, "the same-id local entry is overwritten")
        assertTrue(merged.any { it.id == "incoming-1" })
    }

    @Test
    fun `apply writes subtitle credentials per kind and seerr partially`() = runTest {
        coEvery { serverMetadataStore.restoreServerMetadata(any()) } returns 0

        val result = assembler.apply(
            BackupSecrets(
                subtitleCredentials = listOf(
                    SubtitleCredentialEntry(SubtitleProviderKind.WYZIE, SubtitleProviderCredentials.Wyzie("w-key")),
                ),
                seerr = SeerrSecrets(apiKey = "seerr-key", password = null, sessionCookie = null),
            ),
        )

        assertEquals(1, result.subtitleProvidersApplied)
        verify(exactly = 1) { subtitleStore.setCredentials(SubtitleProviderKind.WYZIE, SubtitleProviderCredentials.Wyzie("w-key")) }
        verify(exactly = 1) { seerrStore.setApiKey("seerr-key") }
        verify(exactly = 0) { seerrStore.setPassword(any()) }
        verify(exactly = 0) { seerrStore.setSessionCookie(any()) }
        assertTrue(result.seerrApplied)
    }

    @Test
    fun `apply with no seerr block touches the seerr store`() = runTest {
        coEvery { serverMetadataStore.restoreServerMetadata(any()) } returns 0

        val result = assembler.apply(BackupSecrets(servers = listOf(ServerEntrySecret("s", "N", "https://n"))))

        assertFalse(result.seerrApplied)
        verify(exactly = 0) { seerrStore.setApiKey(any()) }
        verify(exactly = 0) { seerrStore.setPassword(any()) }
        verify(exactly = 0) { seerrStore.setSessionCookie(any()) }
    }

    @Test
    fun `apply fans the server list through the narrow seam and reports inserts`() = runTest {
        coEvery { serverMetadataStore.restoreServerMetadata(any()) } returns 3

        val result = assembler.apply(
            BackupSecrets(
                servers = listOf(
                    ServerEntrySecret("s-1", "One", "https://one", alternateAddresses = listOf("https://alt"), userNames = listOf("alice")),
                ),
            ),
        )

        assertEquals(3, result.serversRestored)
        val captured = slot<List<ServerBackupMetadata>>()
        coVerify(exactly = 1) { serverMetadataStore.restoreServerMetadata(capture(captured)) }
        val restored = captured.captured.single()
        assertEquals("s-1", restored.id)
        assertEquals("https://one", restored.address)
        assertEquals(listOf("https://alt"), restored.alternateAddresses)
        assertEquals(listOf("alice"), restored.userNames)
    }

    @Test
    fun `apply with an empty payload touches nothing`() = runTest {
        val result = assembler.apply(BackupSecrets())

        assertEquals(0, result.arrServersApplied)
        assertEquals(0, result.subtitleProvidersApplied)
        assertFalse(result.seerrApplied)
        assertEquals(0, result.serversRestored)
        verify(exactly = 0) { arrStore.setManualServers(any()) }
        verify(exactly = 0) { subtitleStore.setCredentials(any(), any()) }
        coVerify(exactly = 0) { serverMetadataStore.restoreServerMetadata(any()) }
    }
}
