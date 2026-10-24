package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.ExoHdrToneMappingMode
import com.raulshma.jellyplay.core.model.HdrFormatPreference
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the pure HDR output policy: the Jellyfin-stream → [HdrContentInfo]
 * derivation, the Android display-type fold, and both engines' output
 * decisions (ExoPlayer GL engagement, mpv passthrough suppression gate).
 */
class HdrOutputPolicyTest {

    // ── hdrContentFromStreams ──

    private fun videoStream(
        range: String? = null,
        rangeType: String? = null,
        doviTitle: String? = null,
    ) = MediaStream(index = 0, type = StreamType.VIDEO, videoRange = range, videoRangeType = rangeType, videoDoViTitle = doviTitle)

    @Test
    fun derivation_serverRangeTypes() {
        assertEquals(HdrContentInfo.NONE, hdrContentFromStreams(listOf(videoStream(rangeType = "SDR"))))
        assertEquals(HdrContentInfo.HDR10, hdrContentFromStreams(listOf(videoStream(rangeType = "HDR10"))))
        assertEquals(HdrContentInfo.HDR10_PLUS, hdrContentFromStreams(listOf(videoStream(rangeType = "HDR10Plus"))))
        assertEquals(HdrContentInfo.HLG, hdrContentFromStreams(listOf(videoStream(rangeType = "HLG"))))
        assertEquals(HdrContentInfo.DOLBY_VISION, hdrContentFromStreams(listOf(videoStream(rangeType = "DolbyVision"))))
        // The combined profiles mention HDR10 — Dolby Vision must win (it is the
        // delivery family that decides decoder/display behavior).
        assertEquals(HdrContentInfo.DOLBY_VISION, hdrContentFromStreams(listOf(videoStream(rangeType = "DolbyVisionWithHDR10"))))
        assertEquals(HdrContentInfo.DOLBY_VISION, hdrContentFromStreams(listOf(videoStream(rangeType = "DolbyVisionWithHLG"))))
    }

    @Test
    fun derivation_legacyAndLocalProbeSpellings() {
        // The legacy `videoRange=HDR` catch-all and the local probe's bare
        // "HDR" videoRangeType fold into the static-info HDR10 family.
        assertEquals(HdrContentInfo.HDR10, hdrContentFromStreams(listOf(videoStream(range = "HDR"))))
        assertEquals(HdrContentInfo.HDR10, hdrContentFromStreams(listOf(videoStream(rangeType = "HDR"))))
        // DoVi title alone (the local MediaExtractor probe's DV path).
        assertEquals(HdrContentInfo.DOLBY_VISION, hdrContentFromStreams(listOf(videoStream(doviTitle = "Dolby Vision"))))
        // Case-insensitive.
        assertEquals(HdrContentInfo.HDR10_PLUS, hdrContentFromStreams(listOf(videoStream(rangeType = "hdr10+"))))
    }

    @Test
    fun derivation_emptyOrNoVideo() {
        assertEquals(HdrContentInfo.NONE, hdrContentFromStreams(emptyList()))
        assertEquals(
            HdrContentInfo.NONE,
            hdrContentFromStreams(listOf(MediaStream(index = 1, type = StreamType.AUDIO))),
        )
    }

    @Test
    fun hdrSourceInvariant() {
        for (value in HdrContentInfo.entries) {
            assertEquals(value.isHdr, value != HdrContentInfo.NONE)
        }
    }

    // ── hdrFamilyOf / hdrLabelFor (the one shared vocabulary) ──

    @Test
    fun familyParser_unionsTheFormerlySeparateSpellingSets() {
        // The stream fold, the sheet's labels and the metadata row's DV badge
        // used to spell the vocabulary three times; every spelling now lands
        // on the same family from any consumer's text shape.
        assertEquals(HdrContentInfo.DOLBY_VISION, hdrFamilyOf("dovi"))
        assertEquals(HdrContentInfo.DOLBY_VISION, hdrFamilyOf("dolbyvision"))
        assertEquals(HdrContentInfo.DOLBY_VISION, hdrFamilyOf("dolby_vision"))
        assertEquals(HdrContentInfo.DOLBY_VISION, hdrFamilyOf("Dolby Vision"))
        assertEquals(HdrContentInfo.HDR10_PLUS, hdrFamilyOf("hdr10plus"))
        assertEquals(HdrContentInfo.HDR10_PLUS, hdrFamilyOf("hdr10+"))
        assertEquals(HdrContentInfo.HDR10_PLUS, hdrFamilyOf("hdr10_plus"))
        assertEquals(HdrContentInfo.HDR10, hdrFamilyOf("hdr10"))
        assertEquals(HdrContentInfo.HLG, hdrFamilyOf("hlg"))
        assertEquals(HdrContentInfo.HDR10, hdrFamilyOf("hdr"))
        assertNull(hdrFamilyOf("sdr"))
        assertNull(hdrFamilyOf(null))
        // DV beats the HDR10 mention inside the combined profile strings, and
        // the rungs scan ALL texts before advancing (the space-joined
        // range+rangeType fold semantics): range "HDR" + rangeType
        // "DolbyVision…" reads DV, not first-text HDR10.
        assertEquals(HdrContentInfo.DOLBY_VISION, hdrFamilyOf("HDR", "DolbyVisionWithHDR10"))
        assertEquals(HdrContentInfo.HDR10, hdrFamilyOf("HDR", "unknown"))
    }

