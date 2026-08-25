package com.raulshma.jellyplay.feature.auth

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.feature.auth.generated.resources.Res
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_check_availability
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_not_enabled
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_timeout
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Port of the legacy :feature:auth AuthViewModelTest (junit4 + mockk, kept on
 * the jvmTest source set — the conveyor test home). The two assertions that
 * checked user-facing copy via stubbed `context.getString` now assert exact
 * [AuthMessage.Resource] identity against the generated accessors (admin
 * UserDetailViewModel test precedent): the seal carries the resource
 * unresolved, so the test compares `Res.string` objects instead of resolved
 * text — stronger than the legacy contains-check ("not enabled" / "timed
 * out") because it also pins the fallback-vs-exception-message choice.
 */
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
        viewModel = AuthViewModel(authRepository)
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
        assertEquals(
            AuthMessage.Resource(Res.string.auth_qc_error_not_enabled),
            (state as QuickConnectUiState.Error).message,
        )
    }

    @Test
    fun quickConnect_enabledCheckFails_returnsErrorWithRawExceptionMessage() = runTest(testDispatcher) {
        coEvery { authRepository.isQuickConnectEnabled() } returns Result.failure(RuntimeException("boom"))

        viewModel.startQuickConnect("http://server")

        advanceUntilIdle()
        val state = viewModel.quickConnectState.value
        assertTrue("expected Error, was $state", state is QuickConnectUiState.Error)
        // HEAD fallback semantics: a present exception message wins over the
        // localized resource (legacy `?:` — the string was only a fallback).
        assertEquals(
            AuthMessage.Raw("boom"),
            (state as QuickConnectUiState.Error).message,
        )
    }

    @Test
    fun quickConnect_enabledCheckFails_withoutMessage_usesResourceFallback() = runTest(testDispatcher) {
        coEvery { authRepository.isQuickConnectEnabled() } returns Result.failure(RuntimeException())

        viewModel.startQuickConnect("http://server")

        advanceUntilIdle()
        val state = viewModel.quickConnectState.value
        assertTrue(state is QuickConnectUiState.Error)
        assertEquals(
            AuthMessage.Resource(Res.string.auth_qc_error_check_availability),
            (state as QuickConnectUiState.Error).message,
        )
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
        assertEquals(
            AuthMessage.Resource(Res.string.auth_qc_error_timeout),
            (state as QuickConnectUiState.Error).message,
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
