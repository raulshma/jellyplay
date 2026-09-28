package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.AudioPassthroughCodec
import com.raulshma.jellyplay.core.model.MaxAudioChannelsEnum

/**
 * Pure mapping helpers for the libVLC audio options that depend on the
 * audio-capability preferences (per-codec passthrough, max channels), the
 * LibVLC twin of the mpv tables in [MpvConfigMapping]/`MpvAudioMappings`:
 * every input is a plain core.model enum/table, so the mappings stay
 * jvmTest-pinnable without a libVLC handle. The Android `LibVlcPlayerEngine`
 * is the sole caller.
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
