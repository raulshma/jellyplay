package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.PlaybackOutboxDao
import com.raulshma.jellyplay.core.database.entity.PlaybackOutboxEntity
import com.raulshma.jellyplay.core.model.PlayMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackOutboxRepositoryImpl @Inject constructor(
    private val dao: PlaybackOutboxDao,
) : PlaybackOutboxRepository {

    // Serialises the read-modify-write coalescence so concurrent PROGRESS
    // reports from the reporter loop and a STOP from release do not interleave.
    private val mutex = Mutex()

    private suspend fun nowMillis(): Long = withContext(Dispatchers.IO) { System.currentTimeMillis() }

    override suspend fun enqueueStart(
        itemId: String,
        sessionId: String,
        playMethod: PlayMethod,
        startPositionTicks: Long?,
    ) = withContext(Dispatchers.IO) {
        val now = nowMillis()
        dao.upsert(
            PlaybackOutboxEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                eventType = PlaybackOutboxEventType.START.name,
                sessionId = sessionId,
                positionTicks = startPositionTicks ?: 0L,
                isPaused = false,
                playMethod = playMethod.name,
                mediaSourceId = null,
                recordedAt = now,
                createdAt = now,
            )
        )
    }

    override suspend fun enqueueProgress(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
        playMethod: PlayMethod,
        mediaSourceId: String?,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = nowMillis()
            val existing = dao.getForItem(itemId).firstOrNull {
                it.eventType == PlaybackOutboxEventType.PROGRESS.name
            }
            if (existing != null) {
                // Coalesce: a newer PROGRESS supersedes the older one for this
                // item. Reuse the id so REPLACE lands in place; bump createdAt
                // so the entry keeps its drain ordering at the new capture time.
                dao.upsert(
                    existing.copy(
                        sessionId = sessionId,
                        positionTicks = positionTicks,
                        isPaused = isPaused,
                        playMethod = playMethod.name,
                        mediaSourceId = mediaSourceId,
                        recordedAt = now,
                        createdAt = now,
                    )
                )
            } else {
                dao.upsert(
                    PlaybackOutboxEntity(
                        id = UUID.randomUUID().toString(),
                        itemId = itemId,
                        eventType = PlaybackOutboxEventType.PROGRESS.name,
                        sessionId = sessionId,
                        positionTicks = positionTicks,
                        isPaused = isPaused,
                        playMethod = playMethod.name,
                        mediaSourceId = mediaSourceId,
                        recordedAt = now,
                        createdAt = now,
                    )
                )
            }
        }
    }

    override suspend fun enqueueStop(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
    ) = withContext(Dispatchers.IO) {
        val now = nowMillis()
        dao.upsert(
            PlaybackOutboxEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                eventType = PlaybackOutboxEventType.STOP.name,
                sessionId = sessionId,
                positionTicks = positionTicks,
                isPaused = false,
                // The Jellyfin PlaybackStopInfo payload does not carry a play
                // method, so the worker's STOP replay path ignores this field.
                // The entity column is non-null, so we stamp a placeholder —
                // it is never read back for STOP events.
                playMethod = PlayMethod.DIRECT_PLAY.name,
                mediaSourceId = null,
                recordedAt = now,
                createdAt = now,
            )
        )
    }

    override suspend fun enqueuePlayedState(itemId: String, isPlayed: Boolean) = withContext(Dispatchers.IO) {
        // Deterministic id so a re-flip for the same item lands in place — the
        // latest user intent wins and there is never more than one row per
        // item for the played-state channel. `positionTicks`/`isPaused`/
        // `playMethod` are unused for this event type but the entity requires
        // them; defaults match STOP's shape.
        val now = nowMillis()
        dao.upsert(
            PlaybackOutboxEntity(
                id = "played_state:$itemId",
                itemId = itemId,
                eventType = if (isPlayed) PlaybackOutboxEventType.PLAYED.name else PlaybackOutboxEventType.UNPLAYED.name,
                sessionId = "",
                positionTicks = 0L,
                isPaused = false,
                playMethod = PlayMethod.DIRECT_PLAY.name,
                mediaSourceId = null,
                recordedAt = now,
                // Preserve original createdAt on an overwrite so drain ordering
                // keeps the first flip's position — only the target state
                // changes, not the queue position. Fresh on first insert.
                createdAt = dao.getForItem(itemId)
                    .firstOrNull { it.id == "played_state:$itemId" }
                    ?.createdAt ?: now,
            )
        )
    }

    override suspend fun drain(): List<PlaybackOutboxEntry> = withContext(Dispatchers.IO) {
        dao.getAll().map { it.toDomain() }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    override suspend fun deleteForItem(itemId: String) = withContext(Dispatchers.IO) {
        dao.deleteForItem(itemId)
    }

    override suspend fun deletePlaybackTelemetryForItem(itemId: String) = withContext(Dispatchers.IO) {
        dao.deletePlaybackTelemetryForItem(itemId)
    }

    override suspend fun count(): Int = withContext(Dispatchers.IO) { dao.count() }

    override fun countFlow(): Flow<Int> = dao.countFlow()

    override fun getAllFlow(): Flow<List<PlaybackOutboxEntry>> =
        dao.getAllFlow().map { list -> list.map { it.toDomain() } }

    private fun PlaybackOutboxEntity.toDomain(): PlaybackOutboxEntry =
        PlaybackOutboxEntry(
            id = id,
            itemId = itemId,
            eventType = PlaybackOutboxEventType.valueOf(eventType),
            sessionId = sessionId,
            positionTicks = positionTicks,
            isPaused = isPaused,
            playMethod = runCatching { PlayMethod.valueOf(playMethod) }.getOrDefault(PlayMethod.DIRECT_PLAY),
            mediaSourceId = mediaSourceId,
            recordedAt = recordedAt,
            createdAt = createdAt,
        )
}
