package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.database.dao.AudioQueueDao
import com.raulshma.jellyplay.core.database.entity.AudioQueueEntity
import com.raulshma.jellyplay.core.database.entity.AudioQueueStateEntity
import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.PlaybackInfoResult
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hand-rolled collaborators for the desktop audio queue tests (this module's
 * test source set carries no mocking library). Internal top-level so both the
 * semantics suite and the real-engine suite share them.
 */

/** Scriptable per-item resolution; mirrors what [DesktopAudioSourceResolver] returns. */
internal class FakeResolver : AudioTrackResolver {
    val tracks = mutableMapOf<String, ResolvedAudioTrack>()
    val unresolved = mutableSetOf<String>()
    val resolveCalls = mutableListOf<Pair<String, Long>>()

    override suspend fun resolve(itemId: String, startPositionMs: Long): ResolvedAudioTrack? {
        resolveCalls += itemId to startPositionMs
        if (itemId in unresolved) return null
        return tracks[itemId]
    }

    fun seed(vararg itemIds: String) {
        itemIds.forEach { tracks[it] = resolvedTrack(it) }
    }
}

internal class FakeImages : ImageUrlProvider {
    override fun getImageUrl(itemId: String, maxWidth: Int?): String =
        "img://$itemId"
    override fun getChapterImageUrl(itemId: String, imageIndex: Int, tag: String?): String = ""
    override fun getBackdropUrl(itemId: String, maxWidth: Int): String = ""
}

internal data class StartRecord(
    val itemId: String,
    val sessionId: String,
    val mediaSourceId: String?,
    val startPositionTicks: Long?,
)

internal class FakePlaybackRepository : PlaybackRepository {
    val starts = CopyOnWriteArrayList<StartRecord>()
    val progresses = CopyOnWriteArrayList<PlaybackProgress>()
    val stops = CopyOnWriteArrayList<Triple<String, String, Long>>()

    override suspend fun reportPlaybackStart(info: PlaybackStartInfo): Result<Unit> {
        starts += StartRecord(info.itemId, info.sessionId, info.mediaSourceId, info.startPositionTicks)
        return Result.success(Unit)
    }

    override suspend fun reportPlaybackProgress(progress: PlaybackProgress): Result<Unit> {
        progresses += progress
        return Result.success(Unit)
    }

    override suspend fun reportPlaybackStopped(itemId: String, sessionId: String, positionTicks: Long): Result<Unit> {
        stops += Triple(itemId, sessionId, positionTicks)
        return Result.success(Unit)
    }

    override suspend fun replayOutboxEntry(entry: PlaybackOutboxEntry): Boolean = true
    override fun getImageUrl(itemId: String, imageType: String, maxWidth: Int?) = "img://$itemId"
    override fun getChapterImageUrl(itemId: String, imageIndex: Int, tag: String?, maxWidth: Int?) = ""
    override fun getBackdropUrl(itemId: String, maxWidth: Int) = ""
    override suspend fun getItemImageBytes(itemId: String, imageType: String, maxWidth: Int): ByteArray? = null

    override fun getStreamUrl(itemId: String, mediaSourceId: String, startTimeTicks: Long, liveStreamId: String?) =
        "legacy"

    override suspend fun fetchPlaybackInfo(
        itemId: String, mediaSourceId: String, startTimeTicks: Long, audioStreamIndex: Int?,
        subtitleStreamIndex: Int?, maxStreamingBitrateBits: Long?, mode: PlaybackMode, playerType: PlayerType,
        liveStreamOption: LiveStreamOption?,
    ): Result<PlaybackInfoResult> = error("unexpected")

    override suspend fun resolvePlayback(
        itemId: String, mediaSourceId: String, startTimeTicks: Long, audioStreamIndex: Int?,
        subtitleStreamIndex: Int?, maxStreamingBitrateBits: Long?, mode: PlaybackMode, playerType: PlayerType,
        liveStreamOption: LiveStreamOption?,
    ): ResolvedPlayback? = null

    override fun getStreamUrl(
        itemId: String, mediaSourceId: String, startTimeTicks: Long, maxBitrate: Int?,
        useAudioEndpoint: Boolean, liveStreamId: String?,
    ) = "stream://$itemId/$mediaSourceId"

    override fun getSubtitleDeliveryUrl(deliveryUrl: String) = deliveryUrl
    override fun getServerUrl(): String? = null
    override fun getAccessToken(): String? = null
    override fun buildSubtitleDeliveryUrl(itemId: String, mediaSourceId: String, index: Int, codec: String?) = ""

    override suspend fun getIntroTimestamps(itemId: String): Result<IntroTimestamps> =
        Result.failure(IllegalStateException())

    override suspend fun getCreditTimestamps(itemId: String): Result<CreditTimestamps> =
        Result.failure(IllegalStateException())

