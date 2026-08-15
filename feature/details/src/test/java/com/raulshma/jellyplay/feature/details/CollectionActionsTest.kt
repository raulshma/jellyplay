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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CollectionActionsTest {

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

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private fun actions(
        scope: CoroutineScope,
        detail: MediaDetail? = movieDetail,
        sortedEpisodes: List<MediaItem> = emptyList(),
        canonicalEpisodeIds: (String) -> List<String> = { emptyList() },
    ): CollectionActions {
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
        return CollectionActions(
            scope = scope,
            session = session,
            messages = messages.flow,
            strings = strings,
            mediaRepository = mediaRepository,
            mediaDetailProvider = mediaDetailProvider,
        )
    }

    // region openCollectionPicker
    @Test
    fun `openCollectionPicker with a movie sets showCollectionPicker and loads collections`() = runTest {
        val c1 = CollectionSummary(id = "c1", name = "Marvel", itemCount = 4)
        coEvery { mediaRepository.getCollections(any()) } returns Result.success(listOf(c1))
        val a = actions(this)

        a.openCollectionPicker()
        advanceUntilIdle()

        val state = a.state.value
        assertTrue(state.showCollectionPicker)
        assertFalse(state.isLoadingCollections)
        assertEquals(listOf(c1), state.collections)
    }

    @Test
    fun `openCollectionPicker with an audio detail is a no-op`() = runTest {
        coEvery { mediaRepository.getCollections(any()) } returns Result.success(emptyList())
        val a = actions(this, detail = audioDetail)

        a.openCollectionPicker()
        advanceUntilIdle()

        assertFalse(a.state.value.showCollectionPicker)
        assertTrue(a.state.value.collections.isEmpty())
        // The audio type is ineligible, so the list should never be fetched.
        coVerify(exactly = 0) { mediaRepository.getCollections(any()) }
    }

    @Test
    fun `openCollectionPicker with a series sets showCollectionPicker and loads collections`() = runTest {
        val c1 = CollectionSummary(id = "c1", name = "Marvel", itemCount = 4)
        coEvery { mediaRepository.getCollections(any()) } returns Result.success(listOf(c1))
        val a = actions(this, detail = seriesDetail)

        a.openCollectionPicker()
        advanceUntilIdle()

        assertTrue(a.state.value.showCollectionPicker)
        assertEquals(listOf(c1), a.state.value.collections)
    }

    @Test
    fun `openCollectionPicker with no loaded detail is a no-op`() = runTest {
        val a = actions(this, detail = null)

        a.openCollectionPicker()
        advanceUntilIdle()

        assertFalse(a.state.value.showCollectionPicker)
        coVerify(exactly = 0) { mediaRepository.getCollections(any()) }
    }

    @Test
    fun `dismissCollectionPicker clears the picker flag`() = runTest {
        coEvery { mediaRepository.getCollections(any()) } returns Result.success(emptyList())
        val a = actions(this)
        a.openCollectionPicker()
        advanceUntilIdle()
        assertTrue(a.state.value.showCollectionPicker)

        a.dismissCollectionPicker()

        assertFalse(a.state.value.showCollectionPicker)
    }

    @Test
    fun `openCreateCollectionDialog closes the picker and opens the dialog`() = runTest {
        coEvery { mediaRepository.getCollections(any()) } returns Result.success(emptyList())
        val a = actions(this)
        a.openCollectionPicker()
        advanceUntilIdle()

        a.openCreateCollectionDialog()

        // The picker and create-dialog are mutually exclusive.
        assertFalse(a.state.value.showCollectionPicker)
        assertTrue(a.state.value.showCreateCollectionDialog)
    }

    @Test
    fun `dismissCreateCollectionDialog closes the dialog`() = runTest {
        val a = actions(this)
        a.openCreateCollectionDialog()
        assertTrue(a.state.value.showCreateCollectionDialog)

        a.dismissCreateCollectionDialog()

        assertFalse(a.state.value.showCreateCollectionDialog)
    }
    // endregion

    // region addToCollection
    @Test
    fun `addToCollection success emits the added-to-collection message`() = runTest {
        coEvery { mediaRepository.addItemsToCollection(any(), any()) } returns Result.success(Unit)
        val collection = CollectionSummary(id = "c1", name = "Marvel", itemCount = 4)
        val a = actions(this)

        a.addToCollection(collection)
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_added_to_collection, collection.name))
            )
        )
        assertFalse(a.state.value.isAddingToCollection)
        assertFalse(a.state.value.showCollectionPicker)
        coVerify { mediaRepository.addItemsToCollection("c1", listOf("m1")) }
    }

    @Test
    fun `addToCollection for a series with no episodes emits the no-episodes-queued message`() = runTest {
        val collection = CollectionSummary(id = "c1", name = "Marvel", itemCount = 4)
        val a = actions(
            this,
            detail = seriesDetail,
            sortedEpisodes = emptyList(),
            canonicalEpisodeIds = { emptyList() },
        )

        a.addToCollection(collection)
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_no_episodes_queued))
            )
        )
        // Nothing should be added against an empty resolution.
        coVerify(exactly = 0) { mediaRepository.addItemsToCollection(any(), any()) }
        assertFalse(a.state.value.isAddingToCollection)
    }

    @Test
    fun `addToCollection for a series resolves ids from sorted episodes over a cold load`() = runTest {
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

        a.addToCollection(collection)
        advanceUntilIdle()

        // Sorted episodes win; the cold-load fallback must not fire.
        coVerify { mediaRepository.addItemsToCollection("c1", listOf("ep1", "ep2")) }
        assertEquals(0, coldLoads)
    }

    @Test
    fun `addToCollection for a series falls back to canonicalEpisodeIds when the snapshot is empty`() = runTest {
        coEvery { mediaRepository.addItemsToCollection(any(), any()) } returns Result.success(Unit)
        val collection = CollectionSummary(id = "c1", name = "Marvel", itemCount = 4)
        val a = actions(
            this,
            detail = seriesDetail,
            sortedEpisodes = emptyList(),
            canonicalEpisodeIds = { listOf("cold-1", "cold-2") },
        )

        a.addToCollection(collection)
        advanceUntilIdle()

        coVerify { mediaRepository.addItemsToCollection("c1", listOf("cold-1", "cold-2")) }
    }

    @Test
    fun `addToCollection repository failure emits couldnt-add message`() = runTest {
        coEvery { mediaRepository.addItemsToCollection(any(), any()) } returns Result.failure(RuntimeException("server"))
        val a = actions(this)

        a.addToCollection(CollectionSummary(id = "c1", name = "Marvel", itemCount = 4))
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_couldnt_add_to_collection))
            )
        )
        assertFalse(a.state.value.isAddingToCollection)
        assertFalse(a.state.value.showCollectionPicker)
    }
    // endregion

    // region createAndAddCollection
    @Test
    fun `createAndAddCollection success creates the collection and closes the dialog`() = runTest {
        coEvery { mediaRepository.createCollection(any(), any()) } returns Result.success("col-new")
        val a = actions(this)
        a.openCreateCollectionDialog()
        assertTrue(a.state.value.showCreateCollectionDialog)

        a.createAndAddCollection(" My Set ")
        advanceUntilIdle()

        // Name is trimmed; the create endpoint is seeded with the current item; dialog closes on success.
        coVerify { mediaRepository.createCollection("My Set", listOf("m1")) }
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_collection_created, "My Set"))
            )
        )
        assertFalse(a.state.value.showCreateCollectionDialog)
        assertFalse(a.state.value.isAddingToCollection)
    }

    @Test
    fun `createAndAddCollection with blank name is a no-op`() = runTest {
        val a = actions(this)
        a.openCreateCollectionDialog()

        a.createAndAddCollection("   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.createCollection(any(), any()) }
        // Dialog stays open; the guard fired before any state mutation.
        assertTrue(a.state.value.showCreateCollectionDialog)
    }

    @Test
    fun `createAndAddCollection with no loaded detail is a no-op`() = runTest {
        val a = actions(this, detail = null)

        a.createAndAddCollection("Name")
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.createCollection(any(), any()) }
    }

    @Test
    fun `createAndAddCollection repository failure emits couldnt-add message`() = runTest {
        coEvery { mediaRepository.createCollection(any(), any()) } returns Result.failure(RuntimeException("server"))
        val a = actions(this)
        a.openCreateCollectionDialog()

        a.createAndAddCollection("Name")
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_couldnt_add_to_collection))
            )
        )
        // The dialog still closes on failure (the create attempt completed).
        assertFalse(a.state.value.showCreateCollectionDialog)
        assertFalse(a.state.value.isAddingToCollection)
    }

    @Test
    fun `createAndAddCollection for a series with no episodes emits no-episodes-queued and skips create`() = runTest {
        val a = actions(
            this,
            detail = seriesDetail,
            sortedEpisodes = emptyList(),
            canonicalEpisodeIds = { emptyList() },
        )
        a.openCreateCollectionDialog()

        a.createAndAddCollection("Name")
        advanceUntilIdle()

        // No seed ids → must not create an empty collection; surfaces the
        // shared no-op message and closes the dialog.
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_no_episodes_queued))
            )
        )
        coVerify(exactly = 0) { mediaRepository.createCollection(any(), any()) }
        assertFalse(a.state.value.showCreateCollectionDialog)
        assertFalse(a.state.value.isAddingToCollection)
    }
    // endregion
}
