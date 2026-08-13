package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderPreferences
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.core.network.subtitle.SubtitleProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies [SubtitleProviderRepositoryImpl]'s core contract: **one provider
 * failing must never blank the others' results** ([ProviderSearchOutcome.Error]
 * for the failure, [ProviderSearchOutcome.Success]/[Skipped] for the rest).
 *
 * Covers both `Result.failure` from a provider (handled by the fold) and a raw
 * throw escaping `provider.search()` — the latter is the regression that once
 * cancelled every sibling via `coroutineScope`/`awaitAll`, losing all results.
 */
class SubtitleProviderRepositoryImplTest {

    private val preferencesStore: SubtitleProviderPreferencesStore = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)

    private val wyzie = stubProvider(SubtitleProviderKind.WYZIE)
    private val openSubtitles = stubProvider(SubtitleProviderKind.OPENSUBTITLES)

    private val wyzieCreds = SubtitleProviderCredentials.Wyzie(apiKey = "k")
    private val osCreds = SubtitleProviderCredentials.OpenSubtitles(username = "u", password = "p")

    private val query = SubtitleQuery(imdbId = "tt1", languages = listOf("eng"))

    private fun stubProvider(kind: SubtitleProviderKind): SubtitleProvider = mockk<SubtitleProvider>(relaxed = true) {
        every { this@mockk.kind } returns kind
    }

    private fun newRepository(providers: Map<SubtitleProviderKind, SubtitleProvider>): SubtitleProviderRepositoryImpl {
        every { preferencesStore.preferences } returns MutableStateFlow(
            SubtitleProviderPreferences(wyzieEnabled = true, openSubtitlesEnabled = true),
        )
        every { preferencesStore.credentials } returns flowOf(
            mapOf(
                SubtitleProviderKind.WYZIE to wyzieCreds,
                SubtitleProviderKind.OPENSUBTITLES to osCreds,
            ),
        )
        every { preferencesStore.getCredentials(SubtitleProviderKind.WYZIE) } returns wyzieCreds
        every { preferencesStore.getCredentials(SubtitleProviderKind.OPENSUBTITLES) } returns osCreds
        return SubtitleProviderRepositoryImpl(preferencesStore, providers, playbackRepository)
    }

    private fun row(kind: SubtitleProviderKind) = SubtitleSearchResult(
        provider = kind, id = "$kind-1", language = "eng", displayName = "Test",
    )

    @Before
    fun setup() {
        coEvery { playbackRepository.searchRemoteSubtitles(any(), any()) } returns Result.success(emptyList())
    }

    @Test
    fun `Result_failure from one provider yields Error, others succeed`() = runTest {
        coEvery { wyzie.search(any(), any()) } returns Result.failure(IllegalStateException("bad key"))
        coEvery { openSubtitles.search(any(), any()) } returns Result.success(listOf(row(SubtitleProviderKind.OPENSUBTITLES)))

        val outcomes = newRepository(
            mapOf(SubtitleProviderKind.WYZIE to wyzie, SubtitleProviderKind.OPENSUBTITLES to openSubtitles),
        ).search(query)

        assertTrue(outcomes[SubtitleProviderKind.WYZIE] is ProviderSearchOutcome.Error)
        val os = outcomes[SubtitleProviderKind.OPENSUBTITLES]
        assertTrue("OS results must survive Wyzie's failure", os is ProviderSearchOutcome.Success)
        assertEquals(1, (os as ProviderSearchOutcome.Success).results.size)
    }

    @Test
    fun `raw throw from one provider does not cancel siblings`() = runTest {
        // A throw escapes provider.search() (e.g. an unexpected RuntimeException).
        // Previously coroutineScope cancelled the OpenSubtitles job → all lost.
        coEvery { wyzie.search(any(), any()) } throws IllegalStateException("boom")
        coEvery { openSubtitles.search(any(), any()) } returns Result.success(listOf(row(SubtitleProviderKind.OPENSUBTITLES)))

        val outcomes = newRepository(
            mapOf(SubtitleProviderKind.WYZIE to wyzie, SubtitleProviderKind.OPENSUBTITLES to openSubtitles),
        ).search(query)

        assertTrue(outcomes[SubtitleProviderKind.WYZIE] is ProviderSearchOutcome.Error)
        val os = outcomes[SubtitleProviderKind.OPENSUBTITLES]
        assertTrue("OpenSubtitles results must survive Wyzie throwing", os is ProviderSearchOutcome.Success)
        assertEquals(1, (os as ProviderSearchOutcome.Success).results.size)
    }

    @Test
    fun `provider without credentials is excluded from fan-out`() = runTest {
        // OpenSubtitles not configured (no credentials) → it never participates
        // in search(); only Wyzie (configured) is probed.
        val repo = newRepository(
            mapOf(SubtitleProviderKind.WYZIE to wyzie, SubtitleProviderKind.OPENSUBTITLES to openSubtitles),
        )
        every { preferencesStore.getCredentials(SubtitleProviderKind.OPENSUBTITLES) } returns null
        coEvery { wyzie.search(any(), any()) } returns Result.success(listOf(row(SubtitleProviderKind.WYZIE)))

        val outcomes = repo.search(query)

        assertTrue("un-configured provider absent from outcomes", !outcomes.containsKey(SubtitleProviderKind.OPENSUBTITLES))
        assertTrue(outcomes[SubtitleProviderKind.WYZIE] is ProviderSearchOutcome.Success)
    }

    @Test
    fun `searchProvider returns Skipped when no credentials`() = runTest {
        // The Test button path ignores the enable toggle — only credentials gate
        // the probe. No credentials → Skipped without hitting the provider.
        val repo = newRepository(
            mapOf(SubtitleProviderKind.WYZIE to wyzie, SubtitleProviderKind.OPENSUBTITLES to openSubtitles),
        )
        every { preferencesStore.getCredentials(SubtitleProviderKind.WYZIE) } returns null

        val outcome = repo.searchProvider(SubtitleProviderKind.WYZIE, query)

        assertTrue(outcome is ProviderSearchOutcome.Skipped)
    }

    @Test
    fun `searchAllStreaming merges results and surfaces errors alongside them`() = runTest {
        coEvery { wyzie.search(any(), any()) } returns Result.failure(IllegalStateException("wyzie down"))
        coEvery { openSubtitles.search(any(), any()) } returns Result.success(listOf(row(SubtitleProviderKind.OPENSUBTITLES)))

        val merged = newRepository(
            mapOf(SubtitleProviderKind.WYZIE to wyzie, SubtitleProviderKind.OPENSUBTITLES to openSubtitles),
        ).searchAllStreaming(query, itemId = "item-1", language = "eng") { }

        // OpenSubtitles row present, Wyzie error surfaced — neither hides the other.
        assertEquals(1, merged.results.size)
        assertEquals(SubtitleProviderKind.OPENSUBTITLES, merged.results.first().provider)
        assertTrue("Wyzie error must be in the error map", merged.errors.containsKey(SubtitleProviderKind.WYZIE))
    }

    @Test
    fun `searchAllStreaming emits each provider as it resolves, not behind a barrier`() = runTest {
        // Wyzie is slow (e.g. retrying through RetryPolicy backoff); OpenSubtitles
        // resolves instantly. The streaming contract: OpenSubtitles' results must
        // reach onPartial BEFORE the slow Wyzie completes — this is the exact
        // scenario the old awaitAll barrier got wrong (it buffered everything).
        coEvery { wyzie.search(any(), any()) } coAnswers {
            delay(5_000) // slow
            Result.failure(IllegalStateException("wyzie down"))
        }
        coEvery { openSubtitles.search(any(), any()) } returns
            Result.success(listOf(row(SubtitleProviderKind.OPENSUBTITLES)))

        val partials = mutableListOf<MergedSubtitleSearch>()
        val merged = newRepository(
            mapOf(SubtitleProviderKind.WYZIE to wyzie, SubtitleProviderKind.OPENSUBTITLES to openSubtitles),
        ).searchAllStreaming(query, itemId = "item-1", language = "eng") { partial ->
            partials += partial
        }

        // Final state: OpenSubtitles row + Wyzie error.
        assertEquals(1, merged.results.size)
        assertEquals(SubtitleProviderKind.OPENSUBTITLES, merged.results.first().provider)
        assertTrue(merged.errors.containsKey(SubtitleProviderKind.WYZIE))

        // Streaming contract: at least one partial was emitted containing
        // OpenSubtitles' results while Wyzie's error was NOT yet present — proof
        // the fast provider's results escaped the barrier instead of waiting.
        assertTrue(
            "Expected a partial with OpenSubtitles results but no Wyzie error yet; got: " +
                partials.joinToString { "results=${it.results.map { r -> r.provider }} errors=${it.errors.keys}" },
            partials.any { p ->
                p.results.any { it.provider == SubtitleProviderKind.OPENSUBTITLES } &&
                    !p.errors.containsKey(SubtitleProviderKind.WYZIE)
            },
        )
    }

    @Test
    fun `searchAllStreaming final snapshot merges all providers in stable order`() = runTest {
        coEvery { wyzie.search(any(), any()) } returns Result.success(listOf(row(SubtitleProviderKind.WYZIE)))
        coEvery { openSubtitles.search(any(), any()) } returns Result.success(listOf(row(SubtitleProviderKind.OPENSUBTITLES)))

        val merged = newRepository(
            mapOf(SubtitleProviderKind.WYZIE to wyzie, SubtitleProviderKind.OPENSUBTITLES to openSubtitles),
        ).searchAllStreaming(query, itemId = "item-1", language = "eng") { }

        // Both providers present in the final snapshot, ordered by provider ordinal.
        val providers = merged.results.map { it.provider }
        assertEquals(listOf(SubtitleProviderKind.WYZIE, SubtitleProviderKind.OPENSUBTITLES), providers)
        assertTrue(merged.errors.isEmpty())
    }
}
