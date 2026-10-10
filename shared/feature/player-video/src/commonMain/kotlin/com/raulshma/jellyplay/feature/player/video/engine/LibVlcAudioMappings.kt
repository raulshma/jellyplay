package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.AudioPassthroughCodec
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MaxAudioChannelsEnum

/**
 * Pure mapping helpers for the libVLC audio options that depend on the
 * audio-capability preferences (per-codec passthrough, max channels) and the
 * night-mode compressor, the LibVLC twin of the mpv tables in
 * [MpvConfigMapping]/`MpvAudioMappings`: every input is a plain core.model
 * enum/table, so the mappings stay jvmTest-pinnable without a libVLC handle.
 * The Android `LibVlcPlayerEngine` is the sole caller.
 */

/**
 * The `--codec` allowlist value for the enabled [codecs], in the enum's
 * fixed order (`dtshd` is the token the engine has always used for dts-hd).
 * With every codec enabled this reproduces the legacy fixed list byte for
 * byte; an empty enabled set composes nothing — `null`, i.e. no allowlist
 * option at all (VLC's default decoder selection, no bitstreaming).
 */
internal fun libVlcPassthroughCodecList(codecs: Set<AudioPassthroughCodec>): String? =
    AudioPassthroughCodec.entries
        .filter { it in codecs }
        .joinToString(",") { it.vlcKey }
        .ifEmpty { null }

/**
 * The option narrowing the output layout to [maxAudioChannels], or `null`
 * when the engine has no lever for it: mono/stereo caps map onto the
 * `--stereo-mode` downmix values the engine already uses for the channel-mix
 * modes; 5.1/7.1 caps have no "max channels" option in libVLC —
 * `--audio-channels` would force-UPmix narrower sources, so the cap is not
 * enforced there (VLC plays the source layout as-is).
 */
internal fun libVlcChannelCapOption(maxAudioChannels: MaxAudioChannelsEnum): String? = when (maxAudioChannels) {
    MaxAudioChannelsEnum.AUTO -> null
    MaxAudioChannelsEnum.MONO -> "--stereo-mode=mono"
    MaxAudioChannelsEnum.STEREO -> "--stereo-mode=stereo"
    MaxAudioChannelsEnum.FIVE_POINT_ONE, MaxAudioChannelsEnum.SEVEN_POINT_ONE -> null
}

/**
 * The night-mode compressor parameter rows for [strength] —
 * the param half of the `--audio-filter=compressor` arm the engine composes
 * at load time. Empty for [EffectStrength.NONE] (no filter, no params).
 *
 * **Attenuation semantics (consistent with core:data's EffectStrengthMapping,
 * the NightModeHelper/Exo/mpv table):** a stronger strength means MORE
 * compression, and the makeup gain reuses that table's loudness-enhancer
 * decibel steps (LOW +1.5 dB / MODERATE +3 dB / HIGH +4.5 dB) — the same
 * "quiet passages stay audible" compensation the session-effect arms apply,
 * folded into the compressor's own makeup stage here (libVLC has no
 * audio-session effect surface for this engine). The threshold stays at the
 * DYNAMIC-normalization arm's −18 dB (the one libVLC compressor value the
 * engine already pinned); only the ratio scales with the strength.
 */
internal fun libVlcNightModeCompressorOptions(strength: EffectStrength): List<String> = when (strength) {
    EffectStrength.NONE -> emptyList()
    EffectStrength.LOW -> listOf(
        "--compressor-ratio=2",
        "--compressor-threshold=-18",
        "--compressor-makeup-gain=1.5",
    )
    EffectStrength.MODERATE -> listOf(
        "--compressor-ratio=4",
        "--compressor-threshold=-18",
        "--compressor-makeup-gain=3",
    )
    EffectStrength.HIGH -> listOf(
        "--compressor-ratio=8",
        "--compressor-threshold=-18",
        "--compressor-makeup-gain=4.5",
    )
}
