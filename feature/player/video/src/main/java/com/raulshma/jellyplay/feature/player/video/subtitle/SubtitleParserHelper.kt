package com.raulshma.jellyplay.feature.player.video.subtitle

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
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

        parser.parse(data, 0, data.size, SubtitleParser.OutputOptions.onlyCuesAfter(C.TIME_UNSET), collector)
        parser.reset()

        return cues.flatMap { cuesWithTiming ->
            cuesWithTiming.cues.map { cue ->
                TimedCue(
                    startTimeUs = cuesWithTiming.startTimeUs,
                    endTimeUs = cuesWithTiming.endTimeUs,
                    text = cue.text ?: "",
                )
            }
        }.sortedBy { it.startTimeUs }
    }

    fun findActiveCue(cues: List<TimedCue>, positionUs: Long, offsetUs: Long): TimedCue? {
        val adjustedPosition = positionUs + offsetUs
        return cues.find { cue ->
            adjustedPosition >= cue.startTimeUs && adjustedPosition < cue.endTimeUs
        }
    }
}
