package com.raulshma.jellyplay.core.network.subtitle

import com.raulshma.jellyplay.core.model.subtitle.SubtitleFile
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult

/**
 * One external subtitle search/download source behind the multi-provider
 * fan-out repository.
 *
 * Implementations are keyed by [kind] and bound into Hilt's
 * `Map<SubtitleProviderKind, SubtitleProvider>` via `@IntoMap` +
 * [SubtitleProviderKey] in [com.raulshma.jellyplay.core.network.di.NetworkModule].
 * The Jellyfin path is **not** a [SubtitleProvider] — it is handled separately
 * by the repository via the existing `PlaybackApiClient` because its download is
 * server-side (no byte stream returned) and its search is `itemId`-scoped.
 *
 * Adding a new external provider = one new enum value, one credentials
 * subclass, one implementation of this interface, one `@IntoMap` bind, and the
 * credential-store / settings-UI wiring. The repository, fan-out, and UI pick
 * it up generically.
 *
 * Credentials are passed **per call** (never baked into the client) so the same
 * singleton instance serves a key change without reconstruction — matching the
 * Sonarr/Seerr client convention.
 */
interface SubtitleProvider {
    val kind: SubtitleProviderKind

    /**
     * Search the provider for [query]. Returns provider-native rows tagged with
     * [kind]; the repository merges/de-dupes across providers. A failure (auth,
     * quota, network) surfaces as `Result.failure` and the fan-out keeps the
     * other providers' results — one bad key never blanks the rest.
     */
    suspend fun search(
        query: SubtitleQuery,
        credentials: SubtitleProviderCredentials,
    ): Result<List<SubtitleSearchResult>>

    /**
     * Download the bytes for [result] (previously returned by [search]).
     * Implementations resolve the file URL their own way (Wyzie's inline `url`,
     * OpenSubtitles' `POST /download` handshake) and return the raw bytes + a
     * guessed file name + format for MIME mapping.
     */
    suspend fun download(
        result: SubtitleSearchResult,
        credentials: SubtitleProviderCredentials,
    ): Result<SubtitleFile>
}
