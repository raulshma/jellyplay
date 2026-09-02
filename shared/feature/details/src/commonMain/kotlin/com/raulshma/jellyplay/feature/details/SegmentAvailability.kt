package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType

/**
 * The two intro/credits skip affordances the detail-side chip cares about,
 * projected from a [MediaSegment] list.
 *
 * Held by [DetailUiState] so the detail screen can render the "Intro skip
 * available" chip without the player attaching, while the act of producing it
 * (calling [PlaybackRepository.getMediaSegments]) pre-warms the player's
 * segment TTL cache so its first skip is instant.
 */
@Immutable
data class SegmentAvailability(val hasIntro: Boolean, val hasCredits: Boolean)

/**
 * Pure projection of [MediaSegment]s into the two skip affordances the detail
 * chip cares about. `INTRO` → [SegmentAvailability.hasIntro], `OUTRO` (the
 * shape legacy credit timestamps are synthesized as) → [SegmentAvailability.hasCredits].
 * Every other [MediaSegmentType] (`PREVIEW`, `RECAP`, `COMMERCIAL`, `UNKNOWN`)
 * is ignored — the chip advertises intro/credits skipping only.
 */
fun List<MediaSegment>.toAvailability(): SegmentAvailability = SegmentAvailability(
    hasIntro = any { it.type == MediaSegmentType.INTRO },
    hasCredits = any { it.type == MediaSegmentType.OUTRO },
)
