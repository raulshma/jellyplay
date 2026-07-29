package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.ChannelMixMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MpvAudioConfigurationTest {

    @Test
    fun channelMix_disabledAlwaysRestoresMpvAutoLayout() {
        ChannelMixMode.entries
            .filter { it != ChannelMixMode.AUTO }
            .forEach { mode ->
                assertEquals("auto", channelMixModeToAudioChannels(mode, enabled = false))
            }
    }

    @Test
    fun channelMix_enabledUsesRequestedMpvLayout() {
        assertEquals("stereo", channelMixModeToAudioChannels(ChannelMixMode.STEREO_DOWNMIX))
        assertEquals("mono", channelMixModeToAudioChannels(ChannelMixMode.MONO))
        assertEquals("5.1", channelMixModeToAudioChannels(ChannelMixMode.SURROUND_UPMIX))
    }
}
