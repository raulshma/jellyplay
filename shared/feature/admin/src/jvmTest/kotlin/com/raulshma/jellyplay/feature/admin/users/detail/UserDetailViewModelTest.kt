package com.raulshma.jellyplay.feature.admin.users.detail

import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.ParentalRatingOption
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.UserEditorContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_could_not_reload
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_saved_reload_failed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserDetailViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var adminRepository: AdminRepository

    private val admin = ManagedUser(id = "u-admin", name = "Alice", policy = ManagedUserPolicy(isAdministrator = true))
    private val otherAdmin = ManagedUser(id = "u-other", name = "Zed", policy = ManagedUserPolicy(isAdministrator = true))
    private val libs = listOf(LibraryFolder(id = "lib-1", name = "Movies"))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        adminRepository = mockk(relaxed = true)
    }

    private fun TestScope.loadViewModel(target: ManagedUser, allUsers: List<ManagedUser>, currentId: String = "me"): UserDetailViewModel {
        coEvery { adminRepository.getUserEditorContext(target.id) } returns Result.success(
            UserEditorContext(
                user = target,
                libraries = libs,
                currentUserId = currentId,
                adminCount = allUsers.count { it.policy.isAdministrator && !it.policy.isDisabled },
            )
        )
        val vm = UserDetailViewModel(adminRepository)
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
        coEvery { adminRepository.renameUser("u-admin", "Alice2") } returns Result.success(reloaded)
        coEvery { adminRepository.updateUserPolicy("u-admin", any()) } returns Result.success(Unit)
        coEvery { adminRepository.getManagedUser("u-admin") } returns Result.success(reloaded)

        vm.editName("Alice2")
        vm.onPolicyChange(admin.policy.copy(isHidden = true))
        vm.save()
        advanceUntilIdle()

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            adminRepository.renameUser("u-admin", "Alice2")
            adminRepository.updateUserPolicy("u-admin", any())
            adminRepository.getManagedUser("u-admin")
        }
        assertNull(vm.uiState.value.editedName)
        assertNull(vm.uiState.value.editedPolicy)
        assertEquals("Alice2", vm.uiState.value.user?.name)
    }

    @Test
    fun `partial save keeps uncommitted editedPolicy`() = runTest {
        val vm = loadViewModel(admin, listOf(admin))
        coEvery { adminRepository.renameUser("u-admin", "Alice2") } returns Result.success(admin.copy(name = "Alice2"))
        coEvery { adminRepository.updateUserPolicy("u-admin", any()) } returns Result.failure(RuntimeException("policy boom"))

        vm.editName("Alice2")
        vm.onPolicyChange(admin.policy.copy(isHidden = true))
        vm.save()
        advanceUntilIdle()

        coVerify { adminRepository.renameUser("u-admin", "Alice2") }
        coVerify { adminRepository.updateUserPolicy("u-admin", any()) }
        // rename succeeded -> cleared; policy failed -> retained for retry
        assertNull(vm.uiState.value.editedName)
        assertNotNull(vm.uiState.value.editedPolicy)
        assertNotNull(vm.uiState.value.saveError)
    }

    @Test
    fun `updatePassword independent of dirty form`() = runTest {
        val vm = loadViewModel(admin, listOf(admin))
        coEvery { adminRepository.updateUserPassword("u-admin", any()) } returns Result.success(Unit)

        vm.updatePassword("secret")
        advanceUntilIdle()

        coVerify { adminRepository.updateUserPassword("u-admin", "secret") }
        assertFalse(vm.uiState.value.isDirty) // password op does not set isDirty
        assertFalse(vm.uiState.value.showPasswordDialog) // dialog dismissed by updatePassword
    }

    @Test
    fun `deleteUser blocked when self`() = runTest {
        val vm = loadViewModel(admin, listOf(admin), currentId = "u-admin") // isSelf=true
        var calledDone = false
        vm.deleteUser(onDone = { calledDone = true })
        advanceUntilIdle()

        coVerify(exactly = 0) { adminRepository.deleteUser(any()) }
        assertFalse(calledDone)
    }

    @Test
    fun `save with reload failure sets honest message and saveError`() = runTest {
        val vm = loadViewModel(admin, listOf(admin))
        // mutation succeeds, but the post-save reload fails
        coEvery { adminRepository.renameUser("u-admin", "Alice2") } returns Result.success(admin.copy(name = "Alice2"))
        coEvery { adminRepository.updateUserPolicy("u-admin", any()) } returns Result.success(Unit)
        coEvery { adminRepository.getManagedUser("u-admin") } returns Result.failure(RuntimeException("reload boom"))

        vm.editName("Alice2")
        vm.save()
        advanceUntilIdle()

        // mutation succeeded -> edited flags cleared, isSaving false
        assertFalse(vm.uiState.value.isSaving)
        assertNull(vm.uiState.value.editedName)
        assertNull(vm.uiState.value.editedPolicy)
        // reload failed -> honest message + soft saveError; user.name stays stale
        assertNotNull(vm.uiState.value.saveError)
        assertEquals(AdminUserMessage.Resource(Res.string.admin_could_not_reload), vm.uiState.value.saveError)
        assertHonestReloadMessage(vm.uiState.value.message)
    }

    /**
     * The reload-failure path must surface the honest `admin_saved_reload_failed`
     * resource — never the stale `admin_changes_saved` one (assertion intent
     * preserved from the legacy Context-stubbed suite, now against the
     * unresolved AdminUserMessage instead of pre-resolved strings).
     */
    private fun assertHonestReloadMessage(actual: AdminUserMessage?) {
        assertNotNull(actual)
        assertEquals(AdminUserMessage.Resource(Res.string.admin_saved_reload_failed), actual)
    }

    @Test
    fun `loadAuxFor ACCESS fetches devices and channels once`() = runTest {
        val vm = loadViewModel(admin, listOf(admin))
        val devs = listOf(DeviceInfo(id = "d1", name = "Phone"))
        val chans = listOf(LiveTvChannel(id = "c1", name = "News"))
        coEvery { adminRepository.getDevices() } returns Result.success(devs)
        coEvery { adminRepository.getLiveTvChannels(limit = 500) } returns Result.success(chans)

        vm.loadAuxFor(UserEditTab.ACCESS)
        advanceUntilIdle()

        assertEquals(devs, vm.uiState.value.devices)
        assertEquals(chans, vm.uiState.value.channels)
        assertTrue(UserEditTab.ACCESS in vm.uiState.value.auxLoadedTabs)

        coVerify(exactly = 1) { adminRepository.getDevices() }
        coVerify(exactly = 1) { adminRepository.getLiveTvChannels(limit = 500) }

        // second call does not refetch
        vm.loadAuxFor(UserEditTab.ACCESS)
        advanceUntilIdle()
        coVerify(exactly = 1) { adminRepository.getDevices() }
    }

    @Test
    fun `loadAuxFor PARENTAL fetches ratings and tags`() = runTest {
        val vm = loadViewModel(admin, listOf(admin))
        val ratings = listOf(ParentalRatingOption("PG", 10, null))
        coEvery { adminRepository.getParentalRatings() } returns Result.success(ratings)
        coEvery { adminRepository.getTags(limit = 500) } returns Result.success(listOf("kids"))

        vm.loadAuxFor(UserEditTab.PARENTAL)
        advanceUntilIdle()

        assertEquals(ratings, vm.uiState.value.parentalRatings)
        assertEquals(listOf("kids"), vm.uiState.value.tags)
    }

    @Test
    fun `loadAuxFor PROFILE and ACCOUNT do nothing`() = runTest {
        val vm = loadViewModel(admin, listOf(admin))
        vm.loadAuxFor(UserEditTab.PROFILE)
        vm.loadAuxFor(UserEditTab.ACCOUNT)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.auxLoadedTabs.isEmpty())
    }

    @Test
    fun `loadAuxFor failure sets auxError without throwing`() = runTest {
        val vm = loadViewModel(admin, listOf(admin))
        coEvery { adminRepository.getDevices() } returns Result.failure(RuntimeException("boom"))
        coEvery { adminRepository.getLiveTvChannels(limit = 500) } returns Result.success(emptyList())

        vm.loadAuxFor(UserEditTab.ACCESS)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.auxError)
    }

    @Test
    fun `per-tab dirty counts track edits per tab`() = runTest {
        val vm = loadViewModel(admin, listOf(admin))
        assertEquals(0, vm.profileDirtyCount())
        assertEquals(0, vm.accessDirtyCount())
        assertEquals(0, vm.parentalDirtyCount())

        // profile change
        vm.onPolicyChange(admin.policy.copy(enableCollectionManagement = true))
        assertEquals(1, vm.profileDirtyCount())
        assertEquals(0, vm.accessDirtyCount())
        assertEquals(0, vm.parentalDirtyCount())

        // access change stacks on top
        val withProfile = vm.uiState.value.editedPolicy!!
        vm.onPolicyChange(withProfile.copy(enabledDevices = listOf("d1")))
        assertEquals(1, vm.profileDirtyCount())
        assertEquals(1, vm.accessDirtyCount())

        // name edit counts toward profile only
        vm.editName("NewName")
        assertEquals(2, vm.profileDirtyCount())
    }

    @Test
    fun `discard clears all dirty counts`() = runTest {
        val vm = loadViewModel(admin, listOf(admin))
        vm.onPolicyChange(admin.policy.copy(enableCollectionManagement = true))
        vm.editName("X")
        vm.discard()

        assertEquals(0, vm.profileDirtyCount())
        assertEquals(0, vm.accessDirtyCount())
        assertEquals(0, vm.parentalDirtyCount())
    }
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

}
