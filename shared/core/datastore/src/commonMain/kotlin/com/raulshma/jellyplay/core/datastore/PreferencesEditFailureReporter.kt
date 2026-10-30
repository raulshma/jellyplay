package com.raulshma.jellyplay.core.datastore

/**
 * The observer [PreferencesEditor] reports rejected preference writes
 * through. The editor is fire-and-forget, so its callers (the settings
 * screens' toggles) never learn a write failed — before this seam a failing
 * edit either crashed the process (unhandled launch exception on the
 * application scope) or, once caught, vanished silently and the toggle
 * simply never flipped.
 *
 * Optional in the graph: the editor's Koin definition resolves it with
 * `getOrNull`, and with no definition loaded the editor degrades to
 * swallow-without-report. Both JVM shells bind a log-backed reporter in
 * core:data (this module has no logger and must not grow one — the
 * construction-owner rule keeps logging facades out of the leaf layers).
 */
fun interface PreferencesEditFailureReporter {
    fun onEditFailure(operation: String, failure: Throwable)
}
