package com.raulshma.jellyplay.core.network.api

/**
 * Reports which Jellyfin codec names the current platform can decode
 * natively (no software fallback).
 *
 * This is the authoritative source for the hardware-path direct-play
 * profile: advertising only codecs the device truly decodes prevents the
 * Jellyfin server from handing back a direct stream that the player then
 * fails to render. Platform implementations map framework codec mimes onto
 * the Jellyfin codec naming convention (see AndroidDeviceCodecCapabilities'
 * MediaCodecList query and the official client's `CodecHelpers`).
 *
 * MPV is exempt — it ships libav/ffmpeg and decodes essentially
 * everything in software, so it advertises a fixed permissive profile.
 */
interface DeviceCodecCapabilities {

    /** Jellyfin video codec names the platform decodes in hardware. */
    val supportedVideoCodecs: Set<String>

    /** Jellyfin audio codec names the platform decodes in hardware. */
    val supportedAudioCodecs: Set<String>
}
