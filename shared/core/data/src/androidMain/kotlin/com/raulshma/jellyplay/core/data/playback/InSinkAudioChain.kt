package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi

/**
 * The ONE in-sink DSP chain order — the `DefaultAudioSink.Builder
 * .setAudioProcessors(...)` array was previously hand-written in three
 * places (music's `AudioPlaybackManager.createPlayer`, the crossfade
 * secondary in `AudioCrossfader`, and player-video's `ExoPlayerEngine`),
 * and the stacks had already silently disagreed on whether the balance
 * processor rides the sink. Copy variance in this array is silent signal
 * corruption (a processor placed before the channel mix sees the wrong
 * layout), not a compile error — so the order lives once, here, beside the
 * processors it orders.
 *
 * ## Order policy (load-bearing, top to bottom)
 *
 *  1. [ChannelMixAudioProcessor] FIRST — it is the only processor that may
 *     CHANGE THE CHANNEL COUNT (5.1→2.0 downmix, 2.0→5.1 upmix), so every
 *     downstream processor must see the remixed layout.
 *  2. [DynamicsCompressorAudioProcessor] — DYNAMIC normalization mode's
 *     feed-forward compressor.
 *  3. [ReplayGainAudioProcessor] — TRACK/ALBUM loudness normalization.
 *     Mutually exclusive with the compressor at runtime, but both live in
 *     the chain.
 *  4. [HighPassFilterAudioProcessor] — the sub-bass rumble cut for dialogue
 *     boost; a no-op when boost is off.
 *  5. [BalanceAudioProcessor] — only when [balanceProcessor] is non-null
 *     (see the divergence below); tail position is the historical slot
 *     Android music gave it.
 *
 * ## Balance location (the declared divergence [balanceProcessor] encodes)
 *
 * Android MUSIC applies L/R balance in-sink: `AudioEffectsManager.lrBalance`
 * (via `AudioEffectsProcessor.balanceProcessor`) is the surface, and its
 * processor rides the chain tail. The VIDEO engine has no balance surface at
 * all — `EngineConfig.audioEffects` carries no balance field and nothing in
 * player-video writes a [BalanceAudioProcessor] — so video's chain ends at
 * the high-pass cut (`balanceProcessor = null`, the default).
 *
 * Takes the processor INSTANCES (not classes) because each caller's chain
 * must be its live, command-driven singletons: music's are the
 * `AudioEffectsProcessor` members its effects commands mutate; video's are
 * the `ExoPlayerEngine`-owned instances its `AudioEffectChain` drives. The
 * factory owns only the ORDER.
 */
@UnstableApi
fun inSinkAudioChain(
    channelMixProcessor: ChannelMixAudioProcessor,
    dynamicsProcessor: DynamicsCompressorAudioProcessor,
    replayGainProcessor: ReplayGainAudioProcessor,
    highPassProcessor: HighPassFilterAudioProcessor,
    balanceProcessor: BalanceAudioProcessor? = null,
): Array<AudioProcessor> = if (balanceProcessor != null) {
    arrayOf(
        channelMixProcessor,
        dynamicsProcessor,
        replayGainProcessor,
        highPassProcessor,
        balanceProcessor,
    )
} else {
    arrayOf(
        channelMixProcessor,
        dynamicsProcessor,
        replayGainProcessor,
        highPassProcessor,
    )
}
