package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * The audio-device slice of the desktop engine-config parity groundwork: the
 * structured [MpvEngineConfig] audio trio actually reaches the live mpv
 * context on `updateConfig` — `audio-device` / `audio-exclusive` /
 * `audio-spdif` read back with the mapped values, including the flip back to
 * an explicit default (the diff cache must WRITE "no"/"" — never skip it).
 * Real libmpv, headless (`vo=null`/`ao=null`), no media loaded — the mapped
 * pairs are ordinary runtime property writes on an idle core. Skips on
 * machines without libmpv (the [MpvDesktopEngineTest] harness pattern).
 */
class MpvDesktopEngineAudioConfigTest {

    private fun libmpvAvailable(): Boolean = try {
        MpvLib.mpv
        true
    } catch (_: Throwable) {
        false
    }

    @Test
    fun audioDevice_write_appliesOnConfigChange() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(
            extraOptions = mapOf("vo" to "null", "ao" to "null"),
        )
        try {
            // "null" is ao_null's registered pseudo-device — present in every
            // mpv build's audio-device-list, valid headless.
            engine.updateConfig(
                EngineConfig(engineSpecific = MpvEngineConfig(audioDevice = "null")),
            )
            assertEquals("null", MpvLib.getPropertyString(checkNotNull(engine.liveMpvHandle()), "audio-device"))

            // Back to the (explicit) default — the null field maps to "auto".
            engine.updateConfig(
                EngineConfig(engineSpecific = MpvEngineConfig(audioDevice = null)),
            )
            assertEquals("auto", MpvLib.getPropertyString(checkNotNull(engine.liveMpvHandle()), "audio-device"))
        } finally {
            engine.release()
        }
    }

    @Test
    fun audioExclusive_flip_writesBothDirections() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(
            extraOptions = mapOf("vo" to "null", "ao" to "null"),
        )
        try {
            engine.updateConfig(
                EngineConfig(engineSpecific = MpvEngineConfig(audioExclusive = true)),
            )
            assertEquals("yes", MpvLib.getPropertyString(checkNotNull(engine.liveMpvHandle()), "audio-exclusive"))

            engine.updateConfig(
                EngineConfig(engineSpecific = MpvEngineConfig(audioExclusive = false)),
            )
            assertEquals("no", MpvLib.getPropertyString(checkNotNull(engine.liveMpvHandle()), "audio-exclusive"))
        } finally {
            engine.release()
        }
    }

    @Test
    fun audioSpdif_modeComposition_reachesMpv() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(
            extraOptions = mapOf("vo" to "null", "ao" to "null"),
        )
        try {
            // An explicit mode wins over the platform boolean (reconciliation).
            engine.updateConfig(
                EngineConfig(
                    audioPassthrough = false,
                    engineSpecific = MpvEngineConfig(audioOutputMode = com.raulshma.jellyplay.core.model.MpvAudioOutputMode.OPTICAL),
                ),
            )
            assertEquals(
                "ac3,dts",
                MpvLib.getPropertyString(checkNotNull(engine.liveMpvHandle()), "audio-spdif"),
            )

            // AUTO + the legacy boolean: the legacy list, byte-identical.
            engine.updateConfig(
                EngineConfig(
                    audioPassthrough = true,
                    engineSpecific = MpvEngineConfig(audioOutputMode = com.raulshma.jellyplay.core.model.MpvAudioOutputMode.AUTO),
                ),
            )
            assertEquals(
                "ac3,eac3,dts,dtshd,truehd",
                MpvLib.getPropertyString(checkNotNull(engine.liveMpvHandle()), "audio-spdif"),
            )

            // AUTO + boolean off: cleared to mpv's list default.
            engine.updateConfig(
                EngineConfig(
                    audioPassthrough = false,
                    engineSpecific = MpvEngineConfig(),
                ),
            )
            assertEquals("", MpvLib.getPropertyString(checkNotNull(engine.liveMpvHandle()), "audio-spdif"))
        } finally {
            engine.release()
        }
    }
}
