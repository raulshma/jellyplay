package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.model.ItemPlaybackPreference
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [ItemPlaybackPreferenceResolver] — the item-vs-series
 * precedence used by [TrackSelectionHelper] for per-series/per-item playback
 * language preferences.
 *
 * Uses [UnconfinedTestDispatcher] so the resolver's async refresh completes
 * eagerly within [refresh], keeping assertions deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemPlaybackPreferenceResolverTest {

    private fun resolver(
        repo: ItemPlaybackPreferenceRepository,
        itemId: () -> String? = { "item-1" },
        seriesId: () -> String? = { "series-1" },
    ): ItemPlaybackPreferenceResolver {
        // An unconfined scope makes resolver.refresh()'s internal launch run to
        // completion synchronously, keeping assertions deterministic.
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        return ItemPlaybackPreferenceResolver(repo, itemId, seriesId, scope)
    }

    @Test
    fun `item scope wins over series scope`() = runTest {
        val repo = mockk<ItemPlaybackPreferenceRepository>()
        val itemPref = ItemPlaybackPreference(PlaybackPrefScope.ITEM, "item-1", audioLanguage = "jpn")
        val seriesPref = ItemPlaybackPreference(PlaybackPrefScope.SERIES, "series-1", audioLanguage = "deu")
        coEvery { repo.get(PlaybackPrefScope.ITEM, "item-1") } returns itemPref
        coEvery { repo.get(PlaybackPrefScope.SERIES, "series-1") } returns seriesPref

        val r = resolver(repo)
        r.refresh()

        assertEquals(itemPref, r.resolved.value)
    }

    @Test
    fun `series scope used when no item rule`() = runTest {
        val repo = mockk<ItemPlaybackPreferenceRepository>()
        val seriesPref = ItemPlaybackPreference(PlaybackPrefScope.SERIES, "series-1", subtitleLanguage = "eng")
        coEvery { repo.get(PlaybackPrefScope.ITEM, "item-1") } returns null
        coEvery { repo.get(PlaybackPrefScope.SERIES, "series-1") } returns seriesPref

        val r = resolver(repo)
        r.refresh()

        assertEquals(seriesPref, r.resolved.value)
    }

    @Test
    fun `null when neither item nor series rule exists`() = runTest {
        val repo = mockk<ItemPlaybackPreferenceRepository>()
        coEvery { repo.get(PlaybackPrefScope.ITEM, "item-1") } returns null
        coEvery { repo.get(PlaybackPrefScope.SERIES, "series-1") } returns null

        val r = resolver(repo)
        r.refresh()

        assertNull(r.resolved.value)
    }

    @Test
    fun `series scope skipped when no series id`() = runTest {
        val repo = mockk<ItemPlaybackPreferenceRepository>()
        coEvery { repo.get(PlaybackPrefScope.ITEM, "item-1") } returns null

        // Movie: no series id. Only item scope should be queried.
        val r = resolver(repo, seriesId = { null })
        r.refresh()

        assertNull(r.resolved.value)
    }

    @Test
    fun `item scope skipped when no item id`() = runTest {
        val repo = mockk<ItemPlaybackPreferenceRepository>()
        val seriesPref = ItemPlaybackPreference(PlaybackPrefScope.SERIES, "series-1", audioLanguage = "deu")
        coEvery { repo.get(PlaybackPrefScope.SERIES, "series-1") } returns seriesPref

        val r = resolver(repo, itemId = { null })
        r.refresh()

        assertEquals(seriesPref, r.resolved.value)
    }

    @Test
    fun `clear resets cached value`() = runTest {
        val repo = mockk<ItemPlaybackPreferenceRepository>()
        val itemPref = ItemPlaybackPreference(PlaybackPrefScope.ITEM, "item-1", audioLanguage = "jpn")
        coEvery { repo.get(PlaybackPrefScope.ITEM, "item-1") } returns itemPref
        coEvery { repo.get(PlaybackPrefScope.SERIES, "series-1") } returns null

        val r = resolver(repo)
        r.refresh()
        assertEquals(itemPref, r.resolved.value)

        r.clear()
        assertNull(r.resolved.value)
    }
}
