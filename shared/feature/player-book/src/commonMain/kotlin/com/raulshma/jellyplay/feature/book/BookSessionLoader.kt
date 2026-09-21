package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.playback.PlaybackIdentity
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.core.model.BookTocEntry
import com.raulshma.jellyplay.core.model.pathExtension
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path

/**
 * The one-book open sequence previously inlined as BookReaderViewModel's
 * `openBook` — the PlaybackSession precedent applied to the reader: this
 * module owns WHEN each step runs (session reset → detail fetch → format
 * resolve with the download-header probe fallback → error classification →
 * content resolve → resume math → document open), while every uiState write
 * stays ViewModel-side. Outcomes surface as [BookSessionOutcome]s the VM
 * applies; the three VM-bound slices of the old body stay behind constructor
 * hooks invoked at exactly their old positions: [onSessionReset] (the
 * per-book state teardown), [onDownloadProgress] (the loading veil's byte
 * fraction) and [onFormatResolved] (the loaded-format latch, which the old
 * code set after content resolve so it survives a later open failure).
 *
 * The deep-link destination rides the entry as a value ([PendingJump]) —
 * `load` overwrites the pending fields before cancelling the in-flight open,
 * so consuming them at entry is indistinguishable from the old mid-sequence
 * read.
 */
internal class BookSessionLoader(
    /** Coroutine scope for TOC-cache writes and the outline parse hop. */
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val playbackIdentity: PlaybackIdentity,
    private val contentResolver: BookContentResolver,
    private val documentOpener: BookDocumentOpener,
    /** Download-header format probe for items whose `Path` yields nothing. */
    private val formatProbe: BookFormatProbe,
    private val pdfOutlineParser: PdfOutlineParser,
    private val tocCacheRepository: BookTocCacheRepository,
    /** The reflowable exact-resume anchor (ReaderStore's last-CFI read). */
    private val storedResumeCfi: (itemId: String) -> String?,
    /** VM-state reset for the outgoing session — open's first step. */
    private val onSessionReset: () -> Unit,
    /** Streaming-fetch byte fraction (null = indeterminate) for the loading veil. */
    private val onDownloadProgress: (Float?) -> Unit,
    /** The loaded-format latch write; runs after content resolve, before the format split. */
    private val onFormatResolved: (BookFormat) -> Unit,
    /**
     * Hop target for the blocking PDF outline parse ([PdfOutlineParser.parse]
     * is file IO). Defaulted to [Dispatchers.Default] — the production VM
     * never passes one; tests inject the scheduler to keep the hop virtual.
     */
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /**
     * Resolves and opens [itemId]. Every failure mode folds into a classified
     * [BookSessionOutcome.Failed]; a paged success hands the opened document
     * over (the caller owns closing it from there on).
     */
    suspend fun open(itemId: String, jump: PendingJump?): BookSessionOutcome {
        onSessionReset()
        val detail = mediaRepository.getMediaDetail(itemId).getOrNull()
            ?: return BookSessionOutcome.Failed(BookOpenError.CannotOpen)
        val downloadUrl = playbackRepository.getBookDownloadUrl(itemId)
        // Header auth for the probe + streaming fetch: Jellyfin 12 401s the
        // legacy ?api_key= query param on data endpoints, so the token must
        // ride `Authorization: MediaBrowser` (the URL's capital ApiKey param
        // stays valid on every server since 10.8).
        val accessToken = playbackIdentity.accessToken()
        val format = BookFormat.fromPath(detail.path) ?: probeDownloadFormat(downloadUrl, accessToken)
            ?: return BookSessionOutcome.Failed(
                BookOpenError.UnsupportedFormat(unsupportedFormatFileExtension(detail.path)),
            )
        val resolved = runCatchingRethrowingCancellation {
            contentResolver.resolve(
                itemId = itemId,
                // The resolver's sanitize owns path stripping — pass the
                // item path through; it lands as the cache file's base name.
                fileName = detail.path,
                format = format,
                downloadUrl = downloadUrl,
                accessToken = accessToken,
                onProgress = { progress -> onDownloadProgress(progress.fraction) },
            )
        }.getOrNull() ?: return BookSessionOutcome.Failed(BookOpenError.CannotOpen)
        onFormatResolved(format)
        // Reflowable books never enter the document/pager layer: the WebView
        // host owns rendering, and the position math is percent-based. The
        // locally stored CFI (ADR 0003 point 4) outranks the server percent
        // on this install; a deep-link destination (detail "Contents" tap)
        // outranks both.
        if (format.isReflowable) {
            return BookSessionOutcome.Reflowable(
                title = detail.item.name,
                bookFile = resolved.path,
                resumePercent = BookProgressPolicy.ticksToPercent(detail.playbackPositionTicks),
                resumeCfi = storedResumeCfi(itemId),
                jumpHref = jump?.href,
            )
        }
        val doc = runCatchingRethrowingCancellation { documentOpener.open(resolved.path, format) }.getOrNull()
            ?: return BookSessionOutcome.Failed(BookOpenError.CannotOpen)
        // A deep-link page outranks the server resume position; both clamp
        // to the document's real page count.
        val resumePage = BookProgressPolicy.clampPage(
            jump?.page ?: BookProgressPolicy.ticksToPage(detail.playbackPositionTicks),
            doc.pageCount,
        )
        // Re-anchor the back-end's own page cache before the handover — the
        // caller re-anchors its pager-side caches when it applies the outcome.
        doc.onPageChanged(resumePage)
        return BookSessionOutcome.Paged(
            title = detail.item.name,
            document = doc,
            resumePage = resumePage,
            format = format,
            file = resolved.path,
        )
    }

    /**
     * The open-time TOC-cache branch, invoked by the VM AFTER it wrote its
     * Ready state (the old ordering): for a comic the page count is the whole
     * story, for a PDF the outline parses off the loading thread
     * ([PdfOutlineParser.parse] is blocking file IO), rides [onOutlineParsed]
     * back into the VM's state and is then flattened into the cache.
     */
    fun startOpenTocCache(
        itemId: String,
        format: BookFormat,
        file: Path,
        pageCount: Int,
        onOutlineParsed: (List<PdfOutlineNode>) -> Unit,
    ) {
        if (format != BookFormat.PDF) {
            cacheToc(itemId, format, pageCount, emptyList())
        } else {
            scope.launch {
                val nodes = withContext(parseDispatcher) {
                    runCatchingRethrowingCancellation { pdfOutlineParser.parse(file) }.getOrDefault(emptyList())
                }
                onOutlineParsed(nodes)
                if (nodes.isNotEmpty()) {
                    cacheToc(
                        itemId = itemId,
                        format = BookFormat.PDF,
                        pageCount = pageCount,
                        entries = nodes.flatMap { it.flattenToTocEntries() },
                    )
                }
            }
        }
    }

    /**
     * Writes what became known into the local TOC cache the media-detail
     * screen reads. A no-op when there is nothing to record — an EPUB that
     * never reported a TOC must not clobber a good cached one with an empty
     * row.
     */
    fun cacheToc(itemId: String, format: BookFormat, pageCount: Int, entries: List<BookTocEntry>) {
        if (entries.isEmpty() && pageCount <= 0) return
        scope.launch {
            runCatchingRethrowingCancellation { tocCacheRepository.putToc(itemId, format, pageCount, entries) }
        }
    }

    /**
     * Format fallback when the item's `Path` carried no known extension:
     * one cheap ranged GET against the download URL reads Content-Type +
     * Content-Disposition filename (both served for every book, 10.9–12).
     * A probe failure (or an unrecognized metadata pair — mobi/azw3) maps to
     * null and the caller reports the precise unsupported-format error.
     */
    private suspend fun probeDownloadFormat(downloadUrl: String, accessToken: String?): BookFormat? {
        val metadata = runCatchingRethrowingCancellation { formatProbe.probe(downloadUrl, accessToken) }.getOrNull() ?: return null
        return BookFormat.fromDownloadMetadata(metadata.contentType, metadata.fileName)
    }
}

