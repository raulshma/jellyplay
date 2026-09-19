package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode

/**
 * Pure mapping helpers for mpv option/property values that were previously
 * duplicated verbatim between `initOptions` (load-time, `setOptionString`)
 * and `updateConfig` (live, `setPropertyString`). Keeping the mapping in one
 * place stops the two sites from drifting.
 *
 * Lives in commonMain (the `MpvOpenableUrl` / `MpvErrorTaxonomy` precedent):
 * every input is a plain core.model enum, so the tables stay jvmTest-pinnable
 * without a libmpv handle. The Android `MpvPlayerEngine` is the sole caller;
 * same package, its call sites are unchanged.
 */

internal fun decoderModeToHwdec(mode: DecoderMode): String = when (mode) {
    // Zero-copy `mediacodec` first: mpv picks the first entry that inits, and
    // `mediacodec-copy` (GPU→CPU→GPU per frame) almost always inits when listed
    // first, so copy-first ordering silently forced every HW decode through the
    // slow path — the primary cause of mpv lag vs. zero-copy ExoPlayer. Keep
    // copy as fallback, then SW last.
    DecoderMode.HW_PREFERRED -> "mediacodec,mediacodec-copy,no"
    DecoderMode.HW_ONLY -> "mediacodec,mediacodec-copy"
    DecoderMode.SW_ONLY -> "no"
}

internal fun channelMixModeToAudioChannels(
    mode: ChannelMixMode,
    enabled: Boolean = true,
): String = if (!enabled) {
    "auto"
} else when (mode) {
    ChannelMixMode.STEREO_DOWNMIX -> "stereo"
    ChannelMixMode.MONO -> "mono"
    ChannelMixMode.SURROUND_UPMIX -> "5.1"
    ChannelMixMode.AUTO -> "auto"
}

/** Returns null for NONE so callers can omit it from the af chain. */
internal fun audioNormalizationModeToAfFilter(mode: AudioNormalizationMode): String? = when (mode) {
    AudioNormalizationMode.DYNAMIC -> "acompressor=ratio=3:threshold=0.05:attack=10:release=200"
    AudioNormalizationMode.TRACK, AudioNormalizationMode.ALBUM -> "loudnorm=I=-23:LRA=7:tp=-1"
    AudioNormalizationMode.NONE -> null
}
