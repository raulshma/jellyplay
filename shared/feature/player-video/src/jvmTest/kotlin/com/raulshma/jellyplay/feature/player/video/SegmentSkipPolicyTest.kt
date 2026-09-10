package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaSegmentType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the PRODUCTION segment-skip ladder ([SegmentSkipPolicy.kt]) — the
 * cinema-intro escape, the outro-near-end next-episode branch, the
 * active-segment seek and the end-ticks fallback that used to live inline in
 * [VideoPlayerViewModel]'s skipIntro / skipCredits where no test could reach
 * them. Replaces the old SkipIntroCreditsTest, whose assertions were
 * ticks→ms division tautologies that never exercised a decision.
 */
class SegmentSkipLadderTest {

    private fun target(
        kind: SegmentSkipKind,
        cinemaIntroActive: Boolean = false,
        isOutroNearEnd: Boolean = false,
        canSkipToNext: Boolean = false,
        activeSegmentType: MediaSegmentType? = null,
        activeSegmentEndTicks: Long? = null,
        introEndTicks: Long? = null,
        creditEndTicks: Long? = null,
    ): SegmentSkipTarget = segmentSkipTarget(
        kind = kind,
        cinemaIntroActive = cinemaIntroActive,
        isOutroNearEnd = isOutroNearEnd,
        canSkipToNext = canSkipToNext,
        segments = SegmentSnapshot(
            activeType = activeSegmentType,
            activeEndTicks = activeSegmentEndTicks,
            introEndTicks = introEndTicks,
            creditEndTicks = creditEndTicks,
        ),
    )

    // ── Cinema-intro escape ───────────────────────────────────────────────

    @Test
    fun intro_cinemaIntro_beatsActiveSegmentAndFallback() {
        assertEquals(
            SegmentSkipTarget.AdvanceCinemaIntro,
            target(
                kind = SegmentSkipKind.INTRO,
                cinemaIntroActive = true,
                activeSegmentType = MediaSegmentType.INTRO,
                activeSegmentEndTicks = 300_000_000L,
                introEndTicks = 300_000_000L,
            ),
        )
    }

    @Test
    fun credits_hasNoCinemaEscape() {
        // The pre-roll has no credits: skipCredits never advances it.
        assertEquals(
            SegmentSkipTarget.None,
            target(kind = SegmentSkipKind.CREDITS, cinemaIntroActive = true),
        )
    }

    // ── Outro-near-end next-episode branch (credits only) ────────────────

    @Test
    fun credits_outroNearEndAndCanSkip_playsNextEpisode_evenOverActiveOutro() {
        assertEquals(
            SegmentSkipTarget.SkipToNextEpisode,
            target(
                kind = SegmentSkipKind.CREDITS,
                isOutroNearEnd = true,
                canSkipToNext = true,
                activeSegmentType = MediaSegmentType.OUTRO,
                activeSegmentEndTicks = 3_600_000_000L,
                creditEndTicks = 3_600_000_000L,
            ),
        )
    }

    @Test
    fun credits_outroNearEnd_cannotSkip_fallsBackToActiveSegment() {
        assertEquals(
            SegmentSkipTarget.SeekToPosition(360_000L),
            target(
                kind = SegmentSkipKind.CREDITS,
                isOutroNearEnd = true,
                canSkipToNext = false,
                activeSegmentType = MediaSegmentType.OUTRO,
                activeSegmentEndTicks = 3_600_000_000L,
            ),
        )
    }

    @Test
    fun credits_outroNearEnd_cannotSkip_noSegment_fallsToCreditEndTicks() {
        assertEquals(
            SegmentSkipTarget.SeekToPosition(360_000L),
            target(
                kind = SegmentSkipKind.CREDITS,
                isOutroNearEnd = true,
                canSkipToNext = false,
                creditEndTicks = 3_600_000_000L,
            ),
        )
    }

    @Test
    fun credits_canSkip_withoutOutroNearEnd_isIgnored() {
        // The next-episode branch needs BOTH the outro window and autoplay.
        assertEquals(
            SegmentSkipTarget.SeekToPosition(360_000L),
            target(
                kind = SegmentSkipKind.CREDITS,
                isOutroNearEnd = false,
                canSkipToNext = true,
                activeSegmentType = MediaSegmentType.OUTRO,
                activeSegmentEndTicks = 3_600_000_000L,
            ),
        )
    }

    @Test
    fun intro_neverTakesTheNextEpisodeBranch() {
        assertEquals(
            SegmentSkipTarget.None,
            target(
                kind = SegmentSkipKind.INTRO,
                isOutroNearEnd = true,
                canSkipToNext = true,
            ),
        )
    }

    // ── Active segment of the pressed kind ───────────────────────────────

