package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_added_to_collection
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_collection_created
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_couldnt_add_to_collection
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_no_episodes_queued

/**
 * Drives the collection adapter over [AddToTargetActions] through its
 * interface — the merged module's collection surface (formerly the
 * CollectionActions mirror).
 */
class CollectionTargetsTest {

    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val mediaDetailProvider: MediaDetailProvider = mockk(relaxed = true)

    private val strings = fakeDetailStrings()
    private val messages = RecordingMessages()

    private val movieDetail = MediaDetail(
        item = MediaItem(id = "m1", name = "A Movie", mediaType = MediaType.MOVIE),
    )
    private val audioDetail = MediaDetail(
        item = MediaItem(id = "a1", name = "An Album", mediaType = MediaType.AUDIO),
    )
    private val seriesDetail = MediaDetail(
        item = MediaItem(id = "s1", name = "A Series", mediaType = MediaType.SERIES),
    )

    @BeforeTest
    fun setUpDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private fun actions(
        scope: CoroutineScope,
        detail: MediaDetail? = movieDetail,
        sortedEpisodes: List<MediaItem> = emptyList(),
        canonicalEpisodeIds: (String) -> List<String> = { emptyList() },
    ): AddToTargetActions<CollectionSummary> {
        val session = MutableStateFlow(
            DetailSession(
                itemId = detail?.item?.id ?: "m1",
                detail = detail,
                sortedEpisodes = sortedEpisodes,
            ),
        )
        coEvery { mediaDetailProvider.canonicalEpisodeIds(any()) } coAnswers {
            canonicalEpisodeIds(firstArg())
        }
        return AddToTargetActions(
            scope = scope,
            session = session,
            messages = messages.flow,
            adapter = CollectionAddTarget(strings, mediaRepository),
            mediaDetailProvider = mediaDetailProvider,
        )
    }

    // region openPicker
    @Test
    fun `openPicker with a movie sets showPicker and loads collections`() = runTest {
        val c1 = CollectionSummary(id = "c1", name = "Marvel", itemCount = 4)
        coEvery { mediaRepository.getCollections(any()) } returns Result.success(listOf(c1))
        val a = actions(this)

        a.openPicker()
        advanceUntilIdle()

        val state = a.state.value
        assertTrue(state.showPicker)
        assertFalse(state.isLoadingTargets)
        assertEquals(listOf(c1), state.targets)
    }

    @Test
    fun `openPicker with an audio detail is a no-op`() = runTest {
        coEvery { mediaRepository.getCollections(any()) } returns Result.success(emptyList())
        val a = actions(this, detail = audioDetail)

        a.openPicker()
        advanceUntilIdle()

        assertFalse(a.state.value.showPicker)
        assertTrue(a.state.value.targets.isEmpty())
        // The audio type is ineligible, so the list should never be fetched.
        coVerify(exactly = 0) { mediaRepository.getCollections(any()) }
    }

    @Test
    fun `openPicker with a series sets showPicker and loads collections`() = runTest {
        val c1 = CollectionSummary(id = "c1", name = "Marvel", itemCount = 4)
        coEvery { mediaRepository.getCollections(any()) } returns Result.success(listOf(c1))
        val a = actions(this, detail = seriesDetail)

        a.openPicker()
        advanceUntilIdle()

        assertTrue(a.state.value.showPicker)
        assertEquals(listOf(c1), a.state.value.targets)
    }

    @Test
    fun `openPicker with no loaded detail is a no-op`() = runTest {
        val a = actions(this, detail = null)

        a.openPicker()
        advanceUntilIdle()

        assertFalse(a.state.value.showPicker)
        coVerify(exactly = 0) { mediaRepository.getCollections(any()) }
    }

    @Test
    fun `dismissPicker clears the picker flag`() = runTest {
        coEvery { mediaRepository.getCollections(any()) } returns Result.success(emptyList())
        val a = actions(this)
        a.openPicker()
        advanceUntilIdle()
        assertTrue(a.state.value.showPicker)

        a.dismissPicker()

        assertFalse(a.state.value.showPicker)
    }

    @Test
    fun `openCreateDialog closes the picker and opens the dialog`() = runTest {
        coEvery { mediaRepository.getCollections(any()) } returns Result.success(emptyList())
        val a = actions(this)
        a.openPicker()
        advanceUntilIdle()

        a.openCreateDialog()

        // The picker and create-dialog are mutually exclusive.
        assertFalse(a.state.value.showPicker)
        assertTrue(a.state.value.showCreateDialog)
    }

    @Test
    fun `dismissCreateDialog closes the dialog`() = runTest {
        val a = actions(this)
        a.openCreateDialog()
        assertTrue(a.state.value.showCreateDialog)

        a.dismissCreateDialog()

        assertFalse(a.state.value.showCreateDialog)
    }
    // endregion

