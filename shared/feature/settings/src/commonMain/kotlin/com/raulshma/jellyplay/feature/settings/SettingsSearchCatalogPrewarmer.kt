package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.ui.settingssearch.resolve
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Warms the compose-resources runtime's per-entry string cache for the whole
 * settings catalog once per process, off the main thread, at app start.
 *
 * Why this exists: on Android and desktop JVM, `stringResource` resolves a
 * not-yet-cached string with `runBlocking` ON the composition thread
 * (compose-resources `ResourceState.blocking.kt`), and the runtime's
 * `stringItemsCache` is keyed per string entry (path+offset+size), not per
 * table — so EVERY distinct string's first read re-opens the locale's
 * resource file and, on Android, inflates a DEFLATE-compressed APK asset
 * from byte 0 to the entry offset. Settings carries the app's largest table
 * (1,583 entries; 105-137 KB per locale), and its search surfaces resolve
 * the entire catalog (260 items × title/subtitle/category = 780 entries) on
 * first use: cold, that is seconds of blocking reads — the filed
 * settings-open ANR (docs/e2e/device-locale-pass.md). Warming at app start
 * turns every later read — first composition of the settings screen
 * included — into a runtime-cache hit; the residual cold path is the
 * O(visible) strings of whatever composes before the warm finishes.
 *
 * The runtime cache is process-wide and its keys are locale-qualified paths,
 * so a later app-locale switch simply resolves the new locale's entries once
 * — the same cost the pre-warm paid for the boot locale. Nothing to
 * invalidate here.
 *
 * Failures are swallowed on purpose: the warm is best-effort. A failed pass
 * leaves reads exactly as they were before this class existed (blocking per
 * first read, then cached); an exception must not take down the injected
 * applicationScope child.
 */
class SettingsSearchCatalogPrewarmer(
    private val scope: CoroutineScope,
    private val warmPass: suspend () -> Unit = { SettingsSearchCatalog.items.resolve() },
) {

    @Volatile
    private var warmJob: Job? = null

    /**
     * Kicks the warm pass; idempotent while the previous pass is still
     * running. A new pass after a completed one is allowed and cheap — every
     * read then hits the runtime cache (the job-replacement race is benign:
     * two concurrent passes resolve the same idempotent catalog).
     */
    fun warm(): Job {
        warmJob?.let { if (it.isActive) return it }
        return scope.launch {
            try {
                warmPass()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // best-effort warm: a failed pass leaves cold reads blocking
                // per first read, exactly the pre-fix behavior
            }
        }.also { warmJob = it }
    }
}
