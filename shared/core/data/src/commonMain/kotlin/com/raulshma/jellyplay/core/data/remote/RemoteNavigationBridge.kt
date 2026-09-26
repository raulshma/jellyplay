package com.raulshma.jellyplay.core.data.remote

import com.raulshma.jellyplay.core.model.remote.NavigationTarget
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The in-process bus between the receiver's General-command ladder and the
 * shell collectors: the receiver [request]s a
 * [com.raulshma.jellyplay.core.model.remote.NavigationTarget], the shell's
 * collector consumes [targets]. The target vocabulary itself lives in
 * core:model (`RemoteNavigationTargets.kt`, beside `RemoteControlRequests`) —
 * this class is only the flow plumbing, so the pure routing folds can sit in
 * shared/feature/shell without a core:data edge.
 */
class RemoteNavigationBridge() {
    private val _targets = MutableSharedFlow<NavigationTarget>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val targets: SharedFlow<NavigationTarget> = _targets.asSharedFlow()

    fun request(target: NavigationTarget) {
        _targets.tryEmit(target)
    }
}
