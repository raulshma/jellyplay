package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.network.api.JellyPlayHeroResult
import com.raulshma.jellyplay.core.network.api.JellyPlayRowCatalog
import com.raulshma.jellyplay.core.network.api.JellyPlayRowResult
import com.raulshma.jellyplay.core.network.api.JellyPlayRowsRoutes
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.library.JellyPlayHomeSectionSources

/**
 * The session-aware transport satisfying the home fetcher's
 * [JellyPlayHomeSectionSources] for the PLUGIN_ROW home rows and the
 * plugin-curated hero: the reads forward verbatim onto the plugin api client
 * (ADR 0010's one-family rule — every `jellyplay/` route rides the
 * `JellyPlayRowsRoutes` role of the one plugin client family), the item
 * resolution forwards onto the library client's batched ids read, and the
 * capability gates read ONLY the probe store's registry — the ONE gating
 * mechanism (ADR 0010 §6).
 *
 * Gate semantics: a store still UNKNOWN (never probed this session) runs the
 * once-per-session capabilities probe first — home is the first feature
 * surface a plugin user meets, and no other surface probed on its behalf yet
 * — then reads the registry. UNAVAILABLE is trusted for the session (the
 * store's own failure semantics; callers re-probe on the next explicit
 * refresh), so a plugin-absent server pays one probe, never one per home
 * fetch. Every gate answer is `AVAILABLE && <row feature on>`, byte-for-byte
 * the docs/jellyplay-plugin.md gating rule.
 *
 * Relocated verbatim from the bottom of DataSessionPlaybackKoinModule.kt
 * (where it lived as a file-private class beside the binding that constructs
 * it — now the `JellyPlayHomeSectionSources` single in
 * `dataProfileSyncModule`); `private` widened to `internal` so the DI
 * binding (same Gradle module) reaches it.
 */
internal class JellyPlayHomeSectionSourcesImpl(
    private val apiClient: JellyPlayRowsRoutes,
    private val statusStore: JellyPlayPluginStatusStore,
    /**
     * The per-feature gate (probe AND the user's toggle) — the row gates read
     * the fresh one-shot arm ([JellyPlayFeatureGate.isAvailableNow]) because
     * they run right after the once-per-session synchronous probe refresh,
     * where the reactive flow's value could still be one dispatch stale.
     */
    private val featureGate: JellyPlayFeatureGate,
    /**
     * Deferred [LibraryApiClient] read — see the binding's comment: resolving
     * the client eagerly here would cycle with the client impl that hosts
     * this adapter.
     */
    private val libraryClient: () -> LibraryApiClient,
) : JellyPlayHomeSectionSources {

    override suspend fun seasonalRowsEnabled(): Boolean =
        rowsEnabled(JellyPlayPluginFeatures.SeasonalRows)

    override suspend fun customRowsEnabled(): Boolean =
        rowsEnabled(JellyPlayPluginFeatures.CustomRows)

    override suspend fun curatedHeroEnabled(): Boolean =
        rowsEnabled(JellyPlayPluginFeatures.Hero)

    override suspend fun getSeasonalRow(keyword: String?): Result<JellyPlayRowResult?> =
        apiClient.getSeasonalRow(keyword)

    override suspend fun getCustomRowCatalog(): Result<JellyPlayRowCatalog?> =
        apiClient.getCustomRowCatalog()

    override suspend fun getCustomRow(title: String): Result<JellyPlayRowResult?> =
        apiClient.getCustomRow(title)

    override suspend fun getCuratedHero(): Result<JellyPlayHeroResult?> =
        apiClient.getCuratedHero()

    override suspend fun getItemsByIds(ids: List<String>): Result<List<MediaItem>> =
        libraryClient().getItemsByIds(ids)

    /**
     * Probe-when-UNKNOWN, then the ONE gate seam — probe AVAILABLE AND the
     * user's per-feature toggle (`pluginFeature.<key>.enabled`), so a switched
     * off `seasonal-rows`/`custom-rows` arm resolves locally with no port
     * calls and the next home fetch picks a toggle flip up.
     */
    private suspend fun rowsEnabled(feature: String): Boolean {
        if (statusStore.status.value == JellyPlayPluginStatus.UNKNOWN) {
            statusStore.refresh()
        }
        return featureGate.isAvailableNow(feature)
    }
}
