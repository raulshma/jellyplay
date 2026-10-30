package com.raulshma.jellyplay.core.network.api

/**
 * The user-data-writes family seam of [LibraryApiClient]: the ONE write funnel
 * ([UserDataWrite] — the parameterized fold of the four former write verbs)
 * plus the [com.raulshma.jellyplay.core.network.library.FavoriteFlagCache]
 * read-flip state it owns — a WRITE family with cache state, not
 * library-browse taxonomy (the carve that emptied the oddball trio off the
 * wide client; same one-family-one-adapter shape as [PlaylistApiClient] /
 * [CollectionApiClient]).
 *
 * The funnel's members live in exactly one place: [UserDataApiClientImpl]
 * (its own single over the SAME [JellyfinApiEngine], constructed in
 * networkJvmModule), and single-family consumers inject this seam, never the
 * wide library surface: the outbox drain's replay funnel
 * (PlaybackRepositoryImpl), the flip funnel (PlayedStateSyncImpl) and the
 * favorite-cache pins all ride this family directly — the former
 * [LibraryApiClient] one-line forward is gone (the carve's completion).
 */
interface UserDataApiClient {
    /**
     * The ONE user-data write member. The [UserDataWriteOutcome] carries the
     * post-toggle favorite state ([UserDataWriteOutcome.FavoriteNow]) so the
     * single member serves the flip and the replay funnels alike.
     */
    suspend fun writeUserData(write: UserDataWrite): Result<UserDataWriteOutcome>
}
