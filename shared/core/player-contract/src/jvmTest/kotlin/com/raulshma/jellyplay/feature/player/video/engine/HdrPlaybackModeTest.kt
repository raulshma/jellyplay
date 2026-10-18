package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.PlayerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the HDR status-card derivations — the pure policy behind the HDR
 * sheet's "Playback mode" card ([resolveHdrPlaybackMode]), the detected-
 * display row ([resolveDetectedDisplayLabel]) and the family labels
 * ([hdrLabelFor]). Load-bearing shapes:
 *
 *  - The engine's ACTIVE verdict wins over the content basis (the Exo
 *    observed-SDR downgrade would already have cleared it).
 *  - The content basis is engine-specific: mpv hosts never publish an
 *    observed transfer (the stats projection reads no transfer property),
 *    so the metadata claim decides there; Exo's observed family is
 *    authoritative (observed null ⇒ no HDR signal in the decode, even on an
 *    item whose metadata claimed HDR — the adaptive transcode-rendition
 *    case).
 *  - The [HdrOutputDecision] notice KNOTS disambiguate Exo's not-active
 *    postures; the references are compile-checked constants, never raw
 *    strings, so a notice-wording change fails here first.
 */
class HdrPlaybackModeTest {

    // ── resolveHdrPlaybackMode ───────────────────────────────────────────

    @Test
    fun `mpv HDR content with an SDR target reads as tone mapped`() {
        val info = resolveHdrPlaybackMode(
            playerType = PlayerType.MPV,
            contentHdrType = "hdr10",
            observedHdrType = null, // mpv hosts never publish an observed family
            hdrOutputActive = false,
            hdrOutputNotice = HdrOutputDecision.NOTICE_SDR_DISPLAY,
        )
        assertEquals(HdrPlaybackMode.ToneMapped, info.mode)
        assertEquals("Tone mapped to SDR", info.title)
    }

    @Test
    fun `mpv HDR content with an active HDR target reads as native output`() {
        val info = resolveHdrPlaybackMode(
            playerType = PlayerType.MPV,
            contentHdrType = "dolbyvision",
            observedHdrType = null,
            hdrOutputActive = true,
            hdrOutputNotice = null,
        )
        assertEquals(HdrPlaybackMode.HdrOutput, info.mode)
        assertEquals("Native HDR output", info.title)
    }

    @Test
    fun `mpv SDR content reads as SDR playback`() {
        val info = resolveHdrPlaybackMode(
            playerType = PlayerType.MPV,
            contentHdrType = null,
            observedHdrType = null,
            hdrOutputActive = false,
            hdrOutputNotice = null,
        )
        assertEquals(HdrPlaybackMode.SdrPlayback, info.mode)
        assertEquals("SDR playback", info.title)
    }

    @Test
    fun `the active verdict wins over an inconclusive content basis`() {
        // Defensive shape: an active output with nothing claiming HDR is a
        // native HDR session, not "SDR playback".
        val info = resolveHdrPlaybackMode(
            playerType = PlayerType.EXO_PLAYER,
            contentHdrType = null,
            observedHdrType = null,
            hdrOutputActive = true,
            hdrOutputNotice = null,
        )
        assertEquals(HdrPlaybackMode.HdrOutput, info.mode)
    }

    @Test
    fun `exo observed SDR beats an HDR metadata claim - transcode rendition`() {
        // The load-bearing downgrade case: the item's streams claim HDR, but
        // the selected adaptive rendition decodes SDR — SDR playback, NOT a
        // tone-mapped/unhandled posture.
        val info = resolveHdrPlaybackMode(
            playerType = PlayerType.EXO_PLAYER,
            contentHdrType = "dolbyvision",
            observedHdrType = null, // the decode publishes no family
            hdrOutputActive = false, // the engine downgraded the expectation
            hdrOutputNotice = null,
        )
        assertEquals(HdrPlaybackMode.SdrPlayback, info.mode)
    }

    @Test
    fun `exo AUTO tone mapping on an SDR display reads as tone mapped`() {
        val info = resolveHdrPlaybackMode(
            playerType = PlayerType.EXO_PLAYER,
            contentHdrType = null,
            observedHdrType = "HDR10",
            hdrOutputActive = false,
            hdrOutputNotice = HdrOutputDecision.NOTICE_SDR_DISPLAY,
        )
        assertEquals(HdrPlaybackMode.ToneMapped, info.mode)
    }

    @Test
    fun `exo tone-mapping-off on an incompatible display reads as unhandled`() {
        val info = resolveHdrPlaybackMode(
            playerType = PlayerType.EXO_PLAYER,
            contentHdrType = null,
            observedHdrType = "HDR10",
            hdrOutputActive = false,
            hdrOutputNotice = HdrOutputDecision.NOTICE_SDR_NO_TONE_MAPPING,
        )
        assertEquals(HdrPlaybackMode.NativeUnhandled, info.mode)
        assertEquals("HDR output unhandled", info.title)
        // The engine's diagnostic IS the explanation (the honest washed-out why).
        assertEquals(HdrOutputDecision.NOTICE_SDR_NO_TONE_MAPPING, info.explanation)
    }

