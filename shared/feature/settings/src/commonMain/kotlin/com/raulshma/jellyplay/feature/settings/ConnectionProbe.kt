package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_connection_failed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_probe_api_key_required
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_probe_email_password_required
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_probe_enter_credentials
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_probe_login_failed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_probe_provider_not_configured
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_probe_server_url_required
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_probe_unexpected_error
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_probe_username_password_required
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.StringResource
import java.util.concurrent.ConcurrentHashMap

/**
 * The ONE connection-probe module behind the three settings service
 * integrations (*arr servers, Seerr connection, subtitle providers) — the
 * status machine, the single-flight probe lifecycle, the cancellation policy,
 * and the fallback error-text policy in one place. Each integration keeps its
 * own repository call (the network hop is the [action] constructor lambda, the
 * [SleepTimerController] constructor-lambda pattern) and its own per-key
 * status board; nothing here touches a repository.
 *
 * Declared policies (pinned by `ConnectionProbeTest`):
 *
 *  - **Status machine** — [Status]: Idle → Testing → Connected([D] details) /
 *    Error([Failure]). [Connected] is parameterized so each integration keeps
 *    its own detail type (*arr/subtitle: Unit, Seerr: server version).
 *  - **Single-flight: RESTART** — a [probe] request while the same key is
 *    already Testing CANCELS the in-flight probe and starts a new one (Seerr's
 *    former `launchTest` discipline, generalized; the refused alternative was
 *    rejected because it would have changed Seerr's rapid-retry UX). A refusal
 *    ([refused] pre-flight validation) supersedes an in-flight probe on the
 *    same key the same way. A superseded probe NEVER lands its outcome: writes
 *    are guarded by a job-identity check, so a probe that resumed after
 *    cancellation cannot clobber the superseding state.
 *  - **Cancellation never lands as Error** — the action runs under
 *    [runCatchingRethrowingCancellation]; a [CancellationException] propagates
 *    (Seerr's rethrow discipline, ported to all three integrations), leaving
 *    the state the probe had. A non-cancellation crash is folded to
 *    [Failure.Declared] [FallbackText.UnexpectedError] instead of killing the
 *    scope — previously only Seerr caught it; *arr/subtitle probes crashed.
 *  - **Fallback error text** — failures either carry the integration's own
 *    message ([Failure.Reported], rendered verbatim — server text is assumed
 *    already readable) or one of the declared [FallbackText]s
 *    ([Failure.Declared]), localized at render time via the shared indicator
 *    (`ConnectionProbeStatusIndicator`). No integration bakes English
 *    literals into the status anymore.
 *
 * The per-integration concurrency cap is deliberately NOT owned here: it is an
 * adapter policy (*arr wraps the action in its semaphore). Batches are
 * caller-side composition — a second batch naturally restarts per key.
 *
 * Status writes are atomic CAS updates ([update]); probes of different keys
 * run concurrently and settle independently. The [jobs] map is a concurrent
 * map because it is written from two thread worlds: the callers'
 * (usually main) dispatcher via [probe]/[reset]/[retain], and the
 * completing coroutine's context via the `invokeOnCompletion` reaper —
 * which runs on whatever thread the action settled on.
 */
