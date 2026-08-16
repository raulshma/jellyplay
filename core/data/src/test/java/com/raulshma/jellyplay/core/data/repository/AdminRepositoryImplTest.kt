package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminRepositoryImplTest {

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val repository = AdminRepositoryImpl(apiClient)

    private val admin = ManagedUser(id = "u-admin", name = "Alice", policy = ManagedUserPolicy(isAdministrator = true))
    private val disabledAdmin = ManagedUser(id = "u-dis", name = "Bob", policy = ManagedUserPolicy(isAdministrator = true, isDisabled = true))
    private val regular = ManagedUser(id = "u-reg", name = "Cara", policy = ManagedUserPolicy(isAdministrator = false))

    @Test
    fun `getSystemInfo passes success through`() = runTest {
        val info = SystemInfo(serverName = "Jelly", version = "10.9.11")
        coEvery { apiClient.getSystemInfo() } returns Result.success(info)

        val result = repository.getSystemInfo()

        assertTrue(result.isSuccess)
        assertEquals(info, result.getOrNull())
        coVerify(exactly = 1) { apiClient.getSystemInfo() }
    }

    @Test
    fun `getSystemInfo passes failure through`() = runTest {
        val error = Exception("server unreachable")
        coEvery { apiClient.getSystemInfo() } returns Result.failure(error)

        val result = repository.getSystemInfo()

        assertTrue(result.isFailure)
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `getUsersOverview joins users with current user id`() = runTest {
        coEvery { apiClient.getManagedUsers() } returns Result.success(listOf(admin, regular))
        coEvery { apiClient.getCurrentUserId() } returns Result.success("u-admin")

        val result = repository.getUsersOverview()

        assertTrue(result.isSuccess)
        val overview = result.getOrNull()!!
        assertEquals(listOf(admin, regular), overview.users)
        assertEquals("u-admin", overview.currentUserId)
        assertEquals(1, overview.adminCount)
    }

    @Test
    fun `getUsersOverview adminCount excludes disabled admins`() = runTest {
        coEvery { apiClient.getManagedUsers() } returns Result.success(listOf(admin, disabledAdmin, regular))
        coEvery { apiClient.getCurrentUserId() } returns Result.success("u-admin")

        val overview = repository.getUsersOverview().getOrNull()!!

        assertEquals(1, overview.adminCount)
    }

    @Test
    fun `getUsersOverview tolerates current-user-id failure`() = runTest {
        coEvery { apiClient.getManagedUsers() } returns Result.success(listOf(admin))
        coEvery { apiClient.getCurrentUserId() } returns Result.failure(Exception("no session"))

        val overview = repository.getUsersOverview().getOrNull()!!

        assertEquals(null, overview.currentUserId)
        assertEquals(listOf(admin), overview.users)
    }

    @Test
    fun `getUsersOverview fails when the user list fails`() = runTest {
        coEvery { apiClient.getManagedUsers() } returns Result.failure(Exception("403"))
        coEvery { apiClient.getCurrentUserId() } returns Result.success("u-admin")

        assertTrue(repository.getUsersOverview().isFailure)
    }

    @Test
    fun `getUserEditorContext succeeds with full join`() = runTest {
        val libs = listOf(LibraryFolder(id = "lib-1", name = "Movies"))
        coEvery { apiClient.getManagedUser("u-reg") } returns Result.success(regular)
        coEvery { apiClient.getLibraryFoldersForEditor() } returns Result.success(libs)
        coEvery { apiClient.getCurrentUserId() } returns Result.success("u-admin")
        coEvery { apiClient.getManagedUsers() } returns Result.success(listOf(admin, disabledAdmin, regular))

        val context = repository.getUserEditorContext("u-reg").getOrNull()!!

        assertEquals(regular, context.user)
        assertEquals(libs, context.libraries)
        assertEquals("u-admin", context.currentUserId)
        assertEquals(1, context.adminCount)
    }

    @Test
    fun `getUserEditorContext degrades libraries and me on partial failure`() = runTest {
        coEvery { apiClient.getManagedUser("u-reg") } returns Result.success(regular)
        coEvery { apiClient.getLibraryFoldersForEditor() } returns Result.failure(Exception("libs boom"))
        coEvery { apiClient.getCurrentUserId() } returns Result.failure(Exception("no session"))
        coEvery { apiClient.getManagedUsers() } returns Result.failure(Exception("users boom"))

        val context = repository.getUserEditorContext("u-reg").getOrNull()!!

        assertEquals(regular, context.user)
        assertEquals(emptyList<LibraryFolder>(), context.libraries)
        assertEquals(null, context.currentUserId)
        assertEquals(0, context.adminCount)
    }

    @Test
    fun `getUserEditorContext fails when the target user fails`() = runTest {
        coEvery { apiClient.getManagedUser("missing") } returns Result.failure(Exception("404"))
        coEvery { apiClient.getLibraryFoldersForEditor() } returns Result.success(emptyList())
        coEvery { apiClient.getCurrentUserId() } returns Result.success("u-admin")
        coEvery { apiClient.getManagedUsers() } returns Result.success(emptyList())

        assertTrue(repository.getUserEditorContext("missing").isFailure)
    }

    @Test
    fun `user mutations delegate to the client`() = runTest {
        coEvery { apiClient.createUser("Dave", null) } returns Result.success(regular)
        coEvery { apiClient.renameUser("u-reg", "Dave2") } returns Result.success(regular.copy(name = "Dave2"))
        coEvery { apiClient.updateUserPolicy("u-reg", any()) } returns Result.success(Unit)
        coEvery { apiClient.updateUserPassword("u-reg", "pw") } returns Result.success(Unit)
        coEvery { apiClient.deleteUser("u-reg") } returns Result.success(Unit)

        repository.createUser("Dave", null)
        repository.renameUser("u-reg", "Dave2")
        repository.updateUserPolicy("u-reg", ManagedUserPolicy())
        repository.updateUserPassword("u-reg", "pw")
        repository.deleteUser("u-reg")

        coVerify(exactly = 1) { apiClient.createUser("Dave", null) }
        coVerify(exactly = 1) { apiClient.renameUser("u-reg", "Dave2") }
        coVerify(exactly = 1) { apiClient.updateUserPolicy("u-reg", any()) }
        coVerify(exactly = 1) { apiClient.updateUserPassword("u-reg", "pw") }
        coVerify(exactly = 1) { apiClient.deleteUser("u-reg") }
    }
}
