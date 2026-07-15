package com.raulshma.jellyplay.feature.subtitle.tester

import org.junit.Assert.assertEquals
import org.junit.Test

class SampleSubtitlePresetsTest {

    @Test
    fun registry_hasFourPresets() {
        assertEquals(4, SampleSubtitlePresets.ALL.size)
    }

    @Test
    fun registry_idsAreUnique() {
        val ids = SampleSubtitlePresets.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun default_isDialogue() {
        assertEquals("dialogue", SampleSubtitlePresets.DEFAULT.id)
    }

    @Test
    fun byId_returnsPresetForKnownId() {
        val preset = SampleSubtitlePresets.byId("dialogue")
        assertEquals("dialogue", preset.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun byId_throwsForUnknownId() {
        SampleSubtitlePresets.byId("nonexistent")
    }

    @Test
    fun eachPreset_hasDistinctResources() {
        val srtIds = SampleSubtitlePresets.ALL.map { it.srtResId }
        val assIds = SampleSubtitlePresets.ALL.map { it.assResId }
        assertEquals(srtIds.size, srtIds.toSet().size)
        assertEquals(assIds.size, assIds.toSet().size)
    }
}
