package com.raulshma.jellyplay.feature.admin.users.detail

import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var apiClient: JellyfinApiClient

    private val admin = ManagedUser(id = "u-admin", name = "Alice", policy = ManagedUserPolicy(isAdministrator = true))
    private val otherAdmin = ManagedUser(id = "u-other", name = "Zed", policy = ManagedUserPolicy(isAdministrator = true))
    private val libs = listOf(LibraryFolder(id = "lib-1", name = "Movies"))

    @Before
    fun setUp() {
        apiClient = mockk(relaxed = true)
    }

    private fun TestScope.loadViewModel(target: ManagedUser, allUsers: List<ManagedUser>, currentId: String = "me"): UserDetailViewModel {
        coEvery { apiClient.getManagedUser(target.id) } returns Result.success(target)
        coEvery { apiClient.getLibraryFoldersForEditor() } returns Result.success(libs)
        coEvery { apiClient.getCurrentUserId() } returns Result.success(currentId)
        coEvery { apiClient.getManagedUsers() } returns Result.success(allUsers)
        val vm = UserDetailViewModel(apiClient)
        vm.loadUser(target.id)
        advanceUntilIdle() // loadUser launches a coroutine; let it complete so the loaded state is ready
        return vm
    }

    @Test
    fun `load sets user libraries isSelf isLastAdmin`() = runTest {
        val vm = loadViewModel(admin, listOf(admin), currentId = "u-admin")

        assertEquals(admin, vm.uiState.value.user)
        assertEquals(libs, vm.uiState.value.libraries)
        assertTrue(vm.uiState.value.isSelf)
        assertTrue(vm.uiState.value.isLastAdmin)
    }

    @Test
    fun `isLastAdmin true when target is sole active admin`() = runTest {
        val vm = loadViewModel(admin, listOf(admin))
        assertTrue(vm.uiState.value.isLastAdmin)
    }

    @Test
    fun `isLastAdmin false with two admins`() = runTest {
        val vm = loadViewModel(admin, listOf(admin, otherAdmin))
        assertFalse(vm.uiState.value.isLastAdmin)
    }

    @Test
    fun `editPolicy dirties state discard clears`() = runTest {
        val vm = loadViewModel(admin, listOf(admin))

        assertFalse(vm.uiState.value.isDirty)

        vm.onPolicyChange(admin.policy.copy(isHidden = true))

        assertTrue(vm.uiState.value.isDirty)
        assertNotNull(vm.uiState.value.editedPolicy)
        assertTrue(vm.uiState.value.editedPolicy!!.isHidden)

        vm.discard()

        assertFalse(vm.uiState.value.isDirty)
        assertNull(vm.uiState.value.editedPolicy)
    }

    @Test
    fun `save commits rename then policy and reloads`() = runTest {
        val vm = loadViewModel(admin, listOf(admin))
        val reloaded = admin.copy(name = "Alice2")
        coEvery { apiClient.renameUser("u-admin", "Alice2") } returns Result.success(reloaded)
        coEvery { apiClient.updateUserPolicy("u-admin", any()) } returns Result.success(Unit)
        coEvery { apiClient.getManagedUser("u-admin") } returns Result.success(reloaded)

        vm.editName("Alice2")
        vm.onPolicyChange(admin.policy.copy(isHidden = true))
        vm.save()
        advanceUntilIdle()

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            apiClient.renameUser("u-admin", "Alice2")
            apiClient.updateUserPolicy("u-admin", any())
            apiClient.getManagedUser("u-admin")
        }
        assertNull(vm.uiState.value.editedName)
        assertNull(vm.uiState.value.editedPolicy)
        assertEquals("Alice2", vm.uiState.value.user?.name)
    }

    @Test
    fun `partial save keeps uncommitted editedPolicy`() = runTest {
        val vm = loadViewModel(admin, listOf(admin))
        coEvery { apiClient.renameUser("u-admin", "Alice2") } returns Result.success(admin.copy(name = "Alice2"))
        coEvery { apiClient.updateUserPolicy("u-admin", any()) } returns Result.failure(RuntimeException("policy boom"))

        vm.editName("Alice2")
        vm.onPolicyChange(admin.policy.copy(isHidden = true))
        vm.save()
        advanceUntilIdle()

        coVerify { apiClient.renameUser("u-admin", "Alice2") }
        coVerify { apiClient.updateUserPolicy("u-admin", any()) }
        // rename succeeded -> cleared; policy failed -> retained for retry
        assertNull(vm.uiState.value.editedName)
        assertNotNull(vm.uiState.value.editedPolicy)
        assertNotNull(vm.uiState.value.saveError)
    }

    @Test
    fun `updatePassword independent of dirty form`() = runTest {
        val vm = loadViewModel(admin, listOf(admin))
        coEvery { apiClient.updateUserPassword("u-admin", any()) } returns Result.success(Unit)

        vm.updatePassword("secret")
        advanceUntilIdle()

        coVerify { apiClient.updateUserPassword("u-admin", "secret") }
        assertFalse(vm.uiState.value.isDirty) // password op does not set isDirty
        assertFalse(vm.uiState.value.showPasswordDialog) // dialog dismissed by updatePassword
    }

    @Test
    fun `deleteUser blocked when self`() = runTest {
        val vm = loadViewModel(admin, listOf(admin), currentId = "u-admin") // isSelf=true
        var calledDone = false
        vm.deleteUser(onDone = { calledDone = true })
        advanceUntilIdle()

        coVerify(exactly = 0) { apiClient.deleteUser(any()) }
        assertFalse(calledDone)
    }

    @Test
    fun `save with reload failure sets honest message and saveError`() = runTest {
        val vm = loadViewModel(admin, listOf(admin))
        // mutation succeeds, but the post-save reload fails
        coEvery { apiClient.renameUser("u-admin", "Alice2") } returns Result.success(admin.copy(name = "Alice2"))
        coEvery { apiClient.updateUserPolicy("u-admin", any()) } returns Result.success(Unit)
        coEvery { apiClient.getManagedUser("u-admin") } returns Result.failure(RuntimeException("reload boom"))

        vm.editName("Alice2")
        vm.save()
        advanceUntilIdle()

        // mutation succeeded -> edited flags cleared, isSaving false
        assertFalse(vm.uiState.value.isSaving)
        assertNull(vm.uiState.value.editedName)
        assertNull(vm.uiState.value.editedPolicy)
        // reload failed -> honest message + soft saveError; user.name stays stale
        assertNotNull(vm.uiState.value.saveError)
        assertEquals("Could not reload updated user", vm.uiState.value.saveError)
        assertNotStaleMessage(vm.uiState.value.message, "Changes saved")
    }

    private fun assertNotStaleMessage(actual: String?, disallowed: String) {
        assertNotNull(actual)
        assertTrue("message must not lie with '$disallowed'", actual != disallowed)
        assertTrue(actual!!.contains("reload", ignoreCase = true))
    }
}
