package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource

/**
 * The session's subtitle-sourcing collaborator: where the side-loaded
 * [SubtitleSource] set for a load comes from and how fresh server streams
 * attach mid-session. Extracted verbatim from [PlayerSessionManager]'s load
 * spine (beside [SubtitleManager], the workflow-side collaborator: that one
 * owns the user-facing download/search/upload choreography, this one owns
 * what the session SOURCES into the engine at load time):
 *
 *  - **Streaming store** ([loadStreamingSubtitles]) — provider subtitles
 *    (OpenSubtitles/Wyzie) persisted in the durable streaming-subtitle store,
 *    side-loaded on both online and offline playback, reconciled against the
 *    server's live stream list so a deleted subtitle is not resurrected.
 *  - **Offline manifest** ([loadOfflineSubtitles]) — the sidecars bundled with
 *    a download, with the bitmap-codec engine-capability gate.
 *  - **Server streams** ([buildExternalSubtitles]) — the shared subtitle URL
 *    ladder over the playing source's subtitle streams (the builder both
 *    initial loads and every reload rebuild the side-loaded set from).
 *  - **Attach-new diff** ([attachNewSubtitleStreams]) — the mid-session
 *    side-load of streams that appeared in a refreshed detail (in-player
 *    download/upload), deduped against the already-attached ids.
 *
 * The collaborator is deliberately stateless beyond its constructor: the
 * side-loaded set lives on the session's [PlaybackRequest] (immutable,
 * replaced wholesale), so every session-owned mutation goes through the
 * [addExternalSubtitle] lambda and every session-state read through the
 * narrow getter lambdas — the [SubtitleManager] seam shape. The load-spine
 * call sites in [PlayerSessionManager] stay one-liners; the orderings and
 * suspension points are unchanged by the move.
 */
