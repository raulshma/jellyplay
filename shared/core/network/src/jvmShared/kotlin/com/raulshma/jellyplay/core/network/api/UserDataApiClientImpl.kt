package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.network.library.FavoriteFlagCache
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.extensions.*

/**
 * The [UserDataApiClient] family adapter: the user-data write funnel (mark /
 * unmark / toggle / set favorite) moved verbatim off [LibraryApiClientImpl],
 * bringing its [FavoriteFlagCache] state with it — the adapter owns the cache
 * that seeds a toggle's "current" flag, so the seed-then-toggle pins live
 * HERE (LibraryApiClientImplTest drives this adapter directly). Like its
 * playlist/collection
 * siblings it closes over nothing but the SAME [JellyfinApiEngine] single the
 * wide client uses, plus the cache-identity lambda (the wide client passes
 * its own atomic-session snapshot fn — ONE identity read, two consumers).
 */
class UserDataApiClientImpl(
    private val engine: JellyfinApiEngine,
    /** The atomic (server, user) cache identity the favorite flags key off. */
    private val cacheIdentity: () -> CacheIdentity,
) : UserDataApiClient {

    /**
     * The shared commonMain favorite-flag cache-aside choreography
     * ([FavoriteFlagCache]) — the family's one stateful member.
     */
    private val favoriteFlags = FavoriteFlagCache(cacheIdentity)

    /**
     * The ONE user-data write funnel: each op keeps its exact former
     * transport — mark/unmark ride [JellyfinApiEngine.withApi], the toggle
     * rides the retry chassis + the favorite-flag cache. The
     * [UserDataWriteOutcome] carries the post-toggle favorite state; state
     * writes answer [UserDataWriteOutcome.Done].
     */
    override suspend fun writeUserData(write: UserDataWrite): Result<UserDataWriteOutcome> = when (write) {
        is UserDataWrite.MarkPlayed -> engine.withApi { api ->
            val userId = engine.requireUserId()
            api.playStateApi.markPlayedItem(
                userId = userId.toUUID(),
                itemId = write.itemId.toUUID(),
            )
            UserDataWriteOutcome.Done
        }

        is UserDataWrite.MarkUnplayed -> engine.withApi { api ->
            val userId = engine.requireUserId()
            api.playStateApi.markUnplayedItem(
                userId = userId.toUUID(),
                itemId = write.itemId.toUUID(),
            )
            UserDataWriteOutcome.Done
        }

        is UserDataWrite.ToggleFavorite -> engine.apiResultWithRetry {
            val userId = engine.requireUserId()
            val uuid = write.itemId.toUUID()
            val toggled = favoriteFlags.toggle(
                cacheKey = uuid.toString(),
                currentIsFavorite = write.currentIsFavorite,
                fetchCurrent = {
                    engine.requireApi().userLibraryApi.getItem(itemId = uuid).content.userData?.isFavorite == true
                },
                markOnServer = {
                    engine.requireApi().userLibraryApi.markFavoriteItem(
                        userId = userId.toUUID(),
                        itemId = uuid,
                    )
                },
                unmarkOnServer = {
                    engine.requireApi().userLibraryApi.unmarkFavoriteItem(
                        userId = userId.toUUID(),
                        itemId = uuid,
                    )
                },
            )
            UserDataWriteOutcome.FavoriteNow(toggled)
        }

        is UserDataWrite.SetFavorite -> engine.withApi { api ->
            val userId = engine.requireUserId()
            val uuid = write.itemId.toUUID()
            if (write.isFavorite) {
                api.userLibraryApi.markFavoriteItem(
                    userId = userId.toUUID(),
                    itemId = uuid,
                )
            } else {
                api.userLibraryApi.unmarkFavoriteItem(
                    userId = userId.toUUID(),
                    itemId = uuid,
                )
            }
            favoriteFlags.put(uuid.toString(), write.isFavorite)
            UserDataWriteOutcome.Done
        }
    }
}
