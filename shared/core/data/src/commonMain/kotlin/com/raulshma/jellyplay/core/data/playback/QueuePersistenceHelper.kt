package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.database.dao.AudioQueueDao
import com.raulshma.jellyplay.core.database.entity.AudioQueueEntity
import com.raulshma.jellyplay.core.database.entity.AudioQueueStateEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge

class QueuePersistenceHelper(
    private val audioQueueDao: AudioQueueDao,
) {
    private object Persist {
        const val POSITION_INTERVAL_MS = 15_000L
    }

    suspend fun loadQueue(): List<AudioQueueItem> {
        return audioQueueDao.getQueue().map { it.toDomain() }
    }

    suspend fun loadState(): AudioQueueStateEntity? {
        return audioQueueDao.getState()
    }

    @OptIn(FlowPreview::class)
    fun observeQueue(
        scope: CoroutineScope,
        queue: MutableStateFlow<List<AudioQueueItem>>,
        currentIndex: MutableStateFlow<Int>,
        currentPositionMs: MutableStateFlow<Long>,
        isPlaying: MutableStateFlow<Boolean>,
        repeatMode: MutableStateFlow<Int>,
        shuffleEnabled: MutableStateFlow<Boolean>,
        playbackSpeed: MutableStateFlow<Float>,
    ) {
        queue.drop(1)
            .distinctUntilChanged()
            .onEach { items -> persistQueue(items) }
            .launchIn(scope)

        merge(
            currentIndex.drop(1).distinctUntilChanged(),
            isPlaying.drop(1).distinctUntilChanged(),
            repeatMode.drop(1).distinctUntilChanged(),
            shuffleEnabled.drop(1).distinctUntilChanged(),
            playbackSpeed.drop(1).distinctUntilChanged(),
        )
            .onEach {
                persistState(
                    currentIndex = currentIndex.value,
                    currentPositionMs = currentPositionMs.value,
                    isPlaying = isPlaying.value,
                    repeatMode = repeatMode.value,
                    shuffleEnabled = shuffleEnabled.value,
                    playbackSpeed = playbackSpeed.value,
                )
            }
            .launchIn(scope)

        // Playback position updates ~4×/s; persisting every tick causes
        // excessive Room I/O and flash wear. Throttle to one write per
        // POSITION_PERSIST_INTERVAL_MS. A crash loses at most this much
        // position — also covered by the 10 s server-side progress reporting.
        currentPositionMs.drop(1).distinctUntilChanged()
            .sample(Persist.POSITION_INTERVAL_MS)
            .onEach {
                persistState(
                    currentIndex = currentIndex.value,
                    currentPositionMs = currentPositionMs.value,
                    isPlaying = isPlaying.value,
                    repeatMode = repeatMode.value,
                    shuffleEnabled = shuffleEnabled.value,
                    playbackSpeed = playbackSpeed.value,
                )
            }
            .launchIn(scope)
    }

    private suspend fun persistQueue(items: List<AudioQueueItem>) {
        if (items.isEmpty()) {
            audioQueueDao.clearQueue()
            return
        }
        val entities = items.mapIndexed { index, item ->
            AudioQueueEntity(
                id = item.id,
                position = index,
                name = item.name,
                artist = item.artist,
                album = item.album,
                imageUrl = item.imageUrl,
                mediaSourceId = item.mediaSourceId,
                durationMs = item.durationMs,
                normalizationGain = item.normalizationGain,
            )
        }
        audioQueueDao.replaceQueue(entities)
    }

    private suspend fun persistState(
        currentIndex: Int,
        currentPositionMs: Long,
        isPlaying: Boolean,
        repeatMode: Int,
        shuffleEnabled: Boolean,
        playbackSpeed: Float,
    ) {
        audioQueueDao.saveState(
            AudioQueueStateEntity(
                id = 1,
                currentIndex = currentIndex,
                currentPositionMs = currentPositionMs,
                isPlaying = isPlaying,
                repeatMode = repeatMode,
                shuffleEnabled = shuffleEnabled,
                playbackSpeed = playbackSpeed,
            )
        )
    }

    private fun AudioQueueEntity.toDomain(): AudioQueueItem {
        return AudioQueueItem(
            id = id,
            name = name,
            artist = artist.orEmpty(),
            album = album,
            imageUrl = imageUrl,
            mediaSourceId = mediaSourceId,
            durationMs = durationMs,
            normalizationGain = normalizationGain,
        )
    }
}