    @Test
    fun familyLabels_agreeWithTheParser() {
        // The label twin renders the parser's displayName (plus the bare-`hdr`
        // and SDR/unknown special cases), so a spelling can't gain a family
        // without gaining its label.
        assertEquals("Dolby Vision", hdrLabelFor("Dolby Vision"))
        assertEquals("HDR10+", hdrLabelFor("hdr10_plus"))
        assertEquals("HDR10", hdrLabelFor("HDR10"))
        assertEquals("HDR", hdrLabelFor("hdr"))
        assertNull(hdrLabelFor("unknown"))
    }

    // ── DisplayHdrSupport ──

    @Test
    fun displayTypeFold_fromAndroidConstants() {
        // android.view.Display.HdrCapabilities values by name.
        val support = DisplayHdrSupport.fromAndroidHdrTypes(
            intArrayOf(
                HdrContentInfo.ANDROID_HDR_TYPE_DOLBY_VISION,
                HdrContentInfo.ANDROID_HDR_TYPE_HDR10,
                HdrContentInfo.ANDROID_HDR_TYPE_HLG,
                99, // unknown type — ignored
            ),
        )
        assertEquals(
            setOf(HdrContentInfo.DOLBY_VISION, HdrContentInfo.HDR10, HdrContentInfo.HLG),
            support.supportedTypes,
        )
        assertTrue(support.isHdrDisplay)
    }

    @Test
    fun displayCompat_folds() {
        val hdr10Only = DisplayHdrSupport(setOf(HdrContentInfo.HDR10))
        // HDR10+ plays on an HDR10 display at its baseline layer…
        assertTrue(hdr10Only.supportsContent(HdrContentInfo.HDR10_PLUS))
        // …but HLG and DV need their own types.
        assertFalse(hdr10Only.supportsContent(HdrContentInfo.HLG))
        assertFalse(hdr10Only.supportsContent(HdrContentInfo.DOLBY_VISION))

        val dvDisplay = DisplayHdrSupport(setOf(HdrContentInfo.DOLBY_VISION))
        assertTrue(dvDisplay.supportsContent(HdrContentInfo.HDR10))
        assertTrue(dvDisplay.supportsContent(HdrContentInfo.DOLBY_VISION))

        assertTrue(DisplayHdrSupport.SDR.supportsContent(HdrContentInfo.NONE))
        assertFalse(DisplayHdrSupport.SDR.isHdrDisplay)
    }

    // ── resolveExoHdrOutput ──

    @Test
    fun exo_sdrContentIsNoOp() {
        assertEquals(HdrOutputDecision.NONE, resolveExoHdrOutput(HdrContentInfo.NONE, DisplayHdrSupport.SDR, ExoHdrToneMappingMode.AUTO))
        assertEquals(HdrOutputDecision.NONE, resolveExoHdrOutput(HdrContentInfo.NONE, null, ExoHdrToneMappingMode.AUTO))
    }

    @Test
    fun exo_offAlwaysNative() {
        // Explicit opt-out: no GL engagement in any branch. A KNOWN-incompatible
        // display gets the honest washed-out notice instead of silence…
        val sdr = resolveExoHdrOutput(HdrContentInfo.HDR10, DisplayHdrSupport.SDR, ExoHdrToneMappingMode.OFF)
        assertFalse(sdr.toneMappingActive)
        assertTrue(sdr.unhandledHdr)
        assertEquals(HdrOutputDecision.NOTICE_SDR_NO_TONE_MAPPING, sdr.notice)
        // …a compatible display still reports the native HDR expectation…
        val hdr = resolveExoHdrOutput(
            HdrContentInfo.HDR10,
            DisplayHdrSupport(setOf(HdrContentInfo.HDR10)),
            ExoHdrToneMappingMode.OFF,
        )
        assertTrue(hdr.hdrOutputExpected)
        assertFalse(hdr.toneMappingActive)
        assertNull(hdr.notice)
        // …and an unknown display stays fully quiet (unknown ≠ SDR).
        assertEquals(
            HdrOutputDecision.NONE,
            resolveExoHdrOutput(HdrContentInfo.HDR10, null, ExoHdrToneMappingMode.OFF),
        )
    }

