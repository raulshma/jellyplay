package com.raulshma.jellyplay.core.network.subtitle

import com.raulshma.jellyplay.core.model.subtitle.SubtitleFile
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.core.network.RetryPolicy
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies [ResilientSubtitleProvider] applies [RetryPolicy] to every
 * [SubtitleProvider] method through a scripted fake delegate (no real
 * provider/HTTP behind it). The invariants: transient failures retry up to
 * [RetryPolicy.DEFAULT_MAX_RETRIES] extra calls, non-retryable failures
 * short-circuit after exactly one call, and successes pass through untouched.
 * Runs on [runTest] so the retry backoff consumes virtual time.
 */
class ResilientSubtitleProviderTest {

    /**
     * Scripted delegate: each behavior lambda receives the call number
     * (1-based) and returns the Result for that call.
     */
    private class FakeSubtitleProvider(
        override val kind: SubtitleProviderKind = SubtitleProviderKind.WYZIE,
    ) : SubtitleProvider {
        var searchCalls = 0
        var downloadCalls = 0
        var verifyCalls = 0

        var searchBehavior: (Int) -> Result<List<SubtitleSearchResult>> = {
            Result.success(emptyList())
        }
        var downloadBehavior: (Int) -> Result<SubtitleFile> = {
            Result.success(SubtitleFile(ByteArray(0), "f.srt", "srt", "eng"))
        }
        var verifyBehavior: (Int) -> Result<Unit> = { Result.success(Unit) }

        override suspend fun search(
            query: SubtitleQuery,
            credentials: SubtitleProviderCredentials,
        ): Result<List<SubtitleSearchResult>> = searchBehavior(++searchCalls)

        override suspend fun download(
            result: SubtitleSearchResult,
            credentials: SubtitleProviderCredentials,
        ): Result<SubtitleFile> = downloadBehavior(++downloadCalls)

        // Overridden despite the interface default so every call is counted
        // (the default body would silently route through search instead).
        override suspend fun verifyCredentials(
            credentials: SubtitleProviderCredentials,
        ): Result<Unit> = verifyBehavior(++verifyCalls)
    }

    private lateinit var delegate: FakeSubtitleProvider
    private lateinit var provider: ResilientSubtitleProvider

    private val query = SubtitleQuery(imdbId = "tt3659388")
    private val creds: SubtitleProviderCredentials = SubtitleProviderCredentials.Wyzie(apiKey = "key")
    private val row = SubtitleSearchResult(
        provider = SubtitleProviderKind.WYZIE,
        id = "row-1",
        language = "eng",
        displayName = "English",
    )

    @BeforeTest
    fun setup() {
        delegate = FakeSubtitleProvider()
        provider = ResilientSubtitleProvider(delegate)
    }

    @Test
    fun `kind is read from the delegate`() {
        assertEquals(SubtitleProviderKind.WYZIE, provider.kind)
        val opensubtitles = ResilientSubtitleProvider(
            FakeSubtitleProvider(kind = SubtitleProviderKind.OPENSUBTITLES),
        )
        assertEquals(SubtitleProviderKind.OPENSUBTITLES, opensubtitles.kind)
    }

    @Test
    fun `search passes a delegate success through without retrying`() = runTest {
        delegate.searchBehavior = { Result.success(listOf(row)) }

        val result = provider.search(query, creds)

        assertTrue(result.isSuccess)
        assertEquals(listOf(row), result.getOrThrow())
        assertEquals(1, delegate.searchCalls, "success must not retry")
    }

    @Test
    fun `search retries a transient failure and succeeds on the second call`() = runTest {
        delegate.searchBehavior = { call ->
            if (call == 1) Result.failure(IOException("connection reset"))
            else Result.success(listOf(row))
        }

        val result = provider.search(query, creds)

        assertTrue(result.isSuccess, "IOException is retryable — second call must win")
        assertEquals(listOf(row), result.getOrThrow())
        assertEquals(2, delegate.searchCalls)
    }

    @Test
    fun `search short-circuits a non-retryable failure after one call`() = runTest {
        delegate.searchBehavior = { Result.failure(IllegalStateException("bug")) }

        val result = provider.search(query, creds)

        assertTrue(result.isFailure)
        assertEquals(1, delegate.searchCalls, "programming errors must not be retried")
    }

    @Test
    fun `search exhausts one initial call plus DEFAULT_MAX_RETRIES on persistent failures`() = runTest {
        delegate.searchBehavior = { Result.failure(IOException("still down")) }

        val result = provider.search(query, creds)

        assertTrue(result.isFailure)
        assertEquals(
            RetryPolicy.DEFAULT_MAX_RETRIES + 1,
            delegate.searchCalls,
            "wrapper must use the policy default, not a per-provider count",
        )
    }

    @Test
    fun `download is wired through retry`() = runTest {
        val file = SubtitleFile(byteArrayOf(1, 2, 3), "f.srt", "srt", "eng")
        delegate.downloadBehavior = { call ->
            if (call == 1) Result.failure(IOException("reset"))
            else Result.success(file)
        }

        val result = provider.download(row, creds)

        assertTrue(result.isSuccess)
        assertEquals(file, result.getOrThrow())
        assertEquals(2, delegate.downloadCalls)
    }

    @Test
    fun `verifyCredentials is wired through retry`() = runTest {
        delegate.verifyBehavior = { call ->
            if (call == 1) Result.failure(IOException("reset"))
            else Result.success(Unit)
        }

        val result = provider.verifyCredentials(creds)

        assertTrue(result.isSuccess)
        assertEquals(2, delegate.verifyCalls, "verify must go through req, not the interface default")
    }

    @Test
    fun `every SubtitleProvider method is overridden by ResilientSubtitleProvider`() {
        // Same regression guard as ResilientSeerrApiClientTest: the wrapper
        // implements the interface directly (no `by` delegation) so new
        // interface methods force a compile error — this catches a default
        // method body later added to the interface, which would silently
        // bypass retry.
        val interfaceMethods = SubtitleProvider::class.java.declaredMethods
            .filter { !it.isSynthetic && !it.name.contains('$') }
        val overriddenMethodNames = ResilientSubtitleProvider::class.java.declaredMethods
            .map { it.name }
            .toSet()

        val missingOverrides = interfaceMethods.map { it.name }
            .filter { it !in overriddenMethodNames }

        assertTrue(
            missingOverrides.isEmpty(),
            "Missing retrying overrides for: ${missingOverrides.joinToString(", ")}",
        )
    }
}
