package com.raulshma.jellyplay.feature.admin.users.detail

import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import kotlin.reflect.KProperty1

enum class UserEditTab { PROFILE, ACCESS, PARENTAL, ACCOUNT }

/**
 * Computes per-tab unsaved-change counts by diffing the edited policy
 * against the original across each tab's field set. Value-based, so
 * toggling a field on-then-off correctly yields 0.
 *
 * Fields belong to exactly one tab — e.g. [ManagedUserPolicy.maxParentalRating]
 * / [ManagedUserPolicy.maxParentalSubRating] are counted under PARENTAL only,
 * not PROFILE, to avoid double-counting.
 */
object PolicyDiff {
    val PROFILE_FIELDS: List<KProperty1<ManagedUserPolicy, *>> = listOf(
        ManagedUserPolicy::isAdministrator,
        ManagedUserPolicy::isHidden,
        ManagedUserPolicy::isDisabled,
        ManagedUserPolicy::enableUserPreferenceAccess,
        ManagedUserPolicy::enableCollectionManagement,
        ManagedUserPolicy::enableSubtitleManagement,
        ManagedUserPolicy::forceRemoteSourceTranscoding,
        ManagedUserPolicy::enableSharedDeviceControl,
        ManagedUserPolicy::enableRemoteAccess,
        ManagedUserPolicy::remoteClientBitrateLimit,
        ManagedUserPolicy::syncPlayAccess,
        ManagedUserPolicy::maxActiveSessions,
        ManagedUserPolicy::loginAttemptsBeforeLockout,
    )
    val ACCESS_FIELDS: List<KProperty1<ManagedUserPolicy, *>> = listOf(
        ManagedUserPolicy::enableAllFolders, ManagedUserPolicy::enabledFolders,
        ManagedUserPolicy::enableAllChannels, ManagedUserPolicy::enabledChannels,
        ManagedUserPolicy::enableAllDevices, ManagedUserPolicy::enabledDevices,
        ManagedUserPolicy::enableContentDeletion,
        ManagedUserPolicy::enableContentDeletionFromFolders,
    )
    val PARENTAL_FIELDS: List<KProperty1<ManagedUserPolicy, *>> = listOf(
        ManagedUserPolicy::maxParentalRating, ManagedUserPolicy::maxParentalSubRating,
        ManagedUserPolicy::blockUnratedItems, ManagedUserPolicy::allowedTags,
        ManagedUserPolicy::blockedTags, ManagedUserPolicy::accessSchedules,
    )

    fun changedCount(
        edited: ManagedUserPolicy?,
        original: ManagedUserPolicy?,
        fields: List<KProperty1<ManagedUserPolicy, *>>,
    ): Int {
        if (edited == null || original == null) return 0
        return fields.count { it.get(edited) != it.get(original) }
    }
}
