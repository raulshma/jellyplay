package com.raulshma.jellyplay.feature.player.video.engine

/**
 * The spatial-audio decision model for the ExoPlayer engine.
 *
 * **Research constraint this model encodes (do not "fix" it into a fake
 * on/off):** Android's spatializer (`android.media.Spatializer`, API 32+) is a
 * QUERY-ONLY surface. There is no app-side enable — the platform spatializes a
 * track when (a) the device has a supported spatializer, (b) the user's system
 * spatial-audio setting is on (`Spatializer.isEnabled()`, user-controlled in
 * system settings), (c) the current output route qualifies
 * (`Spatializer.isAvailable()` / `canBeSpatialized(...)`, typically
 * headphones), and (d) the stream actually reaches the `AudioTrack`
 * multichannel and non-offloaded. The only app-side controls are opt-outs
 * (`SPATIALIZATION_BEHAVIOR_NEVER`, marking content pre-spatialized) — never an
 * enable. Media3 1.11 surfaces the same truth: `SpatializerWrapper` (its public
 * util) only queries/state-listens, and `DefaultAudioSink` exposes no
 * spatialization option — the platform engages at the mixer for qualifying
 * AudioTracks.
 *
 * The app-side feature is therefore: qualification gating + "don't defeat it"
 * + honest UI state. The user's in-app toggle is ADVISORY — it gates what the
 * ENGINE does to keep the stream spatializable (holding the in-sink 5.1→2.0
 * downmix and the speaker-layout cap out of the way, and holding the
 * Virtualizer session effect off — the two effects are mutually exclusive and
 * spatial wins), never the spatialization itself. (Canonical rationale home;
 * mirrored on [com.raulshma.jellyplay.feature.player.video.engine.SpatialAudioState]
 * and [AudioEffectsConfig.spatialAudioEnabled] in the contract.)
 *
 * **Spec reliance (the C5 spec's "auto-disable when passthrough or ≥5.1
 * output is active"):** no dedicated branch exists because both cases are
 * excluded structurally. ExoPlayer cannot passthrough
 * (`EngineCapabilities.supportsAudioPassthrough = false` — it always decodes
 * to PCM), and ≥5.1 speaker/receiver routes are excluded by the
 * headphones-only qualification (HDMI/USB-audio-class outputs are not
 * headphone-class per the route classifier) plus the platform spatializer's
 * own route rules, which decline non-qualifying routes regardless of the
 * gate. If either premise changes (a passthrough-capable Exo path, or the
 * gate being turned off by default), revisit this: the OFF gate below is
 * where such a branch would live.
 *
 * All decision inputs are plain values so JVM tests cover the whole truth
 * table; the Android [SpatializerHelper] resolves them from the platform
 * (API-32-guarded) and re-runs [resolveEffectiveSpatialState] on every change.
 *
 * @param userEnabled the user's in-app advisory toggle ([AudioEffectsConfig.spatialAudioEnabled]).
 * @param onlyHeadphones the headphones-only qualification ([AudioEffectsConfig.spatialAudioOnlyHeadphones]).
 * @param outputIsHeadphoneClass the current output is a headphone-class device (wired/BT/USB-BT/BLE/hearing-aid).
 * @param offloadActive the session renders through audio offload (platform
 *   spatialization is bypassed there) — forced when
 *   `ExoPlayerEngineConfig.audioOffloadMode != DISABLED`.
 * @param systemSpatialEnabled the platform spatializer is enabled system-side
 *   (`Spatializer.isEnabled()`; false on unsupported devices / pre-API-32).
 * @param canBeSpatialized `Spatializer.canBeSpatialized(movieAttributes, format)`
 *   for the playing format — device+route+format capability, independent of
 *   the system setting.
 */
fun resolveEffectiveSpatialState(
    userEnabled: Boolean,
    onlyHeadphones: Boolean,
    outputIsHeadphoneClass: Boolean,
    offloadActive: Boolean,
    systemSpatialEnabled: Boolean,
    canBeSpatialized: Boolean,
): SpatialAudioState {
    // Capability first: without it there is nothing to talk about (this also
    // covers pre-API-32 devices and devices with no spatializer — the helper
    // reports canBeSpatialized=false there).
    if (!canBeSpatialized) return SpatialAudioState.UNAVAILABLE
    // The engine's own configuration holding spatial out.
    if (offloadActive) return SpatialAudioState.OFF
    val headphonesGateHolds = !onlyHeadphones || outputIsHeadphoneClass
    if (!headphonesGateHolds) return SpatialAudioState.OFF
    // Route fine, no hard gate: the only missing pieces are user switches.
    return if (userEnabled && systemSpatialEnabled) SpatialAudioState.ACTIVE
    else SpatialAudioState.AVAILABLE
}

/**
 * The mutual-exclusion report: spatial and the Virtualizer session effect are
 * mutually exclusive, and when both are requested spatial wins — the engine
 * then holds the virtualizer off for the session and the UI/stats may report
 * the supersession. True only when spatial is EFFECTIVE while the virtualizer
 * is (or was) requested.
 */
fun spatialSupersedesVirtualizer(
    state: SpatialAudioState,
    virtualizerActive: Boolean,
): Boolean = state == SpatialAudioState.ACTIVE && virtualizerActive

/**
 * The engine-owned session inputs of one spatial-state evaluation — the
 * engine's half of [resolveEffectiveSpatialState]'s parameter list (the
 * user's advisory switches + the engine's own configuration; the platform
 * answers arrive separately). Bundled so the engine pushes them into the
 * platform helper's `update` as one typed object instead of a loose clump
 * the helper would immediately repack. The virtualizer is deliberately NOT
 * a member: it never gates the state — supersession is reported separately
 * via [spatialSupersedesVirtualizer] from the config the engine already
 * holds.
 *
 * The playing format is deliberately NOT a member: it is the platform-query
 * memo key, not a decision input, and media3 types stay out of commonMain
 * (the source-set rule ExoErrorTaxonomy records) — the helper carries it as
 * its own update parameter.
 */
internal data class SpatialAudioInputs(
    val userEnabled: Boolean,
    val onlyHeadphones: Boolean,
    val offloadActive: Boolean,
)
