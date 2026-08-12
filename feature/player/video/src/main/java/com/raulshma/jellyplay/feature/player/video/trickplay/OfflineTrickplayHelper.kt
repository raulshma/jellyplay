package com.raulshma.jellyplay.feature.player.video.trickplay

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.TrickplayInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object OfflineTrickplayHelper {

    // Compiled once and reused — previously `Regex` was recompiled on every
    // `loadLocalTrickplayInfo` call (7× per call).
    private val WIDTH_REGEX = Regex("\"width\"\\s*:\\s*(\\d+)")
    private val HEIGHT_REGEX = Regex("\"height\"\\s*:\\s*(\\d+)")
    private val TILE_WIDTH_REGEX = Regex("\"tileWidth\"\\s*:\\s*(\\d+)")
    private val TILE_HEIGHT_REGEX = Regex("\"tileHeight\"\\s*:\\s*(\\d+)")
    private val THUMBNAIL_COUNT_REGEX = Regex("\"thumbnailCount\"\\s*:\\s*(\\d+)")
    private val INTERVAL_REGEX = Regex("\"interval\"\\s*:\\s*(\\d+)")
    private val BANDWIDTH_REGEX = Regex("\"bandwidth\"\\s*:\\s*(\\d+)")

    suspend fun downloadTrickplayData(
        itemId: String,
        trickplayInfo: TrickplayInfo,
        playbackRepository: PlaybackRepository,
        targetDir: File,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val trickplayDir = File(targetDir, "trickplay").apply { mkdirs() }
                val thumbnailsPerSheet = trickplayInfo.tileWidth * trickplayInfo.tileHeight
                val totalSheets = (trickplayInfo.thumbnailCount + thumbnailsPerSheet - 1) / thumbnailsPerSheet

                for (sheetIndex in 0 until totalSheets) {
                    val data = playbackRepository.getTrickplayTileImage(
                        itemId,
                        trickplayInfo.width,
                        sheetIndex,
                    ) ?: continue

                    val file = File(trickplayDir, "trickplay_${sheetIndex}.jpg")
                    file.writeBytes(data)
                }

                val metaFile = File(trickplayDir, "meta.json")
                metaFile.writeText(buildString {
                    appendLine("{\"width\":${trickplayInfo.width},")
                    appendLine("\"height\":${trickplayInfo.height},")
                    appendLine("\"tileWidth\":${trickplayInfo.tileWidth},")
                    appendLine("\"tileHeight\":${trickplayInfo.tileHeight},")
                    appendLine("\"thumbnailCount\":${trickplayInfo.thumbnailCount},")
                    appendLine("\"interval\":${trickplayInfo.interval},")
                    appendLine("\"bandwidth\":${trickplayInfo.bandwidth}}")
                })
            } catch (_: Exception) { }
        }
    }

    fun getLocalTrickplayDir(downloadPath: String, itemId: String? = null): File? {
        val file = File(downloadPath)
        val parent = file.parentFile ?: return null
        // Try item-scoped directory first, fall back to legacy un-scoped.
        if (itemId != null) {
            val scoped = File(parent, "trickplay_$itemId")
            if (scoped.exists()) return scoped
        }
        return File(parent, "trickplay")
    }

    suspend fun loadLocalTrickplayInfo(downloadPath: String, itemId: String? = null): TrickplayInfo? {
        val trickplayDir = getLocalTrickplayDir(downloadPath, itemId) ?: return null
        val metaFile = File(trickplayDir, "meta.json")
        if (!metaFile.exists()) return null

        return try {
            val text = withContext(Dispatchers.IO) { metaFile.readText() }
            val width = WIDTH_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
            val height = HEIGHT_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
            val tileWidth = TILE_WIDTH_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
            val tileHeight = TILE_HEIGHT_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
            val thumbnailCount = THUMBNAIL_COUNT_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
            val interval = INTERVAL_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
            val bandwidth = BANDWIDTH_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

            TrickplayInfo(
                width = width,
                height = height,
                tileWidth = tileWidth,
                tileHeight = tileHeight,
                thumbnailCount = thumbnailCount,
                interval = interval,
                bandwidth = bandwidth,
            )
        } catch (_: Exception) { null }
    }
}
