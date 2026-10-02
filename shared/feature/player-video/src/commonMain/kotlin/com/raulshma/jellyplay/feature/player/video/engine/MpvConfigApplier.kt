package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvPropertySurface

/**
 * The ONE mpv config-delta DISPATCH choreography both mpv hosts ride — the
 * runtime twin of the player-contract [MpvSubtitleStyleApplier] for the
 * `MediaEngine.onConfigChanged` ladder. The two engines'
 * `onConfigChanged` bodies (Android `MpvPlayerEngine`, desktop
 * `MpvDesktopEngine`) dispatched the same [EngineConfigDelta] arms through
 * the same write order, hand-mirrored — and the ordering rules already
 * drifted once (both files' comments record the same fixed
 * ownership-refresh-before-style-reapply bug). This applier owns the ORDER
 * and the genuinely-shared arms; the engines keep their platform-native arms
 * as the constructor hooks:
 *
 *  - **Android**: the `ao`/`audioFallback` chain write, the AudioEffect
 *    session re-apply and the `hwdecOverride` value (its hwdec arm consults
 *    the per-config override the desktop has no UI for);
 *  - **Desktop**: the `pitch` property, the extracted `shaderDir` and the
 *    HDR-active `toneMappingSuppressed` gate — all [Extras] the shared-pairs
 *    arm and its audio hook consume.
 *
 * ## The dispatch order (pinned by `MpvConfigApplierTest`)
 *
 *  1. `refreshOwnedKeys` — the sub-* ownership refresh FIRST (the desktop's
 *    unconditional-first discipline; Android's mid-ladder conditional
 *    refresh — gated on `sharedPairsChanged || subtitleStyleChanged` — is
 *    subsumed: every consult below runs against fresh ownership either way,
 *    and the refresh is a pure host-side cache read, no mpv write);
 *  2. `audio-delay` (FORMAT_DOUBLE, seconds) on `audioDelayChanged`;
 *  3. `sub-delay` (FORMAT_DOUBLE, seconds) on `subtitleDelayChanged`;
 *  4. `hwdec` (string) on `decoderModeChanged` OR a changed
 *     `MpvEngineConfig.hwdecOverride`, value from [hwdecValue] (the
 *     condition is the UNION of the two engines' former arms — the desktop's
 *     extra fire on an override-only change writes its own mode-derived
 *     value, mpv-identical to what is already set);
 *  5. the shared-pairs re-diff ([MpvConfigMapping.configPairs] +
 *     [MpvConfigMapping.applyChanged] over the [lastAppliedConfigProps]
 *     cache this applier owns — started empty, seeded from init by
 *     [seedAppliedConfigProps] on Android) on `sharedPairsChanged`;
 *  6. `applySubtitleStyle` on `subtitleStyleChanged`;
 *  7. `applyAudioEffects` — ALWAYS invoked with
 *     `(old, new, delta, full)`; the engine gates its native audio arms
 *     inside (Android: `ao`, `audio-channels`, the `af` rebuild, the
 *     AudioEffect session; desktop: the `audio-channels`/`pitch`/`af`
 *     diff-cached trio);
 *  8. `applyVideoEffects` on `videoEffectsChanged` (the engine hook — the
 *     `vf`/`video-rotate` transports are declared per-host divergences).
 *
 * Declared unifications where the former ladders differed without a
 * dependency: the shared-pairs re-diff runs BEFORE the subtitle-style
 * re-apply (Android's order; the desktop's `applyConfigToMpv` ran style
 * first — disjoint key sets, order-free), and Android's `ao` +
 * AudioEffect-session arms moved into the audio hook (position 7) from
 * straddling the style arm (the `ao` property and the AudioEffect sessions
 * share no surface with the `sub-*` keys or the `vf` chain between them).
 *
 * [applyFull] is the FILE_LOADED full-apply (the desktop's former
 * `applyConfigToMpv`): same order, every arm forced — the diff caches make
 * the unchanged re-apply write-free.
 *
 * The [surface] is a PROVIDER returning null when the host's handle is not
 * live (`mpvView?.mpv ?: return` on Android, `aliveCtx() ?: return` on the
 * desktop) — a null surface skips the whole apply, the engines' former
 * early-return. Implementations ABSORB their platform's write failures (the
 * [MpvPropertySurface] contract), so the choreography is exception-free by
 * construction. Threading: NOT internally synchronized except
 * [lastAppliedConfigProps] being `@Volatile` — the desktop applies from both
 * the UI thread (`updateConfig`) and the mpv event thread (FILE_LOADED),
 * exactly the discipline its former engine field ran.
 *
 * Public (not internal) because the desktop adapter lives in apps/desktop —
 * the [MpvConfigMapping]/[MpvStyleMapping] precedent. Consumers stay the two
 * engine adapters and the pinning test; not a stable API surface. Home: the
 * player-contract mpv fold-applier family ([MpvFoldApplier],
 * [MpvSubtitleStyleApplier]) lives one module UPSTREAM — this dispatcher
 * stays here beside [MpvConfigMapping], whose runtime half (the shared-pairs
 * re-diff) it owns, and that mapping is player-video's: the dependency edge
 * runs player-video → player-contract, so the applier could not live there
 * without dragging the mapping along.
 */
