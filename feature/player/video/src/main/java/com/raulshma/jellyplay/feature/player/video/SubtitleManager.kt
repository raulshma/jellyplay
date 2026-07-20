package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.net.Uri
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the in-player "Subtitle Manager" workflow: downloading server-default
 * subtitles, searching OpenSubtitles (via the server), uploading local files,
 * side-loading local files, and loading the upload/search language cultures.
 *
 * Extracted verbatim from [VideoPlayerViewModel] (recommendation A7 — continue
 * the collaborator-extraction pattern established by [TrackSelectionHelper] and
 * [TrickplayManager]). The ViewModel keeps thin public delegating functions of
 * the same names so the screen call sites are unchanged; this class performs
 * the actual work against the repositories and the subtitle-manager slice of
 * [VideoPlayerUiState].
 *
 * State access mirrors [TrackSelectionHelper]: the VM injects [getUiState] /
 * [updateUiState] lambdas so this class reads and writes its UiState slice
 * without a hard ViewModel reference. After a successful download/upload the
 * media detail is re-fetched and handed to [onMediaDetailRefreshed], which the
 * VM implements to re-apply the detail and refresh the shared
 * `currentMediaSource` / `mediaStreams` / `detectedAspectRatio` fields (those
 * are not owned by this class).
 *
 * File reads (`queryFileSizeBytes` / `readAndEncode`) live here because they
 * only need [context]; they are private to this class.
 */
