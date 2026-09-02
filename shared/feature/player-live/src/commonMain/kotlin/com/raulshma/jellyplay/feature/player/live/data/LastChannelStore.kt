package com.raulshma.jellyplay.feature.player.live.data

import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper over [AppRuntimeStateStore] for the last-watched live channel.
 * Used by [com.raulshma.jellyplay.feature.player.live.LiveTvPlayerViewModel]
 * to reopen the player on the same channel next launch.
 *
 * (The legacy `@Singleton @Inject` annotations were stripped at the
 * player-live conveyor move — Koin owns the single in `playerLiveModule`.)
 */
class LastChannelStore(
    private val prefs: AppRuntimeStateStore,
) {
    fun observeLastChannelId(): Flow<String?> = prefs.observeLiveTvLastChannelId()

    suspend fun setLastChannelId(channelId: String?) =
        prefs.setLiveTvLastChannelId(channelId)
}