    // region addTo
    @Test
    fun `addTo success emits the added-to-collection message`() = runTest {
        coEvery { mediaRepository.addItemsToCollection(any(), any()) } returns Result.success(Unit)
        val collection = CollectionSummary(id = "c1", name = "Marvel", itemCount = 4)
        val a = actions(this)

        a.addTo(collection)
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_added_to_collection, collection.name))
            )
        )
        assertFalse(a.state.value.isAdding)
        assertFalse(a.state.value.showPicker)
        coVerify { mediaRepository.addItemsToCollection("c1", listOf("m1")) }
    }

    @Test
    fun `addTo for a series with no episodes emits the no-episodes-queued message`() = runTest {
        val collection = CollectionSummary(id = "c1", name = "Marvel", itemCount = 4)
        val a = actions(
            this,
            detail = seriesDetail,
            sortedEpisodes = emptyList(),
            canonicalEpisodeIds = { emptyList() },
        )

        a.addTo(collection)
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_no_episodes_queued))
            )
        )
        // Nothing should be added against an empty resolution.
        coVerify(exactly = 0) { mediaRepository.addItemsToCollection(any(), any()) }
        assertFalse(a.state.value.isAdding)
    }

    @Test
    fun `addTo for a series resolves ids from sorted episodes over a cold load`() = runTest {
        coEvery { mediaRepository.addItemsToCollection(any(), any()) } returns Result.success(Unit)
        val collection = CollectionSummary(id = "c1", name = "Marvel", itemCount = 4)
        val ep1 = MediaItem(id = "ep1", name = "E1", mediaType = MediaType.EPISODE)
        val ep2 = MediaItem(id = "ep2", name = "E2", mediaType = MediaType.EPISODE)
        var coldLoads = 0
        val a = actions(
            this,
            detail = seriesDetail,
            sortedEpisodes = listOf(ep1, ep2),
            canonicalEpisodeIds = {
                coldLoads++
                listOf("cold")
            },
        )

        a.addTo(collection)
        advanceUntilIdle()

        // Sorted episodes win; the cold-load fallback must not fire.
        coVerify { mediaRepository.addItemsToCollection("c1", listOf("ep1", "ep2")) }
        assertEquals(0, coldLoads)
    }

    @Test
    fun `addTo for a series falls back to canonicalEpisodeIds when the snapshot is empty`() = runTest {
        coEvery { mediaRepository.addItemsToCollection(any(), any()) } returns Result.success(Unit)
        val collection = CollectionSummary(id = "c1", name = "Marvel", itemCount = 4)
        val a = actions(
            this,
            detail = seriesDetail,
            sortedEpisodes = emptyList(),
            canonicalEpisodeIds = { listOf("cold-1", "cold-2") },
        )

        a.addTo(collection)
        advanceUntilIdle()

        coVerify { mediaRepository.addItemsToCollection("c1", listOf("cold-1", "cold-2")) }
    }

    @Test
    fun `addTo repository failure emits couldnt-add message`() = runTest {
        coEvery { mediaRepository.addItemsToCollection(any(), any()) } returns Result.failure(RuntimeException("server"))
        val a = actions(this)

        a.addTo(CollectionSummary(id = "c1", name = "Marvel", itemCount = 4))
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_couldnt_add_to_collection))
            )
        )
        assertFalse(a.state.value.isAdding)
        assertFalse(a.state.value.showPicker)
    }
    // endregion

    // region createAndAdd
    @Test
    fun `createAndAdd success creates the collection and closes the dialog`() = runTest {
        coEvery { mediaRepository.createCollection(any(), any()) } returns Result.success("col-new")
        val a = actions(this)
        a.openCreateDialog()
        assertTrue(a.state.value.showCreateDialog)

        a.createAndAdd(" My Set ")
        advanceUntilIdle()

        // Name is trimmed; the create endpoint is seeded with the current item; dialog closes on success.
        coVerify { mediaRepository.createCollection("My Set", listOf("m1")) }
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_collection_created, "My Set"))
            )
        )
        assertFalse(a.state.value.showCreateDialog)
        assertFalse(a.state.value.isAdding)
    }

    @Test
    fun `createAndAdd with blank name is a no-op`() = runTest {
        val a = actions(this)
        a.openCreateDialog()

        a.createAndAdd("   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.createCollection(any(), any()) }
        // Dialog stays open; the guard fired before any state mutation.
        assertTrue(a.state.value.showCreateDialog)
    }

    @Test
    fun `createAndAdd with no loaded detail is a no-op`() = runTest {
        val a = actions(this, detail = null)

        a.createAndAdd("Name")
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.createCollection(any(), any()) }
    }

    @Test
    fun `createAndAdd repository failure emits couldnt-add message`() = runTest {
        coEvery { mediaRepository.createCollection(any(), any()) } returns Result.failure(RuntimeException("server"))
        val a = actions(this)
        a.openCreateDialog()

        a.createAndAdd("Name")
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_couldnt_add_to_collection))
            )
        )
        // The dialog still closes on failure (the create attempt completed).
        assertFalse(a.state.value.showCreateDialog)
        assertFalse(a.state.value.isAdding)
    }

    @Test
    fun `createAndAdd for a series with no episodes emits no-episodes-queued and skips create`() = runTest {
        val a = actions(
            this,
            detail = seriesDetail,
            sortedEpisodes = emptyList(),
            canonicalEpisodeIds = { emptyList() },
        )
        a.openCreateDialog()

        a.createAndAdd("Name")
        advanceUntilIdle()

        // No seed ids → must not create an empty collection; surfaces the
        // shared no-op message and closes the dialog.
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(Res.string.detail_msg_no_episodes_queued))
            )
        )
        coVerify(exactly = 0) { mediaRepository.createCollection(any(), any()) }
        assertFalse(a.state.value.showCreateDialog)
        assertFalse(a.state.value.isAdding)
    }
    // endregion
}