internal class SubtitleManager(
    private val context: Context,
    private val playbackRepository: PlaybackRepository,
    private val mediaRepository: MediaRepository,
    private val userMessageBus: UserMessageBus,
    private val scope: CoroutineScope,
    private val addExternalSubtitle: (SubtitleSource) -> Unit,
    private val getUiState: () -> VideoPlayerUiState,
    private val updateUiState: ((VideoPlayerUiState) -> VideoPlayerUiState) -> Unit,
    private val getCurrentItemId: () -> String?,
    private val onMediaDetailRefreshed: (MediaDetail) -> Unit,
) {
    /**
     * In-flight remote-search job. A slow "en" query landing after a
     * fast "fr" query used to overwrite the latter's results, surfacing
     * stale results for the wrong language. Cancel the prior search before
     * starting a new one.
     */
    private var searchJob: Job? = null

    /**
     * In-flight download job. A slow download landing after a newer pick
     * used to race the post-download media-detail refresh and surface stale
     * loading/error state. Cancel the prior download before starting a new one,
     * mirroring [searchJob] — and cancelled jobs must not fire error toasts
     * (cancellation is intentional, not a failure).
     */
    private var downloadJob: Job? = null

    fun loadRemoteSubtitles() {
        val itemId = getCurrentItemId() ?: return
        updateUiState { it.copy(isLoadingRemoteSubtitles = true) }
        scope.launch {
            val subs = playbackRepository.getRemoteSubtitles(itemId).getOrElse { emptyList() }
            updateUiState { it.copy(remoteSubtitles = subs, isLoadingRemoteSubtitles = false) }
        }
    }

    fun downloadSubtitle(subtitleInfo: RemoteSubtitleInfo) {
        val itemId = getCurrentItemId() ?: return
        // Cancel any in-flight download so a stale post-download refresh can't
        // clobber the new one's state (mirrors searchJob's cancellation guard).
        downloadJob?.cancel()
        updateUiState { it.copy(isDownloadingSubtitle = true, subtitleDownloadError = null) }
        downloadJob = scope.launch {
            val result = runCatching { playbackRepository.downloadSubtitle(itemId, subtitleInfo.id) }
            // If superseded (a newer downloadSubtitle() call cancelled us), do
            // not touch UI state — the newer job owns it now. The new job has
            // already reset isDownloadingSubtitle = true on entry.
            ensureActive()
            result.fold(
                onSuccess = {
                    mediaRepository.getMediaDetail(itemId).getOrNull()?.let { detail ->
                        onMediaDetailRefreshed(detail)
                    }
                    updateUiState { it.copy(isDownloadingSubtitle = false, subtitleDownloadError = null) }
                },
                onFailure = { e ->
                    val msg = e.message ?: "Download failed"
                    userMessageBus.error("Subtitle download failed: $msg")
                    updateUiState { it.copy(isDownloadingSubtitle = false, subtitleDownloadError = msg) }
                },
            )
        }
    }

    fun addLocalSubtitle(uri: Uri, fileName: String) {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val codec = when (ext) {
            "srt" -> "srt"
            "ass", "ssa" -> "ass"
            "vtt" -> "vtt"
            "ttml", "dfxp" -> "ttml"
            else -> null
        }

        val label = fileName.substringBeforeLast('.').ifBlank { "Local subtitle" }
        val source = SubtitleSource(
            url = uri.toString(),
            label = label,
            language = null,
            mimeType = null,
            codec = codec,
            isDefault = false,
            isForced = false,
            id = "local:${System.currentTimeMillis()}",
        )
        addExternalSubtitle(source)
    }

    /**
     * Resets the Subtitle Manager's search/cultures state. Called when the sheet
     * opens (or the playback item changes) so results from a previous item don't
     * leak into a new one. Cultures are reloaded on demand since they may
     * be item-scoped on some servers.
     */
    fun resetSubtitleManagerState() {
        updateUiState {
            it.copy(
                searchedSubtitles = emptyList(),
                hasSearchedSubtitles = false,
                isSearchingSubtitles = false,
                subtitleSearchError = null,
                subtitleCultures = emptyList(),
            )
        }
    }

    /**
     * Loads the language cultures the server understands for subtitle
     * upload/search selection. Idempotent: a no-op once cultures are already
     * populated for the current item (e.g. across tab switches / reopens).
     */
    fun loadSubtitleCultures() {
        if (getUiState().subtitleCultures.isNotEmpty()) return
        val itemId = getCurrentItemId() ?: return
        scope.launch {
            playbackRepository.getSubtitleCultures(itemId).fold(
                onSuccess = { cultures -> updateUiState { it.copy(subtitleCultures = cultures) } },
                onFailure = {
                    // An empty cultures list just yields an empty dropdown;
                    // users can still type a code manually, so no error toast.
                    updateUiState { it.copy(subtitleCultures = emptyList()) }
                },
            )
        }
    }

    /**
     * Language-scoped remote subtitle search (OpenSubtitles via the server).
     * Results populate the Search tab and are kept separate from the Download
     * tab's server-default list. A failure surfaces as [VideoPlayerUiState.subtitleSearchError]
     * (distinct from an empty result) so the UI can invite retry rather than
     * implying "no subtitles exist".
     */
    fun searchRemoteSubtitles(language: String) {
        val itemId = getCurrentItemId() ?: return
        searchJob?.cancel()
        updateUiState {
            it.copy(isSearchingSubtitles = true, hasSearchedSubtitles = false, searchedSubtitles = emptyList(), subtitleSearchError = null)
        }
        searchJob = scope.launch {
            playbackRepository.searchRemoteSubtitles(itemId, language).fold(
                onSuccess = { subs ->
                    updateUiState {
                        it.copy(searchedSubtitles = subs, isSearchingSubtitles = false, hasSearchedSubtitles = true, subtitleSearchError = null)
                    }
                },
                onFailure = { e ->
                    updateUiState {
                        it.copy(
                            isSearchingSubtitles = false,
                            hasSearchedSubtitles = false,
                            subtitleSearchError = e.message ?: "Search failed",
                        )
                    }
                },
            )
        }
    }

    /**
     * Uploads a local subtitle file to the current item, then reloads the
     * media detail so the new stream appears in the track list — mirroring
     * [downloadSubtitle]'s refresh and the editor's upload path.
     *
     * The file size is checked up front against [MAX_SUBTITLE_UPLOAD_BYTES] (via
     * OpenableColumns.SIZE) so an oversized pick is rejected before the whole
     * file — and its ~1.33× Base64 expansion — are loaded into memory. This
     * matters on low-RAM TV devices where a stray large pick could OOM.
     */
    fun uploadSubtitle(uri: Uri, fileName: String, language: String?, isForced: Boolean, isHearingImpaired: Boolean) {
        val itemId = getCurrentItemId() ?: return
        updateUiState { it.copy(isUploadingSubtitle = true) }
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val size = queryFileSizeBytes(uri)
                    if (size in 1..MAX_SUBTITLE_UPLOAD_BYTES) {
                        // Expected path: a real subtitle file well under the cap.
                        readAndEncode(uri)
                    } else if (size > MAX_SUBTITLE_UPLOAD_BYTES) {
                        throw java.io.IOException("Subtitle file is too large (${size / 1024} KB). Limit is ${MAX_SUBTITLE_UPLOAD_BYTES / 1024} KB.")
                    } else {
                        // SIZE unknown (some providers return 0 or null) — allow the
                        // read but it will still be bounded by a real subtitle's size.
                        readAndEncode(uri)
                    }
                }
            }.mapCatching { base64 ->
                playbackRepository.uploadSubtitle(itemId, base64, fileName, language, isForced, isHearingImpaired).getOrThrow()
            }
            updateUiState { it.copy(isUploadingSubtitle = false) }
            result.onSuccess {
                // Refresh media detail so the uploaded track surfaces in the
                // subtitle track list — same approach as downloadSubtitle().
                mediaRepository.getMediaDetail(itemId).getOrNull()?.let { detail ->
                    onMediaDetailRefreshed(detail)
                }
                userMessageBus.info("Subtitle uploaded")
            }.onFailure { e ->
                userMessageBus.error(e.message ?: "Failed to upload subtitle")
            }
        }
    }

    /**
     * Subtitle upload size cap (2 MB). Real subtitle files are tens of KB at
     * most; this comfortably rejects a mis-selected large asset while not
     * blocking any legitimate subtitle. See [uploadSubtitle].
     */
    private val MAX_SUBTITLE_UPLOAD_BYTES = 2L * 1024 * 1024

    /** Returns the byte size of [uri] via OpenableColumns.SIZE, or 0 if unknown. */
    private fun queryFileSizeBytes(uri: Uri): Long {
        val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
            ?: return 0
        return cursor.use {
            if (!it.moveToFirst()) return 0
            val idx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (idx < 0) 0 else it.getLong(idx)
        }
    }

    /** Reads [uri] fully and Base64-encodes it (NO_WRAP). Throws on read failure. */
    private fun readAndEncode(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw java.io.IOException("Cannot open input stream for selected subtitle")
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }
}
