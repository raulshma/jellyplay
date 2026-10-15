package com.raulshma.jellyplay.core.ui.settingssearch

import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_loading
import com.raulshma.jellyplay.core.ui.generated.resources.core_search
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_playback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the local settings-search pipeline ([settingsSearchResults]) without
 * resolving any Compose string resources (the provider's catalog is empty or
 * instrumented — `resolve()` over a NON-empty catalog would call `getString`,
 * which these tests never trigger):
 *
 *  - a BLANK query emits an empty list WITHOUT touching the catalog (the
 *    catalog getter AND `resolved()` throw if touched — the short-circuit is
 *    the contract, not an implementation detail);
 *  - the catalog resolves ONCE PER SUBSCRIPTION ([SettingsSearchProvider.resolved]
 *    counted): every later query re-runs only the matcher against the prepared
 *    candidates — the resolve-once contract, which is also why a desktop
 *    in-app locale switch cannot refresh the snapshot mid-subscription (the
 *    documented limitation in [settingsSearchResults]'s KDoc);
 *  - rapid keystrokes are debounced: only the FINAL query of a burst reaches
 *    the catalog;
 *  - identical consecutive queries match only once (distinctUntilChanged sits
 *    before the matcher);
 *  - the empty catalog yields empty results for any query.
 *
 * Note on timing: the pipeline runs on Dispatchers.Default (flowOn), so the
 * 120 ms debounce elapses in REAL time even under a test dispatcher — the
 * assertions wait with generous margins instead of virtual-time advances.
 */
class SettingsSearchFlowTest {

    /** Provider whose catalog getter AND resolved funnel throw — proves blank queries never touch either. */
    private class ExplodingProvider : SettingsSearchProvider {
        override val items: List<SettingsSearchItem>
            get() = throw IllegalStateException("blank query must not resolve the catalog")

        override suspend fun resolved(): List<ResolvedSettingsItem> =
            throw IllegalStateException("blank query must not call resolved()")
    }

    /** Provider that counts catalog reads and serves an empty catalog. */
    private class CountingProvider : SettingsSearchProvider {
        var reads = 0
            private set
        override val items: List<SettingsSearchItem>
            get() {
                reads++
                return emptyList()
            }
    }

    /**
     * Provider whose items getter always throws — non-blank queries must match
     * against the [SettingsSearchProvider.resolved] funnel (platform-filtered),
     * never against raw [SettingsSearchProvider.items] — and whose
     * `resolved()` COUNTS its calls (the resolve-once-per-subscription pin).
     */
    private class FunnelOnlyProvider : SettingsSearchProvider {
        var resolves = 0
            private set
        override val items: List<SettingsSearchItem>
            get() = throw IllegalStateException("search must consume resolved(), not items")

        override suspend fun resolved(): List<ResolvedSettingsItem> {
            resolves++
            return listOf(
                ResolvedSettingsItem(
                    item = SettingsSearchItem(
                        id = "player_engine",
                        titleRes = Res.string.core_search,
                        subtitleRes = Res.string.core_loading,
                        categoryRes = Res.string.ss_cat_playback,
                        keywords = emptyList(),
                        route = com.raulshma.jellyplay.core.ui.navigation.Route.Settings,
                        icon = testIcon,
                    ),
                    title = "Player engine",
                    subtitle = "",
                    category = "Playback",
                ),
            )
        }

        private val testIcon get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
            name = "test",
            defaultWidth = androidx.compose.ui.unit.Dp.Unspecified,
            defaultHeight = androidx.compose.ui.unit.Dp.Unspecified,
            viewportWidth = 1f,
            viewportHeight = 1f,
        ).build()
    }

    private fun realTimeTest(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        withContext(Dispatchers.Default) { withTimeout(10_000) { block() } }
    }

    @Test
    fun blankQuery_emitsEmptyListWithoutTouchingTheCatalog() = realTimeTest {
        val first = settingsSearchResults(MutableStateFlow(""), ExplodingProvider()).first()

        assertTrue(first.isEmpty())
    }

    @Test
    fun nonBlankQuery_matchesAgainstTheResolvedFunnel() = realTimeTest {
        // If the pipeline ever reverts to provider.items.resolve(), the items
        // getter throws here — the funnel is the contract.
        val results = settingsSearchResults(flowOf("engine"), FunnelOnlyProvider()).first()

        assertTrue(results.any { it.id == "player_engine" })
    }

    @Test
    fun emptyCatalog_yieldsEmptyResultsForAnyQuery() = realTimeTest {
        val results = settingsSearchResults(flowOf("playback"), CountingProvider()).first()

        assertEquals(emptyList(), results)
    }

    @Test
    fun rapidBurst_debouncesToTheFinalQueryOnly() = realTimeTest {
        val provider = FunnelOnlyProvider()
        // All three keystrokes land well inside the 120 ms debounce window.
        val burst = flow {
            emit("")
            emit("the")
            emit("them")
            emit("engine")
        }

        val results = settingsSearchResults(burst, provider).first()

        // Only the final query was matched; the blank query and intermediate
        // keystrokes were dropped by the debounce — and the funnel ran once.
        assertEquals(listOf("player_engine"), results.map { it.id })
        assertEquals(1, provider.resolves, "burst must resolve only the final query")
    }

    @Test
    fun identicalConsecutiveQueries_matchOnlyOnce() = realTimeTest {
        val provider = FunnelOnlyProvider()
        val queries = flow {
            emit("theme")
            emit("theme") // duplicate — must be dropped before the matcher step
            emit("themes")
        }

        settingsSearchResults(queries, provider).first()

        assertEquals(1, provider.resolves, "duplicate consecutive queries must not re-run the pipeline")
    }

    @Test
    fun resolvedCatalog_isPreparedOncePerSubscription_acrossDistinctQueries() = realTimeTest {
        val provider = FunnelOnlyProvider()
        val queries = MutableStateFlow("engine")

        val job = launch { settingsSearchResults(queries, provider).take(2).collect { } }
        delay(400) // "engine" settles (debounce 120 ms) and resolves once
        queries.value = "player" // distinct query after the window
        delay(600)
        job.join()

        // The new query re-RAN the matcher (its results are still live) but
        // must NOT re-resolve the catalog — that is the resolve-once contract,
        // and its documented corollary: an in-app locale switch inside one
        // subscription keeps the snapshot's locale (pinned limitation).
        assertEquals(1, provider.resolves, "distinct queries must not re-resolve within one subscription")
    }

    @Test
    fun aSecondSubscription_resolvesItsOwnCatalog() = realTimeTest {
        val provider = FunnelOnlyProvider()

        settingsSearchResults(flowOf("engine"), provider).first()
        settingsSearchResults(flowOf("player"), provider).first()

        assertEquals(2, provider.resolves, "each subscription owns one resolve")
    }
}
