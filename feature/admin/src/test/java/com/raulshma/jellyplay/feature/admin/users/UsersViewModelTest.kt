package com.raulshma.jellyplay.feature.admin.users

import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var apiClient: JellyfinApiClient

    private val admin = ManagedUser(id = "u-admin", name = "Alice", policy = ManagedUserPolicy(isAdministrator = true))
    private val disabledAdmin = ManagedUser(id = "u-dis", name = "Bob", policy = ManagedUserPolicy(isAdministrator = true, isDisabled = true))
    private val regular = ManagedUser(id = "u-reg", name = "Cara", policy = ManagedUserPolicy(isAdministrator = false))

    @Before
    fun setUp() {
        apiClient = mockk(relaxed = true)
    }

    @Test
    fun `load sets users currentUserId and adminCount`() = runTest {
        coEvery { apiClient.getManagedUsers() } returns Result.success(listOf(admin, regular))
        coEvery { apiClient.getCurrentUserId() } returns Result.success("u-admin")

        val viewModel = UsersViewModel(apiClient)
        advanceUntilIdle()

        assertEquals(listOf(admin, regular), viewModel.state.users)
        assertEquals("u-admin", viewModel.state.currentUserId)
        assertEquals(1, viewModel.state.adminCount) // only Alice, Bob not loaded here
    }

    @Test
    fun `adminCount excludes disabled admins`() = runTest {
        coEvery { apiClient.getManagedUsers() } returns Result.success(listOf(admin, disabledAdmin))
        coEvery { apiClient.getCurrentUserId() } returns Result.success("u-admin")

        val viewModel = UsersViewModel(apiClient)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.adminCount) // disabled admin Bob not counted
    }

    @Test
    fun `createUser reloads and closes dialog`() = runTest {
        coEvery { apiClient.getManagedUsers() } returns Result.success(emptyList())
        coEvery { apiClient.getCurrentUserId() } returns Result.success("me")
        coEvery { apiClient.createUser(any(), any()) } returns Result.success(admin)

        val viewModel = UsersViewModel(apiClient)
        advanceUntilIdle()
        viewModel.showCreateDialog()
        assertTrue(viewModel.state.showCreateDialog)

        viewModel.createUser("Alice", null)
        advanceUntilIdle()

        coVerify { apiClient.createUser("Alice", null) }
        assertTrue(!viewModel.state.showCreateDialog)
    }

    @Test
    fun `deleteUser reloads and closes dialog`() = runTest {
        coEvery { apiClient.getManagedUsers() } returns Result.success(listOf(admin, regular))
        coEvery { apiClient.getCurrentUserId() } returns Result.success("me")
        coEvery { apiClient.deleteUser(any()) } returns Result.success(Unit)

        val viewModel = UsersViewModel(apiClient)
        advanceUntilIdle()
        viewModel.showDeleteDialog(regular)
        assertTrue(viewModel.state.showDeleteDialog)

        viewModel.deleteUser()
        advanceUntilIdle()

        coVerify { apiClient.deleteUser("u-reg") }
        assertTrue(!viewModel.state.showDeleteDialog)
        assertNull(viewModel.state.selectedUser)
    }

    @Test
    fun `load failure surfaces error`() = runTest {
        coEvery { apiClient.getManagedUsers() } returns Result.failure(RuntimeException("boom"))
        coEvery { apiClient.getCurrentUserId() } returns Result.success("me")

        val viewModel = UsersViewModel(apiClient)
        advanceUntilIdle()

        assertNotNull(viewModel.state.error)
        assertTrue(viewModel.state.users.isEmpty())
    }

    @Test
    fun `createUser failure keeps dialog open and sets error`() = runTest {
        coEvery { apiClient.getManagedUsers() } returns Result.success(emptyList())
        coEvery { apiClient.getCurrentUserId() } returns Result.success("me")
        coEvery { apiClient.createUser(any(), any()) } returns Result.failure(RuntimeException("name taken"))

        val viewModel = UsersViewModel(apiClient)
        advanceUntilIdle()
        viewModel.showCreateDialog()

        viewModel.createUser("Alice", null)
        advanceUntilIdle()

        // dialog stays open so user can adjust/retry
        assertTrue(viewModel.state.showCreateDialog)
        assertNotNull(viewModel.state.error)
        assertEquals("name taken", viewModel.state.error)
        // must NOT reload on failure
        coVerify(exactly = 1) { apiClient.getManagedUsers() } // only the initial load
    }
}
