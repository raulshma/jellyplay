package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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

class PlaylistActionsTest {

    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val appRuntimeStateStore: AppRuntimeStateStore = mockk(relaxed = true)
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
    ): PlaylistActions {
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
        return PlaylistActions(
            scope = scope,
            session = session,
            messages = messages.flow,
            strings = strings,
            mediaRepository = mediaRepository,
            appRuntimeStateStore = appRuntimeStateStore,
            mediaDetailProvider = mediaDetailProvider,
        )
    }

    // region openPlaylistPicker
    @Test
    fun `openPlaylistPicker with a movie sets showPlaylistPicker and loads editable playlists`() = runTest {
        val editable = Playlist(id = "p1", name = "Favs", canEdit = true)
        val readOnly = Playlist(id = "p2", name = "Read Only", canEdit = false)
        coEvery { mediaRepository.getPlaylists(any()) } returns Result.success(listOf(editable, readOnly))
        val a = actions(this)

        a.openPlaylistPicker()
        advanceUntilIdle()

        val state = a.state.value
        assertTrue(state.showPlaylistPicker)
        assertFalse(state.isLoadingPlaylists)
        assertEquals(listOf(editable), state.playlists)
    }

    @Test
    fun `openPlaylistPicker with an audio detail is a no-op`() = runTest {
        coEvery { mediaRepository.getPlaylists(any()) } returns Result.success(emptyList())
        val a = actions(this, detail = audioDetail)

        a.openPlaylistPicker()
        advanceUntilIdle()

        assertFalse(a.state.value.showPlaylistPicker)
        assertTrue(a.state.value.playlists.isEmpty())
        // The audio type is ineligible, so the list should never be fetched.
        coVerify(exactly = 0) { mediaRepository.getPlaylists(any()) }
    }
    // endregion

    // region addToPlaylist
    @Test
    fun `addToPlaylist success emits the added-to-playlist message`() = runTest {
        coEvery { mediaRepository.addItemsToPlaylist(any(), any()) } returns Result.success(Unit)
        val playlist = Playlist(id = "p1", name = "Favs", canEdit = true)
        val a = actions(this)

        a.addToPlaylist(playlist)
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_added_to_playlist, playlist.name))
            )
        )
        assertFalse(a.state.value.isAddingToPlaylist)
        assertFalse(a.state.value.showPlaylistPicker)
        coVerify { mediaRepository.addItemsToPlaylist("p1", listOf("m1")) }
    }

    @Test
    fun `addToPlaylist for a series with no episodes emits the no-episodes-queued message`() = runTest {
        val playlist = Playlist(id = "p1", name = "Favs", canEdit = true)
        // Series with no sorted episodes and no canonical ids resolves to empty.
        val a = actions(
            this,
            detail = seriesDetail,
            sortedEpisodes = emptyList(),
            canonicalEpisodeIds = { emptyList() },
        )

        a.addToPlaylist(playlist)
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_no_episodes_queued))
            )
        )
        // Nothing should be queued against an empty resolution.
        coVerify(exactly = 0) { mediaRepository.addItemsToPlaylist(any(), any()) }
        assertFalse(a.state.value.isAddingToPlaylist)
    }
    // endregion

    // region openPlaylistPicker — eligibility + dialog toggles
    @Test
    fun `openPlaylistPicker with a series sets showPlaylistPicker and loads playlists`() = runTest {
        val editable = Playlist(id = "p1", name = "Favs", canEdit = true)
        coEvery { mediaRepository.getPlaylists(any()) } returns Result.success(listOf(editable))
        val a = actions(this, detail = seriesDetail)

        a.openPlaylistPicker()
        advanceUntilIdle()

        assertTrue(a.state.value.showPlaylistPicker)
        assertEquals(listOf(editable), a.state.value.playlists)
    }

    @Test
    fun `openPlaylistPicker with no loaded detail is a no-op`() = runTest {
        val a = actions(this, detail = null)

        a.openPlaylistPicker()
        advanceUntilIdle()

        assertFalse(a.state.value.showPlaylistPicker)
        coVerify(exactly = 0) { mediaRepository.getPlaylists(any()) }
    }

    @Test
    fun `dismissPlaylistPicker clears the picker flag`() = runTest {
        coEvery { mediaRepository.getPlaylists(any()) } returns Result.success(emptyList())
        val a = actions(this)
        a.openPlaylistPicker()
        advanceUntilIdle()
        assertTrue(a.state.value.showPlaylistPicker)

        a.dismissPlaylistPicker()

        assertFalse(a.state.value.showPlaylistPicker)
    }

    @Test
    fun `openCreatePlaylistDialog closes the picker and opens the dialog`() = runTest {
        coEvery { mediaRepository.getPlaylists(any()) } returns Result.success(emptyList())
        val a = actions(this)
        a.openPlaylistPicker()
        advanceUntilIdle()

        a.openCreatePlaylistDialog()

        // The picker and create-dialog are mutually exclusive.
        assertFalse(a.state.value.showPlaylistPicker)
        assertTrue(a.state.value.showCreatePlaylistDialog)
    }

    @Test
    fun `dismissCreatePlaylistDialog closes the dialog`() = runTest {
        val a = actions(this)
        a.openCreatePlaylistDialog()
        assertTrue(a.state.value.showCreatePlaylistDialog)

        a.dismissCreatePlaylistDialog()

        assertFalse(a.state.value.showCreatePlaylistDialog)
    }
    // endregion

    // region addToPlaylist — series id resolution precedence
    @Test
    fun `addToPlaylist for a series resolves ids from sorted episodes over a cold load`() = runTest {
        coEvery { mediaRepository.addItemsToPlaylist(any(), any()) } returns Result.success(Unit)
        val playlist = Playlist(id = "p1", name = "Favs", canEdit = true)
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

        a.addToPlaylist(playlist)
        advanceUntilIdle()

        // Sorted episodes win; the cold-load fallback must not fire.
        coVerify { mediaRepository.addItemsToPlaylist("p1", listOf("ep1", "ep2")) }
        assertEquals(0, coldLoads)
    }

    @Test
    fun `addToPlaylist for a series falls back to canonicalEpisodeIds when the snapshot is empty`() = runTest {
        coEvery { mediaRepository.addItemsToPlaylist(any(), any()) } returns Result.success(Unit)
        val playlist = Playlist(id = "p1", name = "Favs", canEdit = true)
        val a = actions(
            this,
            detail = seriesDetail,
            sortedEpisodes = emptyList(),
            canonicalEpisodeIds = { listOf("cold-1", "cold-2") },
        )

        a.addToPlaylist(playlist)
        advanceUntilIdle()

        coVerify { mediaRepository.addItemsToPlaylist("p1", listOf("cold-1", "cold-2")) }
    }

    @Test
    fun `addToPlaylist repository failure emits couldnt-add message`() = runTest {
        coEvery { mediaRepository.addItemsToPlaylist(any(), any()) } returns Result.failure(RuntimeException("server"))
        val a = actions(this)

        a.addToPlaylist(Playlist(id = "p1", name = "Favs", canEdit = true))
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_couldnt_add_to_playlist))
            )
        )
        assertFalse(a.state.value.isAddingToPlaylist)
        assertFalse(a.state.value.showPlaylistPicker)
    }
    // endregion

    // region addToWatchLater — cached id reuse + first-use creation
    @Test
    fun `addToWatchLater with cached id reuses it and adds the items`() = runTest {
        every { appRuntimeStateStore.state } returns
            MutableStateFlow(AppRuntimeState(watchLaterPlaylistId = "wl-1"))
        coEvery { mediaRepository.addItemsToPlaylist(any(), any()) } returns Result.success(Unit)
        val a = actions(this)

        a.addToWatchLater()
        advanceUntilIdle()

        // Reuses the cached Watch Later id; never creates a new playlist.
        coVerify { mediaRepository.addItemsToPlaylist("wl-1", listOf("m1")) }
        coVerify(exactly = 0) { mediaRepository.createPlaylist(any(), any(), any(), any()) }
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_added_to_watch_later))
            )
        )
    }

    @Test
    fun `addToWatchLater without cached id creates the playlist and persists its id`() = runTest {
        every { appRuntimeStateStore.state } returns
            MutableStateFlow(AppRuntimeState(watchLaterPlaylistId = null))
        coEvery {
            mediaRepository.createPlaylist(any(), any(), any(), any())
        } returns Result.success("wl-new")

        val a = actions(this)
        a.addToWatchLater()
        advanceUntilIdle()

        // Creates the Watch Later playlist seeded with the item and caches its id.
        coVerify {
            mediaRepository.createPlaylist(
                strings.get(R.string.detail_playlist_watch_later),
                null,
                listOf("m1"),
                MediaType.MOVIE,
            )
        }
        coVerify { appRuntimeStateStore.setWatchLaterPlaylistId("wl-new") }
        coVerify(exactly = 0) { mediaRepository.addItemsToPlaylist(any(), any()) }
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_added_to_watch_later))
            )
        )
    }

    @Test
    fun `addToWatchLater with no resolvable ids emits no-episodes-queued`() = runTest {
        every { appRuntimeStateStore.state } returns
            MutableStateFlow(AppRuntimeState(watchLaterPlaylistId = "wl-1"))
        val a = actions(
            this,
            detail = seriesDetail,
            sortedEpisodes = emptyList(),
            canonicalEpisodeIds = { emptyList() },
        )

        a.addToWatchLater()
        advanceUntilIdle()

        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_no_episodes_queued))
            )
        )
        coVerify(exactly = 0) { mediaRepository.addItemsToPlaylist(any(), any()) }
    }
    // endregion

    // region createAndAddPlaylist
    @Test
    fun `createAndAddPlaylist success creates the playlist and closes the dialog`() = runTest {
        coEvery {
            mediaRepository.createPlaylist(any(), any(), any(), any())
        } returns Result.success("pl-new")
        val a = actions(this)
        a.openCreatePlaylistDialog()
        assertTrue(a.state.value.showCreatePlaylistDialog)

        a.createAndAddPlaylist(" My List ", "overview text")
        advanceUntilIdle()

        // Name is trimmed; overview passes through; dialog closes on success.
        coVerify {
            mediaRepository.createPlaylist("My List", "overview text", listOf("m1"), MediaType.MOVIE)
        }
        assertTrue(
            messages.recorded.contains(
                DetailMessage.Text(strings.get(R.string.detail_msg_playlist_created, "My List"))
            )
        )
        assertFalse(a.state.value.showCreatePlaylistDialog)
        assertFalse(a.state.value.isAddingToPlaylist)
    }

    @Test
    fun `createAndAddPlaylist with blank name is a no-op`() = runTest {
        val a = actions(this)
        a.openCreatePlaylistDialog()

        a.createAndAddPlaylist("   ", "")
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.createPlaylist(any(), any(), any(), any()) }
        // Dialog stays open; the guard fired before any state mutation.
        assertTrue(a.state.value.showCreatePlaylistDialog)
    }

    @Test
    fun `createAndAddPlaylist with no loaded detail is a no-op`() = runTest {
        val a = actions(this, detail = null)

        a.createAndAddPlaylist("Name", "")
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.createPlaylist(any(), any(), any(), any()) }
    }

    @Test
    fun `createAndAddPlaylist with blank overview passes null overview`() = runTest {
        coEvery { mediaRepository.createPlaylist(any(), any(), any(), any()) } returns Result.success("pl-new")
        val a = actions(this)

        a.createAndAddPlaylist("Name", "   ")
        advanceUntilIdle()

        // A blank overview is normalized to null.
        coVerify { mediaRepository.createPlaylist("Name", null, listOf("m1"), MediaType.MOVIE) }
    }
    // endregion
}
