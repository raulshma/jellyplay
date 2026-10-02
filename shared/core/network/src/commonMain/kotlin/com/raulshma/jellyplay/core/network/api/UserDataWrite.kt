package com.raulshma.jellyplay.core.network.api

/**
 * One user-data write intent — the parameterized fold of the four former
 * [LibraryApiClient] write verbs ([MarkPlayed] / [MarkUnplayed] /
 * [ToggleFavorite] / [SetFavorite]). LibraryApiClient carried all four as
 * separate members although the caller census is two data-layer funnels
 * (the played-state flip and the outbox replay), each already holding a
 * when/boolean over the same four cases; the fold gives both ONE member and
 * keeps the verb vocabulary explicit at the type level.
 */
sealed interface UserDataWrite {
    /** `POST /UserPlayedItems/{itemId}` — mark watched (resets the resume position). */
    data class MarkPlayed(val itemId: String) : UserDataWrite

    /** `DELETE /UserPlayedItems/{itemId}` — mark unwatched. */
    data class MarkUnplayed(val itemId: String) : UserDataWrite

    /**
     * Reads-and-flips the favorite state. [currentIsFavorite] seeds the flip —
     * null lets the client resolve it (one extra GET on a cold favorite-flag
     * cache); a known value saves the round-trip and the cache.
     */
    data class ToggleFavorite(val itemId: String, val currentIsFavorite: Boolean? = null) : UserDataWrite

    /**
     * Sets an absolute favorite state (deterministic — unlike [ToggleFavorite],
     * suitable for outbox replay where the staged target must land verbatim).
     */
    data class SetFavorite(val itemId: String, val isFavorite: Boolean) : UserDataWrite
}

/**
 * The outcome of a [UserDataWrite]: [Done] for the state writes,
 * [FavoriteNow] for a toggle (the authoritative post-flip state the caller
 * mirrors into the offline row). One return type so the single member stays
 * single.
 */
sealed interface UserDataWriteOutcome {
    data object Done : UserDataWriteOutcome

    /** The favorite state AFTER the toggle landed. */
    data class FavoriteNow(val isFavorite: Boolean) : UserDataWriteOutcome
}
