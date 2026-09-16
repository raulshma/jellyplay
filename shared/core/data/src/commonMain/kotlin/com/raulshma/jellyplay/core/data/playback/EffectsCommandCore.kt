package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Deep module for the shared effects-feature COMMAND half — the ONE
 * "apply → state write → persist" choreography that player-audio's
 * `AudioEffectsController` and player-video's `VideoEffectsController`
 * used to duplicate as private `applyAndPersist` templates (each itself
 * extracted from the same inline ViewModel setter pattern). Generic over
 * the state slice type [S]: the core owns the slice as a
 * [MutableStateFlow], exposes it read-only as [state], and guarantees the
 * ordering every command reduces to.
 *
 * ## Ordering guarantee
 *
 * [applyAndPersist] runs its two synchronous legs — [apply] (the
 * platform/DSP/engine mutation) and [update] (the state-slice write) —
 * BEFORE the caller returns, in the adapter's declared [Order] (below);
 * the persist leg is then launched fire-and-forget on [scope] and is
 * never awaited (DataStore writes stay off the caller's thread; neither
 * former controller awaited it either). The state mirror is therefore
 * correct in the same frame as the engine/manager flip, and a persist
 * block that reads `.value` sources — manager flows, [state] itself —
 * sees the post-apply value.
 *
 * ## Declared divergence: the leg ORDER is a constructor parameter
 *
 * The two adapters genuinely order the apply leg and the state write
 * differently, and the core encodes instead of unifying it:
 *
 *  - [Order.APPLY_FIRST] (player-audio) — the DSP session manager is the
 *    source of truth: its setter flips first (synchronously, so the
 *    manager's `StateFlow.value` is readable by the persist leg in the
 *    same command), then the optional mirror write covers only the
 *    flow-less strength fields. Everything else reaches [state] via the
 *    adapter's own manager-flow collectors, not the command.
 *  - [Order.STATE_FIRST] (player-video) — the state slice IS the engine
 *    config input: it must flip before `syncConfig` (the apply leg)
 *    rebuilds the config, or the engine would be built from stale state.
 *
 * Flipping either adapter onto the other's order would be a silent
 * behavior change (a STATE_FIRST audio write would persist a pre-apply
 * read-back; an APPLY_FIRST video sync would push the previous config).
 *
 * ## Declared divergence: the state classes stay separate
 *
 * [S] is deliberately NOT concreted to one effects-state type: the two
 * features' `AudioEffectsState` slices have genuinely different field
 * sets (player-audio carries the equalizer family + replay gain; player-
 * video carries passthrough/decoderMode/audioDelayMs) and remain
 * module-local. The core shares only the choreography, not the
 * vocabulary.
 *
 * Not a Koin type: feature controllers construct it directly over their
 * ViewModel's scope so the state and the persist jobs share the VM's
 * lifecycle.
 */
class EffectsCommandCore<S>(
    initialState: S,
    private val scope: CoroutineScope,
    private val order: Order,
) {

    /**
     * Declared divergence — which synchronous leg of [applyAndPersist]
     * runs first. See the class KDoc: APPLY_FIRST keeps the DSP manager
     * the source of truth (player-audio); STATE_FIRST feeds the state
     * slice to the apply leg (player-video's engine-config rebuild).
     */
    enum class Order { APPLY_FIRST, STATE_FIRST }

    private val _state = MutableStateFlow(initialState)

    /** The owned state slice, read-only outside this core. */
    val state: StateFlow<S> = _state.asStateFlow()

    /**
     * The ONE command template. [apply] and [update] run synchronously in
     * the constructor-declared [Order] before this fun returns; [persist]
     * is launched fire-and-forget on [scope] and is never awaited.
     * [update] is nullable because APPLY_FIRST adapters mirror only some
     * commands (the flow-less fields); STATE_FIRST adapters pass one on
     * every command (the write IS the apply leg's input).
     */
    fun applyAndPersist(
        apply: () -> Unit,
        update: ((S) -> S)? = null,
        persist: suspend () -> Unit,
    ) {
        when (order) {
            Order.APPLY_FIRST -> {
                apply()
                update?.let(_state::update)
            }
            Order.STATE_FIRST -> {
                update?.let(_state::update)
                apply()
            }
        }
        scope.launch { persist() }
    }

    /**
     * Bare state write for the non-command paths that share the slice:
     * the manager/prefs mirror collectors and the seeding entries
     * (apply-only by design — no engine/config leg, no persist leg).
     */
    fun updateState(transform: (S) -> S) {
        _state.update(transform)
    }
}
