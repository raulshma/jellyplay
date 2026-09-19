package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig

/**
 * Construction-relevant inputs of the last ExoPlayer build. When a new load
 * carries an identical set, the existing player is reused with a bare
 * `Player.setMediaItem` + prepare — the common binge-watch/autoplay case
 * where renderers factory (decoder mode, fallback, audio-processor chain),
 * LoadControl buffers, DRM hook, auth and the ASS session shape are all
 * unchanged. Any delta takes the full teardown/rebuild path, so behavior
 * stays identical to a fresh engine.
 *
 * Lives in commonMain (the `MpvErrorTaxonomy` pattern) so the reuse
 * predicate over it is jvmTest-pinnable — every input is a common type
 * (core.model enums/data classes, the type-erased
 * [EngineDrmSessionManagerProvider], plain values). The androidMain
 * `ExoPlayerEngine` builds one per load and consults
 * [shouldReuseExistingPlayer]; the media3 player handle itself stays behind
 * the call site (passed in as a plain presence flag).
 */
internal data class LoadRebuildInputs(
    val decoderMode: DecoderMode,
    val exoCfg: ExoPlayerEngineConfig,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val serverUrl: String?,
    val authToken: String?,
    val headers: Map<String, String>,
    val assSession: Boolean,
    val pauseOnAudioFocusLoss: Boolean,
    // The provider itself (not its product): providers have no equals,
    // so data-class equality degrades to identity — same instance means
    // same DRM hook, different instance forces the rebuild path.
    val drmProvider: EngineDrmSessionManagerProvider?,
    // Whether the data source chain gets the byte-level `VideoStreamCache`
    // wrapper. Part of the equality set because the wrapper is baked into
    // the player's MediaSourceFactory: a reused player keeps its existing
    // chain, so an eligibility flip between items (direct play → transcode,
    // clear → DRM) MUST take the teardown/rebuild path instead of leaking
    // the cached chain onto a non-cacheable item (or vice versa).
    val streamCacheEligible: Boolean,
)

/**
 * The reuse-vs-rebuild decision over [LoadRebuildInputs]: an existing player
 * is reused only when one is present AND the new request's construction
 * inputs are equal to the last build's. Pure — the androidMain engine passes
 * player presence as a flag so no Media3 type crosses into commonMain.
 */
internal fun shouldReuseExistingPlayer(
    existingPlayerPresent: Boolean,
    lastInputs: LoadRebuildInputs?,
    newInputs: LoadRebuildInputs,
): Boolean = existingPlayerPresent && lastInputs == newInputs
