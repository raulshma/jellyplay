package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class MediaSegmentType(
    val displayName: String,
    val description: String,
    val colorLong: Long,
    val skipLabel: String,
) {
    INTRO(
        displayName = "Intro",
        description = "Opening/intro sequence",
        colorLong = 0xFF66BB6A,
        skipLabel = "Skip Intro",
    ),
    OUTRO(
        displayName = "Outro",
        description = "Credits/outro sequence",
        colorLong = 0xFF42A5F5,
        skipLabel = "Skip Credits",
    ),
    PREVIEW(
        displayName = "Preview",
        description = "Coming up / cold open / teaser",
        colorLong = 0xFFFFA726,
        skipLabel = "Skip Preview",
    ),
    RECAP(
        displayName = "Recap",
        description = "Previously on...",
        colorLong = 0xFFAB47BC,
        skipLabel = "Skip Recap",
    ),
    COMMERCIAL(
        displayName = "Commercial",
        description = "Commercial break",
        colorLong = 0xFFEF5350,
        skipLabel = "Skip Commercial",
    ),
    UNKNOWN(
        displayName = "Other",
        description = "Custom segment",
        colorLong = 0xFF78909C,
        skipLabel = "Skip Segment",
    ),
    ;

    companion object {
        private val API_NAME_MAP = entries.associateBy { it.name.uppercase() }

        fun fromApiName(name: String): MediaSegmentType =
            API_NAME_MAP[name.uppercase()] ?: UNKNOWN

        val SEGMENT_PRIORITY = listOf(
            COMMERCIAL, RECAP, PREVIEW, INTRO, OUTRO, UNKNOWN,
        )
    }
}

@Immutable
@Serializable
data class MediaSegment(
    @SerialName("Id") val id: String,
    @SerialName("ItemId") val itemId: String,
    @SerialName("Type") val type: MediaSegmentType,
    @SerialName("StartTicks") val startTicks: Long,
    @SerialName("EndTicks") val endTicks: Long,
) {
    val hasSegment: Boolean get() = endTicks > startTicks
    val startMs: Long get() = startTicks / 10_000
    val endMs: Long get() = endTicks / 10_000
    val durationMs: Long get() = (endTicks - startTicks) / 10_000
}

@Immutable
@Serializable
enum class SegmentBehavior(
    val displayName: String,
    val description: String,
) {
    SHOW_BUTTON(
        displayName = "Show Button",
        description = "Show a skip button during the segment",
    ),
    AUTO_SKIP(
        displayName = "Auto-Skip",
        description = "Automatically skip past the segment",
    ),
    IGNORE(
        displayName = "Ignore",
        description = "Do nothing for this segment type",
    ),
    ;

    companion object {
        val DEFAULT_BEHAVIORS: Map<MediaSegmentType, SegmentBehavior> = mapOf(
            MediaSegmentType.INTRO to SHOW_BUTTON,
            MediaSegmentType.OUTRO to SHOW_BUTTON,
            MediaSegmentType.PREVIEW to IGNORE,
            MediaSegmentType.RECAP to IGNORE,
            MediaSegmentType.COMMERCIAL to AUTO_SKIP,
            MediaSegmentType.UNKNOWN to IGNORE,
        )
    }
}
