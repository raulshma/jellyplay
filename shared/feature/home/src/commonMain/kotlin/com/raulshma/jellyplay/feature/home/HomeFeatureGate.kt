package com.raulshma.jellyplay.feature.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * One home feature-gate's reactive spine — the shape the VM's opt-in
 * decoration features repeat (H17 stats card, H18 genre rail, H19 presence
 * poll): combine the feature's gate flows into ONE value (a small gate data
 * class so [distinctUntilChanged] compares every leg atomically), dedupe it,
 * then run the feature's effect once per distinct gate emission.
 *
 * The holder owns the policy half: the dedupe, the cancel-and-restart
 * collector, and the launch that binds the collection to the VM scope. The
 * call site owns the type-shaped half: the combine of its own flows into its
 * own gate value (heterogeneous inputs, so it stays at the call site) and
 * the effect — a loader call folding into the state bag, or a controller's
 * poll loop.
 *
 * Effects collect with [collectLatest] deliberately: a gate change cancels
 * the previous emission's in-flight effect instead of queueing behind it.
 * The poll loops park on their cadence delay and MUST restart on a gate
 * flip (a toggle / user switch / going-offline re-polls immediately or drops
 * the loop); the load-then-write effects are last-write-wins bag folds, so a
 * cancelled stale load simply never paints what the newer gate is about to
 * overwrite.
 */
internal class HomeFeatureGate<T>(
    scope: CoroutineScope,
    gate: Flow<T>,
    effect: suspend (T) -> Unit,
) {
    init {
        scope.launch {
            gate.distinctUntilChanged().collectLatest { effect(it) }
        }
    }
}
