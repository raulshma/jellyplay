package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Delegation assertion (plan 03): Favorites' mark-played is intentionally
 * silent — the paged grid keeps the user's scroll position — so the whole
 * contract is "routes through the mutator with the silent defaults". The
 * mutation protocol itself is pinned by UserDataMutatorTest in :core:data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var userDataMutator: UserDataMutator
    private lateinit var viewModel: FavoritesViewModel

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        userDataMutator = mockk(relaxed = true)
        viewModel = FavoritesViewModel(
            mediaRepository = mediaRepository,
            userDataMutator = userDataMutator,
            imageUrlProvider = mockk<ImageUrlProvider>(relaxed = true),
        )
    }

    @Test
    fun `markItemPlayed delegates to the mutator silently`() = runTest {
        val item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)

        viewModel.markItemPlayed(item, played = true)
        advanceUntilIdle()

        coVerify {
            userDataMutator.setPlayed("m1", true, UserDataMutator.FlipMode.Silent, emptyList(), null)
        }
        coVerify(exactly = 0) { mediaRepository.markPlayed(any()) }
    }
}