    @Test
    fun exo_auto_hdrDisplayPassthrough() {
        val decision = resolveExoHdrOutput(
            HdrContentInfo.HDR10,
            DisplayHdrSupport(setOf(HdrContentInfo.HDR10)),
            ExoHdrToneMappingMode.AUTO,
        )
        assertTrue(decision.hdrOutputExpected)
        assertFalse(decision.toneMappingActive)
        assertNull(decision.notice)
    }

    @Test
    fun exo_auto_sdrDisplayToneMaps() {
        val decision = resolveExoHdrOutput(HdrContentInfo.HLG, DisplayHdrSupport.SDR, ExoHdrToneMappingMode.AUTO)
        assertTrue(decision.toneMappingActive)
        assertFalse(decision.hdrOutputExpected)
        assertEquals(HdrOutputDecision.NOTICE_SDR_DISPLAY, decision.notice)
    }

    @Test
    fun exo_auto_unknownDisplayStaysNative() {
        // A failed/unavailable probe is UNKNOWN, not SDR — never regress a
        // device into the GL path because its display could not be read.
        assertEquals(
            HdrOutputDecision.NONE,
            resolveExoHdrOutput(HdrContentInfo.HDR10, null, ExoHdrToneMappingMode.AUTO),
        )
    }

    @Test
    fun exo_auto_dolbyVisionOnSdrDisplay() {
        val decision = resolveExoHdrOutput(HdrContentInfo.DOLBY_VISION, DisplayHdrSupport.SDR, ExoHdrToneMappingMode.AUTO)
        assertTrue(decision.toneMappingActive)
        // DV profile 5 on a non-DV display can decode to uncorrectable colors —
        // the decision marks it unhandled so the stats overlay can say so.
        assertTrue(decision.unhandledHdr)
        assertEquals(HdrOutputDecision.NOTICE_DV_NON_DV_DISPLAY, decision.notice)
    }

    // ── resolveMpvHdrOutput ──

    @Test
    fun mpv_sdrContentIsNoOp() {
        assertEquals(HdrOutputDecision.NONE, resolveMpvHdrOutput(HdrContentInfo.NONE, passthroughRequested = true, targetIsHdr = true))
    }

    @Test
    fun mpv_passthroughActiveOnlyWithHdrTarget() {
        assertTrue(
            resolveMpvHdrOutput(HdrContentInfo.HDR10, passthroughRequested = true, targetIsHdr = true)
                .hdrOutputExpected,
        )
        // Requested but the display ignored the colorspace hint → the tone
        // mapping handles it, with the SDR-fallback notice (the desktop text).
        val fallback = resolveMpvHdrOutput(HdrContentInfo.HDR10, passthroughRequested = true, targetIsHdr = false)
        assertTrue(fallback.toneMappingActive)
        assertEquals(HdrOutputDecision.NOTICE_SDR_DISPLAY, fallback.notice)
    }

    @Test
    fun mpv_noPassthroughToneMapsQuietly() {
        // mpv's default posture: tone map on an SDR target, no nag.
        val decision = resolveMpvHdrOutput(HdrContentInfo.HDR10, passthroughRequested = false, targetIsHdr = false)
        assertTrue(decision.toneMappingActive)
        assertNull(decision.notice)
    }

    @Test
    fun mpv_preProbeGateStaysOpen() {
        // Before the per-file probe lands, targetIsHdr=false (the safe
        // fallback: the preset is written). Requested + unprobed reports the
        // SDR posture, matching the desktop's pre-probe gate.
        val decision = resolveMpvHdrOutput(HdrContentInfo.HDR10, passthroughRequested = true, targetIsHdr = false)
        assertFalse(decision.hdrOutputExpected)
        assertTrue(decision.toneMappingActive)
    }

    // ── applyHdrFormatPreference (the #5 output posture) ──

    private val hdr10Display = DisplayHdrSupport(setOf(HdrContentInfo.HDR10))
    private val dvDisplay = DisplayHdrSupport(setOf(HdrContentInfo.DOLBY_VISION))

    @Test
    fun preference_autoAndPreferDvChangeNothing() {
        val native = HdrOutputDecision(hdrOutputExpected = true)
        for (pref in listOf(HdrFormatPreference.AUTO, HdrFormatPreference.PREFER_DV_OVER_HDR10)) {
            assertEquals(native, applyHdrFormatPreference(native, HdrContentInfo.HDR10, pref))
            assertEquals(native, applyHdrFormatPreference(native, HdrContentInfo.DOLBY_VISION, pref))
        }
        // PREFER_DV_OVER_HDR10 is a DECODER concern — the output policy
        // treats it as AUTO (pinned twice above for both families).
    }

