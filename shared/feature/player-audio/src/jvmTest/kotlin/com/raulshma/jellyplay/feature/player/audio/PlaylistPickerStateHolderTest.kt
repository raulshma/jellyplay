package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.model.Playlist
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Holder-level pins for [PlaylistPickerStateHolder] beyond the VM-pair
 * lifecycle tests in [AudioPlayerViewModelGapsTest]: the snapshot defaults
 * (moved here from AudioPlayerUiStateTest when the picker fields left the
 * uiState), the load-on-every-open contract, the entry-time capture of the
 * item id (a mid-add track change must not reroute the add), and the
 * deliberate dismiss-fence asymmetry (adds fenced, loads not — with a late
 * load result not resurrecting a dismissed picker).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistPickerStateHolderTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val playlistRepository: PlaylistRepository = mockk(relaxed = true)

    /** The playing-item seam, swappable per test. */
    private var itemId: String? = null

    private val holder = PlaylistPickerStateHolder(
        scope = CoroutineScope(testDispatcher),
        playlistRepository = playlistRepository,
        currentItemId = { itemId },
    )

    private fun playlist(id: String, name: String = "Playlist $id", canEdit: Boolean = true) =
        Playlist(id = id, name = name, canEdit = canEdit)

    @Test
    fun defaults_areHiddenAndIdle() {
        val state = holder.state.value
        assertFalse(state.visible)
        assertFalse(state.loading)
        assertTrue(state.playlists.isEmpty())
        assertFalse(state.adding)
        assertNull(state.message)
    }

    @Test
    fun open_withoutACurrentItem_neverFetches() {
        itemId = null

        holder.open()

        assertFalse(holder.state.value.visible)
        coVerify(exactly = 0) { playlistRepository.getPlaylists(any()) }
    }

    @Test
    fun reopening_afterDismiss_refetchesPlaylists() {
        itemId = "track-1"
        coEvery { playlistRepository.getPlaylists(any()) } returns Result.success(emptyList())
        holder.open()
        holder.dismiss()

        holder.open()

        coVerify(exactly = 2) { playlistRepository.getPlaylists(any()) }
        assertTrue(holder.state.value.visible)
    }

    @Test
    fun add_capturesTheItemIdAtEntry_soAMidAddTrackChangeCannotRerouteIt() {
        itemId = "track-1"
        val gate = CompletableDeferred<Unit>()
        coEvery { playlistRepository.addItemsToPlaylist(any(), any()) } coAnswers {
            gate.await()
            Result.success(Unit)
        }

        holder.addTo(playlist("p1", "Road Trip"))
        itemId = "track-2" // the track changed while the add was in flight

        gate.complete(Unit)

        coVerify(exactly = 1) { playlistRepository.addItemsToPlaylist("p1", listOf("track-1")) }
        val state = holder.state.value
        assertFalse(state.adding)
        assertFalse(state.visible)
        assertEquals("Road Trip", state.message)
    }

    @Test
    fun dismiss_isFencedForAdds_butNotForLoads_andALateLoadDoesNotResurrectThePicker() {
        itemId = "track-1"
        val addGate = CompletableDeferred<Unit>()
        val loadGate = CompletableDeferred<Unit>()
        coEvery { playlistRepository.getPlaylists(any()) } coAnswers {
            loadGate.await()
            Result.success(emptyList())
        }
        coEvery { playlistRepository.addItemsToPlaylist(any(), any()) } coAnswers {
            addGate.await()
            Result.success(Unit)
        }

        holder.open() // the picker is open, its load in flight on loadGate
        holder.addTo(playlist("p1"))
        holder.dismiss() // fenced: the add is in flight
        assertTrue(holder.state.value.visible)

        addGate.complete(Unit) // the successful add closes the picker itself
        assertFalse(holder.state.value.visible)

        holder.open() // a load starts, in flight
        holder.dismiss() // not fenced: loads can be dismissed away
        assertFalse(holder.state.value.visible)

        loadGate.complete(Unit) // the late fold writes loading/playlists only
        val state = holder.state.value
        assertFalse(state.visible, "the late load result must not reopen the picker")
        assertFalse(state.loading)
    }
}