class ConnectionProbe<R : Any, K : Any, D>(
    private val scope: CoroutineScope,
    /** Derives the status-board key from a probe request. */
    private val keyOf: (R) -> K,
    /**
     * The network call under test: returns [Outcome.Reachable] on success or
     * [Outcome.Failed] with the failure to surface. Thrown non-cancellation
     * exceptions are folded to the UnexpectedError fallback by the machine;
     * cancellation propagates.
     */
    private val action: suspend (R) -> Outcome<D>,
    /**
     * Synchronous pre-flight validation: a non-null [FallbackText] refuses the
     * probe (fail-fast Error, no Testing frame, no action call) — the
     * blank-credentials guards the Seerr/subtitle integrations used to run by
     * hand. Null = the request is acceptable.
     */
    private val refused: ((R) -> FallbackText?)? = null,
) {

    private val _status = MutableStateFlow<Map<K, Status<D>>>(emptyMap())

    /** Per-key status board; absent key == [Status.Idle]. */
    val status: StateFlow<Map<K, Status<D>>> = _status.asStateFlow()

    /**
     * In-flight probe jobs by key; at most one per key (restart policy).
     * Concurrent because the [Job.invokeOnCompletion] reaper runs on the
     * completing coroutine's thread, not the callers' dispatcher.
     */
    private val jobs: MutableMap<K, Job> = ConcurrentHashMap()

    /** [status] entry for [key], or [Status.Idle] when never probed/reset. */
    fun statusOf(key: K): Status<D> = _status.value[key] ?: Status.Idle

    /**
     * Probes [request]. Refused requests settle synchronously (before this
     * returns); accepted requests launch into [scope], mark the key
     * [Status.Testing] on the first dispatch, and settle per the [action]
     * result — unless superseded by a newer probe/refusal/reset for the same
     * key, in which case the outcome is discarded (restart policy).
     */
    fun probe(request: R) {
        val key = keyOf(request)
        refused?.invoke(request)?.let { refusal ->
            // A refusal supersedes: the in-flight probe's late outcome must not
            // overwrite the fresh validation error (the old Seerr flow let it).
            supersede(key)
            _status.update { it + (key to Status.Error(Failure.Declared(refusal))) }
            return
        }
        supersede(key)
        // LAZY + explicit start: the job MUST be registered before its body can
        // run. An eagerly-started launch on an immediate dispatcher (the
        // ViewModelScope reality) would run a suspension-free action inline,
        // and the identity guard below would see no registered job and
        // discard a perfectly fresh outcome.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            _status.update { it + (key to Status.Testing) }
            val outcome = runCatchingRethrowingCancellation { action(request) }.fold(
                onSuccess = { it },
                // Non-cancellation crash: declared fallback instead of a dead
                // scope (only Seerr used to catch this; the *arr/subtitle
                // probes used to crash).
                onFailure = { Outcome.Failed(Failure.Declared(FallbackText.UnexpectedError)) },
            )
            // Restart guard: only the CURRENT job for this key may settle it.
            // Checked INSIDE the update: a supersede that lands between the
            // map read and the status write removes the job and puts down a
            // fresh status, and the stale outcome must not overwrite it (the
            // update's CAS retry re-runs the lambda, re-reading the map).
            _status.update { current ->
                if (jobs[key] !== self) {
                    current
                } else {
                    current + (key to when (outcome) {
                        is Outcome.Reachable -> Status.Connected(outcome.details)
                        is Outcome.Failed -> Status.Error(outcome.failure)
                    })
                }
            }
        }
        jobs[key] = job
        job.start()
        // Two-arg remove: only reap while this job is still the registered one
        // (the map is concurrent; a superseding probe may already have replaced it).
        job.invokeOnCompletion { jobs.remove(key, job) }
    }

    /**
     * Drops [key]: cancels any in-flight probe for it and removes its status
     * (reads degrade to [Status.Idle]). The Seerr field-changed /
     * disconnect resets.
     */
    fun reset(key: K) {
        supersede(key)
        _status.update { it - key }
    }

    /**
     * Keeps only [liveKeys]: cancels and removes every other key's probe and
     * status, so a mutated server/provider set never keeps a stale entry —
     * and a dropped key's in-flight probe can never land (the identity guard
     * sees the job gone). Surviving keys keep their current status and any
     * in-flight probe (a fresh batch supersedes them per key).
     */
    fun retain(liveKeys: Set<K>) {
        jobs.keys.filterNot { it in liveKeys }.forEach { supersede(it) }
        _status.update { it.filterKeys { key -> key in liveKeys } }
    }

    /**
     * Seeds [key] as [Status.Connected] without launching the action — the
     * restore-from-store path (e.g. Seerr's init marks a configured,
     * credential-backed connection as already connected). Not a general write
     * hatch: it exists for state restoration, never to fake a probe result
     * after user action.
     */
    fun restoreConnected(key: K, details: D) {
        supersede(key)
        _status.update { it + (key to Status.Connected(details)) }
    }

    private fun supersede(key: K) {
        jobs.remove(key)?.cancel()
    }

    companion object {
        /**
         * A transport-reported failure outcome. A non-null [message] is surfaced
         * verbatim ([Failure.Reported]); a null message degrades to the
         * localized [fallback] text — the former `err.message ?: "Connection
         * failed"` folds, now declared here instead of per-VM.
         */
        fun <D> unreachable(
            message: String?,
            fallback: FallbackText = FallbackText.ConnectionFailed,
        ): Outcome<D> = Outcome.Failed(
            if (message != null) Failure.Reported(message) else Failure.Declared(fallback),
        )
    }

    /**
     * Probe status for one key. [Idle] is a shared object (also the
     * absent-key read), [Connected] carries the integration's own [D] details.
     */
    sealed class Status<out D> {
        /** Not yet probed (or reset). */
        data object Idle : Status<Nothing>()
        /** Probe in flight. */
        data object Testing : Status<Nothing>()
        /** Probe succeeded; [details] is the integration's own payload. */
        data class Connected<D>(val details: D) : Status<D>()
        /** Probe failed; see [Failure] for the verbatim/fallback split. */
        data class Error(val failure: Failure) : Status<Nothing>()
    }

    /**
     * Why a probe failed. [Reported] carries the integration's own message and
     * renders verbatim; [Declared] is one of the module's fallback texts and
     * localizes at render time — no bare English literal travels in status.
     */
    sealed class Failure {
        /** Transport/integration-provided text (e.g. an ApiException message). */
        data class Reported(val message: String) : Failure()
        /** A declared fallback; localized by the shared indicator. */
        data class Declared(val text: FallbackText) : Failure()
    }

    /**
     * The declared fallback vocabulary. Backed by this module's Compose
     * resources in every supported locale; [resource] is the pure text →
     * resource mapping the indicator renders and the tests pin.
     */
    enum class FallbackText {
        /** Probe failed with no usable message. Reuses the existing `settings_connection_failed` entry. */
        ConnectionFailed,
        /** Login-specific probe failure with no usable message (former Seerr literal). */
        LoginFailed,
        /** The probe crashed unexpectedly (former Seerr catch-all literal). */
        UnexpectedError,
        /** Seerr: blank server URL. */
        ServerUrlRequired,
        /** Seerr: blank API key. */
        ApiKeyRequired,
        /** Seerr Jellyfin login: blank username or password. */
        UsernamePasswordRequired,
        /** Seerr local login: blank email or password. */
        EmailPasswordRequired,
        /** Subtitle providers: form credentials blank (fail-fast). */
        EnterCredentialsFirst,
        /** Subtitle providers: repository skipped the verification. */
        ProviderNotConfigured,
        ;

        fun resource(): StringResource = when (this) {
            ConnectionFailed -> Res.string.settings_connection_failed
            LoginFailed -> Res.string.settings_probe_login_failed
            UnexpectedError -> Res.string.settings_probe_unexpected_error
            ServerUrlRequired -> Res.string.settings_probe_server_url_required
            ApiKeyRequired -> Res.string.settings_probe_api_key_required
            UsernamePasswordRequired -> Res.string.settings_probe_username_password_required
            EmailPasswordRequired -> Res.string.settings_probe_email_password_required
            EnterCredentialsFirst -> Res.string.settings_probe_enter_credentials
            ProviderNotConfigured -> Res.string.settings_probe_provider_not_configured
        }
    }

    /** What the [action] lambda returns: the machine owns the status fold. */
    sealed interface Outcome<out D> {
        /** Probe succeeded; [details] becomes [Status.Connected.details]. */
        data class Reachable<D>(val details: D) : Outcome<D>
        /** Probe failed; [failure] becomes [Status.Error.failure]. */
        data class Failed(val failure: Failure) : Outcome<Nothing>
    }
}
