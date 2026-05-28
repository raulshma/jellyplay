package com.raulshma.jellyplay.feature.player.video.trickplay

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.TrickplayInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object OfflineTrickplayHelper {

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

    fun getLocalTrickplayDir(downloadPath: String): File? {
        val file = File(downloadPath)
        val parent = file.parentFile ?: return null
        return File(parent, "trickplay")
    }

    fun loadLocalTrickplayInfo(downloadPath: String): TrickplayInfo? {
        val trickplayDir = getLocalTrickplayDir(downloadPath) ?: return null
        val metaFile = File(trickplayDir, "meta.json")
        if (!metaFile.exists()) return null

        return try {
            val text = metaFile.readText()
            val width = text.regexMatch("\"width\"\\s*:\\s*(\\d+)")?.toIntOrNull() ?: return null
            val height = text.regexMatch("\"height\"\\s*:\\s*(\\d+)")?.toIntOrNull() ?: return null
            val tileWidth = text.regexMatch("\"tileWidth\"\\s*:\\s*(\\d+)")?.toIntOrNull() ?: return null
            val tileHeight = text.regexMatch("\"tileHeight\"\\s*:\\s*(\\d+)")?.toIntOrNull() ?: return null
            val thumbnailCount = text.regexMatch("\"thumbnailCount\"\\s*:\\s*(\\d+)")?.toIntOrNull() ?: return null
            val interval = text.regexMatch("\"interval\"\\s*:\\s*(\\d+)")?.toIntOrNull() ?: return null
            val bandwidth = text.regexMatch("\"bandwidth\"\\s*:\\s*(\\d+)")?.toIntOrNull() ?: 0

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

    private fun String.regexMatch(pattern: String): String? {
        val regex = Regex(pattern)
        return regex.find(this)?.groupValues?.getOrNull(1)
    }
}
