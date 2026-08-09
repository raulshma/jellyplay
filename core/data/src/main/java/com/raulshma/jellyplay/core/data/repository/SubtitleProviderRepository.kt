package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.subtitle.SubtitleFile
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import kotlinx.coroutines.flow.Flow

/**
 * Fan-out coordinator over the configured subtitle providers (Jellyfin + any
 * external provider the user has enabled with credentials under
 * *Integrations → Subtitle Providers*).
 *
 * Search comes in three flavours because Jellyfin is `itemId`-scoped while the
 * external providers are TMDB/IMDb/title-keyed:
 *
 * - [search] — fans out across every **configured external** provider
 *   concurrently. Jellyfin cannot participate (it lacks the server `itemId`
 *   here), so callers that want a merged Jellyfin + external list use
 *   [searchAllStreaming].
 * - [searchAllStreaming] — merges [searchJellyfin] with [search] into one
 *   ordered list with per-provider error messages, streaming partials as each
 *   provider resolves; this is the player/editor entry point.
 * - [searchProvider] — probes a **single** provider by credentials alone
 *   (ignores the enable toggle), used by the *Test* button so a freshly pasted
 *   key can be verified before the user flips the Switch on.
 *
 * Download dispatches to the owning provider by [SubtitleSearchResult.provider]:
 *
 * - **External providers** (Wyzie/OpenSubtitles) return the raw subtitle bytes
 *   via [downloadExternal] — the player side-loads them locally, the editor
 *   uploads them to Jellyfin.
 * - **Jellyfin** rows are NOT downloaded through this repository: their
 *   `jellyfinInfo` carries the server-native id and the caller routes them
 *   through the existing `PlaybackRepository.downloadSubtitle` server-side +
 *   poll path (Jellyfin's download is not a byte stream).
 */
interface SubtitleProviderRepository {

    /** Hot flow of the providers the user has actually configured (enabled + credentialed). */
    fun configuredProviders(): Flow<Set<SubtitleProviderKind>>

    /**
     * Searches every configured **external** provider concurrently. Jellyfin is
     * intentionally absent — it is `itemId`-scoped and cannot participate in a
     * provider-agnostic [SubtitleQuery]; use [searchAllStreaming] for the merged list.
     *
     * @return a map keyed by provider, where each value is either the merged
     *   list of results (on success) or an error message. The UI renders the
     *   union as a merged list with per-provider error chips.
     */
    suspend fun search(query: SubtitleQuery): Map<SubtitleProviderKind, ProviderSearchOutcome>

    /**
     * Probes a single [kind] by credentials alone — the enable toggle is
     * ignored, so a freshly pasted key verifies before the user turns the
     * provider on. Used by the *Test* button in settings.
     */
    suspend fun searchProvider(
        kind: SubtitleProviderKind,
        query: SubtitleQuery,
    ): ProviderSearchOutcome

    /**
     * Merges Jellyfin results ([searchJellyfin]) with every configured external
     * provider ([search]) into one stable-ordered list plus a per-provider error
     * map, streaming partial snapshots as each provider resolves. This is the
     * player/editor entry point: it centralizes the merge + sort that callers
     * used to duplicate.
     *
     * [onPartial] is invoked once per provider as each resolves (Jellyfin + every
     * external provider), carrying the merged snapshot *so far*. This removes
     * the all-or-nothing barrier: a slow/retrying provider can no longer gate
     * its siblings' results, because each provider's rows/errors land in the UI
     * the instant that provider resolves.
     *
     * [onPartial] may fire from background coroutines; callers must marshal onto
     * their own dispatcher if they touch UI state from it.
     */
    suspend fun searchAllStreaming(
        query: SubtitleQuery,
        itemId: String,
        language: String,
        onPartial: (MergedSubtitleSearch) -> Unit,
    ): MergedSubtitleSearch

    /**
     * Searches the Jellyfin server for [itemId] in [language] (ISO 639-3) and
     * wraps the native [com.raulshma.jellyplay.core.model.RemoteSubtitleInfo]
     * rows as [SubtitleSearchResult]s so the player/editor can render them in
     * the same merged list as external-provider rows.
     */
    suspend fun searchJellyfin(itemId: String, language: String): Result<List<SubtitleSearchResult>>

    /**
     * Downloads the subtitle bytes for an **external-provider** [result]. Throws
     * for Jellyfin rows (route those through `PlaybackRepository.downloadSubtitle`).
     * Returns the raw bytes + a guessed file name + format for MIME mapping.
     */
    suspend fun downloadExternal(result: SubtitleSearchResult): Result<SubtitleFile>
}

/** Per-provider search outcome: either results or an error message for the chip. */
sealed class ProviderSearchOutcome {
    data class Success(val results: List<SubtitleSearchResult>) : ProviderSearchOutcome()
    data class Error(val message: String) : ProviderSearchOutcome()
    data object Skipped : ProviderSearchOutcome() // provider not configured for this query
}

/**
 * Merged cross-provider search result: the union of Jellyfin + external rows in
 * a stable order, plus a per-provider error map for chips.
 */
data class MergedSubtitleSearch(
    val results: List<SubtitleSearchResult>,
    val errors: Map<SubtitleProviderKind, String>,
)
