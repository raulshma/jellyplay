package com.raulshma.jellyplay.feature.player.video.subtitle

import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class AssSupportTest {

    private fun sub(codec: String?, id: String = "s1") = SubtitleSource(
        url = "https://x/sub",
        label = "Sub",
        language = null,
        mimeType = null,
        codec = codec,
        id = id,
    )

    private fun request(codecs: List<String?>) = PlaybackRequest(
        uri = "https://x/item",
        title = "x",
        externalSubtitles = codecs.map { sub(it) },
    )

    @Test
    fun hasAssSubtitles_assCodec_true() {
        assertTrue(AssSupport.hasAssSubtitles(request(listOf("ass"))))
    }

    @Test
    fun hasAssSubtitles_ssaCodec_true() {
        assertTrue(AssSupport.hasAssSubtitles(request(listOf("ssa"))))
    }

    @Test
    fun hasAssSubtitles_caseInsensitive_true() {
        assertTrue(AssSupport.hasAssSubtitles(request(listOf("ASS"))))
    }

    @Test
    fun hasAssSubtitles_srtCodec_false() {
        assertFalse(AssSupport.hasAssSubtitles(request(listOf("srt"))))
    }

    @Test
    fun hasAssSubtitles_nullCodec_false() {
        assertFalse(AssSupport.hasAssSubtitles(request(listOf(null))))
    }

    @Test
    fun hasAssSubtitles_mixedCodecs_trueIfAnyAss() {
        assertTrue(AssSupport.hasAssSubtitles(request(listOf("srt", null, "ass"))))
    }

    @Test
    fun hasAssSubtitles_emptySubtitles_false() {
        assertFalse(AssSupport.hasAssSubtitles(request(emptyList())))
    }
}
