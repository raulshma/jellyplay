package com.raulshma.jellyplay.core.datastore.experimental

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The one hot gate over the Direct *arr Integration flag for the *arr-gated
 * feature ViewModels (requests / arr-queue / upcoming calendar), which read
 * the flag via `.value` without holding a collector.
 *
 * [directArrEnabled] is shared `Eagerly` (not `WhileSubscribed`) on [scope]
 * for the reason every consuming ViewModel used to hand-roll its own
 * `stateIn(scope, Eagerly, false)`: under `WhileSubscribed` the upstream
 * preferences Flow would never start and `.value` would stay `false` forever,
 * leaving the entire gated feature unreachable. (That rationale — and the
 * sharing setup — previously lived as one verbatim copy per ViewModel, each
 * KDoc cross-referencing the others.)
 *
 * Timing note (pinned by the consumers' VM tests, which build a fresh gate
 * per VM): the `false`-seed window is PER GATE INSTANCE — until [scope] has
 * run the sharing collector, [directArrEnabled] reports the `false` seed even
 * when the flag is already persisted on. In production the gate is one Koin
 * singleton on the application scope, so the window closes once per process:
 * the FIRST ViewModel constructed sees the no-op-on-first-read behaviour the
 * per-VM `stateIn` copies had; later VMs read the warmed value and their
 * `init` refresh actually loads (benign — the refresh is flag-gated, not
 * flag-armed).
 *
 * The next experimental flag folds onto THIS holder as a second StateFlow
 * member — do not grow a second gate singleton beside it.
 */
class ExperimentalFeatureGate(
    experimentalStore: ExperimentalStore,
    scope: CoroutineScope,
) {
    val directArrEnabled: StateFlow<Boolean> = experimentalStore.directArrEnabled()
        .stateIn(scope, SharingStarted.Eagerly, false)
}
