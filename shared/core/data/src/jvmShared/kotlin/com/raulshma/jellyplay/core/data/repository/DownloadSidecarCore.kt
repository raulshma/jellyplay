package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.playback.PlaybackIdentity
import com.raulshma.jellyplay.core.data.worker.awaitResponse
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.model.DownloadFileEntry
import com.raulshma.jellyplay.core.model.DownloadFileInventory
import com.raulshma.jellyplay.core.model.DownloadedFileCategory
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.OfflineSubtitleEntry
import com.raulshma.jellyplay.core.model.OfflineSubtitleManifest
import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.core.model.isImageSubtitleCodec
import com.raulshma.jellyplay.core.model.isVobsubFamilyCodec
import com.raulshma.jellyplay.core.model.subtitleCompanionFileName
import com.raulshma.jellyplay.core.model.subtitleSidecarExtension
import com.raulshma.jellyplay.core.network.auth.tokenAuthHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The sidecar/artifact half of a download — everything that lands BESIDE the
 * media file and everything that reads it back. Extracted verbatim from
 * [DownloadRepositoryImpl] (the trickplay bundle, the external-subtitle
 * bundle, segments, offline images, the local manifest/segments/inventory
 * reads, and the subtitle fetch primitives
 * [fetchSingleSidecar]/[fetchVobsubPair]/[downloadSubtitleFile]) so the
 * ~380-line cluster is unit-testable without the queue lifecycle, series
 * orchestration, or offline-metadata machinery the repository also carries.
 *
 * Ownership split with the other download collaborators:
 *  - this core owns ARTIFACT BYTES (fetch sidecar/image data, write the
 *    per-item directory grammar of [DownloadArtifacts], read it back);
 *  - [SubtitleBundleWriter] owns the manifest/orphan-prune disk semantics
 *    this core calls into at the end of a subtitle pass;
 *  - [OfflineDeletionCore] owns deletion; [DownloadRepositoryImpl] keeps
 *    queue lifecycle, series orchestration, offline metadata, and cleanup,
 *    delegating the members below one-to-one.
 *
 * The repository constructs this from its own constructor dependencies (the
 * [OfflineDeletionCore] precedent), so its public constructor — and every
 * existing test construction of it — is unchanged.
 */
