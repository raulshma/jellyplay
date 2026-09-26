package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.PluginConfigPage
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.api.PluginApiClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins [PluginAdminRepositoryImpl]'s two compositions (moved verbatim from
 * AdminRepositoryImpl at the admin facade split; the stateless forwards are
 * not retested here — the androidHostTest twin pins those):
 *  1. `getPluginConfigPage` maps found/missing/error cases precisely;
 *  2. `pluginWebViewSession` composes the ctor's narrow session seams — the
 *     active-address read (failover-correct) plus ONE atomic session read —
 *     and falls back to empty strings without a session.
 */
class PluginAdminRepositoryImplTest {

    private lateinit var pluginApiClient: PluginApiClient
    private lateinit var repository: PluginAdminRepositoryImpl

    // The seam inputs, as plain lambdas over local state — the Koin wiring
    // reads the same two engine members through identical lambdas.
    private val sessionServer = ServerInfo(
        id = "server-1",
        name = "Test",
        address = "https://server.example.com",
    )
    private var activeSession: ActiveSession? = ActiveSession(
        server = sessionServer,
        user = UserInfo(
            id = "11111111-1111-4111-8111-111111111111",
            name = "admin",
            serverAddress = "https://server.example.com",
            accessToken = "token-1",
            serverId = "server-1",
        ),
    )
    private var serverAddress: String? = "https://server.example.com"
    private val okHttpClient = OkHttpClient()

    @BeforeTest
    fun setup() {
        pluginApiClient = mockk()
        repository = PluginAdminRepositoryImpl(
            pluginApiClient = pluginApiClient,
            activeServerAddress = { serverAddress },
            session = { activeSession },
            okHttpClient = okHttpClient,
        )
    }

    @Test
    fun `getPluginConfigPage fetches the html for a matching page`() = runTest {
        coEvery { pluginApiClient.getConfigurationPages() } returns Result.success(
            listOf(
                PluginConfigPage(name = "Other", pluginId = "other-plugin"),
                PluginConfigPage(name = "Config", pluginId = "target-plugin"),
            ),
        )
        coEvery { pluginApiClient.getDashboardConfigurationPage("Config") } returns
            Result.success("<html>body</html>")

        val page = repository.getPluginConfigPage("target-plugin").getOrThrow()

        assertEquals("Config", page!!.name)
        assertEquals("<html>body</html>", page.html)
    }

    @Test
    fun `getPluginConfigPage succeeds with null when no page matches`() = runTest {
        coEvery { pluginApiClient.getConfigurationPages() } returns Result.success(emptyList())

        assertNull(repository.getPluginConfigPage("target-plugin").getOrThrow())
    }

    @Test
    fun `getPluginConfigPage propagates the configuration-pages failure`() = runTest {
        coEvery { pluginApiClient.getConfigurationPages() } returns Result.failure(IllegalStateException("down"))

        assertTrue(repository.getPluginConfigPage("x").isFailure)
    }

    @Test
    fun `pluginWebViewSession composes the session seams`() {
        val session = repository.pluginWebViewSession

        assertEquals("https://server.example.com", session.serverAddress)
        assertEquals("11111111-1111-4111-8111-111111111111", session.userId)
        assertEquals("token-1", session.accessToken)
    }

    @Test
    fun `pluginWebViewSession follows the failover address, not the session server's primary`() {
        // Failover pin: the router moved to an alternate while the session's
        // server still carries the (dead) primary — the bridge must target
        // the active endpoint, never the straggler primary address.
        serverAddress = "https://failover.example.com"

        val session = repository.pluginWebViewSession

        assertEquals("https://failover.example.com", session.serverAddress)
        assertEquals("https://server.example.com", activeSession!!.server.address)
    }

    @Test
    fun `pluginWebViewSession falls back to empty strings without a session`() {
        activeSession = null
        serverAddress = null

        val session = repository.pluginWebViewSession

        assertEquals("", session.serverAddress)
        assertEquals("", session.userId)
        assertEquals("", session.accessToken)
    }

    @Test
    fun `pluginWebViewSession carries the injected http client`() {
        assertSame(okHttpClient, repository.pluginWebViewSession.okHttpClient)
    }
}
