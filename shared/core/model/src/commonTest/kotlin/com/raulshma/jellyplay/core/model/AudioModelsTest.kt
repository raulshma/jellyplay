package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class AudioModelsTest {

    @Test
    fun testBandLevelsForAllPresets() {
        assertEquals(List(10) { 0 }, EqualizerPreset.FLAT.bandLevels())
        assertEquals(listOf(600, 500, 400, 200, 0, 0, 0, 0, 0, 0), EqualizerPreset.BASS_BOOST.bandLevels())
        assertEquals(listOf(0, 0, 0, 0, 0, 200, 400, 500, 600, 600), EqualizerPreset.TREBLE_BOOST.bandLevels())
        assertEquals(listOf(400, 300, 100, 0, -100, 100, 300, 400, 400, 400), EqualizerPreset.ROCK.bandLevels())
        assertEquals(listOf(-100, 100, 300, 400, 300, 100, -100, -100, 100, 200), EqualizerPreset.POP.bandLevels())
        assertEquals(listOf(300, 200, 100, 200, -100, -100, 0, 100, 200, 300), EqualizerPreset.JAZZ.bandLevels())
        assertEquals(listOf(400, 300, 200, 100, -100, -100, 0, 200, 300, 400), EqualizerPreset.CLASSICAL.bandLevels())
        assertEquals(listOf(500, 400, 100, 0, -200, 0, 100, 300, 400, 500), EqualizerPreset.ELECTRONIC.bandLevels())
        assertEquals(listOf(500, 400, 300, 100, -100, -100, 100, 100, 200, 300), EqualizerPreset.HIP_HOP.bandLevels())
        assertEquals(listOf(-200, -100, 0, 300, 500, 500, 400, 200, 0, -100), EqualizerPreset.VOCAL.bandLevels())
        assertEquals(listOf(300, 200, 100, 200, 100, 200, 200, 300, 300, 200), EqualizerPreset.ACOUSTIC.bandLevels())
        assertEquals(listOf(-300, 0, 100, 500, 500, 400, 200, 0, -200, -300), EqualizerPreset.PODCAST.bandLevels())
        assertEquals(listOf(400, 300, 100, 0, 100, 200, 200, 300, 300, 300), EqualizerPreset.LATIN.bandLevels())
        assertEquals(List(10) { 0 }, EqualizerPreset.CUSTOM.bandLevels())
    }

    @Test
    fun testFromGenre() {
        // Rock matches
        assertEquals(EqualizerPreset.ROCK, EqualizerPreset.fromGenre("Rock"))
        assertEquals(EqualizerPreset.ROCK, EqualizerPreset.fromGenre("hard rock"))
        assertEquals(EqualizerPreset.ROCK, EqualizerPreset.fromGenre("heavy metal"))
        assertEquals(EqualizerPreset.ROCK, EqualizerPreset.fromGenre("punk rock"))

        // Pop matches
        assertEquals(EqualizerPreset.POP, EqualizerPreset.fromGenre("Pop"))
        assertEquals(EqualizerPreset.POP, EqualizerPreset.fromGenre("synth-pop"))
        assertEquals(EqualizerPreset.POP, EqualizerPreset.fromGenre("soul"))
        assertEquals(EqualizerPreset.POP, EqualizerPreset.fromGenre("funk"))
        assertEquals(EqualizerPreset.POP, EqualizerPreset.fromGenre("disco"))

        // Jazz matches
        assertEquals(EqualizerPreset.JAZZ, EqualizerPreset.fromGenre("Jazz"))
        assertEquals(EqualizerPreset.JAZZ, EqualizerPreset.fromGenre("acid jazz"))
        assertEquals(EqualizerPreset.JAZZ, EqualizerPreset.fromGenre("delta blues"))

        // Classical matches
        assertEquals(EqualizerPreset.CLASSICAL, EqualizerPreset.fromGenre("Classical"))
        assertEquals(EqualizerPreset.CLASSICAL, EqualizerPreset.fromGenre("soundtrack"))
        assertEquals(EqualizerPreset.CLASSICAL, EqualizerPreset.fromGenre("orchestral score"))
        assertEquals(EqualizerPreset.CLASSICAL, EqualizerPreset.fromGenre("instrumental"))

        // Electronic matches
        assertEquals(EqualizerPreset.ELECTRONIC, EqualizerPreset.fromGenre("Electronic"))
        assertEquals(EqualizerPreset.ELECTRONIC, EqualizerPreset.fromGenre("edm"))
        assertEquals(EqualizerPreset.ELECTRONIC, EqualizerPreset.fromGenre("house music"))
        assertEquals(EqualizerPreset.ELECTRONIC, EqualizerPreset.fromGenre("trance"))
        assertEquals(EqualizerPreset.ELECTRONIC, EqualizerPreset.fromGenre("drum and bass"))
        assertEquals(EqualizerPreset.ELECTRONIC, EqualizerPreset.fromGenre("dubstep"))

        // Hip Hop matches
        assertEquals(EqualizerPreset.HIP_HOP, EqualizerPreset.fromGenre("hip hop"))
        assertEquals(EqualizerPreset.HIP_HOP, EqualizerPreset.fromGenre("gangsta rap"))
        assertEquals(EqualizerPreset.HIP_HOP, EqualizerPreset.fromGenre("rnb"))
        assertEquals(EqualizerPreset.HIP_HOP, EqualizerPreset.fromGenre("r&b"))

        // Vocal matches
        assertEquals(EqualizerPreset.VOCAL, EqualizerPreset.fromGenre("Vocal"))
        assertEquals(EqualizerPreset.VOCAL, EqualizerPreset.fromGenre("acapella group"))
        assertEquals(EqualizerPreset.VOCAL, EqualizerPreset.fromGenre("choral harmony"))

        // Acoustic matches
        assertEquals(EqualizerPreset.ACOUSTIC, EqualizerPreset.fromGenre("Acoustic"))
        assertEquals(EqualizerPreset.ACOUSTIC, EqualizerPreset.fromGenre("folk"))
        assertEquals(EqualizerPreset.ACOUSTIC, EqualizerPreset.fromGenre("singer-songwriter"))
        assertEquals(EqualizerPreset.ACOUSTIC, EqualizerPreset.fromGenre("country music"))

        // Podcast matches
        assertEquals(EqualizerPreset.PODCAST, EqualizerPreset.fromGenre("Podcast"))
        assertEquals(EqualizerPreset.PODCAST, EqualizerPreset.fromGenre("spoken word"))
        assertEquals(EqualizerPreset.PODCAST, EqualizerPreset.fromGenre("speech"))
        assertEquals(EqualizerPreset.PODCAST, EqualizerPreset.fromGenre("audiobook reader"))

        // Latin matches
        assertEquals(EqualizerPreset.LATIN, EqualizerPreset.fromGenre("Latin"))
        assertEquals(EqualizerPreset.LATIN, EqualizerPreset.fromGenre("reggaeton"))
        assertEquals(EqualizerPreset.LATIN, EqualizerPreset.fromGenre("salsa"))
        assertEquals(EqualizerPreset.LATIN, EqualizerPreset.fromGenre("reggae fusion"))

        // Bass boost matches
        assertEquals(EqualizerPreset.BASS_BOOST, EqualizerPreset.fromGenre("sub-bass booster"))

        // Null fallbacks
        assertNull(EqualizerPreset.fromGenre("unknown-genre"))
        assertNull(EqualizerPreset.fromGenre("xyz"))
    }
}
