package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
import com.raulshma.jellyplay.core.model.IdentifyItemType
import com.raulshma.jellyplay.core.model.IdentifyResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the Identify half of [MetadataAdminActions] (the refresh half lives
 * in [MetadataAdminActionsTest]): prefill, search, apply-with-reload, and the
 * failure paths.
 */
class IdentifyActionsTest {

    private val editorRepository: MetadataEditorRepository = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk()
    private val strings = fakeDetailStrings()
    private val messages = RecordingMessages()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        messages.reset()
    }

    private fun actions(
        scope: kotlinx.coroutines.CoroutineScope? = null,
        session: MutableStateFlow<DetailSession?> = MutableStateFlow(null),
    ): MetadataAdminActions {
        every { authRepository.currentUser } returns flowOf(null)
        return MetadataAdminActions(
            scope = scope ?: kotlinx.coroutines.test.TestScope(),
            session = session,
            messages = messages.flow,
            strings = strings,
            editorRepository = editorRepository,
            mediaRepository = mockk<MediaRepository>(relaxed = true),
            authRepository = authRepository,
        )
    }

    private fun seriesSession(): MutableStateFlow<DetailSession?> = MutableStateFlow(
        DetailSession(
            itemId = "s1",
            detail = MediaDetail(
                item = MediaItem(id = "s1", name = "Wr0ng N4me", mediaType = MediaType.SERIES, year = 1999),
                providerIds = mapOf("tvdb" to "121361"),
            ),
        ),
    )

    @Test
    fun `openIdentifyScreenItem prefills name year and provider ids from the session`() = runTest {
        val actions = actions(this, seriesSession())
        actions.openIdentifyScreenItem()

        val query = actions.identifyState.value.query
        assertTrue(query != null && query.itemType == IdentifyItemType.SERIES)
        assertEquals("Wr0ng N4me", query.name)
        assertEquals(1999, query.year)
        assertEquals(mapOf("tvdb" to "121361"), query.providerIds)
    }

    @Test
    fun `openIdentifyScreenItem ignores non-Series non-Movie items`() = runTest {
        val session: MutableStateFlow<DetailSession?> = MutableStateFlow(
            DetailSession(
                itemId = "b1",
                detail = MediaDetail(item = MediaItem(id = "b1", name = "Book", mediaType = MediaType.BOOK)),
            ),
        )
        val actions = actions(this, session)
        actions.openIdentifyScreenItem()

        assertNull(actions.identifyState.value.query)
    }

    @Test
    fun `searchIdentify stores results and flips hasSearched`() = runTest {
        val results = listOf(IdentifyResult(name = "The Real Show", year = 2005))
        coEvery { editorRepository.identifyRemoteSearch(any()) } returns Result.success(results)

        val actions = actions(this, seriesSession())
        actions.openIdentifyScreenItem()
        actions.searchIdentify()
        advanceUntilIdle()

        assertTrue(actions.identifyState.value.hasSearched)
        assertEquals(results, actions.identifyState.value.results)
        assertFalse(actions.identifyState.value.isSearching)
    }

    @Test
    fun `applyIdentify writes server-side then closes and bumps appliedCount`() = runTest {
        coEvery { editorRepository.applyIdentifyResult(any(), any(), any()) } returns Result.success(Unit)

        val actions = actions(this, seriesSession())
        actions.openIdentifyScreenItem()
        actions.applyIdentify(IdentifyResult(name = "The Real Show", year = 2005), replaceAllImages = true)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            editorRepository.applyIdentifyResult("s1", IdentifyResult(name = "The Real Show", year = 2005), true)
        }
        assertNull(actions.identifyState.value.query, "the sheet must close after a successful apply")
        assertEquals(1, actions.identifyState.value.appliedCount)
    }

    @Test
    fun `appliedCount is monotonic across dismiss and sheet re-opens`() = runTest {
        coEvery { editorRepository.applyIdentifyResult(any(), any(), any()) } returns Result.success(Unit)

        val actions = actions(this, seriesSession())
        actions.openIdentifyScreenItem()
        actions.applyIdentify(IdentifyResult(name = "A"), replaceAllImages = false)
        advanceUntilIdle()
        actions.openIdentifyScreenItem() // re-open must not zero the counter
        assertEquals(1, actions.identifyState.value.appliedCount)
        actions.applyIdentify(IdentifyResult(name = "B"), replaceAllImages = false)
        advanceUntilIdle()

        // The screen reloads on each counter bump; equal consecutive values
        // would silently skip the second reload.
        assertEquals(2, actions.identifyState.value.appliedCount, "each apply must bump past every prior value")
    }

    @Test
    fun `applyIdentify failure keeps the sheet open with no appliedCount bump`() = runTest {
        coEvery { editorRepository.applyIdentifyResult(any(), any(), any()) } returns
            Result.failure(IllegalStateException("boom"))

        val actions = actions(this, seriesSession())
        actions.openIdentifyScreenItem()
        actions.applyIdentify(IdentifyResult(name = "x"), replaceAllImages = false)
        advanceUntilIdle()

        assertTrue(actions.identifyState.value.query != null)
        assertEquals(0, actions.identifyState.value.appliedCount)
        assertEquals(1, messages.recorded.size, "failure must surface a message")
    }

    @Test
    fun `dismissIdentify clears the sheet state`() = runTest {
        val actions = actions(this, seriesSession())
        actions.openIdentifyScreenItem()
        actions.dismissIdentify()

        assertNull(actions.identifyState.value.query)
        assertFalse(actions.identifyState.value.hasSearched)
    }
}
