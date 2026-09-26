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
 */
class FactoryResetViewModel(
    private val snapshotReader: PreferenceSnapshotReader,
    private val editor: PreferencesEditor,
    private val diffLabelResolver: suspend (List<StringResource>) -> (StringResource) -> String = ::resolveDiffLabels,
) : JellyPlayViewModel() {

    /** Resolved label lookup shared by both snapshots; swapped in once on entry. */
    private var labels: (StringResource) -> String = { it.toString() }

    private fun diffSnapshot(slices: PreferenceSliceSnapshot): PreferenceDiffSnapshot =
        PreferenceDiffSnapshot(slices) { res -> labels(res) }

    /** Factory baseline — every slice at its default value. */
    val factory: PreferenceDiffSnapshot = diffSnapshot(PreferenceSliceSnapshot.FACTORY)

    var preferences by composeState(diffSnapshot(PreferenceSliceSnapshot.FACTORY))
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

    /** Resets every preference in [category] to its factory default. */
    fun resetCategory(category: PreferenceResetCategory) {
        editor.resetCategory(category)
    }

    /** Resets the entire preferences DataStore to factory defaults. */
    fun resetAll() {
        editor.clearAllPreferences()
    }
}