internal class SessionSubtitleSources(
    private val streamingSubtitleStore: StreamingSubtitleStore,
    private val downloadRepository: DownloadRepository,
    private val playbackRepository: PlaybackRepository,
    /** The session's side-load mutation — see the class KDoc. */
    private val addExternalSubtitle: (SubtitleSource) -> Unit,
    /** The external sources already side-loaded in the current playback request, or null when nothing is loaded. */
    private val getExternalSubtitles: () -> List<SubtitleSource>?,
    private val getCurrentItemId: () -> String?,
    private val getCurrentPlayMethod: () -> PlayMethod,
    /** The [MediaSource] of [detail] the session is playing (the session's [PlayerSessionManager.matchedMediaSource]). */
    private val matchPlayingMediaSource: (MediaDetail) -> MediaSource?,
    /** The live engine's capabilities, or null with no engine (the bitmap-sidecar gate reads it). */
    private val getEngineCapabilities: () -> EngineCapabilities?,
) {

    /**
     * Side-loads subtitles previously persisted by `SubtitleManager` into the
     * durable streaming-subtitle store. Mirrors [loadOfflineSubtitles] but for
     * streaming (non-downloaded) items — keyed by `itemId`, not a media-file
     * path. Files missing on disk are silently skipped.
     *
     * Entries that recorded a SavedSubtitle.serverStreamIndex are reconciled
     * against [currentStreams]: when the user deleted the subtitle from the
     * metadata editor, its server stream is gone — and side-loading the local
     * copy would resurrect the deleted track on every playback. Null disables
     * reconciliation (offline playback has no server state to reconcile
     * against). Legacy entries (no recorded index) and device-only downloads
     * (upload failed, so no index was ever recorded) always load: the durable
     * local copy is their only copy.
     */
    suspend fun loadStreamingSubtitles(itemId: String, currentStreams: List<MediaStream>?) {
        val saved = streamingSubtitleStore.loadAll(itemId)
        val currentIndexes = currentStreams
            ?.filter { it.type == StreamType.SUBTITLE }
            ?.map { it.index }
            ?.toSet()
        for (entry in saved) {
            val recordedIndex = entry.serverStreamIndex
            if (recordedIndex != null && currentIndexes != null && recordedIndex !in currentIndexes) {
                Log.d(
                    "SubtitleUse",
                    "loadStreamingSubtitles: skipping ${entry.provider}:${entry.providerSubtitleId} " +
                        "(server stream $recordedIndex deleted)",
                )
                continue
            }
            val file = streamingSubtitleStore.fileFor(itemId, entry)
            if (!file.exists()) continue
            addExternalSubtitle(
                SubtitleSource(
                    url = fileUriString(file),
                    label = entry.language ?: entry.fileName,
                    language = entry.language,
                    mimeType = null,
                    codec = entry.codec,
                    isDefault = false,
                    isForced = entry.isForced,
                    id = streamingSubtitleTrackId(entry.provider, entry.providerSubtitleId),
                ),
            )
        }
    }

    /**
     * Builds the side-loaded [SubtitleSource] list for the engine.
     *
     * Every stream resolves through the shared subtitle URL ladder
     * ([PlaybackRepository.resolveSubtitleStreamUrl]): a server
     * [MediaStream.deliveryUrl] (the PlaybackInfo response populates this for
     * externally-delivered subs, including image subs when the PGS-direct-play
     * profile opts in) rides verbatim; everything else goes through the
     * text-only subtitle endpoint, whose builder refuses image formats
     * (PGS/VOBSUB/DVB) — those are skipped (left to burn-in on transcode, or
     * container demux on direct play).
     *
     * For the text subs that survive the codec gate, side-loading is
     * method-dependent: external subs are always side-loaded; embedded text
     * subs are side-loaded only when NOT direct-playing (transcoded HLS does
     * not reliably expose them in-manifest). On DIRECT_PLAY every engine
     * (ExoPlayer, LibVLC, MPV) demuxes embedded text subs from the container
     * natively — confirmed for MPV via logcat, which lists the demuxed tracks
     * — so side-loading them too would duplicate each track and could render
     * the selected sub twice. We therefore never side-load embedded subs
     * alongside container demuxing.
     */
    fun buildExternalSubtitles(
        detail: MediaDetail,
        source: MediaSource?,
        playMethod: PlayMethod,
    ): List<SubtitleSource> {
        val streams = source?.mediaStreams ?: return emptyList()
        return streams.filter { it.type == StreamType.SUBTITLE }.mapNotNull { stream ->
            // The shared ladder (see
            // PlaybackRepository.resolveSubtitleStreamUrl): delivery URLs
            // verbatim, everything else through the text-only subtitle
            // endpoint, embedded tracks only when not direct-playing (the
            // KDoc above records the direct-play rationale).
            val subUrl = playbackRepository.resolveSubtitleStreamUrl(
                stream = stream,
                itemId = detail.item.id,
                mediaSourceId = source.id,
                includeEmbedded = playMethod != PlayMethod.DIRECT_PLAY,
            )
            if (subUrl == null) {
                Log.d("SubtitleUse", "buildExternalSubtitles: skipping stream index=${stream.index} codec=${stream.codec}")
                return@mapNotNull null
            }

            SubtitleSource(
                url = subUrl,
                label = stream.displayName,
                language = stream.language,
                mimeType = null, // Mapped by the engine using codec or extension.
                codec = stream.codec,
                isDefault = stream.isDefault,
                isForced = stream.isForced,
                id = externalSubtitleTrackId(stream.index),
            )
        }
    }

    suspend fun loadOfflineSubtitles(itemId: String, downloadPath: String) {
        val manifest = downloadRepository.loadLocalSubtitleManifest(downloadPath, itemId) ?: return
        if (manifest.subtitles.isEmpty()) return
        val parentDir = java.io.File(downloadPath).parentFile ?: return
        // Try item-scoped directory first, fall back to legacy un-scoped.
        val scopedDir = java.io.File(parentDir, "subtitles_$itemId")
        val subtitlesDir = if (scopedDir.exists()) scopedDir else java.io.File(parentDir, "subtitles")
        val engineCapabilities = getEngineCapabilities()
        for (entry in manifest.subtitles) {
            val file = java.io.File(subtitlesDir, entry.fileName)
            if (!file.exists()) continue
            // Bitmap sidecars (PGS/VOBSUB — bytes delivered verbatim via a server
            // deliveryUrl) decode only through mpv's libav decoders; Exo/LibVLC
            // would fail silently at render time, so skip them there. Mirrors the
            // streaming path in [buildExternalSubtitles], which relies on the
            // server refusing image endpoints instead.
            if (entry.isBitmapSidecar && engineCapabilities?.supportsImageSubtitles != true) {
                Log.d(
                    "SubtitleUse",
                    "loadOfflineSubtitles: skipping image sidecar index=${entry.index} " +
                        "codec=${entry.codec} — engine lacks bitmap-subtitle support",
                )
                continue
            }
            addExternalSubtitle(
                SubtitleSource(
                    url = fileUriString(file),
                    label = entry.displayTitle ?: entry.title ?: entry.language ?: "Subtitle ${entry.index}",
                    language = entry.language,
                    mimeType = null,
                    codec = entry.codec,
                    isDefault = entry.isDefault,
                    isForced = entry.isForced,
                    id = offlineSubtitleTrackId(entry.index),
                )
            )
        }
    }

    /**
     * Side-loads subtitle streams that appeared in [detail] but are not yet
     * attached to the current session — the missing step after an in-player
     * subtitle download/upload. The engine was loaded with the pre-change
     * subtitle set, and on Direct Play the picker is built purely from engine
     * tracks, so a server-attached stream stays invisible until the engine
     * itself learns about it. Re-runs [buildExternalSubtitles]' per-stream
     * gates so the mid-session set matches a fresh load, then attaches only
     * genuinely new entries (by `external:{index}` / `offline:{index}` id) to
     * avoid duplicating already side-loaded streams.
     */
    fun attachNewSubtitleStreams(detail: MediaDetail) {
        val externalSubtitles = getExternalSubtitles() ?: return
        val itemId = getCurrentItemId() ?: return
        if (detail.item.id != itemId) return
        val source = matchPlayingMediaSource(detail) ?: return
        val playMethod = getCurrentPlayMethod()
        val existingIds = externalSubtitles.map { it.id }.toSet()
        val built = buildExternalSubtitles(detail, source, playMethod)
            .filter { sub ->
                if (sub.id in existingIds) return@filter false
                // An offline-bundled sidecar with the same stream index is the
                // same subtitle — don't re-attach it under the external id.
                val index = externalSubtitleTrackStreamIndex(sub.id)
                index == null || offlineSubtitleTrackId(index) !in existingIds
            }
        Log.d(
            "SubtitleUse",
            "attachNewSubtitleStreams: playMethod=$playMethod, existing=${existingIds.size}, " +
                "attaching=${built.map { "${it.id} '${it.label.take(24)}'" }}",
        )
        built.forEach { addExternalSubtitle(it) }
    }
}
