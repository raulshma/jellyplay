package com.raulshma.jellyplay.core.concurrency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A single restartable Job slot shared by the shell coordinators:
 * [launchIn] cancels the previous occupant before launching, so re-calling
 * a coordinator's start (e.g. after activity-state loss rebuilt the
 * ViewModel) never duplicates collectors.
 *
 * Distinct sibling vocabulary to [TaskBundle]: TaskBundle is the KEYED
 * multi-slot variant (several named cancel-and-replace slots on one object,
 * deliberately not thread-safe), while this is ONE unnamed slot made safe
 * under concurrent callers with `@Synchronized`. Reach for TaskBundle when
 * a host owns several independent relaunchable jobs; for this when there is
 * exactly one.
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
