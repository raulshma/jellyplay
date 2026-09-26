package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.network.api.AuthApiClient
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins [DefaultPlaybackIdentity]: the playback session-identity reads pass
 * straight through to the [AuthApiClient] session (the engine's atomic
 * active-server/current-user state) — `null` means "no active session" on
 * both axes, and the two reads are independent (a server without a token is
 * representable during disconnect).
 */
class DefaultPlaybackIdentityTest {

    private fun identityWith(serverUrl: String?, token: String?): PlaybackIdentity {
        val apiClient = mockk<AuthApiClient>()
        every { apiClient.getServerUrl() } returns serverUrl
        every { apiClient.getAccessToken() } returns token
        return DefaultPlaybackIdentity(apiClient)
    }

    @Test
    fun `active session exposes both server url and token`() {
        val identity = identityWith(serverUrl = "http://server:8096", token = "tok")

        assertEquals("http://server:8096", identity.serverUrl())
        assertEquals("tok", identity.accessToken())
    }

    @Test
    fun `no session yields null on both reads`() {
        val identity = identityWith(serverUrl = null, token = null)

        assertNull(identity.serverUrl())
        assertNull(identity.accessToken())
    }

    @Test
    fun `a server address without a token still reads on each axis`() {
        val identity = identityWith(serverUrl = "http://server:8096", token = null)

        assertEquals("http://server:8096", identity.serverUrl())
        assertNull(identity.accessToken())
    }
}