internal class DownloadSidecarCore(
    private val playbackRepository: PlaybackRepository,
    private val playbackIdentity: PlaybackIdentity,
    private val downloadDao: DownloadDao,
    private val offlineMediaDao: OfflineMediaDao,
    private val syncBaselineDao: SyncBaselineDao,
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun downloadTrickplayData(
        itemId: String,
        trickplayInfo: TrickplayInfo,
        downloadPath: String,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val parentDir = File(downloadPath).parentFile ?: return@withContext false
            val trickplayDir = File(parentDir, DownloadArtifacts.trickplayDir(itemId)).apply { mkdirs() }
            val thumbnailsPerSheet = trickplayInfo.tileWidth * trickplayInfo.tileHeight
            val totalSheets = (trickplayInfo.thumbnailCount + thumbnailsPerSheet - 1) / thumbnailsPerSheet

            for (sheetIndex in 0 until totalSheets) {
                val data = playbackRepository.getTrickplayTileImage(
                    itemId,
                    trickplayInfo.width,
                    sheetIndex,
                ) ?: continue
                File(trickplayDir, "trickplay_${sheetIndex}.jpg").writeBytes(data)
            }

            File(trickplayDir, "meta.json").writeText(buildString {
                appendLine("{\"width\":${trickplayInfo.width},")
                appendLine("\"height\":${trickplayInfo.height},")
                appendLine("\"tileWidth\":${trickplayInfo.tileWidth},")
                appendLine("\"tileHeight\":${trickplayInfo.tileHeight},")
                appendLine("\"thumbnailCount\":${trickplayInfo.thumbnailCount},")
                appendLine("\"interval\":${trickplayInfo.interval},")
                appendLine("\"bandwidth\":${trickplayInfo.bandwidth}}")
            })
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Failed to write trickplay meta.json", e)
            false
        }
    }

    suspend fun downloadExternalSubtitles(
        itemId: String,
        mediaSourceId: String,
        mediaStreams: List<MediaStream>,
        downloadPath: String,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val parentDir = File(downloadPath).parentFile ?: return@withContext false
            // Drop streams the URL builders can never serve — an external
            // image-codec sub (PGS/VOBSUB) has no delivery endpoint, so
            // fetching it fails on every pass. Without this pre-filter an
            // image-only inventory would report failure forever and the resync
            // would retry a permanently-unfetchable list each sync.
            val subtitleStreams = mediaStreams
                .filter { it.isBundleableSubtitle }
                .filterNot { it.isExternal && it.deliveryUrl.isNullOrBlank() && isImageSubtitleCodec(it.codec) }
            val subtitlesDir = File(parentDir, DownloadArtifacts.subtitlesDir(itemId))

            // Nothing deliverable remains — either a genuine server-side
            // removal or an inventory of never-fetchable streams. Mirror that
            // to disk and report success (the baseline seeds as empty). Doing
            // this here — not on fetch failure — means a server change is
            // reflected without wiping sidecars on a transient error.
            if (subtitleStreams.isEmpty()) {
                if (subtitlesDir.exists()) subtitlesDir.deleteRecursively()
                return@withContext true
            }

            subtitlesDir.mkdirs()
            val entries = mutableListOf<OfflineSubtitleEntry>()
            // Any stream that fails to resolve a delivery URL or fetch marks
            // the pass incomplete; only a complete pass may prune (contract in
            // [pruneOrphanSidecarFiles]).
            var incompletePass = false

            for (stream in subtitleStreams) {
                try {
                    val subUrl = when {
                        !stream.deliveryUrl.isNullOrBlank() ->
                            playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
                        stream.isExternal ->
                            playbackRepository.buildSubtitleDeliveryUrl(itemId, mediaSourceId, stream.index, stream.codec)
                        else -> continue
                    }
                    if (subUrl.isBlank()) {
                        incompletePass = true
                        continue
                    }

                    // VobSub renders only as an .idx+.sub pair; both halves are
                    // fetched and the manifest points at the .idx (the player's
                    // vobsub demuxer picks up the .sub sibling by base name).
                    val fileName = if (isVobsubFamilyCodec(stream.codec)) {
                        fetchVobsubPair(subUrl, subtitlesDir, stream.index)
                    } else {
                        fetchSingleSidecar(subUrl, subtitlesDir, stream.index, stream.codec)
                    }
                    if (fileName == null) {
                        incompletePass = true
                        continue
                    }

                    entries.add(
                        OfflineSubtitleEntry(
                            index = stream.index,
                            fileName = fileName,
                            language = stream.language,
                            codec = stream.codec,
                            title = stream.title,
                            displayTitle = stream.displayTitle,
                            isDefault = stream.isDefault,
                            isForced = stream.isForced,
                            isImage = isImageSubtitleCodec(stream.codec),
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    incompletePass = true
                    Log.d(TAG, "Failed to download subtitle stream ${stream.index} for $itemId", e)
                }
            }

            if (entries.isNotEmpty()) {
                // Persist a manifest describing exactly what landed on disk.
                writeSubtitleManifest(subtitlesDir, entries, json)
                if (!incompletePass) {
                    // Pair halves count as live alongside the manifest's own
                    // entry — a pruned .idx or .sub breaks the whole pair.
                    val liveNames = entries.flatMap {
                        listOfNotNull(it.fileName, subtitleCompanionFileName(it.fileName))
                    }.toSet()
                    pruneOrphanSidecarFiles(subtitlesDir, liveNames)
                }
                true
            } else {
                // Deliverable streams existed but none fetched (transient
                // network/auth/delivery-URL failure). Leave the existing dir and
                // manifest untouched and report failure so the resync baseline
                // rolls its subtitle axis back and the next sync retries — instead
                // of destroying working sidecars and seeding the baseline as synced.
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Failed to download external subtitles for $itemId", e)
            false
        }
    }

    suspend fun markSubtitlesPending(itemId: String) {
        // Atomicity and the stub/raise pairing live on the @Transaction DAO
        // method — the canonical description of this flag's lifecycle.
        syncBaselineDao.markSubtitlesPending(itemId)
    }

    suspend fun downloadMediaSegments(itemId: String, downloadPath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val segments = playbackRepository.getMediaSegments(itemId).getOrDefault(emptyList())
                if (segments.isEmpty()) return@withContext true
                val parentDir = File(downloadPath).parentFile ?: return@withContext false
                File(parentDir, DownloadArtifacts.segmentsFile(itemId))
                    .writeText(json.encodeToString(segments))
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Failed to download media segments for $itemId", e)
                false
            }
        }

    /**
     * Downloads the given item's image to a local file so it is viewable fully
     * offline. Returns the absolute file path on success, or null if the item
     * has no such image or the download failed — callers then fall back to the
     * remote URL so an image fetch failure never blocks a download.
     *
     * @param parentDir directory that holds the downloaded media (the image is
     *   written as a sibling file there, matching the other offline artifacts).
     */
    suspend fun downloadImageToDisk(
        itemId: String,
        imageType: String,
        maxWidth: Int,
        parentDir: File,
        fileName: String,
    ): String? = withContext(Dispatchers.IO) {
        val bytes = playbackRepository.getItemImageBytes(itemId, imageType, maxWidth)
            ?: return@withContext null
        if (bytes.isEmpty()) return@withContext null
        val target = File(parentDir, fileName)
        try {
            target.writeBytes(bytes)
            target.absolutePath
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Failed to write offline image $fileName for $itemId", e)
            null
        }
    }

    suspend fun loadLocalSubtitleManifest(
        downloadPath: String,
        itemId: String?,
    ): OfflineSubtitleManifest? = withContext(Dispatchers.IO) {
        val dir = File(downloadPath).parentFile ?: return@withContext null
        // Try item-scoped path first (new downloads).
        if (itemId != null) {
            val scopedFile = File(dir, "${DownloadArtifacts.subtitlesDir(itemId)}/${DownloadArtifacts.SUBTITLE_MANIFEST_FILE}")
            if (scopedFile.exists()) {
                return@withContext runCatchingRethrowingCancellation { json.decodeFromString<OfflineSubtitleManifest>(scopedFile.readText()) }
                    .onFailure { Log.w(TAG, "Failed to decode local subtitle manifest", it) }
                    .getOrNull()
            }
        }
        // Fall back to legacy un-scoped path (pre-fix downloads).
        val file = File(dir, "${DownloadArtifacts.LEGACY_SUBTITLES_DIR}/${DownloadArtifacts.SUBTITLE_MANIFEST_FILE}")
        if (!file.exists()) return@withContext null
        runCatchingRethrowingCancellation { json.decodeFromString<OfflineSubtitleManifest>(file.readText()) }
            .onFailure { Log.w(TAG, "Failed to decode local subtitle manifest", it) }
            .getOrNull()
    }

    suspend fun loadLocalSegments(itemId: String): List<MediaSegment>? = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownloadByMediaItemId(itemId) ?: return@withContext null
        val dir = File(download.downloadPath).parentFile ?: return@withContext null
        // Try item-scoped file first.
        val scopedFile = File(dir, DownloadArtifacts.segmentsFile(itemId))
        val file = if (scopedFile.exists()) scopedFile else {
            val legacy = File(dir, DownloadArtifacts.LEGACY_SEGMENTS_FILE)
            if (!legacy.exists()) return@withContext null
            legacy
        }
        runCatchingRethrowingCancellation { json.decodeFromString<List<MediaSegment>>(file.readText()) }
            .onFailure { Log.w(TAG, "Failed to decode local segments", it) }
            .getOrNull()
    }

    suspend fun getDownloadFileInventory(itemId: String): DownloadFileInventory = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownloadByMediaItemId(itemId)
        val mediaPath = download?.downloadPath?.takeIf { it.isNotBlank() && File(it).isFile }
        if (mediaPath == null) return@withContext DownloadFileInventory.EMPTY
        val parentDir = File(mediaPath).parentFile ?: return@withContext DownloadFileInventory.EMPTY

        // Person ids (for cast-image enumeration) + series id (for series-keyed
        // artwork) are sourced from the offline_media row; the downloads row
        // alone doesn't carry cast. Both tables are keyed by the same item id.
        val offline = offlineMediaDao.getById(itemId)
        val seriesId = download.seriesId ?: offline?.seriesId
        val personIds = offline?.let { decodeCast(it.peopleJson).map { person -> person.id } } ?: emptyList()

        val entries = mutableListOf<DownloadFileEntry>()

        fun addFile(category: DownloadedFileCategory, file: File) {
            if (file.isFile) {
                entries += DownloadFileEntry(
                    category = category,
                    displayName = file.name,
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                )
            }
        }

        // ── Media file ──
        addFile(DownloadedFileCategory.MEDIA, File(mediaPath))

        // ── Trickplay sprite sheets + meta (item-scoped dir, then legacy) ──
        listOf(DownloadArtifacts.trickplayDir(itemId), DownloadArtifacts.LEGACY_TRICKPLAY_DIR)
            .map { File(parentDir, it) }
            .filter { it.isDirectory }
            .forEach { dir ->
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    addFile(DownloadedFileCategory.TRICKPLAY, f)
                }
            }

        // ── Subtitle bundle (item-scoped dir, then legacy) ──
        listOf(DownloadArtifacts.subtitlesDir(itemId), DownloadArtifacts.LEGACY_SUBTITLES_DIR)
            .map { File(parentDir, it) }
            .filter { it.isDirectory }
            .forEach { dir ->
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    addFile(DownloadedFileCategory.SUBTITLE, f)
                }
            }

        // ── Segments (intro/outro/recap markers JSON) ──
        addFile(DownloadedFileCategory.SEGMENT, File(parentDir, DownloadArtifacts.segmentsFile(itemId)))
        addFile(DownloadedFileCategory.SEGMENT, File(parentDir, DownloadArtifacts.LEGACY_SEGMENTS_FILE))

        // ── Images: per-item poster/backdrop, series-keyed artwork, cast portraits ──
        addFile(DownloadedFileCategory.IMAGE, File(parentDir, DownloadArtifacts.posterFile(itemId)))
        addFile(DownloadedFileCategory.IMAGE, File(parentDir, DownloadArtifacts.backdropFile(itemId)))
        if (!seriesId.isNullOrBlank() && seriesId != itemId) {
            addFile(DownloadedFileCategory.IMAGE, File(parentDir, DownloadArtifacts.posterFile(seriesId)))
            addFile(DownloadedFileCategory.IMAGE, File(parentDir, DownloadArtifacts.backdropFile(seriesId)))
        }
        personIds.forEach { personId ->
            addFile(DownloadedFileCategory.IMAGE, File(parentDir, DownloadArtifacts.personImageFile(personId)))
        }

        DownloadFileInventory(
            entries = entries.sortedBy { it.category.ordinal },
            totalSizeBytes = entries.sumOf { it.sizeBytes },
        )
    }

    /**
     * Fetches one text/image sidecar as `{index}.{ext}`. Returns the manifest
     * file name, or null when the fetch failed (the caller marks the pass
     * incomplete; [downloadSubtitleFile]'s temp-file write leaves any previous
     * pass's sidecar untouched).
     */
    private suspend fun fetchSingleSidecar(
        subUrl: String,
        subtitlesDir: File,
        index: Int,
        codec: String?,
    ): String? {
        val fileName = "$index.${subtitleSidecarExtension(codec)}"
        return if (downloadSubtitleFile(subUrl, File(subtitlesDir, fileName))) fileName else null
    }

    /**
     * Fetches both halves of a VobSub pair ([index].idx palette + [index].sub
     * bitmap). The server's deliveryUrl for an external VobSub stream points
     * at whichever file the MediaStream advertises; [vobsubPairUrls] derives
     * the other half. Either half alone is unrenderable, so both fetches must
     * succeed. Returns the manifest file name (`{index}.idx`, which the
     * player's vobsub demuxer pairs with the `.sub` sibling), or null.
     *
     * On failure nothing is deleted: [downloadSubtitleFile] stages writes in
     * temp files, so a pair fetched by a previous successful pass stays
     * intact and keeps serving the still-valid manifest until the resync
     * retries.
     */
    private suspend fun fetchVobsubPair(subUrl: String, subtitlesDir: File, index: Int): String? {
        val (paletteUrl, bitmapUrl) = vobsubPairUrls(subUrl)
        val idxFile = File(subtitlesDir, "$index.idx")
        val subFile = File(subtitlesDir, "$index.sub")
        if (!downloadSubtitleFile(paletteUrl, idxFile)) return null
        if (!downloadSubtitleFile(bitmapUrl, subFile)) return null
        return idxFile.name
    }

    /**
     * Fetches one subtitle sidecar to [target]. Subtitle-specific policy lives
     * here on purpose: the auth-header fallback and the HTML/JSON rejection
     * below apply only to subtitle fetches (the video transfer in
     * DownloadTransferClient has its own), so this is deliberately not a
     * generic file-download helper.
     */
    private suspend fun downloadSubtitleFile(url: String, target: File): Boolean {
        // Staged write: the stream goes to a `.part` sibling and is moved into
        // place only when complete, so a mid-transfer failure never truncates
        // (or replaces) the sidecar a previous successful pass wrote — offline
        // playback keeps serving it until the resync retries.
        val staging = File(target.parentFile, target.name + ".part")
        return try {
            // Auth rides on the baked-in ApiKey query param, with the
            // Authorization header as a fallback for servers/reverse proxies
            // that reject or strip query-token auth (the same pairing
            // DownloadTransferClient uses for the video itself).
            val requestBuilder = Request.Builder().url(url)
            playbackIdentity.accessToken()?.takeIf { it.isNotBlank() }?.let {
                requestBuilder.tokenAuthHeader(it)
            }
            httpClient.newCall(requestBuilder.build()).awaitResponse().use { resp ->
                if (!resp.isSuccessful) return@use false
                // An auth/proxy failure can arrive as HTTP 200 with an HTML
                // login page (or a JSON error body); persisting either yields a
                // sidecar the player can't parse — indistinguishable offline
                // from "subtitle missing". No subtitle format is ever served
                // as HTML/JSON, so both are rejected outright.
                val contentType = resp.header("Content-Type")?.lowercase().orEmpty()
                if (REJECTED_SUBTITLE_CONTENT_TYPES.any { contentType.contains(it) }) {
                    Log.d(TAG, "Rejected subtitle response from $url: Content-Type $contentType")
                    return@use false
                }
                var moved = false
                resp.body?.byteStream()?.use { input ->
                    staging.outputStream().use { output -> input.copyTo(output) }
                    Files.move(
                        staging.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                    moved = true
                }
                moved
            }
        } catch (e: CancellationException) {
            staging.delete()
            throw e
        } catch (e: Exception) {
            staging.delete()
            Log.d(TAG, "Failed to download file from $url", e)
            false
        }
    }

    companion object {
        private const val TAG = "DownloadSidecarCore"

        // Content types that can never be a subtitle file. An HTTP 200 body of
        // one of these is an auth/proxy error page, not sidecar content.
        private val REJECTED_SUBTITLE_CONTENT_TYPES =
            listOf("text/html", "application/json")
    }
}
