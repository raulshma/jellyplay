package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

/**
 * The read window over a host screen's track downloads that
 * [TrackDownloadActions] needs: whether the platform carries a download
 * pipeline at all, the current rows for a scoped id list, and the delete
 * command behind the remove-download flip.
 *
 * The natural source — core:data's jvmShared `DownloadRepository` — is
 * invisible to feature commonMain (its constructor closure is the JVM
 * download engine), which is exactly why this interface exists. It replaced
 * the feature-local seams that used to bridge that wall (player-audio's
 * AudioTrackDownloads, music's MusicTrackDownloads — both deleted with the
 * download-actions seam consolidation): the JVM actual
 * [JvmTrackDownloadStatusWindow] (jvmShared, bound in dataJvmModule) and the
 * honest no-op wasmJs actual [WasmTrackDownloadStatusWindow] (wasmJsMain,
 * bound in dataWasmModule) are core:data's own, so the hosts inject this one
 * window directly.
 *
 * IDIOM RULE (declared with the download-actions seam consolidation): a seam
 * that exists to cross the commonMain↔jvmShared visibility wall is DECLARED,
 * IMPLEMENTED AND BOUND by core:data on both platforms. Features never grow
 * their own wall-crossing template — the deleted AudioTrackDownloads /
 * MusicTrackDownloads twins this interface absorbed were exactly that
 * mistake.
 */
interface TrackDownloadStatusWindow {

    /** Whether this platform has a download pipeline; gates download CTAs. */
    val isSupported: Boolean

    /**
     * Current rows for the given media item ids — the caller-scoped window
     * (the album screen reads only its ~10-20 tracks, the player exactly the
     * playing one).
     */
    fun downloadsFor(ids: List<String>): Flow<List<DownloadItem>>

    /** Removes [downloadId]'s local download (artifacts + offline rows). */
    suspend fun remove(downloadId: String)
}

/**
 * The ONE music/track download "flip" choreography behind the audio player
 * and the album screen, replacing the per-ViewModel copies that had drifted
 * into paste: fetch the item's detail (silently skipping unresolvable items —
 * the [MediaRepository] fetch is commonMain, so it is a direct constructor
 * dep here) and hand the detail to the [DownloadIntake] seam, which owns the
 * full artifact bundle. Constructed over the intake, the repository, the
 * owning screen's [CoroutineScope] (RecordActions pattern) and the host's
 * [TrackDownloadStatusWindow] adapter.
 *
 * Deliberately the START half only. The REMOVE half — an existing COMPLETED
 * download flips the CTA to "remove" — stays at the call sites, because the
 * confirm-before-remove policy genuinely differs per host: the audio player
 * shows a confirm dialog before deleting, the album track row removes
 * directly, and the album's bulk delete removes every existing row. Folding
 * that decision in here would have swallowed a real product difference.
 *
 * Failure envelope: a failed detail fetch or a failed intake start is
 * swallowed (both hosts treat a flip failure as silent — there is no error
 * surface on a track row), but CANCELLATION RETHROWS
 * ([runCatchingRethrowingCancellation] — the hand-copied `catch (_:
 * Exception)` this class replaced also caught CancellationException, masking
 * scope teardown as a "failed" flip; that bug class is the concurrency
 * module's ratcheted one).
 */
class TrackDownloadActions(
    private val scope: CoroutineScope,
    private val intake: DownloadIntake,
    private val mediaRepository: MediaRepository,
    private val statusWindow: TrackDownloadStatusWindow,
) {

    /**
     * Starts a download for [itemId] — detail fetch (null result aborts
     * silently), then [DownloadIntake.start]. One flip in flight per call;
     * the returned [Job] is the caller's cancellation handle.
     */
    fun flip(itemId: String): Job = scope.launch {
        startAfterDetail(itemId)
    }

    /**
     * The album-level bulk start: admits only items with no download row yet
     * or a terminal failure (null || FAILED || CANCELLED, read from the
     * status window's current snapshot), then starts each admitted item under
     * a [Semaphore] of [concurrency] permits so tapping "download album"
     * never opens 15 simultaneous transfers. Items that already carry a live
     * or completed download are skipped — never removed; the remove decision
     * belongs to the hosts' own policies.
     */
    fun bulk(items: List<MediaItem>, concurrency: Int = 3): Job = scope.launch {
        if (items.isEmpty()) return@launch
        val currentRows = statusWindow.downloadsFor(items.map { it.id }).first()
            .associateBy { it.mediaItemId }
        val permits = Semaphore(concurrency)
        items.forEach { item ->
            val existing = currentRows[item.id]
            if (existing == null || existing.status == DownloadStatus.FAILED || existing.status == DownloadStatus.CANCELLED) {
                launch {
                    permits.acquire()
                    try {
                        startAfterDetail(item.id)
                    } finally {
                        permits.release()
                    }
                }
            }
        }
    }

    /** The shared flip body: detail fetch → intake start, failures swallowed. */
    private suspend fun startAfterDetail(itemId: String) {
        runCatchingRethrowingCancellation {
            val detail = mediaRepository.getMediaDetail(itemId).getOrNull()
            // Null detail (fetch failure) aborts the flip silently — the
            // original copies' `?: return` without an error surface.
            if (detail != null) intake.start(detail)
        }
    }
}
