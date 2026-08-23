package com.raulshma.jellyplay.shell

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A single restartable Job slot shared by the shell coordinators:
 * [launchIn] cancels the previous occupant before launching, so re-calling
 * a coordinator's start (e.g. after activity-state loss rebuilt the
 * ViewModel) never duplicates collectors.
 */
class RestartableJob {
    private var job: Job? = null

    /**
     * Cancels any previous launch, then starts [block] on [scope].
     *
     * `@Synchronized` keeps the cancel-then-replace slot safe under
     * concurrent callers. Both shell start paths today run on Main from
     * ViewModel init, so this is un-contended — it makes that safety a
     * property of the class rather than of its call sites.
     */
    @Synchronized
    fun launchIn(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        job = scope.launch(block = block)
    }
}