/**
 * The deep-link destination of one load (detail-screen "Contents" tap):
 * reflowable jumps by chapter href, paged jumps by page. Consumed by the
 * [BookSessionLoader.open] call it was carried into; a later plain re-load
 * passes null and resumes.
 */
internal data class PendingJump(
    val href: String?,
    val page: Int?,
)

/**
 * One open's result. The ViewModel maps [Failed] onto the error veil and
 * applies the ready kinds onto its session state (document, caches, uiState)
 * — the loader never touches the ui state.
 */
internal sealed interface BookSessionOutcome {

    /**
     * Reflowable open: the screen's WebView host renders [bookFile];
     * [resumeCfi] is the locally stored exact-resume anchor and [jumpHref]
     * the one-shot deep link that outranks it.
     */
    data class Reflowable(
        val title: String,
        val bookFile: Path,
        val resumePercent: Double,
        val resumeCfi: String?,
        val jumpHref: String?,
    ) : BookSessionOutcome

    /**
     * Paged open: [document] ownership transfers to the caller (it closes it
     * on re-load and teardown), already re-anchored at [resumePage].
     */
    data class Paged(
        val title: String,
        val document: BookDocument,
        val resumePage: Int,
        val format: BookFormat,
        val file: Path,
    ) : BookSessionOutcome

    /** The classified failure for the error veil (see [BookOpenError]). */
    data class Failed(val error: BookOpenError) : BookSessionOutcome
}

/**
 * The open-failure taxonomy: "the item's path names a file the reader can't
 * open" (e.g. .mobi — download-only) is distinguishable from "no format was
 * knowable at all" (path blank AND the download probe failed) so the error
 * veil can name the cause instead of a bare "unsupported format".
 */
internal sealed interface BookOpenError {

    /** Detail fetch, content resolve or document open failed. */
    data object CannotOpen : BookOpenError

    /**
     * Neither the path nor the download headers named a readable format.
     * [fileExtension] is the dotted extension the item's path actually
     * carried (".mobi"), or null when the server reported no path at all.
     */
    data class UnsupportedFormat(val fileExtension: String?) : BookOpenError
}

/**
 * The `.ext` derivation for the unsupported-format veil — pure so the
 * classification is unit-testable: blank/null paths report null (the veil's
 * "no format was knowable" line), a path with a known-but-unreadable
 * extension reports it dotted.
 */
internal fun unsupportedFormatFileExtension(path: String?): String? =
    path
        ?.takeIf { it.isNotBlank() }
        ?.pathExtension()
        ?.takeIf { it.isNotEmpty() }
        ?.let { ".$it" }