public class MpvConfigApplier(
    private val surface: () -> MpvPropertySurface?,
    /** Live per-apply extras (the desktop's shader dir / HDR gate are mutable state). */
    private val extras: () -> Extras,
    /** The sub-* ownership cache refresh — run FIRST, before any write. */
    private val refreshOwnedKeys: () -> Unit,
    /** The host's `hwdec` value for a config (Android: override ?: decoder-mode map; desktop: `hwdecFor`). */
    private val hwdecValue: (EngineConfig) -> String,
    private val applySubtitleStyle: (EngineConfig) -> Unit,
    private val applyAudioEffects: (old: EngineConfig, new: EngineConfig, delta: EngineConfigDelta, full: Boolean) -> Unit,
    private val applyVideoEffects: (EngineConfig) -> Unit,
) {

    /**
     * The engine-side inputs the shared arms need beyond the configs:
     * [lowRamDevice] picks the AUTO demuxer budget (Android), [shaderDir]
     * resolves the shader-pack paths and [toneMappingSuppressed] forces the
     * `tone-mapping` default while HDR passthrough is active (desktop).
     */
    public data class Extras(
        val lowRamDevice: Boolean = false,
        val shaderDir: String? = null,
        val toneMappingSuppressed: Boolean = false,
    )

    /** The shared-pairs diff cache — the former engines' `lastApplied*` field. */
    @Volatile
    public var lastAppliedConfigProps: Map<String, String> = emptyMap()
        private set

    /**
     * Seeds the diff cache with the pairs init already wrote (Android's
     * `initOptions` — it writes OPTIONS pre-init, a different transport, so
     * its writes stay engine-side; only the cache knowledge moves here).
     */
    public fun seedAppliedConfigProps(applied: Map<String, String>) {
        lastAppliedConfigProps = applied
    }

    /** The runtime delta dispatch — the engines' former `onConfigChanged` body. */
    public fun applyDelta(old: EngineConfig, new: EngineConfig) {
        if (surface() == null) return
        dispatch(old, new, EngineConfigDelta.of(old, new), full = false)
    }

    /** The FILE_LOADED full apply — every arm forced, same order (the desktop's former `applyConfigToMpv`). */
    public fun applyFull(config: EngineConfig) {
        if (surface() == null) return
        dispatch(null, config, EngineConfigDelta.NONE, full = true)
    }

    private fun dispatch(old: EngineConfig?, new: EngineConfig, delta: EngineConfigDelta, full: Boolean) {
        val s = surface() ?: return
        refreshOwnedKeys()
        val mpvCfg = new.engineSpecific as? MpvEngineConfig ?: MpvEngineConfig()
        val oldMpvCfg = old?.engineSpecific as? MpvEngineConfig

        if (full || delta.audioDelayChanged) {
            s.setPropertyDouble("audio-delay", new.audioDelayMs / 1000.0)
        }
        if (full || delta.subtitleDelayChanged) {
            s.setPropertyDouble("sub-delay", new.subtitleDelayMs / 1000.0)
        }
        if (full || delta.decoderModeChanged || oldMpvCfg?.hwdecOverride != mpvCfg.hwdecOverride) {
            s.setPropertyString("hwdec", hwdecValue(new))
        }
        if (full || delta.sharedPairsChanged) {
            // engineSpecific + audioPassthrough + audioPassthroughCodecs +
            // deinterlace + hdrSource (see EngineConfigDelta.sharedPairsChanged).
            val e = extras()
            val pairs = MpvConfigMapping.configPairs(
                config = mpvCfg,
                audioPassthrough = new.audioPassthrough,
                passthroughCodecs = new.audioPassthroughCodecs,
                lowRamDevice = e.lowRamDevice,
                deinterlace = new.deinterlace,
                shaderDir = e.shaderDir,
                toneMappingSuppressed = e.toneMappingSuppressed,
            )
            lastAppliedConfigProps = MpvConfigMapping.applyChanged(pairs, lastAppliedConfigProps) { key, value ->
                s.setPropertyString(key, value)
            }
        }
        if (full || delta.subtitleStyleChanged) {
            applySubtitleStyle(new)
        }
        applyAudioEffects(old ?: new, new, delta, full)
        if (full || delta.videoEffectsChanged) {
            applyVideoEffects(new)
        }
    }
}