    @Test
    fun intro_activeIntroSegment_seeksItsResolvedEnd() {
        assertEquals(
            SegmentSkipTarget.SeekToPosition(30_000L),
            target(
                kind = SegmentSkipKind.INTRO,
                activeSegmentType = MediaSegmentType.INTRO,
                activeSegmentEndTicks = 300_000_000L,
            ),
        )
    }

    @Test
    fun credits_activeOutroSegment_seeksItsResolvedEnd() {
        assertEquals(
            SegmentSkipTarget.SeekToPosition(360_000L),
            target(
                kind = SegmentSkipKind.CREDITS,
                activeSegmentType = MediaSegmentType.OUTRO,
                activeSegmentEndTicks = 3_600_000_000L,
            ),
        )
    }

    @Test
    fun intro_activeSegmentOfAnotherType_isIgnored_fallsToIntroEndTicks() {
        assertEquals(
            SegmentSkipTarget.SeekToPosition(25_000L),
            target(
                kind = SegmentSkipKind.INTRO,
                activeSegmentType = MediaSegmentType.OUTRO,
                activeSegmentEndTicks = 3_600_000_000L,
                introEndTicks = 250_000_000L,
            ),
        )
    }

    @Test
    fun credits_activeSegmentOfAnotherType_isIgnored_fallsToCreditEndTicks() {
        assertEquals(
            SegmentSkipTarget.SeekToPosition(360_000L),
            target(
                kind = SegmentSkipKind.CREDITS,
                activeSegmentType = MediaSegmentType.INTRO,
                activeSegmentEndTicks = 300_000_000L,
                creditEndTicks = 3_600_000_000L,
            ),
        )
    }

    @Test
    fun activeSegmentWinningWithInvalidTicks_doesNotFallThroughToFallback() {
        // The original ladder early-returns inside the segment branch: a
        // winning segment whose resolved end ticks are unusable is a no-op,
        // not a fall-through to the end-ticks fallback.
        for (invalidTicks in listOf<Long?>(null, 0L, -1L)) {
            assertEquals(
                SegmentSkipTarget.None,
                target(
                    kind = SegmentSkipKind.INTRO,
                    activeSegmentType = MediaSegmentType.INTRO,
                    activeSegmentEndTicks = invalidTicks,
                    introEndTicks = 300_000_000L,
                ),
                "activeSegmentEndTicks $invalidTicks",
            )
        }
    }

    // ── End-ticks fallback ───────────────────────────────────────────────

    @Test
    fun intro_noActiveSegment_usesIntroEndTicks() {
        assertEquals(
            SegmentSkipTarget.SeekToPosition(30_000L),
            target(kind = SegmentSkipKind.INTRO, introEndTicks = 300_000_000L),
        )
    }

    @Test
    fun credits_noActiveSegment_usesCreditEndTicks() {
        assertEquals(
            SegmentSkipTarget.SeekToPosition(360_000L),
            target(kind = SegmentSkipKind.CREDITS, creditEndTicks = 3_600_000_000L),
        )
    }

    @Test
    fun eachKind_ignoresTheOtherKindEndTicks() {
        assertEquals(
            SegmentSkipTarget.None,
            target(kind = SegmentSkipKind.INTRO, creditEndTicks = 3_600_000_000L),
        )
        assertEquals(
            SegmentSkipTarget.None,
            target(kind = SegmentSkipKind.CREDITS, introEndTicks = 300_000_000L),
        )
    }

    @Test
    fun nothingKnown_isANoOp() {
        for (kind in SegmentSkipKind.entries) {
            assertEquals(SegmentSkipTarget.None, target(kind = kind), "kind $kind")
        }
    }
}

class SegmentEndSeekTargetTest {

    @Test
    fun positiveTicks_seekToTruncatedMilliseconds() {
        assertEquals(
            SegmentSkipTarget.SeekToPosition(30_000L),
            segmentEndSeekTarget(300_000_000L),
        )
        assertEquals(
            SegmentSkipTarget.SeekToPosition(360_000L),
            segmentEndSeekTarget(3_600_000_000L),
        )
    }

    @Test
    fun null_zeroAndNegativeTicks_areANoOp() {
        assertEquals(SegmentSkipTarget.None, segmentEndSeekTarget(null))
        assertEquals(SegmentSkipTarget.None, segmentEndSeekTarget(0L))
        assertEquals(SegmentSkipTarget.None, segmentEndSeekTarget(-10_000L))
    }

    @Test
    fun conversionTruncates_subTickRemainder() {
        assertEquals(
            SegmentSkipTarget.SeekToPosition(60_000L),
            segmentEndSeekTarget(600_005_000L),
        )
    }

    @Test
    fun subTickPositiveValue_guardsOnTicks_notOnMs_seeksToZero() {
        // The guard is `ticks > 0`, so a positive sub-millisecond tick value
        // passes it and seeks to 0 ms.
        assertEquals(
            SegmentSkipTarget.SeekToPosition(0L),
            segmentEndSeekTarget(5_000L),
        )
    }
}
