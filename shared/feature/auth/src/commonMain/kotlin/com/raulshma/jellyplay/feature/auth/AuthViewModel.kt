package com.raulshma.jellyplay.feature.auth

import com.raulshma.jellyplay.core.concurrency.DEFAULT_FANOUT_PARALLELISM
import com.raulshma.jellyplay.core.concurrency.PollSpec
import com.raulshma.jellyplay.core.concurrency.boundedPoll
import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.auth.generated.resources.Res
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_login_failed
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_auth
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_check_availability
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_initiate
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_not_enabled
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_polling
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_timeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Semaphore
import kotlin.time.TimeSource
import com.raulshma.jellyplay.core.ui.message.UiMessage

sealed class QuickConnectUiState {
    data object Idle : QuickConnectUiState()
    data object Initiating : QuickConnectUiState()
    data class WaitingForApproval(val code: String, val secret: String) : QuickConnectUiState()
    data object Authenticating : QuickConnectUiState()
    data object Success : QuickConnectUiState()
    data class Error(val message: AuthMessage) : QuickConnectUiState()
}

/**
 * Which credential field (if any) a login error is attached to. Ambiguous auth
 * failures (e.g. HTTP 401) use [SERVER] so neither field is reddened while the
 * message still surfaces in the error banner.
 *
 * Lives beside the login machine in [AuthViewModel] (it used to sit in
 * LoginScreen.kt): the VM owns the whole attempt machine, the screen only
 * dispatches intents and renders. Same package, so the move is invisible to
 * every reader.
 */
enum class LoginFieldError { USERNAME, PASSWORD, SERVER }

/**
 * The login-attempt lifecycle for [LoginScreen] — a sealed phase replacing
 * the former screen-local `isLoggingIn` rememberSaveable flag, whose only
 * writer lived inside the ViewModel's result callback: a process death
 * mid-login restored a stuck `true` that disabled the submit button forever
 * (the recorded `pinVerifying` bug shape, worse because saveable PERSISTED
 * it across recreation). VM-owned now:
 *
 *  - process death → a fresh [AuthViewModel] constructs at [Idle] with no
 *    [LoginError] — a consistent blank slate;
 *  - config change → the surviving VM keeps the phase (the saveable pair's
 *    preservation behavior, minus the stringly-typed enum-name encoding).
 */
sealed interface LoginPhase {
    /** Form idle — submit and Quick Connect are enabled. */
    data object Idle : LoginPhase

    /** A login attempt is in flight — submit and Quick Connect are disabled. */
    data object Submitting : LoginPhase
}

/**
 * Typed login error: which field is at fault + the message for the banner.
 * `null` [AuthViewModel.loginError] means no error; non-null MEANS the banner
 * is on screen (the `PlaybackErrorUi?` shape — silent-error and a blank
 * banner are unrepresentable). The message is a [UiMessage] so the
 * exception-vs-fallback fold happens VM-side (the same fold the former
 * `UserErrorMessages.resolve(it, loginFailedError)` call rendered) and
 * resolution stays at the render edge under locale changes.
 */
data class LoginError(val field: LoginFieldError, val message: UiMessage)

