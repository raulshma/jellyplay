package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import org.jellyfin.sdk.model.api.SyncPlayUserAccessType
import org.jellyfin.sdk.model.api.UserPolicy
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDtoMappingTest {

    private fun fullServerPolicy(
        isAdministrator: Boolean = true,
        blockedTags: List<String>? = listOf("secret"),
        authProviderId: String = "Default",
        syncPlayAccess: SyncPlayUserAccessType = SyncPlayUserAccessType.CREATE_AND_JOIN_GROUPS,
        enableSubtitleManagement: Boolean = true,
    ) = UserPolicy(
        isAdministrator = isAdministrator,
        isHidden = false,
        isDisabled = false,
        enableUserPreferenceAccess = true,
        enableRemoteControlOfOtherUsers = false,
        enableSharedDeviceControl = false,
        enableRemoteAccess = true,
        enableLiveTvManagement = false,
        enableLiveTvAccess = true,
        enableMediaPlayback = true,
        enableAudioPlaybackTranscoding = true,
        enableVideoPlaybackTranscoding = true,
        enablePlaybackRemuxing = true,
        forceRemoteSourceTranscoding = false,
        enableContentDeletion = false,
        enableContentDownloading = true,
        enableSyncTranscoding = true,
        enableMediaConversion = false,
        enableAllDevices = true,
        enableAllChannels = true,
        enableAllFolders = true,
        invalidLoginAttemptCount = 0,
        loginAttemptsBeforeLockout = -1,
        maxActiveSessions = 0,
        enablePublicSharing = false,
        remoteClientBitrateLimit = 0,
        authenticationProviderId = authProviderId,
        passwordResetProviderId = "Default",
        syncPlayAccess = syncPlayAccess,
        blockedTags = blockedTags,
        enableSubtitleManagement = enableSubtitleManagement,
    )

    @Test
    fun `toManagedPolicy maps editable fields`() {
        val serverPolicy = fullServerPolicy(isAdministrator = true).copy(maxActiveSessions = 3)
        val managed = serverPolicy.toManagedPolicy()

        assertTrue(managed.isAdministrator)
        assertEquals(3, managed.maxActiveSessions)
        assertEquals(0, managed.enabledFolders.size) // null server list -> empty
    }

    @Test
    fun `toManagedPolicy maps enabledFolders UUIDs to strings`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val serverPolicy = fullServerPolicy().copy(
            enableAllFolders = false,
            enabledFolders = listOf(id1, id2),
        )

        val managed = serverPolicy.toManagedPolicy()

        assertFalse(managed.enableAllFolders)
        assertEquals(listOf(id1.toString(), id2.toString()), managed.enabledFolders)
    }

    @Test
    fun `overlayWith writes only the 19 edited fields and retains bookkeeping`() {
        val serverPolicy = fullServerPolicy(
            isAdministrator = false,
            blockedTags = listOf("secret"),
            authProviderId = "keep-me",
            syncPlayAccess = SyncPlayUserAccessType.CREATE_AND_JOIN_GROUPS,
            enableSubtitleManagement = true,
        )
        val edited = ManagedUserPolicy(
            isAdministrator = true, // flipped
            maxActiveSessions = 5,  // changed
            // everything else default
        )

        val merged = serverPolicy.overlayWith(edited)

        // edited fields applied
        assertTrue(merged.isAdministrator)
        assertEquals(5, merged.maxActiveSessions)
        // bookkeeping fields retained verbatim (the whole point of overlayWith)
        assertEquals(listOf("secret"), merged.blockedTags)
        assertEquals("keep-me", merged.authenticationProviderId)
        assertEquals(SyncPlayUserAccessType.CREATE_AND_JOIN_GROUPS, merged.syncPlayAccess)
        assertTrue(merged.enableSubtitleManagement)
        assertEquals("Default", merged.passwordResetProviderId)
        assertTrue(merged.enableAllFolders)
    }

    @Test
    fun `overlayWith converts enabledFolders strings back to UUIDs`() {
        val id1 = UUID.randomUUID()
        val serverPolicy = fullServerPolicy().copy(enableAllFolders = false)
        val edited = ManagedUserPolicy(
            enableAllFolders = false,
            enabledFolders = listOf(id1.toString()),
        )

        val merged = serverPolicy.overlayWith(edited)

        assertFalse(merged.enableAllFolders)
        assertEquals(listOf(id1), merged.enabledFolders)
    }

    @Test
    fun `overlayWith null maxParentalRating writes through`() {
        val serverPolicy = fullServerPolicy().copy(maxParentalRating = 100)
        val edited = ManagedUserPolicy(maxParentalRating = null)

        val merged = serverPolicy.overlayWith(edited)

        assertNull(merged.maxParentalRating)
    }
}
