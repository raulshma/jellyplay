package com.raulshma.jellyplay.core.data.repository

import android.util.Log
import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.subtitle.SubtitleFile
import com.raulshma.jellyplay.core.model.subtitle.SubtitleLanguageCodes
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderPreferences
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.core.network.subtitle.SubtitleProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [SubtitleProviderRepository].
 *
 * Resolves the configured providers from the preferences + credential stores,
 * then fans out search across the external providers concurrently via the Hilt
 * `Map<SubtitleProviderKind, SubtitleProvider>`. Jellyfin is searched through
 * [searchJellyfin]/[searchAll] (server-mediated via [PlaybackRepository],
 * returns [RemoteSubtitleInfo] wrapped with [SubtitleSearchResult.jellyfinInfo]);
 * it is intentionally not part of [search] (it is `itemId`-scoped, while
 * [search] takes a provider-agnostic [SubtitleQuery]).
 *
 * Per-provider failures are isolated (one expired key can't blank the others),
 * mirroring [ArrRepositoryImpl]'s fan-out. Bounded concurrency isn't needed here
 * because the set of providers is tiny (≤3) and each is already rate-limited
 * client-side via [com.raulshma.jellyplay.core.network.subtitle.SubtitleRateLimiter].
 */
@Singleton
class SubtitleProviderRepositoryImpl @Inject constructor(
    private val preferencesStore: SubtitleProviderPreferencesStore,
    private val externalProviders: Map<SubtitleProviderKind, @JvmSuppressWildcards SubtitleProvider>,
    private val playbackRepository: PlaybackRepository,
) : SubtitleProviderRepository {

    override fun configuredProviders(): Flow<Set<SubtitleProviderKind>> =
        combine(preferencesStore.preferences, preferencesStore.credentials) { prefs, creds ->
            configuredProvidersSnapshot(prefs, creds)
        }

    private fun configuredProvidersSnapshot(
        prefs: SubtitleProviderPreferences,
        creds: Map<SubtitleProviderKind, SubtitleProviderCredentials>,
    ): Set<SubtitleProviderKind> {
        val result = mutableSetOf<SubtitleProviderKind>()
        // Jellyfin is always available (server session).
        result += SubtitleProviderKind.JELLYFIN
        for (kind in prefs.externalProviders) {
            if (prefs.isEnabled(kind) && creds[kind]?.isConfigured == true) {
                result += kind
            }
        }
        return result
    }

    override suspend fun search(
        query: SubtitleQuery,
    ): Map<SubtitleProviderKind, ProviderSearchOutcome> {
        val prefs = preferencesStore.preferences.first()
        val creds = credentialsSnapshot()
        val configured = configuredProvidersSnapshot(prefs, creds)

        return coroutineScope {
            val externalJobs = externalProviders.keys
                .filter { it in configured }
                .map { kind ->
                    async {
                        val provider = externalProviders.getValue(kind)
                        val cred = creds[kind]
                        if (cred == null) {
                            Log.d(TAG, "search $kind skipped: no credentials")
                        }
                        // A raw throw (anything escaping provider.search()/
                        // searchExternal) must never cancel sibling jobs — the
                        // class contract is "one bad key never blanks the rest".
                        // coroutineScope cancels siblings on a throw, so catch
                        // here and degrade to an Error outcome instead.
                        val outcome = runCatching { searchExternal(provider, query, cred) }
                            .getOrElse { e ->
                                Log.e(TAG, "search $kind threw, isolating: ${e.javaClass.simpleName}: ${e.message}", e)
                                ProviderSearchOutcome.Error(e.message ?: "$kind search failed")
                            }
                        kind to outcome
                    }
                }
            val outcomes = externalJobs.awaitAll().toMap()
            outcomes.forEach { (kind, outcome) ->
                when (outcome) {
                    is ProviderSearchOutcome.Success ->
                        Log.d(TAG, "search $kind success: ${outcome.results.size} result(s)")
                    is ProviderSearchOutcome.Error ->
                        Log.w(TAG, "search $kind error: ${outcome.message}")
                    is ProviderSearchOutcome.Skipped ->
                        Log.d(TAG, "search $kind skipped")
                }
            }
            outcomes
        }
    }

    override suspend fun searchProvider(
        kind: SubtitleProviderKind,
        query: SubtitleQuery,
    ): ProviderSearchOutcome {
        // Intentionally ignore the enable toggle — Test must verify a pasted key
        // before the user turns the provider on. Only credentials gate the probe.
        if (kind == SubtitleProviderKind.JELLYFIN) return ProviderSearchOutcome.Skipped
        val provider = externalProviders[kind] ?: return ProviderSearchOutcome.Skipped
        val cred = preferencesStore.getCredentials(kind) ?: return ProviderSearchOutcome.Skipped
        return provider.search(query, cred).fold(
            onSuccess = { ProviderSearchOutcome.Success(it) },
            onFailure = { e ->
                ProviderSearchOutcome.Error(e.message ?: "$kind search failed")
            },
        )
    }

    override suspend fun searchAll(
        query: SubtitleQuery,
        itemId: String,
        language: String,
    ): MergedSubtitleSearch = searchAllStreaming(query, itemId, language) { /* no-op */ }

    /**
     * Streaming merge of Jellyfin + external providers. Unlike the old
     * barrier-based implementation (which buffered every provider behind
     * `awaitAll` and returned a single snapshot), this emits a partial
     * [MergedSubtitleSearch] through [onPartial] the instant each provider
     * resolves — so a slow/retrying provider (e.g. Wyzie exhausting its
     * [RetryPolicy] backoff) can no longer hide a fast provider's results.
     *
     * Each provider runs in its own [launch]; on completion it writes its
     * outcome into the shared accumulator under [accumulatorMutex], then invokes
     * [onPartial] with the current merged + sorted snapshot. [joinAll] gates the
     * final return until everyone is done. The final snapshot is identical to
     * what the old [searchAll] produced (same [mergeOutcomes] + sort), so the
     * isolation contract is preserved: one bad provider degrades to an
     * [ProviderSearchOutcome.Error] in its slot and never cancels its siblings.
     */
    override suspend fun searchAllStreaming(
        query: SubtitleQuery,
        itemId: String,
        language: String,
        onPartial: (MergedSubtitleSearch) -> Unit,
    ): MergedSubtitleSearch = coroutineScope {
        val prefs = preferencesStore.preferences.first()
        val creds = credentialsSnapshot()
        val configured = configuredProvidersSnapshot(prefs, creds)

        // Accumulator for the incremental merge. Guarded so concurrent
        // provider completions never produce a torn snapshot.
        val mutex = Mutex()
        var jellyfinResults: List<SubtitleSearchResult> = emptyList()
        val outcomes = mutableMapOf<SubtitleProviderKind, ProviderSearchOutcome>()

        suspend fun emitPartial() {
            val snapshot = mutex.withLock { mergeOutcomes(jellyfinResults, outcomes.toMap()) }
            onPartial(snapshot)
        }

        val jobs = mutableListOf<kotlinx.coroutines.Job>()

        // Jellyfin: server-scoped language search (tolerate failure → empty).
        jobs += launch {
            val result = runCatching { searchJellyfin(itemId, language) }
                .getOrElse { e ->
                    Log.e(TAG, "searchJellyfin threw, isolating: ${e.javaClass.simpleName}: ${e.message}", e)
                    Result.failure(e)
                }
            val rows = result.getOrElse { emptyList() }
            mutex.withLock { jellyfinResults = rows }
            Log.d(TAG, "searchAllStreaming jellyfin: ${rows.size} result(s)")
            emitPartial()
        }

        // External providers: TMDB/IMDb/title-keyed fan-out.
        externalProviders.keys.filter { it in configured }.forEach { kind ->
            jobs += launch {
                val provider = externalProviders.getValue(kind)
                val cred = creds[kind]
                if (cred == null) {
                    Log.d(TAG, "search $kind skipped: no credentials")
                }
                // A raw throw escaping provider.search()/searchExternal must
                // never cancel siblings — degrade to an Error outcome instead.
                val outcome = runCatching { searchExternal(provider, query, cred) }
                    .getOrElse { e ->
                        Log.e(TAG, "search $kind threw, isolating: ${e.javaClass.simpleName}: ${e.message}", e)
                        ProviderSearchOutcome.Error(e.message ?: "$kind search failed")
                    }
                mutex.withLock { outcomes[kind] = outcome }
                when (outcome) {
                    is ProviderSearchOutcome.Success ->
                        Log.d(TAG, "search $kind success: ${outcome.results.size} result(s)")
                    is ProviderSearchOutcome.Error ->
                        Log.w(TAG, "search $kind error: ${outcome.message}")
                    is ProviderSearchOutcome.Skipped ->
                        Log.d(TAG, "search $kind skipped")
                }
                emitPartial()
            }
        }

        jobs.joinAll()
        val final = mutex.withLock { mergeOutcomes(jellyfinResults, outcomes.toMap()) }
        Log.d(
            TAG,
            "searchAllStreaming merged: ${final.results.size} result(s), " +
                "${final.errors.size} provider error(s)" +
                (final.errors.takeIf { it.isNotEmpty() }?.entries?.joinToString { "${it.key}=${it.value}" }
                    ?.let { " [$it]" } ?: ""),
        )
        MergedSubtitleSearch(results = final.results, errors = final.errors)
    }

    /**
     * Merges [jellyfinResults] with the per-provider [outcomes] into one
     * stable-ordered list + error map. Shared by [searchAll] so the player and
     * editor use identical merge + sort semantics.
     */
    private fun mergeOutcomes(
        jellyfinResults: List<SubtitleSearchResult>,
        outcomes: Map<SubtitleProviderKind, ProviderSearchOutcome>,
    ): MergedSubtitleSearch {
        val merged = mutableListOf<SubtitleSearchResult>()
        val errors = mutableMapOf<SubtitleProviderKind, String>()
        merged += jellyfinResults
        outcomes.forEach { (kind, outcome) ->
            when (outcome) {
                is ProviderSearchOutcome.Success -> merged += outcome.results
                is ProviderSearchOutcome.Error -> errors[kind] = outcome.message
                is ProviderSearchOutcome.Skipped -> Unit
            }
        }
        // Stable, readable ordering: provider → language → download count desc.
        val ordered = merged.sortedWith(
            compareBy(
                { it.provider.ordinal },
                { it.language ?: "" },
                { -(it.downloadCount ?: 0) },
            ),
        )
        return MergedSubtitleSearch(results = ordered, errors = errors)
    }

    private suspend fun searchExternal(
        provider: SubtitleProvider,
        query: SubtitleQuery,
        credentials: SubtitleProviderCredentials?,
    ): ProviderSearchOutcome {
        if (credentials == null) return ProviderSearchOutcome.Skipped
        return provider.search(query, credentials).fold(
            onSuccess = { ProviderSearchOutcome.Success(it) },
            onFailure = { e ->
                ProviderSearchOutcome.Error(e.message ?: "${provider.kind} search failed")
            },
        )
    }

    override suspend fun downloadExternal(result: SubtitleSearchResult): Result<SubtitleFile> {
        val kind = result.provider
        if (kind == SubtitleProviderKind.JELLYFIN) {
            return Result.failure(
                IllegalStateException(
                    "Jellyfin subtitle downloads must route through PlaybackRepository.downloadSubtitle",
                ),
            )
        }
        val provider = externalProviders[kind]
            ?: return Result.failure(IllegalStateException("No provider bound for $kind"))
        val credentials = credentialsSnapshot()[kind]
            ?: return Result.failure(IllegalStateException("No credentials configured for $kind"))
        return provider.download(result, credentials)
    }

    // --- Jellyfin-specific bridging -------------------------------------------------

    /**
     * Searches the Jellyfin server for [itemId] in [language] (ISO 639-3) and
     * wraps the native [RemoteSubtitleInfo] rows as [SubtitleSearchResult]s so
     * the player/editor can render them in the same merged list. The native info
     * is preserved in [SubtitleSearchResult.jellyfinInfo] for the server-side
     * download path.
     *
     * Kept here (not in PlaybackRepository) so the UI has a single
     * [SubtitleSearchResult] type to render across providers. The caller routes
     * Jellyfin *downloads* through `PlaybackRepository.downloadSubtitle` using
     * the [SubtitleSearchResult.id] preserved in
     * [SubtitleSearchResult.jellyfinInfo].id — so the row's id MUST equal the
     * wrapped RemoteSubtitleInfo.id.
     */
    override suspend fun searchJellyfin(itemId: String, language: String): Result<List<SubtitleSearchResult>> =
        playbackRepository.searchRemoteSubtitles(itemId, language).map { rows ->
            rows.map { it.toJellyfinResult() }
        }

    private fun RemoteSubtitleInfo.toJellyfinResult(): SubtitleSearchResult {
        val lang = threeLetterISOLanguageName.ifBlank { SubtitleLanguageCodes.toIso3(language) }
        return SubtitleSearchResult(
            provider = SubtitleProviderKind.JELLYFIN,
            id = id,
            language = lang,
            displayName = name ?: SubtitleLanguageCodes.displayName(lang) ?: lang ?: "Unknown",
            format = format?.lowercase(),
            isHearingImpaired = isHearingImpaired,
            isForced = isForced,
            downloadCount = downloadCount.takeIf { it > 0 },
            isAiTranslated = isAiTranslated,
            jellyfinInfo = this,
        )
    }

    // EncryptedSharedPreferences is not observable; the preferencesStore.credentials
    // flow re-emits on writes (for configuredProviders), but a synchronous snapshot
    // (needed inside search/download) reads straight from the secure store.
    private fun credentialsSnapshot(): Map<SubtitleProviderKind, SubtitleProviderCredentials> =
        SubtitleProviderKind.entries
            .mapNotNull { kind -> preferencesStore.getCredentials(kind)?.let { kind to it } }
            .toMap()

    companion object {
        private const val TAG = "SubtitlesRepo"
    }
}