    @Test
    fun `exo DV on a non-DV display reads as unhandled with the engine notice`() {
        val info = resolveHdrPlaybackMode(
            playerType = PlayerType.EXO_PLAYER,
            contentHdrType = "dolbyvision",
            observedHdrType = "Dolby Vision",
            hdrOutputActive = false,
            hdrOutputNotice = HdrOutputDecision.NOTICE_DV_NON_DV_DISPLAY,
        )
        assertEquals(HdrPlaybackMode.NativeUnhandled, info.mode)
        assertEquals(HdrOutputDecision.NOTICE_DV_NON_DV_DISPLAY, info.explanation)
    }

    @Test
    fun `exo HDR decode with a silent probe reads as unhandled`() {
        // AUTO/OFF with no answer from the display probe: the tone-mapping
        // lever was not engaged — native output, display support unknown.
        val info = resolveHdrPlaybackMode(
            playerType = PlayerType.EXO_PLAYER,
            contentHdrType = "hdr10",
            observedHdrType = "HDR10",
            hdrOutputActive = false,
            hdrOutputNotice = null,
        )
        assertEquals(HdrPlaybackMode.NativeUnhandled, info.mode)
    }

    @Test
    fun `exo compatible display reads as native output regardless of the lever`() {
        val info = resolveHdrPlaybackMode(
            playerType = PlayerType.EXO_PLAYER,
            contentHdrType = null,
            observedHdrType = "Dolby Vision",
            hdrOutputActive = true,
            hdrOutputNotice = null,
        )
        assertEquals(HdrPlaybackMode.HdrOutput, info.mode)
    }

    @Test
    fun `engines without an HDR report read as not reported`() {
        for (playerType in listOf(PlayerType.LIBVLC, PlayerType.EXTERNAL)) {
            val info = resolveHdrPlaybackMode(
                playerType = playerType,
                contentHdrType = "hdr10",
                observedHdrType = null,
                hdrOutputActive = false,
                hdrOutputNotice = null,
            )
            assertEquals(HdrPlaybackMode.NotReported, info.mode, playerType.name)
            assertEquals("No HDR report", info.title, playerType.name)
        }
    }

    // ── resolveDetectedDisplayLabel ──────────────────────────────────────

    @Test
    fun `the probe answer wins wherever it is a family list or SDR`() {
        assertEquals(
            "HDR10, Dolby Vision",
            resolveDetectedDisplayLabel("HDR10, Dolby Vision", HdrPlaybackMode.SdrPlayback),
        )
        assertEquals(
            "SDR",
            resolveDetectedDisplayLabel("SDR", HdrPlaybackMode.ToneMapped),
        )
    }

    @Test
    fun `an active output without a display probe spot-checks to HDR`() {
        // mpv hosts answer per-target instead of per-display: a live HDR
        // target IS a display-capability verdict.
        assertEquals(
            "HDR (detected from the output)",
            resolveDetectedDisplayLabel(null, HdrPlaybackMode.HdrOutput),
        )
    }

    @Test
    fun `a tone-mapped output itself proves the display stayed SDR`() {
        // The render-path inference: when the engine tone mapped, the output
        // IS SDR — no need to say "not detected" just because the platform
        // display probe failed to answer.
        assertEquals(
            "SDR (the output stayed SDR)",
            resolveDetectedDisplayLabel(null, HdrPlaybackMode.ToneMapped),
        )
    }

    @Test
    fun `no probe and no output verdict reads as not detected`() {
        assertEquals(
            "Not detected",
            resolveDetectedDisplayLabel(null, HdrPlaybackMode.SdrPlayback),
        )
    }

    @Test
    fun `an unhandled output without a display fact names the failed probe`() {
        // HDR decode, tone-mapping lever not engaged, display probe silent —
        // the row says precisely which observation is missing.
        assertEquals(
            "Unknown (the display probe didn't answer)",
            resolveDetectedDisplayLabel(null, HdrPlaybackMode.NativeUnhandled),
        )
    }

    // ── hdrLabelFor ──────────────────────────────────────────────────────

    @Test
    fun `family labels normalize the Jellyfin claim vocabulary`() {
        assertEquals("HDR10", hdrLabelFor("hdr10"))
        assertEquals("HDR10+", hdrLabelFor("hdr10plus"))
        assertEquals("HDR10+", hdrLabelFor("hdr10_plus"))
        assertEquals("Dolby Vision", hdrLabelFor("dovi"))
        assertEquals("Dolby Vision", hdrLabelFor("dolbyvision"))
        assertEquals("HLG", hdrLabelFor("hlg"))
        assertEquals("HDR", hdrLabelFor("hdr"))
    }

    @Test
    fun `unknown and SDR-shaped families normalize honestly`() {
        assertEquals("SOMETHING", hdrLabelFor("something"))
        assertNull(hdrLabelFor(null))
        assertNull(hdrLabelFor(""))
        assertNull(hdrLabelFor("sdr"))
    }
}
