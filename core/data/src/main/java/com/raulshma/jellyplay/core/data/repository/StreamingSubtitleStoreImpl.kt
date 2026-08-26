package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import com.raulshma.jellyplay.core.model.subtitle.SavedSubtitle
import com.raulshma.jellyplay.core.model.subtitle.StreamingSubtitleManifest
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * File-backed [StreamingSubtitleStore]. Layout under `filesDir`:
 *
 *  - `streaming-subtitles/<itemId>/manifest.json`
 *  - `streaming-subtitles/<itemId>/<provider>_<id>.<ext>`   subtitle bytes
 *
 * Uses `filesDir` (durable) deliberately — the disposable cache copy the
 * `SubtitleManager` keeps for the immediate side-load is distinct. The store
 * owns this subtree, so callers never build these paths directly.
 */
class StreamingSubtitleStoreImpl(
    private val context: Context,
    private val json: Json,
) : StreamingSubtitleStore {

    private val rootDir: File
        get() = File(context.filesDir, ROOT_DIR_NAME)

    private fun itemDir(itemId: String): File = File(rootDir, itemId)

    private fun manifestFile(itemId: String): File =
        File(itemDir(itemId), MANIFEST_FILE_NAME)

    override suspend fun save(
        itemId: String,
        provider: SubtitleProviderKind,
        providerSubtitleId: String,
        fileName: String,
        language: String?,
        codec: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
        bytes: ByteArray,
    ): SavedSubtitle = withContext(Dispatchers.IO) {
        val dir = itemDir(itemId).apply { mkdirs() }
        val ext = codec?.takeIf { it.isNotBlank() }
            ?: fileName.substringAfterLast('.', "srt")
        // Stable filename keyed by provider+id so repeated downloads overwrite
        // rather than accumulate. Sanitized the same way the cache path is.
        val safeId = "${provider.name.lowercase()}_$providerSubtitleId"
            .replace(Regex("[^A-Za-z0-9_-]"), "")
        val storedFileName = "$safeId.$ext"
        File(dir, storedFileName).writeBytes(bytes)

        val saved = SavedSubtitle(
            provider = provider,
            providerSubtitleId = providerSubtitleId,
            fileName = fileName,
            language = language,
            codec = codec,
            isForced = isForced,
            isHearingImpaired = isHearingImpaired,
            fileRelativePath = storedFileName,
        )

        val updated = readManifest(itemId).let { manifest ->
            val without = manifest.subtitles.filterNot { existing ->
                existing.provider == provider && existing.providerSubtitleId == providerSubtitleId
            }
            StreamingSubtitleManifest(subtitles = without + saved)
        }
        writeManifest(itemId, updated)
        saved
    }

    override suspend fun loadAll(itemId: String): List<SavedSubtitle> = withContext(Dispatchers.IO) {
        readManifest(itemId).subtitles
    }

    override suspend fun fileFor(itemId: String, saved: SavedSubtitle): File = withContext(Dispatchers.IO) {
        File(itemDir(itemId), saved.fileRelativePath)
    }

    override suspend fun delete(itemId: String, saved: SavedSubtitle) {
        withContext(Dispatchers.IO) {
            File(itemDir(itemId), saved.fileRelativePath).takeIf { it.exists() }?.delete()
            val updated = readManifest(itemId).let { manifest ->
                StreamingSubtitleManifest(
                    subtitles = manifest.subtitles.filterNot { existing ->
                        existing.provider == saved.provider &&
                            existing.providerSubtitleId == saved.providerSubtitleId
                    },
                )
            }
            writeManifest(itemId, updated)
        }
    }

    override suspend fun clear(itemId: String) {
        withContext(Dispatchers.IO) {
            itemDir(itemId).takeIf { it.exists() }?.deleteRecursively()
        }
    }

    private fun readManifest(itemId: String): StreamingSubtitleManifest {
        val file = manifestFile(itemId)
        if (!file.exists()) return StreamingSubtitleManifest()
        return runCatching {
            json.decodeFromString(StreamingSubtitleManifest.serializer(), file.readText())
        }.getOrDefault(StreamingSubtitleManifest())
    }

    private fun writeManifest(itemId: String, manifest: StreamingSubtitleManifest) {
        val file = manifestFile(itemId)
        if (manifest.subtitles.isEmpty()) {
            // Empty manifest → drop the whole item dir so streaming-subtitles
            // doesn't accumulate orphan empty directories over time.
            itemDir(itemId).takeIf { it.exists() }?.deleteRecursively()
            return
        }
        file.writeText(json.encodeToString(StreamingSubtitleManifest.serializer(), manifest))
    }

    private companion object {
        const val ROOT_DIR_NAME = "streaming-subtitles"
        const val MANIFEST_FILE_NAME = "manifest.json"
    }
}