    override suspend fun fetchActiveTranscodeReasons(itemId: String): List<String> = emptyList()
    override suspend fun getMediaSegments(itemId: String): Result<List<MediaSegment>> =
        Result.success(emptyList())

    override fun invalidateSegmentsCache(itemId: String) {}

    override suspend fun getRemoteSubtitles(itemId: String): Result<List<RemoteSubtitleInfo>> =
        Result.failure(IllegalStateException())

    override suspend fun downloadSubtitle(itemId: String, subtitleId: String): Result<Unit> =
        Result.failure(IllegalStateException())

    override suspend fun searchRemoteSubtitles(itemId: String, language: String): Result<List<RemoteSubtitleInfo>> =
        Result.failure(IllegalStateException())

    override suspend fun uploadSubtitle(
        itemId: String, data: String, fileName: String, language: String?, isForced: Boolean,
        isHearingImpaired: Boolean,
    ): Result<Unit> = Result.failure(IllegalStateException())

    override suspend fun getSubtitleCultures(itemId: String): Result<List<CultureInfo>> =
        Result.failure(IllegalStateException())

    override suspend fun getTrickplayTileImage(itemId: String, width: Int, index: Int): ByteArray? = null
}

internal class FakeLyricsRepository : LyricsRepository {
    private val fail = Result.failure<LyricsResult>(IllegalStateException("offline test"))
    override suspend fun getLyrics(itemId: String): Result<LyricsResult> = fail
    override suspend fun getLyricsWithFallback(itemId: String, artistName: String?, trackName: String?, duration: Double?): Result<LyricsResult> = fail
    override suspend fun searchLyrics(query: String): Result<List<LrcLibTrack>> =
        Result.failure(IllegalStateException("offline test"))
    override suspend fun getLyricsById(lrcLibId: Long, itemId: String): Result<LyricsResult> = fail
    override suspend fun cleanupLyricsCache() = Unit
}

/** In-memory Room DAO twin — the real QueuePersistenceHelper runs on top of it. */
internal class InMemoryQueueDao : AudioQueueDao() {
    // All mutable state is cross-thread visible: the helper's persist chain
    // runs on the manager's scope thread while tests poll from the JUnit thread
    // (a plain ArrayList here once hid committed writes behind a JMM visibility
    // lag and turned "rows persisted" polls into false 15 s timeouts).
    @Volatile
    var replaceQueueCalls = 0
        private set
    private val rows = CopyOnWriteArrayList<AudioQueueEntity>()
    val savedStates = CopyOnWriteArrayList<AudioQueueStateEntity>()
    @Volatile
    private var persistedState: AudioQueueStateEntity? = null
    private val queueObs = MutableStateFlow<List<AudioQueueEntity>>(emptyList())
    private val stateObs = MutableStateFlow<AudioQueueStateEntity?>(null)

    /** Position-ordered snapshot of the current queue rows. */
    val rowsSnapshot: List<AudioQueueEntity> get() = rows.sortedBy { it.position }

    override fun observeQueue(): Flow<List<AudioQueueEntity>> = queueObs.asStateFlow()
    override suspend fun getQueue(): List<AudioQueueEntity> = rowsSnapshot

    override suspend fun insertAll(items: List<AudioQueueEntity>) {
        items.forEach { entity ->
            rows.removeAll { it.id == entity.id }
            rows.add(entity)
        }
        queueObs.value = rowsSnapshot
    }

    override suspend fun clearQueue() {
        rows.clear()
        queueObs.value = emptyList()
    }

    override suspend fun deleteById(itemId: String) {
        rows.removeAll { it.id == itemId }
        queueObs.value = rowsSnapshot
    }

    override fun observeState(): Flow<AudioQueueStateEntity?> = stateObs.asStateFlow()
    override suspend fun getState(): AudioQueueStateEntity? = persistedState

    override suspend fun saveState(state: AudioQueueStateEntity) {
        savedStates += state
        persistedState = state
        stateObs.value = state
    }

    override suspend fun replaceQueue(items: List<AudioQueueEntity>) {
        replaceQueueCalls++
        super.replaceQueue(items)
    }
}

internal class TestTimeSource : TimeSource {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
    override fun today(zone: ZoneId): LocalDate = LocalDate.now(zone)
    override fun nowElapsedRealtimeMillis(): Long = System.nanoTime() / 1_000_000
}

internal fun resolvedTrack(
    itemId: String,
    uri: String = "https://stream.example/$itemId",
    resumePositionTicks: Long? = null,
    normalizationGain: Float? = null,
) = ResolvedAudioTrack(
    itemId = itemId,
    uri = uri,
    isLocalFile = !uri.startsWith("http"),
    title = "Track $itemId",
    artist = "Artist of $itemId",
    artistId = "artist-$itemId",
    album = "Album $itemId",
    mediaSourceId = "ms-$itemId",
    durationMs = 180_000L,
    normalizationGain = normalizationGain,
    resumePositionTicks = resumePositionTicks,
)
