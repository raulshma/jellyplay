package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.AudioPassthroughCodec
import com.raulshma.jellyplay.core.model.MaxAudioChannelsEnum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins for the pure libVLC audio-option mappings ([LibVlcAudioMappings.kt])
 * the Android `LibVlcPlayerEngine` composes into its `--codec` allowlist and
 * channel-cap option — the per-codec token table is behavior (the full-set
 * composition must reproduce the engine's legacy fixed list byte for byte),
 * so the engine cannot drift from the tested table.
 */
class LibVlcAudioMappingsTest {

    // ── libVlcPassthroughCodecList ─────────────────────────────────────────

    @Test
    fun codecList_fullSet_reproducesTheLegacyFixedList() {
        assertEquals("ac3,eac3,dts,dtshd,truehd", libVlcPassthroughCodecList(AudioPassthroughCodec.ALL))
    }

    @Test
    fun codecList_disabledCodecs_dropFromTheAllowlist() {
        val list = libVlcPassthroughCodecList(
            setOf(AudioPassthroughCodec.AC3, AudioPassthroughCodec.DTS, AudioPassthroughCodec.TRUEHD),
        )
        assertEquals("ac3,dts,truehd", list)
        assertFalse(list!!.contains("eac3"))
        assertFalse(list.contains("dtshd"))
    }

    @Test
    fun codecList_emptySet_composesNothing() {
        assertNull(libVlcPassthroughCodecList(emptySet()))
    }

    // ── libVlcChannelCapOption ─────────────────────────────────────────────

    @Test
    fun channelCap_monoAndStereo_mapOntoStereoMode() {
        assertEquals("--stereo-mode=mono", libVlcChannelCapOption(MaxAudioChannelsEnum.MONO))
        assertEquals("--stereo-mode=stereo", libVlcChannelCapOption(MaxAudioChannelsEnum.STEREO))
    }

    @Test
    fun channelCap_autoAndSurroundLayouts_haveNoLever() {
        // `--audio-channels` would force-UPmix narrower sources, so the wider
        // caps deliberately have no option (VLC plays the source layout).
        assertNull(libVlcChannelCapOption(MaxAudioChannelsEnum.AUTO))
        assertNull(libVlcChannelCapOption(MaxAudioChannelsEnum.FIVE_POINT_ONE))
        assertNull(libVlcChannelCapOption(MaxAudioChannelsEnum.SEVEN_POINT_ONE))
    }

    @Test
    fun codecList_tokens_matchTheEnumTable() {
        // Every enabled codec's vlcKey appears exactly once, in enum order —
        // the order the engine has always used.
        val list = libVlcPassthroughCodecList(AudioPassthroughCodec.ALL)!!
        val tokens = list.split(',')
        assertEquals(AudioPassthroughCodec.entries.map { it.vlcKey }, tokens)
        assertTrue(tokens.noDuplicates())
    }

    private fun List<String>.noDuplicates(): Boolean = size == toSet().size
}
