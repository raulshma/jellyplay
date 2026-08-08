package com.raulshma.jellyplay.feature.auth

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.UserInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk()
        every { authRepository.servers } returns MutableStateFlow(emptyList())
        every { authRepository.currentServerUsers } returns MutableStateFlow(emptyList())
        val appContext = mockk<Context>()
        every { appContext.getString(any()) } returns ""
        // The disabled + timeout assertions check the exact user-facing copy, so
        // route those two keys to the real strings.xml values.
        every { appContext.getString(R.string.auth_qc_error_not_enabled) } returns "Quick Connect is not enabled on this server"
        every { appContext.getString(R.string.auth_qc_error_timeout) } returns "Quick Connect timed out. Please try again."
        val apiClient = mockk<JellyfinApiClient>()
        viewModel = AuthViewModel(authRepository, apiClient, appContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun quickConnect_disabled_returnsError() = runTest(testDispatcher) {
        coEvery { authRepository.isQuickConnectEnabled() } returns Result.success(false)

        viewModel.startQuickConnect("http://server")

        advanceUntilIdle()
        val state = viewModel.quickConnectState.value
        assertTrue("expected Error, was $state", state is QuickConnectUiState.Error)
        assertTrue(
            (state as QuickConnectUiState.Error).message.contains("not enabled", ignoreCase = true)
        )
    }

    @Test
    fun quickConnect_enabledCheckFails_returnsError() = runTest(testDispatcher) {
        coEvery { authRepository.isQuickConnectEnabled() } returns Result.failure(RuntimeException("boom"))

        viewModel.startQuickConnect("http://server")

        advanceUntilIdle()
        assertTrue(viewModel.quickConnectState.value is QuickConnectUiState.Error)
    }

    @Test
    fun quickConnect_initiateFails_returnsError() = runTest(testDispatcher) {
        coEvery { authRepository.isQuickConnectEnabled() } returns Result.success(true)
        coEvery { authRepository.initiateQuickConnect() } returns Result.failure(RuntimeException("no auth"))

        viewModel.startQuickConnect("http://server")

        advanceUntilIdle()
        val state = viewModel.quickConnectState.value
        assertTrue("expected Error, was $state", state is QuickConnectUiState.Error)
    }

    @Test
    fun quickConnect_pollAuthenticated_completesSuccess() = runTest(testDispatcher) {
        coEvery { authRepository.isQuickConnectEnabled() } returns Result.success(true)
        coEvery { authRepository.initiateQuickConnect() } returns Result.success(
            QuickConnectInfo(secret = "secret", code = "CODE")
        )
        coEvery { authRepository.pollQuickConnect("secret") } returns Result.success(
            QuickConnectState(authenticated = true, secret = "secret")
        )
        coEvery { authRepository.loginWithQuickConnect("http://server", "secret") } returns
            Result.success(mockk<UserInfo>())

        viewModel.startQuickConnect("http://server")

        advanceUntilIdle()
        assertTrue(
            "expected Success, was ${viewModel.quickConnectState.value}",
            viewModel.quickConnectState.value is QuickConnectUiState.Success
        )
    }

    @Test
    fun quickConnect_pollNeverAuthenticated_timesOut() = runTest(testDispatcher) {
        coEvery { authRepository.isQuickConnectEnabled() } returns Result.success(true)
        coEvery { authRepository.initiateQuickConnect() } returns Result.success(
            QuickConnectInfo(secret = "secret", code = "CODE")
        )
        coEvery { authRepository.pollQuickConnect("secret") } returns Result.success(
            QuickConnectState(authenticated = false, secret = "secret")
        )

        viewModel.startQuickConnect("http://server")

        advanceUntilIdle()
        val state = viewModel.quickConnectState.value
        assertTrue("expected Error, was $state", state is QuickConnectUiState.Error)
        assertTrue(
            (state as QuickConnectUiState.Error).message.contains("timed out", ignoreCase = true)
        )
    }

    @Test
    fun quickConnect_pollFails_returnsError() = runTest(testDispatcher) {
        coEvery { authRepository.isQuickConnectEnabled() } returns Result.success(true)
        coEvery { authRepository.initiateQuickConnect() } returns Result.success(
            QuickConnectInfo(secret = "secret", code = "CODE")
        )
        coEvery { authRepository.pollQuickConnect("secret") } returns Result.failure(RuntimeException("network"))

        viewModel.startQuickConnect("http://server")

        advanceUntilIdle()
        assertTrue(viewModel.quickConnectState.value is QuickConnectUiState.Error)
    }

    @Test
    fun cancelQuickConnect_resetsToIdle() = runTest(testDispatcher) {
        coEvery { authRepository.isQuickConnectEnabled() } returns Result.success(true)
        coEvery { authRepository.initiateQuickConnect() } returns Result.success(
            QuickConnectInfo(secret = "secret", code = "CODE")
        )
        coEvery { authRepository.pollQuickConnect("secret") } returns Result.success(
            QuickConnectState(authenticated = false, secret = "secret")
        )

        viewModel.startQuickConnect("http://server")
        advanceUntilIdle()

        viewModel.cancelQuickConnect()

        assertTrue(
            "expected Idle, was ${viewModel.quickConnectState.value}",
            viewModel.quickConnectState.value is QuickConnectUiState.Idle
        )
    }
}
