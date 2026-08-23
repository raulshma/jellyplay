package com.raulshma.jellyplay.shell

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Shared scaffolding for the shell coordinators:
 *
 *  - [commandScope] runs the coordinator's commands so they always execute —
 *    even before [SessionCoordinator.start] or after the caller's scope has
 *    been cancelled — instead of being silently dropped.
 *  - [lifecycleJob] owns the collectors a coordinator's start launches on the
 *    caller's scope; re-calling start (e.g. after activity-state loss rebuilt
 *    the ViewModel) cancels the previous occupants first, so collectors are
 *    never duplicated.
 */
abstract class ShellCoordinator {
    /**
     * Lazy so merely constructing a coordinator never touches the Main
     * dispatcher — only subclasses that actually issue commands pay for it
     * (and their tests must install a Main dispatcher before the first one).
     */
    protected val commandScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    protected val lifecycleJob = RestartableJob()
}
