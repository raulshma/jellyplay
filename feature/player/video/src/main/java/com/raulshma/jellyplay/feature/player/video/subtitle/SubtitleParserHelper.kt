package com.raulshma.jellyplay.feature.player.video.subtitle

import androidx.media3.common.Format
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser

@UnstableApi
data class TimedCue(
    val startTimeUs: Long,
    val endTimeUs: Long,
    val text: CharSequence,
)

@UnstableApi
object SubtitleParserHelper {

    private val defaultFactory = DefaultSubtitleParserFactory()

    fun parseSubtitles(data: ByteArray, mimeType: String): List<TimedCue> {
        val format = Format.Builder()
            .setSampleMimeType(mimeType)
            .build()

        if (!defaultFactory.supportsFormat(format)) {
            return emptyList()
        }

        val parser = defaultFactory.create(format)
        val cues = mutableListOf<CuesWithTiming>()
        val collector = Consumer<CuesWithTiming> { cues.add(it) }

        parser.parse(data, 0, data.size, SubtitleParser.OutputOptions.allCues(), collector)
        parser.reset()

        return cues.flatMap { cuesWithTiming ->
            cuesWithTiming.cues.mapNotNull { cue ->
                val text = cue.text
                if (text.isNullOrBlank()) return@mapNotNull null
                TimedCue(
                    startTimeUs = cuesWithTiming.startTimeUs,
                    endTimeUs = cuesWithTiming.endTimeUs,
                    text = text,
                )
            }
        }.sortedBy { it.startTimeUs }
    }

    fun findActiveCue(cues: List<TimedCue>, positionUs: Long, offsetUs: Long): TimedCue? =
        findActiveCues(cues, positionUs, offsetUs).firstOrNull()

    fun findActiveCues(cues: List<TimedCue>, positionUs: Long, offsetUs: Long): List<TimedCue> {
        if (cues.isEmpty()) return emptyList()
        val adjustedPosition = positionUs + offsetUs
        val startIndex = findFirstActiveCandidate(cues, adjustedPosition)
        if (startIndex < 0) return emptyList()
        val result = mutableListOf<TimedCue>()
        for (i in startIndex..cues.lastIndex) {
            val cue = cues[i]
            if (cue.startTimeUs > adjustedPosition) break
            if (adjustedPosition < cue.endTimeUs) {
                result.add(cue)
            }
        }
        return result
    }

    private fun findFirstActiveCandidate(cues: List<TimedCue>, positionUs: Long): Int {
        var low = 0
        var high = cues.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val cue = cues[mid]
            if (positionUs < cue.startTimeUs) {
                high = mid - 1
            } else if (positionUs >= cue.endTimeUs) {
                low = mid + 1
            } else {
                result = mid
                high = mid - 1
            }
        }
        if (result >= 0) return result
        return if (low in cues.indices && cues[low].startTimeUs <= positionUs) low else -1
    }
}
