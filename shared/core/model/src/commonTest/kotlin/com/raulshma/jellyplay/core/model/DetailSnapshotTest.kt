package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pure model tests for the [DetailSnapshot] contract introduced with the
 * unified media-detail screen. These invariants were previously only exercised
 * indirectly through the provider/ViewModel; pinning them here keeps the
 * capability/attachment/label decisions first-class as the model evolves.
 *
 * Covers:
 *  - [DetailOrigin.isLocal] — the single "sourced from local data?" predicate.
 *  - [DownloadAttachment.isCompleted] / [isInProgress] — the lifecycle-action
 *    distinction (a download in progress is NOT a deletable completed item).
 *  - [LocalSubtitleOption.displayLabel] — the row-label fallback chain.
 *  - [DetailAssets] defaults (local artwork starts empty until the provider
 *    resolves it).
 */
class DetailSnapshotTest {

    // ── DetailOrigin.isLocal ─────────────────────────────────────────────────

    @Test
    fun `REMOTE origin is not local`() {
        assertFalse(DetailOrigin.REMOTE.isLocal)
    }

    @Test
    fun `LOCAL_OFFLINE_MODE origin is local`() {
        assertTrue(DetailOrigin.LOCAL_OFFLINE_MODE.isLocal)
    }

    @Test
    fun `LOCAL_REMOTE_FAILURE origin is local`() {
        // A remote-failure fallback is still sourced from local data, so it must
        // satisfy isLocal even though connectivity may be AVAILABLE for a retry.
        assertTrue(DetailOrigin.LOCAL_REMOTE_FAILURE.isLocal)
    }

    @Test
    fun `isLocal partitions every origin exactly`() {
        // Guard against a new origin variant being misclassified.
        DetailOrigin.values().forEach { origin ->
            assertEquals(
origin != DetailOrigin.REMOTE,
                origin.isLocal,
"isLocal must agree with != REMOTE for $origin",
)
        }
    }

    // ── DownloadAttachment.isCompleted ───────────────────────────────────────
    //
    // isCompleted requires BOTH a confirmed file on disk AND status COMPLETED —
    // a DB-status guess is not enough. This is the gate for delete/freshness/
    // resync/re-download surfacing (localDownloadManagement capability).

    private fun attachment(
        status: DownloadStatus,
        isCompletedFilePresent: Boolean,
    ) = DownloadAttachment(
        status = status,
        downloadedBytes = 0L,
        totalSizeBytes = 0L,
        mediaSourceId = null,
        container = null,
        downloadPath = null,
        createdAtEpochMillis = 0L,
        isCompletedFilePresent = isCompletedFilePresent,
    )

    @Test
    fun `isCompleted true only when COMPLETED and file present`() {
        assertTrue(attachment(DownloadStatus.COMPLETED, isCompletedFilePresent = true).isCompleted)
    }

    @Test
    fun `isCompleted false when COMPLETED but file missing`() {
        // The DB says COMPLETED but the file was deleted externally — must NOT
        // advertise as a deletable completed item.
        assertFalse(attachment(DownloadStatus.COMPLETED, isCompletedFilePresent = false).isCompleted)
    }

    @Test
    fun `isCompleted false when file present but not COMPLETED`() {
        assertFalse(attachment(DownloadStatus.DOWNLOADING, isCompletedFilePresent = true).isCompleted)
    }

    // ── DownloadAttachment.isInProgress ──────────────────────────────────────
    //
    // "In progress (or paused/queued) — not yet deletable as completed content."
    // FAILED / CANCELLED / COMPLETED are explicitly NOT in progress.

    @Test
    fun `isInProgress true for active lifecycle states`() {
        listOf(
            DownloadStatus.DOWNLOADING,
            DownloadStatus.QUEUED,
            DownloadStatus.PAUSED,
            DownloadStatus.PENDING,
        ).forEach { status ->
            assertTrue(
attachment(status, isCompletedFilePresent = false).isInProgress,
"expected isInProgress for $status",
)
        }
    }

    @Test
    fun `isInProgress false for terminal states`() {
        listOf(
            DownloadStatus.COMPLETED,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELLED,
        ).forEach { status ->
            assertFalse(
attachment(status, isCompletedFilePresent = false).isInProgress,
"expected NOT isInProgress for $status",
)
        }
    }

    // ── LocalSubtitleOption.displayLabel fallback chain ──────────────────────
    //
    // displayTitle (if non-blank) → language.uppercase().take(3) → fileName.
    // The single source of truth for the local-subtitle picker row label.

    private fun option(
        displayTitle: String? = null,
        language: String? = null,
        fileName: String = "episode.srt",
    ) = LocalSubtitleOption(
        index = 0,
        fileName = fileName,
        displayTitle = displayTitle,
        language = language,
        isDefault = false,
        isForced = false,
    )

    @Test
    fun `displayLabel prefers a non-blank displayTitle`() {
        assertEquals(
option(displayTitle = "English SDH").displayLabel(),
"English SDH",
)
    }

    @Test
    fun `displayLabel ignores a blank displayTitle and falls back to language`() {
        // A whitespace-only displayTitle is treated as absent.
        assertEquals(
option(displayTitle = "   ", language = "eng").displayLabel(),
"ENG",
)
    }

    @Test
    fun `displayLabel uppercases and truncates a 3-letter language code`() {
        assertEquals(
option(language = "eng").displayLabel(),
"ENG",
)
    }

    @Test
    fun `displayLabel truncates a longer language token to 3 letters`() {
        // "english" → "ENG" (matches the offline picker's iso-code rendering).
        assertEquals(
option(language = "english").displayLabel(),
"ENG",
)
    }

    @Test
    fun `displayLabel falls back to the file name when title and language are absent`() {
        assertEquals(
option(displayTitle = null, language = null).displayLabel(),
"episode.srt",
)
    }

    @Test
    fun `displayLabel falls back to file name when language is null but displayTitle is blank`() {
        assertEquals(
option(displayTitle = "", language = null, fileName = "subs.srt").displayLabel(),
"subs.srt",
)
    }

    // ── DetailAssets defaults ────────────────────────────────────────────────

    @Test
    fun `DetailAssets defaults to empty artwork maps`() {
        // A REMOTE snapshot carries an empty DetailAssets (local artwork is a
        // storage concern surfaced only for local origins by the provider).
        val assets = DetailAssets()
        assertNull(assets.posterPath)
        assertNull(assets.backdropPath)
        assertTrue(assets.castImages.isEmpty())
        assertTrue(assets.episodeImages.isEmpty())
    }

    // ── DetailContext default shape ──────────────────────────────────────────

    @Test
    fun `DetailContext carries the origin-vs-attachment distinction`() {
        // A REMOTE origin with a completed download attached: origin stays REMOTE,
        // the download comes from context.download — the contract the unified
        // screen relies on so a remote detail never advertises a misleading
        // local origin.
        val download = attachment(DownloadStatus.COMPLETED, isCompletedFilePresent = true)
        val context = DetailContext(
            origin = DetailOrigin.REMOTE,
            connectivity = RemoteConnectivity.AVAILABLE,
            download = download,
            syncState = null,
            seriesAggregate = null,
        )
        assertEquals(DetailOrigin.REMOTE, context.origin)
        assertFalse(context.origin.isLocal)
        assertEquals(download, context.download)
    }
}