class AuthViewModel(
    private val authRepository: AuthRepository,
) : JellyPlayViewModel() {

    val servers = authRepository.servers
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _serverHealth = stateFlow<Map<String, ServerHealth>>(emptyMap())
    val serverHealth = _serverHealth.flow

    val currentServerUsers: StateFlow<List<UserInfo>> = authRepository.currentServerUsers

    private val _loginPhase = stateFlow<LoginPhase>(LoginPhase.Idle)
    val loginPhase = _loginPhase.flow

    private val _loginError = stateFlow<LoginError?>(null)
    val loginError = _loginError.flow

    private val _quickConnectState = stateFlow<QuickConnectUiState>(QuickConnectUiState.Idle)
    val quickConnectState = _quickConnectState.flow

    private var quickConnectPollingJob: Job? = null

    fun removeServer(serverId: String) {
        launch {
            authRepository.removeServer(serverId)
        }
    }

    /**
     * Pings each saved server once and publishes per-server [ServerHealth]
     * into [serverHealth] (keyed by the server's primary address, which the
     * server list renders). A server counts as reachable when ANY of its
     * addresses answers — the primary first, and only when it is down the
     * alternates concurrently — mirroring the app's address failover.
     * Servers fan out bounded by
     * [DEFAULT_FANOUT_PARALLELISM] (the MusicHomeViewModel/arrqueue
     * pattern), so one slow server does not delay the other rows' dots.
     * Safe to call repeatedly; concurrent calls are guarded by cancelling
     * any in-flight batch.
     */
    private var healthCheckJob: Job? = null
    private val healthSemaphore = Semaphore(DEFAULT_FANOUT_PARALLELISM)

    fun checkServersHealth(servers: List<ServerInfo>) {
        healthCheckJob?.cancel()
        if (servers.isEmpty()) {
            _serverHealth.set(emptyMap())
            return
        }
        // Immediately mark everything as Checking so the UI can render a dot.
        _serverHealth.set(servers.associate { it.address to ServerHealth.Checking })
        healthCheckJob = launch {
            healthSemaphore.mapConcurrent(servers) { server ->
                // Monotonic elapsed read (stdlib) — latency wants the
                // wall-clock-independent measure anyway.
                val probeClock = TimeSource.Monotonic.markNow()
                // Primary probed alone first (the healthy common case costs
                // one cheap probe); only when it is down are the alternates
                // probed concurrently, so a dead primary costs one probe
                // window instead of one timeout per alternate — the same
                // fan-out shape as ServerAddressRouter.reselectActiveEndpoint.
                // Healthy iff the primary or ANY alternate answers.
                val reachable = authRepository.probeServer(server.address).isSuccess ||
                    run {
                        val alternates = server.alternateAddresses
                        if (alternates.isEmpty()) {
                            false
                        } else {
                            coroutineScope {
                                alternates.map { alternate ->
                                    async { authRepository.probeServer(alternate).isSuccess }
                                }.awaitAll().any { it }
                            }
                        }
                    }
                val latency = probeClock.elapsedNow().inWholeMilliseconds
                val health = if (reachable) {
                    ServerHealth.Healthy(latencyMs = latency)
                } else {
                    ServerHealth.Unreachable
                }
                _serverHealth.update { it + (server.address to health) }
            }
        }
    }

    /**
     * The screen's form-validation intent (the username-required check happens
     * composable-side where the field state lives): parks the error into the
     * same typed state the login attempt writes, so [LoginScreen] renders one
     * error surface from one owner.
     */
    fun setLoginFieldError(field: LoginFieldError, message: UiMessage) {
        _loginError.set(LoginError(field, message))
    }

    /** Clears the parked login error (field edits dispatch this). */
    fun clearLoginError() {
        _loginError.set(null)
    }

    /**
     * Runs the login attempt as the VM-owned [LoginPhase] machine. The
     * observable outcomes match the former screen-local flag exactly — the
     * spinner face, the failure banner, the success callback — with two
     * declared differences:
     *
     *  - **Re-entry guard** (phase-typed `!is Submitting`, the
     *    SnapshotActionPhase precedent): a second submit while one is in
     *    flight is dropped. The screen disables the button; this guards the
     *    paths that bypass it.
     *  - **A thrown repository read folds into the failure** instead of
     *    skipping [onResult]: an exception would otherwise strand the machine
     *    in [LoginPhase.Submitting] forever — the eternal-spinner class
     *    [getUsersForServer]'s KDoc records. Cancellation still propagates
     *    (the caller is going away; the VM is cleared with it).
     *
     * The phase settles to [LoginPhase.Idle] BEFORE [onResult] runs, so the
     * callback's navigation lands with the machine already settled. Failures
     * park a [LoginError] (field [LoginFieldError.SERVER], exception message
     * or the localized `auth_login_failed` fallback — the same resolution the
     * former screen-side `UserErrorMessages.resolve` call rendered).
     */
    fun login(serverAddress: String, username: String, password: String, onResult: (Result<Unit>) -> Unit) {
        if (_loginPhase.value is LoginPhase.Submitting) return
        launch {
            _loginPhase.set(LoginPhase.Submitting)
            _loginError.set(null)
            val result = try {
                authRepository.login(serverAddress, username, password).map {}
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Result.failure(t)
            }
            result.onFailure { failure ->
                _loginError.set(
                    LoginError(
                        LoginFieldError.SERVER,
                        UiMessage.of(failure, Res.string.auth_login_failed),
                    )
                )
            }
            _loginPhase.set(LoginPhase.Idle)
            onResult(result)
        }
    }

    fun switchUser(userId: String, onResult: (Result<Unit>) -> Unit) {
        launch {
            val result = authRepository.switchUser(userId)
            onResult(result)
        }
    }

    fun removeUser(userId: String) {
        launch {
            authRepository.removeUser(userId)
        }
    }

    /**
     * Loads a server's pick-list users (the local one-shot Room read,
     * [AuthRepository.getUsersForServer]) and delivers the outcome to
     * [onResult] as a [Result] — the same callback shape the sibling
     * [login]/[switchUser] callbacks use. The bare-list callback this
     * replaces had NO failure channel: a thrown repository read (a DB error
     * behind the one-shot query) propagated into this coroutine and never
     * invoked [onResult], which stranded UserSelectionScreen's loading flag
     * true forever — an eternal spinner with no error arm. [runCatching]
     * folds that thrown path into the Result so the screen's Retry re-runs
     * this and lands in its error arm instead. A cancellation escaping the
     * read also folds into a failure; the screen is gone by then, so the
     * folded result is inert.
     */
    fun getUsersForServer(serverId: String, onResult: (Result<List<UserInfo>>) -> Unit) {
        launch {
            onResult(runCatching { authRepository.getUsersForServer(serverId) })
        }
    }

    fun startQuickConnect(serverAddress: String) {
        quickConnectPollingJob?.cancel()
        _quickConnectState.set(QuickConnectUiState.Initiating)

        launch {
            val enabledResult = authRepository.isQuickConnectEnabled()
            if (enabledResult.isFailure) {
                _quickConnectState.set(
                    QuickConnectUiState.Error(
                        UiMessage.of(enabledResult, Res.string.auth_qc_error_check_availability)
                    )
                )
                return@launch
            }
            if (enabledResult.getOrNull() != true) {
                _quickConnectState.set(
                    QuickConnectUiState.Error(
                        UiMessage.Resource(Res.string.auth_qc_error_not_enabled)
                    )
                )
                return@launch
            }

            val initiateResult = authRepository.initiateQuickConnect()
            if (initiateResult.isFailure) {
                _quickConnectState.set(
                    QuickConnectUiState.Error(
                        UiMessage.of(initiateResult, Res.string.auth_qc_error_initiate)
                    )
                )
                return@launch
            }

            val qcInfo = initiateResult.getOrNull()!!
            _quickConnectState.set(
                QuickConnectUiState.WaitingForApproval(
                    code = qcInfo.code,
                    secret = qcInfo.secret,
                )
            )

            quickConnectPollingJob = launch {
                // The approval wait is a boundedPoll (a bounded
                // wait-for-one-answer): a 3 s head start before the first
                // probe — the user needs to read the code — then one probe
                // per 3 s round, 40 rounds ≈ 2 minutes before the timeout
                // error. Authenticated/Failed stop the poll (non-null); a
                // pending round answers null; exhaustion maps to the timeout
                // error below. Cancellation (cancel/clear/onCleared) unwinds
                // through the poll's gaps.
                val answer = boundedPoll(
                    PollSpec(
                        initialDelayMs = 3_000,
                        intervalMs = 3_000,
                        maxAttempts = 40,
                    ),
                ) { _ ->
                    val pollResult = authRepository.pollQuickConnect(qcInfo.secret)
                    when {
                        pollResult.isFailure -> QuickConnectPollAnswer.Failed(pollResult)
                        pollResult.getOrNull()?.authenticated == true ->
                            QuickConnectPollAnswer.Authenticated
                        else -> null
                    }
                }
                when (answer) {
                    null -> _quickConnectState.set(
                        QuickConnectUiState.Error(
                            UiMessage.Resource(Res.string.auth_qc_error_timeout)
                        )
                    )
                    is QuickConnectPollAnswer.Failed -> _quickConnectState.set(
                        QuickConnectUiState.Error(
                            UiMessage.of(answer.result, Res.string.auth_qc_error_polling)
                        )
                    )
                    QuickConnectPollAnswer.Authenticated -> {
                        _quickConnectState.set(QuickConnectUiState.Authenticating)
                        val loginResult = authRepository.loginWithQuickConnect(
                            serverAddress, qcInfo.secret
                        )
                        _quickConnectState.set(
                            if (loginResult.isSuccess) {
                                QuickConnectUiState.Success
                            } else {
                                QuickConnectUiState.Error(
                                    UiMessage.of(loginResult, Res.string.auth_qc_error_auth)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    fun cancelQuickConnect() {
        quickConnectPollingJob?.cancel()
        quickConnectPollingJob = null
        _quickConnectState.set(QuickConnectUiState.Idle)
    }

    fun resetQuickConnectState() {
        quickConnectPollingJob?.cancel()
        quickConnectPollingJob = null
        _quickConnectState.set(QuickConnectUiState.Idle)
    }

    override fun onCleared() {
        super.onCleared()
        quickConnectPollingJob?.cancel()
        healthCheckJob?.cancel()
    }
}

/**
 * One round's answer for the QuickConnect approval poll in
 * [AuthViewModel.startQuickConnect] — the stop-or-wait vocabulary
 * [boundedPoll] asks for, where a pending round is a `null` answer and owns
 * no variant:
 *
 *  - **[QuickConnectPollAnswer.Authenticated]** — the user approved on their
 *    device; the poll stops and the login proceeds.
 *  - **[QuickConnectPollAnswer.Failed]** — the poll call itself errored; the
 *    poll stops too (a failing endpoint is not re-attempted today) and
 *    carries the failed [Result] so the error state keeps the
 *    exception-vs-resource fold ([UiMessage.of]) — distinguishable from the
 *    exhaustion timeout, which carries the bare resource.
 */
private sealed interface QuickConnectPollAnswer {
    data object Authenticated : QuickConnectPollAnswer

    data class Failed(val result: Result<QuickConnectState>) : QuickConnectPollAnswer
}
