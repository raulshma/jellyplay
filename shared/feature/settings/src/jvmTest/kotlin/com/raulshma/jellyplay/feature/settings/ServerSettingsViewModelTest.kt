package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.UserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Pins the Server (user switcher) settings wiring (LibraryLayout jvmTest
 * pattern: mockk collaborators + real [MutableStateFlow] stubs + inlined
 * Main-dispatcher rule): the on-screen user list mirrors the repository's
 * `currentServerUsers` flow (and clears the loading flag on the first
 * emission), `currentUser` is the subscribed share of the repository flow,
 * and switch/remove route to the auth repository with switchUser invoking its
 * completion callback only after the switch succeeds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerSettingsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var authRepository: AuthRepository
    private val usersState =
        MutableStateFlow(listOf(UserInfo(id = "u1", name = "Alice", serverAddress = "https://jelly.example", accessToken = "t1")))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        authRepository = mockk(relaxed = true)
        every { authRepository.currentUser } returns MutableStateFlow(
            UserInfo(id = "u1", name = "Alice", serverAddress = "https://jelly.example", accessToken = "t1")
        )
        every { authRepository.currentServerUsers } returns usersState
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ServerSettingsViewModel(authRepository)

    @Test
    fun `currentServerUsers mirrors the repository flow and clears loading`() = runTest {
        usersState.value = listOf(
            UserInfo(id = "u1", name = "Alice", serverAddress = "https://jelly.example", accessToken = "t1"),
            UserInfo(id = "u2", name = "Bob", serverAddress = "https://jelly.example", accessToken = "t2"),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("u1", "u2"), viewModel.currentServerUsers.value.map { it.id })
        assertFalse(viewModel.isLoadingUsers.value, "first emission must settle the loading flag")
    }

    @Test
    fun `currentUser exposes the signed-in user once subscribed`() = runTest {
        val viewModel = viewModel()

        backgroundScope.launch { viewModel.currentUser.collect {} }
        advanceUntilIdle()

        assertEquals("u1", viewModel.currentUser.value?.id)
    }

    @Test
    fun `switchUser calls the repository then the completion callback`() = runTest {
        coEvery { authRepository.switchUser("u2") } returns Result.success(Unit)
        val viewModel = viewModel()

        var completed = false
        viewModel.switchUser("u2") { completed = true }
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.switchUser("u2") }
        assertEquals(true, completed)
    }

    @Test
    fun `removeUser routes to the repository`() = runTest {
        coEvery { authRepository.removeUser("u2") } returns Unit
        val viewModel = viewModel()

        viewModel.removeUser("u2")
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.removeUser("u2") }
    }
}
