package com.raulshma.jellyplay.feature.admin.plugins

import kotlinx.coroutines.delay

/**
 * The plugins' settle-then-refetch idiom, single-sited (the
 * `settleThenRefetch(delayMs) { refetch() }` the eight call sites
 * [PluginsViewModel] and [PluginDetailViewModel] used to write as a bare
 * `delay(…)` followed by their follow-up action): wait [delayMs] for the
 * server-side effect of the just-issued command to settle, then run
 * [refetch].
 *
 * The delays are the sites' own literals, kept verbatim: installs get
 * 1000 ms (a package install takes longer to register server-side than the
 * enable/disable/uninstall/cancel commands, which settle inside 500 ms) —
 * no earlier note records more rationale than that, so the numbers stay
 * literal rather than being "unified".
 */
internal suspend fun settleThenRefetch(delayMs: Long, refetch: () -> Unit) {
    delay(delayMs)
    refetch()
}
