@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.network.api.JellyPlayHeroResult
import com.raulshma.jellyplay.core.network.api.JellyPlayRowCatalog
import com.raulshma.jellyplay.core.network.api.JellyPlayRowItem
import com.raulshma.jellyplay.core.network.api.JellyPlayRowResult
import com.raulshma.jellyplay.core.network.api.JellyPlayRowTitle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the extracted plugin content module ([PluginHomeContent]) at its own
 * grain — the fetcher-level behavior (gates, fetch-map shape, drop-don't-fail,
 * force bypass, the single-row refresh arms) stays on
 * [HomeSectionsFetcherPluginRowTest], which now exercises the module through
 * the fetcher's delegates. The module-grain additions here:
 *
 *  - both memos (plugin rows AND the curated hero) are identity-scoped and
 *    reachable through the module's [PluginHomeContent.subcallCaches] — the
 *    fetcher's wholesale drop funnels through that list, so a new plugin memo
 *    cannot be forgotten at clear time (the fetcher cache spec's rule);
 *  - the titled-row enumerate maps every catalog entry through the same
 *    title-keyed memo path (the batch shape the fetcher's
 *    `fetchTitledPluginRows` delegate serves).
 */
class PluginHomeContentTest {

    /** The plugin-side port double: call log + scripted results (all gates on). */
    private class FakeJellyPlayHomeSectionSources : JellyPlayHomeSectionSources {
        override suspend fun seasonalRowsEnabled(): Boolean = true
        override suspend fun customRowsEnabled(): Boolean = true
        override suspend fun curatedHeroEnabled(): Boolean = true

        val calls = mutableListOf<String>()
        val seasonalResults = ArrayDeque<Result<JellyPlayRowResult?>>()
        val customResults = ArrayDeque<Result<JellyPlayRowResult?>>()
        val heroResults = ArrayDeque<Result<JellyPlayHeroResult?>>()
        val itemResults = ArrayDeque<Result<List<MediaItem>>>()

        override suspend fun getSeasonalRow(keyword: String?): Result<JellyPlayRowResult?> {
            calls += "seasonal"
            return seasonalResults.removeFirstOrNull() ?: Result.success(null)
        }

        override suspend fun getCustomRow(title: String): Result<JellyPlayRowResult?> {
            calls += "custom:$title"
            return customResults.removeFirstOrNull() ?: Result.success(null)
        }

        override suspend fun getCustomRowCatalog(): Result<JellyPlayRowCatalog?> = Result.success(
            JellyPlayRowCatalog(rows = listOf(JellyPlayRowTitle(title = "Top 10 of 2025"))),
        )

        override suspend fun getCuratedHero(): Result<JellyPlayHeroResult?> {
            calls += "hero"
            return heroResults.removeFirstOrNull() ?: Result.success(null)
        }

        override suspend fun getItemsByIds(ids: List<String>): Result<List<MediaItem>> {
            calls += "items:${ids.size}"
            return itemResults.removeFirstOrNull() ?: Result.success(emptyList())
        }
    }

    private val identityA = CacheIdentity.ofOrNull("server-1", "user-1")
    private val identityB = CacheIdentity.ofOrNull("server-1", "user-2")

    private fun module(
        plugin: FakeJellyPlayHomeSectionSources,
        identity: () -> CacheIdentity? = { identityA },
    ) = PluginHomeContent(
        jellyPlaySources = plugin,
        cacheIdentity = identity,
    )

    private fun mediaItem(id: String) = MediaItem(id = id, name = "Local $id", mediaType = MediaType.MOVIE)

    private fun seasonalPayload(
        items: List<JellyPlayRowItem>,
        title: String = "Spooky Season",
    ): Result<JellyPlayRowResult?> = Result.success(
        JellyPlayRowResult(title = title, source = "letterboxd", items = items),
    )

    // ── the wholesale drop reaches both memos through the module's list ──

    @Test
    fun `subcallCaches exposes both memos and clearing them re-reads the transport`() = runTest {
        val plugin = FakeJellyPlayHomeSectionSources().apply {
            seasonalResults += seasonalPayload(items = emptyList())
            heroResults += Result.success(JellyPlayHeroResult(candidates = emptyList()))
        }
        val home = module(plugin)

        home.fetchSeasonalPluginRow(force = false, identity = identityA)
        home.fetchCuratedHero(identity = identityA)
        home.fetchSeasonalPluginRow(force = false, identity = identityA)
        home.fetchCuratedHero(identity = identityA)
        assertEquals(1, plugin.calls.count { it == "seasonal" }, "the row memo serves the second ordinary read")
        assertEquals(1, plugin.calls.count { it == "hero" }, "the hero memo serves the second ordinary read")

        home.subcallCaches.forEach { it.clear() }
        home.fetchSeasonalPluginRow(force = false, identity = identityA)
        home.fetchCuratedHero(identity = identityA)
        assertEquals(2, plugin.calls.count { it == "seasonal" }, "the dropped row memo re-reads the transport")
        assertEquals(2, plugin.calls.count { it == "hero" }, "the dropped hero memo re-reads the transport")
    }

    @Test
    fun `both memos are identity-scoped`() = runTest {
        var identity: CacheIdentity? = identityA
        val plugin = FakeJellyPlayHomeSectionSources().apply {
            seasonalResults += seasonalPayload(items = emptyList())
            heroResults += Result.success(JellyPlayHeroResult(candidates = emptyList()))
        }
        val home = module(plugin, identity = { identity })

        home.fetchSeasonalPluginRow(force = false, identity = identityA)
        home.fetchCuratedHero(identity = identityA)
        identity = identityB
        // The module normalizes the identity lambda itself when the caller
        // passes none — the miss must come from the lambda's switch.
        home.fetchSeasonalPluginRow(force = false, identity = identity)
        home.fetchCuratedHero(identity = identity)

        assertEquals(2, plugin.calls.count { it == "seasonal" }, "a user switch misses the row memo")
        assertEquals(2, plugin.calls.count { it == "hero" }, "a user switch misses the hero memo")
    }

    // ── the titled-row enumerate through the module ──────────────────────

    @Test
    fun `titled rows map every catalog entry through the title-keyed memo`() = runTest {
        val plugin = FakeJellyPlayHomeSectionSources().apply {
            customResults += seasonalPayload(
                items = listOf(JellyPlayRowItem(title = "Only", localItemId = "l1")),
                title = "Top 10 of 2025",
            )
            itemResults += Result.success(listOf(mediaItem("l1")))
        }
        val home = module(plugin)

        val rows = home.fetchTitledPluginRows(force = false, identity = null)
        val cached = home.fetchPluginCustomRow("Top 10 of 2025")

        assertEquals(listOf("custom:Top 10 of 2025", "items:1"), plugin.calls, "the second read serves the title-keyed memo")
        val row = assertNotNull(rows.single())
        assertEquals("jellyplay_custom_Top 10 of 2025", row.id)
        assertEquals("Top 10 of 2025", row.title)
        assertTrue(row.items.isEmpty(), "plugin rows ride jellyPlayRowEntries, never items")
        assertEquals("l1", cached?.jellyPlayRowEntries?.single()?.localItem?.id)
    }
}
