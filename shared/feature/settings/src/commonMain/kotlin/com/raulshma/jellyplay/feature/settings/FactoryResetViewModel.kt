package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.settings.PreferenceSliceSnapshot
import com.raulshma.jellyplay.core.datastore.settings.PreferenceSnapshotReader
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import org.jetbrains.compose.resources.StringResource

/**
 * Backs the Factory Reset review screen. Holds the live [preferences] (current)
 * alongside the immutable [factory] baseline ([PreferenceSliceSnapshot.FACTORY]
 * — every slice at its default) so the UI can render a per-category
 * current-vs-default diff and changed-count without duplicating default values.
 *
 * The diff carrier is the slice snapshot itself (see [PreferenceSliceSnapshot]):
 * the review rows read the same slice fields the screens consume, so a new
 * preference surfaces here by adding one declared diff row in
 * [PreferenceCategoryPresentation] — no aggregate re-mapping to keep in sync.
 *
 * This is a rarely-opened screen, so [preferences] is built ONE-SHOT on entry
 * via [PreferenceSnapshotReader] — the read-side twin of [PreferencesEditor],
 * which owns the 18-domain-store-slice + `AppRuntimeStateStore` +
 * `PinRateLimiter` gather — instead of subscribing to an eager aggregate
 * `StateFlow`. All writes flow through [PreferencesEditor] (the single
 * auditable write seam) — no new mutation path is introduced.
 *
 * The diff-row labels resolve ONCE per entry ([resolveDiffLabels] over the
 * registry's declared resources) and are shared by both snapshots; `factory`
 * and `preferences` therefore always speak the same locale.
 *
 * Wave 6: every confirmed reset (all or per-category) captures a best-effort
 * safety restore point FIRST ([captureSafetySnapshot] — the one helper shared
 * with the wizard apply and the sync namespace reset). The seams are
 * nullable-with-default (the sync-screen idiom): graphs without the plugin
 * resolve nulls, the gate re-check runs inside the helper, and a missed
 * capture only raises [safetySnapshotMissed] — it never blocks the reset.
 */
class FactoryResetViewModel(
    private val snapshotReader: PreferenceSnapshotReader,
    private val editor: PreferencesEditor,
    private val diffLabelResolver: suspend (List<StringResource>) -> (StringResource) -> String = ::resolveDiffLabels,
    /** The plugin's sync routes — the safety capture's api (null = the arm is off). */
    private val pluginApiClient: com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSyncRoutes? = null,
    /** The ONE gate seam the capture re-checks (ADR 0010's gating rule). */
    private val statusStore: com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore? = null,
) : JellyPlayViewModel() {

    /** Resolved label lookup shared by both snapshots; swapped in once on entry. */
    private var labels: (StringResource) -> String = { it.toString() }

    private fun diffSnapshot(slices: PreferenceSliceSnapshot): PreferenceDiffSnapshot =
        PreferenceDiffSnapshot(slices) { res -> labels(res) }

    /** Factory baseline — every slice at its default value. */
    val factory: PreferenceDiffSnapshot = diffSnapshot(PreferenceSliceSnapshot.FACTORY)

    var preferences by composeState(diffSnapshot(PreferenceSliceSnapshot.FACTORY))
        private set

    /**
     * One-shot Wave-6 face: the pre-reset safety capture failed. The screen
     * warns through the bus and acknowledges via [clearSafetySnapshotMissed].
     */
    var safetySnapshotMissed by composeState(false)
        private set

    /**
     * True between a confirmed reset's safety capture and the reset settling
     * (the seam-present, suspend path only — the seam-absent path stays
     * synchronous and never raises this). The screen gates back navigation on
     * it: backing out mid-capture must not look like the reset was abandoned
     * (and a second confirmed reset is refused while one is running).
     */
    var resetRunning by composeState(false)
        private set

    init {
        launch {
            labels = diffLabelResolver(PreferenceCategoryViews.flatMap { it.labelResources })
            preferences = buildFromSlices()
        }
    }

    /**
     * Builds the live snapshot once from the 18 domain-store slices + runtime/
     * PIN extras via the reader's one-shot gather (each slice a single
     * `.first()`; no live subscription).
     */
    private suspend fun buildFromSlices(): PreferenceDiffSnapshot =
        diffSnapshot(snapshotReader.snapshotOnce())

    /** Wave-6 hook #3: best-effort pre-reset restore point; a miss only warns. */
    private suspend fun captureSafetySnapshotBestEffort() {
        val client = pluginApiClient
        val store = statusStore
        if (client == null || store == null) return
        if (!captureSafetySnapshot(client, store)) safetySnapshotMissed = true
    }

    /** Acknowledges the one-shot Wave-6 warning (the screen surfaced it). */
    fun clearSafetySnapshotMissed() {
        safetySnapshotMissed = false
    }

    /**
     * Resets every preference in [category] to its factory default. With the
     * Wave-6 seams absent (direct-construction harnesses, plugin-less graphs)
     * the reset stays SYNCHRONOUS — exactly the pre-Wave-6 behavior; the
     * capture only becomes worth a suspension when it can actually run. With
     * the seams present the capture+reset runs guarded by [resetRunning] so a
     * second confirmation mid-capture cannot queue a second destructive pass.
     */
    fun resetCategory(category: PreferenceResetCategory) =
        guardedReset { editor.resetCategory(category) }

    /** Resets the entire preferences DataStore to factory defaults (see [resetCategory]'s sync note). */
    fun resetAll() =
        guardedReset { editor.clearAllPreferences() }

    /**
     * The one reset choreography both confirmed resets run: the seam-absent
     * path resets synchronously, the seam-present path captures the Wave-6
     * safety restore point first, guarded by [resetRunning].
     */
    private fun guardedReset(reset: () -> Unit) {
        if (pluginApiClient == null || statusStore == null) {
            reset()
            return
        }
        if (resetRunning) return
        launch {
            resetRunning = true
            try {
                captureSafetySnapshotBestEffort()
                reset()
            } finally {
                resetRunning = false
            }
        }
    }
}
