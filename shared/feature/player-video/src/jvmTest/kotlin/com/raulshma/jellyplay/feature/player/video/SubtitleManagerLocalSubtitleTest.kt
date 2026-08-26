package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

/**
 * Wave 8C: SubtitleManager is commonMain and jvmTest-reachable for the first
 * time. These tests pin the local-subtitle side-load mapping — the codec
 * inference from the picked file name and the label derivation — which the
 * androidMain Uri overload extension forwards into unchanged.
 */
class SubtitleManagerLocalSubtitleTest {

    private fun manager(captured: MutableList<SubtitleSource>): SubtitleManager = SubtitleManager(
        contentGateway = object : SubtitleContentGateway {
            override fun queryFileSizeBytes(uri: String): Long = 0L
            override fun readBytes(uri: String): ByteArray = ByteArray(0)
        },
        playbackRepository = mockk(relaxed = true),
        mediaRepository = mockk(relaxed = true),
        subtitleProviderRepository = mockk(relaxed = true),
        streamingSubtitleStore = mockk(relaxed = true),
        userMessageBus = object : PlayerVideoMessageBus {
            override fun info(message: String) {}
            override fun error(message: String) {}
            override fun info(message: PlayerVideoMessage) {}
        },
        scope = CoroutineScope(Dispatchers.Unconfined),
        addExternalSubtitle = { captured.add(it) },
        getMediaStreams = { emptyList<MediaStream>() },
        getCurrentItemId = { "item-1" },
        onMediaDetailRefreshed = { },
        getCurrentMediaDetail = { null },
    )

    @Test
    fun `srt pick maps to the srt codec and derives the label`() = runTest {
        val captured = mutableListOf<SubtitleSource>()
        manager(captured).addLocalSubtitle("content://sub/1", "My Subtitle.srt")
        assertEquals(1, captured.size)
        val source = captured.first()
        assertEquals("content://sub/1", source.url)
        assertEquals("My Subtitle", source.label)
        assertEquals("srt", source.codec)
        assertNull(source.language)
        assertTrue(source.id.startsWith("local:"))
        assertEquals(false, source.isDefault)
        assertEquals(false, source.isForced)
    }

    @Test
    fun `ass and ssa picks map to the ass codec`() {
        val captured = mutableListOf<SubtitleSource>()
        val m = manager(captured)
        m.addLocalSubtitle("content://sub/a", "styles.ass")
        m.addLocalSubtitle("content://sub/b", "styles.ssa")
        assertEquals("ass", captured[0].codec)
        assertEquals("ass", captured[1].codec)
    }

    @Test
    fun `vtt and ttml picks map to their codecs`() {
        val captured = mutableListOf<SubtitleSource>()
        val m = manager(captured)
        m.addLocalSubtitle("content://sub/c", "web.vtt")
        m.addLocalSubtitle("content://sub/d", "doc.ttml")
        m.addLocalSubtitle("content://sub/e", "doc.dfxp")
        assertEquals("vtt", captured[0].codec)
        assertEquals("ttml", captured[1].codec)
        assertEquals("ttml", captured[2].codec)
    }

    @Test
    fun `unknown extension maps to a null codec and blank label falls back`() {
        val captured = mutableListOf<SubtitleSource>()
        manager(captured).addLocalSubtitle("content://sub/f", ".sub")
        val source = captured.first()
        assertNull(source.codec)
        // ".sub" has an empty stem -> the hardcoded fallback label.
        assertEquals("Local subtitle", source.label)
    }
}
