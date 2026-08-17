package com.raulshma.jellyplay.feature.admin.users

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.UsersOverview
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

    private lateinit var adminRepository: AdminRepository

    private val admin = ManagedUser(id = "u-admin", name = "Alice", policy = ManagedUserPolicy(isAdministrator = true))
    private val regular = ManagedUser(id = "u-reg", name = "Cara", policy = ManagedUserPolicy(isAdministrator = false))

    @Before
    fun setUp() {
        adminRepository = mockk(relaxed = true)
    }

    private fun overview(users: List<ManagedUser>, currentId: String? = "u-admin", adminCount: Int = 1) =
        UsersOverview(users = users, currentUserId = currentId, adminCount = adminCount)

    @Test
    fun `load maps overview into state`() = runTest {
        coEvery { adminRepository.getUsersOverview() } returns
            Result.success(overview(listOf(admin, regular), currentId = "u-admin", adminCount = 1))

        val viewModel = UsersViewModel(adminRepository)
        advanceUntilIdle()

        assertEquals(listOf(admin, regular), viewModel.state.users)
        assertEquals("u-admin", viewModel.state.currentUserId)
        assertEquals(1, viewModel.state.adminCount)
    }

    @Test
    fun `createUser reloads and closes dialog`() = runTest {
        coEvery { adminRepository.getUsersOverview() } returns Result.success(overview(emptyList(), "me", 0))
        coEvery { adminRepository.createUser(any(), any()) } returns Result.success(admin)

        val viewModel = UsersViewModel(adminRepository)
        advanceUntilIdle()
        viewModel.showCreateDialog()
        assertTrue(viewModel.state.showCreateDialog)

        viewModel.createUser("Alice", null)
        advanceUntilIdle()

        coVerify { adminRepository.createUser("Alice", null) }
        assertTrue(!viewModel.state.showCreateDialog)
    }

    @Test
    fun `deleteUser reloads and closes dialog`() = runTest {
        coEvery { adminRepository.getUsersOverview() } returns
            Result.success(overview(listOf(admin, regular), "me", 1))
        coEvery { adminRepository.deleteUser(any()) } returns Result.success(Unit)

        val viewModel = UsersViewModel(adminRepository)
        advanceUntilIdle()
        viewModel.showDeleteDialog(regular)
        assertTrue(viewModel.state.showDeleteDialog)

        viewModel.deleteUser()
        advanceUntilIdle()

        coVerify { adminRepository.deleteUser("u-reg") }
        assertTrue(!viewModel.state.showDeleteDialog)
        assertNull(viewModel.state.selectedUser)
    }

    @Test
    fun `load failure surfaces error`() = runTest {
        coEvery { adminRepository.getUsersOverview() } returns Result.failure(RuntimeException("boom"))

        val viewModel = UsersViewModel(adminRepository)
        advanceUntilIdle()

        assertNotNull(viewModel.state.error)
        assertTrue(viewModel.state.users.isEmpty())
    }

    @Test
    fun `createUser failure keeps dialog open and sets error`() = runTest {
        coEvery { adminRepository.getUsersOverview() } returns Result.success(overview(emptyList(), "me", 0))
        coEvery { adminRepository.createUser(any(), any()) } returns Result.failure(RuntimeException("name taken"))

        val viewModel = UsersViewModel(adminRepository)
        advanceUntilIdle()
        viewModel.showCreateDialog()

        viewModel.createUser("Alice", null)
        advanceUntilIdle()

        // dialog stays open so user can adjust/retry
        assertTrue(viewModel.state.showCreateDialog)
        assertEquals("name taken", viewModel.state.error)
        // must NOT reload on failure
        coVerify(exactly = 1) { adminRepository.getUsersOverview() } // only the initial load
    }
}
