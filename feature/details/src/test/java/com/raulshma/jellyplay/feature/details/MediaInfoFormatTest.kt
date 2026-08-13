package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests [MediaInfoFormat] — the pure technical-info formatters previously
 * trapped as private functions on [MediaInfoScreen] (untestable without driving
 * the whole composable). Now synchronous and direct.
 */
class MediaInfoFormatTest {

    // ── formatBitrate ──────────────────────────────────────────────────

    @Test
    fun `formatBitrate renders Mbps at and above one million bps`() {
        // Exactly 1 Mbps boundary → Mbps branch.
        assertEquals("1.0 Mbps", MediaInfoFormat.formatBitrate(1_000_000L))
        // 5.5 Mbps → one decimal.
        assertEquals("5.5 Mbps", MediaInfoFormat.formatBitrate(5_500_000L))
    }

    @Test
    fun `formatBitrate renders Kbps in the thousands range`() {
        // 1 Kbps boundary (just below the Mbps threshold) → Kbps branch.
        assertEquals("1 Kbps", MediaInfoFormat.formatBitrate(1_000L))
        assertEquals("500 Kbps", MediaInfoFormat.formatBitrate(500_000L))
        // 999 500 bps → 999.5 Kbps, "%.0f" rounds to "1000" — confirms Kbps values
        // near the Mbps boundary round up within the Kbps unit (not silently capped).
        assertEquals("1000 Kbps", MediaInfoFormat.formatBitrate(999_500L))
    }

    @Test
    fun `formatBitrate renders raw bps below one thousand`() {
        assertEquals("0 bps", MediaInfoFormat.formatBitrate(0L))
        assertEquals("999 bps", MediaInfoFormat.formatBitrate(999L))
    }

    // ── formatTicks ────────────────────────────────────────────────────

    @Test
    fun `formatTicks renders hours and minutes when over an hour`() {
        // 1 h 30 m = 5400 s → 54 000 000 000 ticks.
        assertEquals("1h 30m", MediaInfoFormat.formatTicks(54_000_000_000L))
    }

    @Test
    fun `formatTicks renders hours without seconds when over an hour`() {
        // 2 h exactly → 7 200 s.
        assertEquals("2h 0m", MediaInfoFormat.formatTicks(72_000_000_000L))
    }

    @Test
    fun `formatTicks renders minutes and seconds under an hour`() {
        // 45 m 30 s = 2730 s → 27 300 000 000 ticks.
        assertEquals("45m 30s", MediaInfoFormat.formatTicks(27_300_000_000L))
    }

    @Test
    fun `formatTicks zero renders zero minutes zero seconds`() {
        assertEquals("0m 0s", MediaInfoFormat.formatTicks(0L))
    }

    @Test
    fun `formatTicks drops sub-tick remainder`() {
        // 59 s + a partial tick (< 10 000 000) — integer division floors to 59 s.
        assertEquals("0m 59s", MediaInfoFormat.formatTicks(590_000_000L + 5_000_000L))
    }

    // ── resolutionLabel ────────────────────────────────────────────────

    @Test
    fun `resolutionLabel null height is unknown`() {
        assertEquals("Unknown", MediaInfoFormat.resolutionLabel(null))
    }

    @Test
    fun `resolutionLabel 2160 and above is 4K UHD`() {
        assertEquals("4K UHD", MediaInfoFormat.resolutionLabel(2160))
        assertEquals("4K UHD", MediaInfoFormat.resolutionLabel(4320))
    }

    @Test
    fun `resolutionLabel 1440 to 2159 is QHD`() {
        assertEquals("1440p QHD", MediaInfoFormat.resolutionLabel(1440))
        assertEquals("1440p QHD", MediaInfoFormat.resolutionLabel(2159))
    }

    @Test
    fun `resolutionLabel 1080 to 1439 is Full HD`() {
        assertEquals("1080p Full HD", MediaInfoFormat.resolutionLabel(1080))
    }

    @Test
    fun `resolutionLabel 720 to 1079 is HD`() {
        assertEquals("720p HD", MediaInfoFormat.resolutionLabel(720))
    }

