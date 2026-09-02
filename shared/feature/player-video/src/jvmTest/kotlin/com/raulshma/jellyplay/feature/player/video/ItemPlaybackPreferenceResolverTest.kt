package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.model.ItemPlaybackPreference
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItemPlaybackPreferenceResolverTest {

    private lateinit var repository: ItemPlaybackPreferenceRepository
    private var currentItemId: String? = null
    private var currentSeriesId: String? = null
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var resolver: ItemPlaybackPreferenceResolver

    @BeforeTest
    fun setUp() {
        repository = mockk(relaxed = true)
        currentItemId = null
        currentSeriesId = null

        resolver = ItemPlaybackPreferenceResolver(
            repository = repository,
            getCurrentItemId = { currentItemId },
            getCurrentSeriesId = { currentSeriesId },
            scope = testScope,
        )
    }

    @Test
    fun initialResolved_isNull() {
        assertNull(resolver.resolved.value)
    }

    @Test
    fun refresh_itemScopeTakesPrecedenceOverSeriesScope() = testScope.runTest {
        currentItemId = "item-1"
        currentSeriesId = "series-1"

        val itemPref = ItemPlaybackPreference(scope = PlaybackPrefScope.ITEM, key = "item-1", audioLanguage = "eng")
        val seriesPref = ItemPlaybackPreference(scope = PlaybackPrefScope.SERIES, key = "series-1", audioLanguage = "jpn")

        coEvery { repository.get(PlaybackPrefScope.ITEM, "item-1") } returns itemPref
        coEvery { repository.get(PlaybackPrefScope.SERIES, "series-1") } returns seriesPref

        resolver.refresh()
        assertEquals(itemPref, resolver.resolved.value)
    }

    @Test
    fun refresh_fallsBackToSeriesScopeWhenItemPrefMissing() = testScope.runTest {
        currentItemId = "item-2"
        currentSeriesId = "series-1"

        val seriesPref = ItemPlaybackPreference(scope = PlaybackPrefScope.SERIES, key = "series-1", audioLanguage = "jpn")

        coEvery { repository.get(PlaybackPrefScope.ITEM, "item-2") } returns null
        coEvery { repository.get(PlaybackPrefScope.SERIES, "series-1") } returns seriesPref

        resolver.refresh()
        assertEquals(seriesPref, resolver.resolved.value)
    }

    @Test
    fun clear_resetsResolvedToNull() = testScope.runTest {
        currentItemId = "item-1"
        val itemPref = ItemPlaybackPreference(scope = PlaybackPrefScope.ITEM, key = "item-1", audioLanguage = "eng")
        coEvery { repository.get(PlaybackPrefScope.ITEM, "item-1") } returns itemPref

        resolver.refresh()
        assertEquals(itemPref, resolver.resolved.value)

        resolver.clear()
        assertNull(resolver.resolved.value)
    }
}
