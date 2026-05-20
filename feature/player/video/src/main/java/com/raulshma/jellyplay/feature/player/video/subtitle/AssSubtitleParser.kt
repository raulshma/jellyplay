package com.raulshma.jellyplay.feature.player.video.subtitle

import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Consumer
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser

class AssSubtitleParser : SubtitleParser {

    companion object {
        private val ASS_TAG_REGEX = Regex("\\{[^}]*}")
    }

    override fun getCueReplacementBehavior(): Int = androidx.media3.common.Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>,
    ) {
        val text = String(data, offset, length, Charsets.UTF_8)
        val timedCues = parseAssEvents(text)
        val startTimeUs = outputOptions.startTimeUs

        for (timedCue in timedCues) {
            if (startTimeUs != C.TIME_UNSET && timedCue.endTimeUs < startTimeUs) continue
            val cue = Cue.Builder()
                .setText(timedCue.text)
                .build()
            output.accept(
                CuesWithTiming(
                    listOf(cue),
                    timedCue.startTimeUs,
                    timedCue.endTimeUs - timedCue.startTimeUs,
                )
            )
        }
    }

    override fun reset() {}

    internal fun parseAssEvents(text: String): List<TimedCue> {
        val result = mutableListOf<TimedCue>()
        var inEvents = false
        var formatFields: List<String>? = null

        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[Events]")) {
                inEvents = true
                continue
            }
            if (trimmed.startsWith("[")) {
                inEvents = false
                continue
            }
            if (!inEvents) continue

            if (trimmed.startsWith("Format:")) {
                formatFields = trimmed.removePrefix("Format:").split(",").map { it.trim() }
                continue
            }

            if (trimmed.startsWith("Dialogue:") && formatFields != null) {
                val parts = trimmed.removePrefix("Dialogue:").split(",", limit = formatFields.size)
                if (parts.size < 10) continue

                val textIndex = formatFields.indexOf("Text")
                if (textIndex < 0 || textIndex >= parts.size) continue

                val startStr = parts.getOrNull(formatFields.indexOf("Start")) ?: continue
                val endStr = parts.getOrNull(formatFields.indexOf("End")) ?: continue

                val startUs = parseAssTimestamp(startStr.trim()) ?: continue
                val endUs = parseAssTimestamp(endStr.trim()) ?: continue

                val rawText = parts[textIndex].trim()
                    .replace("\\N", "\n")
                    .replace("\\n", "\n")
                    .replace("\\h", " ")
                    .replace(ASS_TAG_REGEX, "")

                if (rawText.isBlank()) continue

                result.add(
                    TimedCue(
                        startTimeUs = startUs,
                        endTimeUs = endUs,
                        text = rawText,
                    )
                )
            }
        }

        return result.sortedBy { it.startTimeUs }
    }

    private fun parseAssTimestamp(ts: String): Long? {
        val parts = ts.split(":")
        if (parts.size != 3) return null
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        val secParts = parts[2].split(".")
        val seconds = secParts.getOrNull(0)?.toIntOrNull() ?: return null
        val centiseconds = secParts.getOrNull(1)?.toIntOrNull() ?: 0
        return ((hours * 3600L + minutes * 60L + seconds) * 1000L + centiseconds * 10L) * 1000L
    }
}
