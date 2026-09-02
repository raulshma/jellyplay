package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.SyncPlayAccessOption
import com.raulshma.jellyplay.core.model.UnratedItemOption
import org.jellyfin.sdk.model.api.AccessSchedule
import org.jellyfin.sdk.model.api.DynamicDayOfWeek
import org.jellyfin.sdk.model.api.SyncPlayUserAccessType
import org.jellyfin.sdk.model.api.UnratedItem
import org.jellyfin.sdk.model.api.UserPolicy
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDtoMappingTest {

    private val testUserId = "00000000-0000-0000-0000-000000000002"

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
    fun `overlayWith applies edited fields and retains true bookkeeping`() {
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
            // everything else default: blockedTags=[], syncPlay=CREATE_AND_JOIN,
            // subtitleMgmt=false — these are now EDITABLE, so defaults overwrite server.
        )

        val merged = serverPolicy.overlayWith(edited, testUserId)

        // edited fields applied
        assertTrue(merged.isAdministrator)
        assertEquals(5, merged.maxActiveSessions)
        // syncPlay/subtitle/tags are now editable -> overwritten by edited's defaults
        assertEquals(emptyList<String>(), merged.blockedTags)
        assertEquals(SyncPlayUserAccessType.CREATE_AND_JOIN_GROUPS, merged.syncPlayAccess)
        assertFalse(merged.enableSubtitleManagement)
        // true bookkeeping fields still retained verbatim (never editable)
        assertEquals("keep-me", merged.authenticationProviderId)
        assertEquals("Default", merged.passwordResetProviderId)
        assertEquals(0, merged.invalidLoginAttemptCount)
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

        val merged = serverPolicy.overlayWith(edited, testUserId)

        assertFalse(merged.enableAllFolders)
        assertEquals(listOf(id1), merged.enabledFolders)
    }

    @Test
    fun `overlayWith null maxParentalRating writes through`() {
        val serverPolicy = fullServerPolicy().copy(maxParentalRating = 100)
        val edited = ManagedUserPolicy(maxParentalRating = null)

        val merged = serverPolicy.overlayWith(edited, testUserId)

        assertNull(merged.maxParentalRating)
    }

    @Test
    fun `managed policy round-trips all new fields`() {
        val channelId = "00000000-0000-0000-0000-000000000001"
        val sdkPolicy = fullServerPolicy().copy(
            enableCollectionManagement = true,
            enableSubtitleManagement = true,
            forceRemoteSourceTranscoding = true,
            enableSharedDeviceControl = true,
            remoteClientBitrateLimit = 8_000_000,
            syncPlayAccess = SyncPlayUserAccessType.JOIN_GROUPS,
            enableAllChannels = false,
            enabledChannels = listOf(UUID.fromString(channelId)),
            enableAllDevices = false,
            enabledDevices = listOf("dev1"),
            enableContentDeletionFromFolders = listOf("folder1"),
            maxParentalRating = 200,
            maxParentalSubRating = 1,
            blockUnratedItems = listOf(UnratedItem.MOVIE, UnratedItem.SERIES),
            allowedTags = listOf("kids"),
            blockedTags = listOf("horror"),
            accessSchedules = listOf(
                AccessSchedule(
                    id = 5,
                    userId = UUID.fromString(testUserId),
                    dayOfWeek = DynamicDayOfWeek.WEEKDAY,
                    startHour = 8.0,
                    endHour = 22.0,
                ),
            ),
        )

        val managed = sdkPolicy.toManagedPolicy()
        // SDK -> app
        assertTrue(managed.enableCollectionManagement)
        assertTrue(managed.forceRemoteSourceTranscoding)
        assertTrue(managed.enableSharedDeviceControl)
        assertEquals(8_000_000, managed.remoteClientBitrateLimit)
        assertEquals(SyncPlayAccessOption.JOIN_ONLY, managed.syncPlayAccess)
        assertFalse(managed.enableAllChannels)
        assertEquals(listOf(channelId), managed.enabledChannels)
        assertEquals(listOf("dev1"), managed.enabledDevices)
        assertEquals(listOf("folder1"), managed.enableContentDeletionFromFolders)
        assertEquals(200, managed.maxParentalRating)
        assertEquals(1, managed.maxParentalSubRating)
        assertEquals(listOf(UnratedItemOption.MOVIE, UnratedItemOption.SERIES), managed.blockUnratedItems)
        assertEquals(listOf("kids"), managed.allowedTags)
        assertEquals(listOf("horror"), managed.blockedTags)
        assertEquals(1, managed.accessSchedules.size)
        assertEquals("Weekday", managed.accessSchedules.single().dayOfWeek)
        assertEquals(8.0, managed.accessSchedules.single().startHour, 0.0)

        // app -> SDK round-trip
        val overlaid = sdkPolicy.overlayWith(managed, testUserId)
        assertEquals(8_000_000, overlaid.remoteClientBitrateLimit)
        assertEquals(SyncPlayUserAccessType.JOIN_GROUPS, overlaid.syncPlayAccess)
        assertEquals(listOf(UUID.fromString(channelId)), overlaid.enabledChannels)
        assertEquals(listOf("dev1"), overlaid.enabledDevices)
        assertEquals(listOf(UnratedItem.MOVIE, UnratedItem.SERIES), overlaid.blockUnratedItems)
        assertEquals(listOf("kids"), overlaid.allowedTags)
        assertEquals(listOf("horror"), overlaid.blockedTags)
        assertEquals(1, overlaid.accessSchedules!!.size)
        assertEquals(8.0, overlaid.accessSchedules!!.single().startHour, 0.0)
        assertEquals(DynamicDayOfWeek.WEEKDAY, overlaid.accessSchedules!!.single().dayOfWeek)
    }

    @Test
    fun `toManagedPolicy handles null list fields`() {
        val sdkPolicy = fullServerPolicy().copy(
            enabledChannels = null,
            enabledDevices = null,
            enableContentDeletionFromFolders = null,
            blockUnratedItems = null,
            allowedTags = null,
            blockedTags = null,
            accessSchedules = null,
        )
        val managed = sdkPolicy.toManagedPolicy()
        assertTrue(managed.enabledChannels.isEmpty())
        assertTrue(managed.enabledDevices.isEmpty())
        assertTrue(managed.enableContentDeletionFromFolders.isEmpty())
        assertTrue(managed.blockUnratedItems.isEmpty())
        assertTrue(managed.allowedTags.isEmpty())
        assertTrue(managed.blockedTags.isEmpty())
        assertTrue(managed.accessSchedules.isEmpty())
    }
}
