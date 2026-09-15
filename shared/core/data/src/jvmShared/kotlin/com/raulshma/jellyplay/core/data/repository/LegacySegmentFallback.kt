package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType

/**
 * Pure legacy fallback synthesis of [PlaybackRepositoryImpl.getMediaSegments]:
 * servers on pre-10.10 Jellyfin have no MediaSegments endpoint, so when the
 * segments API answers empty the repository consults the old intro/credit
 * timestamp endpoints and maps their payloads into the equivalent
 * [MediaSegment]s. Behaviour moved verbatim from the facade's former inline
 * fallback block; the repository keeps only the choreography (the parallel
 * legacy reads, the success-gated cache decision).
 *
 * Mapping edges:
 *  - credits become [MediaSegmentType.OUTRO] — the segment vocabulary has no
 *    CREDIT type; OUTRO is the credits segment ("Skip Credits" label);
 *  - ids are synthesized as `legacy-intro-{itemId}` / `legacy-outro-{itemId}`
 *    off the timestamps' *own* [IntroTimestamps.itemId] /
 *    [CreditTimestamps.itemId] (the server echoes the item it answered for),
 *    and are stable wire/cache identities — never rename the prefixes;
 *  - `hasIntro` / `hasCredits` gate each half (strict end > start): a null
 *    payload (endpoint failed) or an empty/inverted run contributes nothing;
 *  - the intro segment always precedes the outro.
 */
internal fun legacySegmentFallback(
    intro: IntroTimestamps?,
    credits: CreditTimestamps?,
): List<MediaSegment> = buildList {
    intro?.let { ts ->
        if (ts.hasIntro) {
            add(
                MediaSegment(
                    id = "legacy-intro-${ts.itemId}",
                    itemId = ts.itemId,
                    type = MediaSegmentType.INTRO,
                    startTicks = ts.introStartTicks,
                    endTicks = ts.introEndTicks,
                )
            )
        }
    }
    credits?.let { ts ->
        if (ts.hasCredits) {
            add(
                MediaSegment(
                    id = "legacy-outro-${ts.itemId}",
                    itemId = ts.itemId,
                    type = MediaSegmentType.OUTRO,
                    startTicks = ts.creditStartTicks,
                    endTicks = ts.creditEndTicks,
                )
            )
        }
    }
}
