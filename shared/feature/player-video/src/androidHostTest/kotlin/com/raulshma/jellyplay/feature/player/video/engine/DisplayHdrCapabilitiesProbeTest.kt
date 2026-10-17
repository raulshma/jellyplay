package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins [DisplayHdrCapabilitiesProbe] over the injected read seam: the Android
 * HDR-type int fold (via the shared [HdrContentInfo.fromAndroidHdrType]
 * mapping), the unknown-vs-SDR distinction (null stays null — resolveExoHdrOutput
 * keeps the native path for a display the probe could not read), and the
 * display-event refresh emitting only on an actual change.
 */
@RunWith(AndroidJUnit4::class)
class DisplayHdrCapabilitiesProbeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun probeNullTypes_reportsUnknownNotSdr() {
        val probe = DisplayHdrCapabilitiesProbe(context) { null }
        // Message-first: JUnit 4's assertNull(String, Object).
        assertNull(
            "a failed/absent read is UNKNOWN, not a declared-SDR display",
            probe.support.value,
        )
    }

    @Test
    fun probeEmptyTypes_reportsSdrDisplay() {
        val probe = DisplayHdrCapabilitiesProbe(context) { intArrayOf() }
        assertEquals(DisplayHdrSupport.SDR, probe.support.value)
    }

    @Test
    fun probeTypes_foldThroughSharedMapping() {
        val probe = DisplayHdrCapabilitiesProbe(context) {
            intArrayOf(
                HdrContentInfo.ANDROID_HDR_TYPE_HDR10,
                HdrContentInfo.ANDROID_HDR_TYPE_HLG,
                42, // unknown type int — ignored, not a crash
            )
        }
        assertEquals(
            DisplayHdrSupport(setOf(HdrContentInfo.HDR10, HdrContentInfo.HLG)),
            probe.support.value,
        )
    }

    @Test
    fun refresh_emitsOnlyOnChange() {
        var types: IntArray? = intArrayOf(HdrContentInfo.ANDROID_HDR_TYPE_HDR10)
        val probe = DisplayHdrCapabilitiesProbe(context) { types }
        assertEquals(
            DisplayHdrSupport(setOf(HdrContentInfo.HDR10)),
            probe.support.value,
        )

        // Same read → no emission (distinct instance, equal value).
        probe.refresh()
        assertEquals(
            DisplayHdrSupport(setOf(HdrContentInfo.HDR10)),
            probe.support.value,
        )

        // Display gains DV support (monitor hot-plug) → the change emits.
        types = intArrayOf(
            HdrContentInfo.ANDROID_HDR_TYPE_HDR10,
            HdrContentInfo.ANDROID_HDR_TYPE_DOLBY_VISION,
        )
        probe.refresh()
        assertEquals(
            DisplayHdrSupport(setOf(HdrContentInfo.HDR10, HdrContentInfo.DOLBY_VISION)),
            probe.support.value,
        )

        // Display goes dark (SDR) → empty array, a real SDR verdict.
        types = intArrayOf()
        probe.refresh()
        assertEquals(DisplayHdrSupport.SDR, probe.support.value)
    }

    @Test
    fun frameworkRead_neverThrows() {
        // Robolectric Display has no HDR capabilities — the framework read
        // must return null (unknown), not throw, and the fold must be total.
        val displayManager =
            context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        assertNull(DisplayHdrCapabilitiesProbe.readDisplayHdrTypes(display))
    }

    // ── displayHdrTypesSummary (the stats display-Hdr types row) ──

    @Test
    fun summary_joinsTheAcceptedFamilies() {
        val probe = DisplayHdrCapabilitiesProbe(context) {
            intArrayOf(
                HdrContentInfo.ANDROID_HDR_TYPE_HDR10,
                HdrContentInfo.ANDROID_HDR_TYPE_DOLBY_VISION,
            )
        }
        assertEquals("HDR10, Dolby Vision", probe.support.value?.displayHdrTypesSummary())
    }

    @Test
    fun summary_declaredSdrDisplay_reportsSdrNotUnknown() {
        val probe = DisplayHdrCapabilitiesProbe(context) { intArrayOf() }
        assertEquals("SDR", probe.support.value?.displayHdrTypesSummary())
    }
}
