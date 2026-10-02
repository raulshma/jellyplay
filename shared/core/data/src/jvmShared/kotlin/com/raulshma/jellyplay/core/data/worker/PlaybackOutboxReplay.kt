package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry

/**
 * The narrow collaborator the former [com.raulshma.jellyplay.core.data.repository.PlaybackRepository.replayOutboxEntry]
 * member was retired into. Its only production caller is the outbox drain loop
 * ([PlaybackOutboxDrainerImpl]), which now takes this port instead of the wide
 * playback surface — the drainer cannot replay a report it was never handed a
 * surface to reach, and the playback interface stops paying the member across
 * every player/detail/insight consumer that never calls it.
 *
 * Koin-bound to the same [com.raulshma.jellyplay.core.data.repository.PlaybackRepositoryImpl]
 * single as [com.raulshma.jellyplay.core.data.repository.PlaybackRepository]
 * (the dispatch body is unchanged — the single home for the entry-type →
 * API-call mapping so capture and drain can't drift apart).
 */
interface PlaybackOutboxReplay {
    suspend fun replayOutboxEntry(entry: PlaybackOutboxEntry): Boolean
}
