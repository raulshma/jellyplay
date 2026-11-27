package com.raulshma.jellyplay.feature.player.video.subtitle

import android.content.Context
import android.graphics.Typeface
import androidx.media3.common.text.Cue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExoCueCollisionResolverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver = ExoCueCollisionResolver(context)

    @Test
    fun simultaneousUnpositionedCues_areStackedWithoutOverlapping() {
        val cue1 = Cue.Builder().setText("Speaker one line").build()
        val cue2 = Cue.Builder().setText("Speaker two line").build()

        val resolved = resolver.resolve(
            cues = listOf(cue1, cue2),
            parentWidth = 1920,
            parentHeight = 1080,
            fontSizeSp = 24f,
            typeface = Typeface.SANS_SERIF,
            bottomPaddingFraction = 0.05f,
            displayMetrics = context.resources.displayMetrics,
            gapPx = 8,
        )

        assertEquals(2, resolved.size)
        // First cue stays at its natural bottom position (DIMEN_UNSET);
        // second cue is promoted above it with a positive fractional line.
        assertEquals(Cue.DIMEN_UNSET, resolved[0].line)
        assertTrue(resolved[1].line != Cue.DIMEN_UNSET)
        assertEquals(Cue.LINE_TYPE_FRACTION, resolved[1].lineType)
        assertEquals(Cue.ANCHOR_TYPE_START, resolved[1].lineAnchor)
        assertTrue("Second cue must sit strictly above bottom region", resolved[1].line < 0.95f)
    }

    @Test
    fun wrappedWebVttBottomCue_pushesUpperCueAboveFullWrappedHeight() {
        val singleLineCue1 = Cue.Builder()
            .setText("First speaker line 1")
            .setLine(-1f, Cue.LINE_TYPE_NUMBER)
            .build()
        val multiLineCue1 = Cue.Builder()
            .setText("First speaker line 1\nFirst speaker line 2\nFirst speaker line 3")
            .setLine(-1f, Cue.LINE_TYPE_NUMBER)
            .build()
        val cue2 = Cue.Builder()
            .setText("Second speaker")
            .setLine(-2f, Cue.LINE_TYPE_NUMBER)
            .build()

        val resolvedSingle = resolver.resolve(
            cues = listOf(singleLineCue1, cue2),
            parentWidth = 1920,
            parentHeight = 1080,
            fontSizeSp = 24f,
            typeface = Typeface.SANS_SERIF,
            bottomPaddingFraction = 0.05f,
            displayMetrics = context.resources.displayMetrics,
            gapPx = 0,
        )
        val resolvedMulti = resolver.resolve(
            cues = listOf(multiLineCue1, cue2),
            parentWidth = 1920,
            parentHeight = 1080,
            fontSizeSp = 24f,
            typeface = Typeface.SANS_SERIF,
            bottomPaddingFraction = 0.05f,
            displayMetrics = context.resources.displayMetrics,
            gapPx = 0,
        )

        assertEquals(2, resolvedMulti.size)
        // With 1-line cue1 and gap=0, cue2 at line=-2 sits just above cue1 and stays untouched.
        assertEquals(-2f, resolvedSingle[1].line)
        assertEquals(Cue.LINE_TYPE_NUMBER, resolvedSingle[1].lineType)
        // With 3-line cue1, cue2 at line=-2 overlaps lines 1-2 and must be promoted upward.
        assertEquals(Cue.LINE_TYPE_FRACTION, resolvedMulti[1].lineType)
        assertEquals(Cue.ANCHOR_TYPE_START, resolvedMulti[1].lineAnchor)
        assertTrue(
            "Multi-line cue1 must push cue2 strictly above bottom 2 lines (was ${resolvedMulti[1].line})",
            resolvedMulti[1].line in 0f..0.95f,
        )
    }

    @Test
    fun singleCue_returnsListUntouched() {
        val cue = Cue.Builder().setText("Solo line").build()
        val input = listOf(cue)

        val resolved = resolver.resolve(
            cues = input,
            parentWidth = 1920,
            parentHeight = 1080,
            fontSizeSp = 24f,
            typeface = Typeface.SANS_SERIF,
            bottomPaddingFraction = 0.05f,
            displayMetrics = context.resources.displayMetrics,
        )

        assertEquals(input, resolved)
    }
}