    @Test
    fun preference_sdrOnlyForcesToneMapDownAndNeverHdrPassthrough() {
        for (content in listOf(HdrContentInfo.HDR10, HdrContentInfo.HDR10_PLUS, HdrContentInfo.HLG, HdrContentInfo.DOLBY_VISION)) {
            val forced = applyHdrFormatPreference(
                HdrOutputDecision(hdrOutputExpected = true),
                content,
                HdrFormatPreference.SDR_ONLY,
            )
            assertTrue(forced.toneMappingActive, content.name)
            assertFalse(forced.hdrOutputExpected, content.name)
            assertFalse(forced.unhandledHdr, content.name)
            assertEquals(HdrOutputDecision.NOTICE_PREFERENCE_SDR_ONLY, forced.notice, content.name)
        }
        // …and it holds even when passthrough was requested with an HDR target…
        val mpv = resolveMpvHdrOutput(
            HdrContentInfo.HDR10,
            passthroughRequested = true,
            targetIsHdr = true,
            formatPreference = HdrFormatPreference.SDR_ONLY,
        )
        assertTrue(mpv.toneMappingActive)
        assertFalse(mpv.hdrOutputExpected)
        // …and on Exo over a compatible HDR display.
        val exo = resolveExoHdrOutput(
            HdrContentInfo.HDR10,
            hdr10Display,
            ExoHdrToneMappingMode.AUTO,
            formatPreference = HdrFormatPreference.SDR_ONLY,
        )
        assertTrue(exo.toneMappingActive)
        assertFalse(exo.hdrOutputExpected)
    }

    @Test
    fun preference_sdrOnlyLeavesSdrContentUntouched() {
        assertEquals(
            HdrOutputDecision.NONE,
            applyHdrFormatPreference(HdrOutputDecision.NONE, HdrContentInfo.NONE, HdrFormatPreference.SDR_ONLY),
        )
    }

    @Test
    fun preference_hdr10OnlyAllowsTheHdr10FamilyAndToneMapsTheRest() {
        // HDR10 and HDR10+ (the baseline-layer compatible pair) keep the
        // resolved decision…
        for (content in listOf(HdrContentInfo.HDR10, HdrContentInfo.HDR10_PLUS)) {
            val native = HdrOutputDecision(hdrOutputExpected = true)
            assertEquals(native, applyHdrFormatPreference(native, content, HdrFormatPreference.HDR10_ONLY), content.name)
        }
        // …HLG and DV are tone mapped down.
        for (content in listOf(HdrContentInfo.HLG, HdrContentInfo.DOLBY_VISION)) {
            val restricted = applyHdrFormatPreference(
                HdrOutputDecision(hdrOutputExpected = true),
                content,
                HdrFormatPreference.HDR10_ONLY,
            )
            assertTrue(restricted.toneMappingActive, content.name)
            assertFalse(restricted.hdrOutputExpected, content.name)
            assertEquals(HdrOutputDecision.NOTICE_PREFERENCE_FAMILY_RESTRICTED, restricted.notice, content.name)
        }
    }

    @Test
    fun preference_dolbyVisionOnlyToneMapsEveryOtherFamily() {
        val native = HdrOutputDecision(hdrOutputExpected = true)
        assertEquals(native, applyHdrFormatPreference(native, HdrContentInfo.DOLBY_VISION, HdrFormatPreference.DOLBY_VISION_ONLY))
        for (content in listOf(HdrContentInfo.HDR10, HdrContentInfo.HDR10_PLUS, HdrContentInfo.HLG)) {
            val restricted = applyHdrFormatPreference(native, content, HdrFormatPreference.DOLBY_VISION_ONLY)
            assertTrue(restricted.toneMappingActive, content.name)
            assertEquals(HdrOutputDecision.NOTICE_PREFERENCE_FAMILY_RESTRICTED, restricted.notice, content.name)
        }
    }

    @Test
    fun preference_familyRestrictionWinsOverDisplayNegotiation() {
        // DV content on a DV display would negotiate native output; the
        // HDR10_ONLY preference restricts the OUTPUT family regardless.
        val restricted = resolveExoHdrOutput(
            HdrContentInfo.DOLBY_VISION,
            dvDisplay,
            ExoHdrToneMappingMode.AUTO,
            formatPreference = HdrFormatPreference.HDR10_ONLY,
        )
        assertTrue(restricted.toneMappingActive)
        assertFalse(restricted.hdrOutputExpected)
    }
}
