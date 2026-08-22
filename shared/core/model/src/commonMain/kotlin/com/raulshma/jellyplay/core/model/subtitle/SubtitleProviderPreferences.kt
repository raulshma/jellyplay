package com.raulshma.jellyplay.core.model.subtitle

import androidx.compose.runtime.Immutable

/**
 * Non-secret subtitle-provider preferences. The external provider enable
 * toggles live here; their secrets live in
 * [com.raulshma.jellyplay.core.datastore.SubtitleProviderSecureCredentialsStore].
 * A provider is *configured* (searchable) only when enabled here AND its
 * credentials are non-blank — see
 * [com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepositoryImpl.configuredProviders].
 *
 * [JELLYFIN] is always enabled (it has no credentials) and is intentionally
 * absent from the user-facing toggle list.
 */
@Immutable
data class SubtitleProviderPreferences(
    val wyzieEnabled: Boolean = false,
    val openSubtitlesEnabled: Boolean = false,
) {
    /** True when the user has opted in to [kind]. JELLYFIN is always on. */
    fun isEnabled(kind: SubtitleProviderKind): Boolean = when (kind) {
        SubtitleProviderKind.JELLYFIN -> true
        SubtitleProviderKind.WYZIE -> wyzieEnabled
        SubtitleProviderKind.OPENSUBTITLES -> openSubtitlesEnabled
    }

    /** The user-toggleable external providers. */
    val externalProviders: List<SubtitleProviderKind>
        get() = listOf(SubtitleProviderKind.WYZIE, SubtitleProviderKind.OPENSUBTITLES)
}
