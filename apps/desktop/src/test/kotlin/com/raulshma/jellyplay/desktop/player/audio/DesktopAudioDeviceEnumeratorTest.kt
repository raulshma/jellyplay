package com.raulshma.jellyplay.desktop.player.audio

import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Pins the `audio-device-list` node-dump parsing behind the settings screen's
 * device picker: the raw [MpvLib.readNode] plain shape (a list of
 * maps with `name`/`description` string entries) maps to devices with the
 * synthesized `auto` entry first — malformed entries skipped, mpv's own
 * duplicated `auto` row deduped, a failed read degraded to auto-only. The
 * live enumeration rides a real libmpv when one is present.
 */
class DesktopAudioDeviceEnumeratorTest {

    // ── the fixture node dump (MpvLib.readNode's plain shape) ──────────────

    /** Mirrors a real `audio-device-list` read: `auto` first, then AOs. */
    private val fixture = listOf(
        mapOf<String, Any?>(
            "name" to "auto",
            "description" to "Autoselect device",
        ),
        mapOf<String, Any?>(
            "name" to "openal",
            "description" to "OpenAL audio on Default Output Device",
        ),
        mapOf<String, Any?>(
            "name" to "null",
            "description" to "Null audio output",
        ),
        mapOf<String, Any?>(
            "name" to "wasapi/{0.0.0.00000000}.{a1b2c3}/speakers",
            "description" to "Speakers (Realtek High Definition Audio)",
        ),
    )

    @Test
    fun parse_autoIsAlwaysTheFirstEntry() {
        val devices = DesktopAudioDeviceEnumerator.parse(fixture)
        assertEquals(DesktopAudioDeviceEnumerator.AUTO_ENTRY, devices.first())
        assertEquals("auto", devices.first().name)
    }

    @Test
    fun parse_mapsEveryDeviceAfterAuto() {
        val devices = DesktopAudioDeviceEnumerator.parse(fixture).drop(1)
        assertEquals(3, devices.size)
        assertEquals("openal", devices[0].name)
        assertEquals("OpenAL audio on Default Output Device", devices[0].description)
        assertEquals("null", devices[1].name)
        assertEquals("Null audio output", devices[1].description)
        assertEquals("wasapi/{0.0.0.00000000}.{a1b2c3}/speakers", devices[2].name)
        assertEquals("Speakers (Realtek High Definition Audio)", devices[2].description)
    }

    @Test
    fun parse_dedupesMpvOwnAutoRow() {
        // mpv lists `auto` as a real entry — the synthesized row covers it.
        val devices = DesktopAudioDeviceEnumerator.parse(fixture)
        assertEquals(1, devices.count { it.name == "auto" })
    }

    @Test
    fun parse_skipsMalformedEntries() {
        val dirty = listOf(
            mapOf<String, Any?>("name" to "openal", "description" to "OpenAL"),
            mapOf<String, Any?>("description" to "no name — skipped"),
            mapOf<String, Any?>("name" to "", "description" to "blank name — skipped"),
            "a bare string — skipped",
            42,
        )
        val devices = DesktopAudioDeviceEnumerator.parse(dirty).drop(1)
        assertEquals(listOf("openal"), devices.map { it.name })
    }

    @Test
    fun parse_missingDescription_degradesToEmptyString() {
        val devices = DesktopAudioDeviceEnumerator.parse(
            listOf(mapOf<String, Any?>("name" to "openal")),
        ).drop(1)
        assertEquals(1, devices.size)
        assertEquals("", devices[0].description)
    }

    @Test
    fun parse_failedRead_degradesToAutoOnly() {
        for (degenerate in listOf(null, emptyList<Any?>())) {
            val devices = DesktopAudioDeviceEnumerator.parse(degenerate as? List<*>)
            assertEquals(listOf(DesktopAudioDeviceEnumerator.AUTO_ENTRY), devices)
        }
    }

    // ── the live path (real libmpv, when the machine has one) ──────────────

    @Test
    fun enumerate_liveContext_returnsAutoPlusDevices() {
        assumeTrue(try { MpvLib.mpv; true } catch (_: Throwable) { false }, { "libmpv not available" })
        val devices = kotlinx.coroutines.runBlocking {
            DesktopAudioDeviceEnumerator().enumerateAudioDevices()
        }
        assertEquals("auto", devices.first().name)
        // Machine-dependent device sets (WASAPI/OpenAL/SDL/…): the only
        // contract is well-formed entries behind the auto row.
        assertTrue(
            devices.drop(1).isNotEmpty() && devices.drop(1).all { it.name.isNotBlank() },
            "expected well-formed devices behind auto, got ${devices.map { it.name }}",
        )
    }
}
