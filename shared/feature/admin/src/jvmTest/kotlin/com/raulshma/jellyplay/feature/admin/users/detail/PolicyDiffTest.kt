package com.raulshma.jellyplay.feature.admin.users.detail

import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.SyncPlayAccessOption
import kotlin.test.assertEquals
import kotlin.test.Test

class PolicyDiffTest {
    private val base = ManagedUserPolicy()

    @Test
    fun `untouched policy has zero changes per tab`() {
        assertEquals(0, PolicyDiff.changedCount(base, base, PolicyDiff.PROFILE_FIELDS))
        assertEquals(0, PolicyDiff.changedCount(base, base, PolicyDiff.ACCESS_FIELDS))
        assertEquals(0, PolicyDiff.changedCount(base, base, PolicyDiff.PARENTAL_FIELDS))
    }

    @Test
    fun `single profile change counts one`() {
        val edited = base.copy(enableCollectionManagement = true)
        assertEquals(1, PolicyDiff.changedCount(edited, base, PolicyDiff.PROFILE_FIELDS))
    }

    @Test
    fun `reverted change counts zero`() {
        val edited = base
            .copy(syncPlayAccess = SyncPlayAccessOption.NONE)
            .copy(syncPlayAccess = base.syncPlayAccess)
        assertEquals(0, PolicyDiff.changedCount(edited, base, PolicyDiff.PROFILE_FIELDS))
    }

    @Test
    fun `multiple distinct changes count N`() {
        val edited = base.copy(
            enableSharedDeviceControl = true,
            forceRemoteSourceTranscoding = true,
            remoteClientBitrateLimit = 5_000_000,
        )
        assertEquals(3, PolicyDiff.changedCount(edited, base, PolicyDiff.PROFILE_FIELDS))
    }

    @Test
    fun `access list change counts one`() {
        val edited = base.copy(enabledDevices = listOf("d1"))
        assertEquals(1, PolicyDiff.changedCount(edited, base, PolicyDiff.ACCESS_FIELDS))
    }

    @Test
    fun `parental rating change counts under parental only`() {
        val edited = base.copy(maxParentalRating = 100)
        // must NOT double-count into profile
        assertEquals(0, PolicyDiff.changedCount(edited, base, PolicyDiff.PROFILE_FIELDS))
        assertEquals(1, PolicyDiff.changedCount(edited, base, PolicyDiff.PARENTAL_FIELDS))
    }

    @Test
    fun `null edited or original yields zero`() {
        assertEquals(0, PolicyDiff.changedCount(null, base, PolicyDiff.ACCESS_FIELDS))
        assertEquals(0, PolicyDiff.changedCount(base, null, PolicyDiff.ACCESS_FIELDS))
    }
}
