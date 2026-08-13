package com.raulshma.jellyplay.core.network.subtitle

import android.util.Log
import com.raulshma.jellyplay.core.model.subtitle.SubtitleFile
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.core.network.RetryPolicy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resilient wrapper that applies [RetryPolicy] to every [SubtitleProvider]
 * method, delegating to the wrapped [delegate]. One class serves all providers
 * (`Wyzie`, `OpenSubtitles`, …): [kind] is read from the delegate so there is
 * nothing provider-specific here, and a new provider needs no new wrapper class.
 *
 * Mirrors [com.raulshma.jellyplay.core.network.arr.ResilientSonarrApiClient]:
 * implements the interface directly (not via `by` delegation) so adding a new
 * method to [SubtitleProvider] produces a compile error here, forcing the
 * author to wire it through [req].
 *
 * Constructed per provider in [com.raulshma.jellyplay.core.network.di.SubtitleProviderModule]
 * — each `@IntoMap` entry wraps the matching raw impl so the map value is the
 * resilient variant and retry applies to every call.
 */
@Singleton
class ResilientSubtitleProvider @Inject constructor(
    private val delegate: SubtitleProvider,
) : SubtitleProvider {

    override val kind: SubtitleProviderKind = delegate.kind

    private suspend fun <T> req(block: suspend () -> Result<T>): Result<T> =
        RetryPolicy.executeWithRetry(maxRetries = RetryPolicy.DEFAULT_MAX_RETRIES, block = block)

    override suspend fun search(
        query: SubtitleQuery,
        credentials: SubtitleProviderCredentials,
    ): Result<List<SubtitleSearchResult>> = req { delegate.search(query, credentials) }
        .also { outcome ->
            if (outcome.isSuccess) {
                Log.d(TAG, "${delegate.kind} search ok: ${outcome.getOrThrow().size} result(s)")
            } else {
                val e = outcome.exceptionOrNull()
                Log.w(TAG, "${delegate.kind} search failed: ${e?.javaClass?.simpleName}: ${e?.message}", e)
            }
        }

    override suspend fun download(
        result: SubtitleSearchResult,
        credentials: SubtitleProviderCredentials,
    ): Result<SubtitleFile> = req { delegate.download(result, credentials) }

    override suspend fun verifyCredentials(
        credentials: SubtitleProviderCredentials,
    ): Result<Unit> = req { delegate.verifyCredentials(credentials) }

    companion object {
        private const val TAG = "Subtitles"
    }
}