    @Test
    fun `resolutionLabel 480 to 719 is SD`() {
        assertEquals("480p SD", MediaInfoFormat.resolutionLabel(480))
    }

    @Test
    fun `resolutionLabel below 480 renders raw height`() {
        assertEquals("360p", MediaInfoFormat.resolutionLabel(360))
        assertEquals("240p", MediaInfoFormat.resolutionLabel(240))
    }

    // ── channelLabel ───────────────────────────────────────────────────

    @Test
    fun `channelLabel maps known channel counts`() {
        assertEquals("1.0 (Mono)", MediaInfoFormat.channelLabel(1))
        assertEquals("2.0 (Stereo)", MediaInfoFormat.channelLabel(2))
        assertEquals("5.1 (Surround)", MediaInfoFormat.channelLabel(6))
        assertEquals("7.1 (Surround)", MediaInfoFormat.channelLabel(8))
    }

    @Test
    fun `channelLabel renders raw count for uncommon layouts`() {
        assertEquals("4 ch", MediaInfoFormat.channelLabel(4))
        assertEquals("0 ch", MediaInfoFormat.channelLabel(0))
        assertEquals("16 ch", MediaInfoFormat.channelLabel(16))
    }

    // ── mediaQualityLabel (compact pill label, shared by remote + offline) ──

    @Test
    fun `mediaQualityLabel null video is Auto SDR`() {
        assertEquals("Auto SDR", mediaQualityLabel(null))
    }

    @Test
    fun `mediaQualityLabel buckets height into 4K HD SD`() {
        assertEquals("4K SDR", mediaQualityLabel(video(height = 2160)))
        assertEquals("4K SDR", mediaQualityLabel(video(height = 4320)))
        assertEquals("HD SDR", mediaQualityLabel(video(height = 1080)))
        assertEquals("HD SDR", mediaQualityLabel(video(height = 720)))
        assertEquals("SD SDR", mediaQualityLabel(video(height = 480)))
    }

    @Test
    fun `mediaQualityLabel appends uppercased range with DoVi precedence`() {
        assertEquals("4K DOLBY VISION", mediaQualityLabel(video(height = 2160, doVi = "Dolby Vision")))
        assertEquals("HD HDR", mediaQualityLabel(video(height = 1080, rangeType = "hdr")))
        assertEquals("HD SDR", mediaQualityLabel(video(height = 1080, range = "sdr")))
    }

    // ── mediaAudioLabel (compact pill label, shared by remote + offline) ──

    @Test
    fun `mediaAudioLabel null audio is AUTO`() {
        assertEquals("AUTO", mediaAudioLabel(null, "%d ch"))
    }

    @Test
    fun `mediaAudioLabel prepends uppercased language and channel layout`() {
        assertEquals("ENG - 5.1", mediaAudioLabel(audio(lang = "eng", channels = 6), "%d ch"))
        assertEquals("ENG - MONO", mediaAudioLabel(audio(lang = "eng", channels = 1), "%d ch"))
        assertEquals("ENG - STEREO", mediaAudioLabel(audio(lang = "eng", channels = 2), "%d ch"))
        assertEquals("ENG - 7.1", mediaAudioLabel(audio(lang = "eng", channels = 8), "%d ch"))
    }

    @Test
    fun `mediaAudioLabel falls back to format string for uncommon channel counts`() {
        assertEquals("FRA - 4 ch", mediaAudioLabel(audio(lang = "fra", channels = 4), "%d ch"))
    }

    private fun video(
        height: Int? = 1080,
        range: String? = null,
        rangeType: String? = null,
        doVi: String? = null,
    ) = MediaStream(
        index = 0,
        type = StreamType.VIDEO,
        height = height,
        videoRange = range,
        videoRangeType = rangeType,
        videoDoViTitle = doVi,
    )

    private fun audio(lang: String? = null, channels: Int? = null) = MediaStream(
        index = 1,
        type = StreamType.AUDIO,
        language = lang,
        channels = channels,
    )
}
