package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.feature.settings.ConnectionProbe.FallbackText
import com.raulshma.jellyplay.feature.settings.ConnectionProbe.Failure
import com.raulshma.jellyplay.feature.settings.ConnectionProbe.Outcome
import com.raulshma.jellyplay.feature.settings.ConnectionProbe.Status
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the shared [ConnectionProbe] machine behind the three settings service
 * integrations — the status algebra (idle → testing → connected/error), the
 * declared single-flight RESTART policy (a second probe supersedes the
 * in-flight one, whose late outcome never lands), the refusal pre-flight
 * (synchronous, no action call), the cancellation policy (a cancelled probe
 * leaves the state it had — NEVER Error), and the fallback-text policy (null
 * transport messages and crashes degrade to declared [FallbackText]s, each
 * mapped to its Compose resource).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionProbeTest {

    /** Request/key/details are all String here; the machine is generic over all three. */
    private fun CoroutineScope.probeBoard(
        refused: ((String) -> FallbackText?)? = null,
        action: suspend (String) -> Outcome<String>,
    ): ConnectionProbe<String, String, String> = ConnectionProbe(
        scope = this,
        keyOf = { it },
        action = action,
        refused = refused,
    )

    @Test
    fun `a probe walks idle then testing then connected`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val board = probeBoard { gate.await(); Outcome.Reachable("v1") }

        assertEquals(Status.Idle, board.statusOf("k"))
        board.probe("k")
        runCurrent()
        assertEquals(Status.Testing, board.statusOf("k"))

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(Status.Connected("v1"), board.statusOf("k"))
    }

    @Test
    fun `a probe that settles without suspending still lands its outcome`() = runTest {
        // The production shape: the adapters pass viewModelScope, whose
        // Main.immediate dispatch runs a launch's body INLINE — an eager
        // start means the body runs before probe() has registered the job,
        // and the identity guard would discard the fresh outcome (stuck
        // Testing). UnconfinedTestDispatcher reproduces the inline start;
        // the machine must register the job before it can run.
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val board = scope.probeBoard { Outcome.Reachable("immediate") }

        board.probe("k")

        assertEquals(Status.Connected("immediate"), board.statusOf("k"))
        scope.cancel()
    }

    @Test
    fun `a probe failure with a server message lands reported verbatim`() = runTest {
        val board = probeBoard { ConnectionProbe.unreachable("boom") }

        board.probe("k")
        advanceUntilIdle()

        assertEquals(Status.Error(Failure.Reported("boom")), board.statusOf("k"))
    }

    @Test
    fun `a null failure message degrades to the localized ConnectionFailed fallback`() = runTest {
        val board = probeBoard { ConnectionProbe.unreachable(null) }

        board.probe("k")
        advanceUntilIdle()

        assertEquals(
            Status.Error(Failure.Declared(FallbackText.ConnectionFailed)),
            board.statusOf("k"),
        )
    }

    @Test
    fun `an unexpected crash lands the UnexpectedError fallback and the scope survives`() = runTest {
        var crash = true
        val board = probeBoard {
            if (crash) throw RuntimeException("bug")
            Outcome.Reachable("recovered")
        }

        board.probe("k")
        advanceUntilIdle()
        assertEquals(
            Status.Error(Failure.Declared(FallbackText.UnexpectedError)),
            board.statusOf("k"),
        )

        // The machine folded the crash instead of killing the scope.
        crash = false
        board.probe("k")
        advanceUntilIdle()
        assertEquals(Status.Connected("recovered"), board.statusOf("k"))
    }

    @Test
    fun `a refused request fails fast without launching the action`() = runTest {
        var calls = 0
        val board = probeBoard(
            refused = { if (it == "bad") FallbackText.EnterCredentialsFirst else null },
        ) { calls++; Outcome.Reachable("v") }

        board.probe("bad")
        advanceUntilIdle()

        assertEquals(
            Status.Error(Failure.Declared(FallbackText.EnterCredentialsFirst)),
            board.statusOf("bad"),
        )
        assertEquals(0, calls)
    }

    @Test
    fun `a second probe while testing restarts and the superseded probe never lands`() = runTest {
        val gate1 = CompletableDeferred<Unit>()
        val gate2 = CompletableDeferred<Unit>()
        var attempt = 0
        val board = probeBoard { _ ->
            // Attempt 1 (the probe about to be superseded) parks on gate1 with
            // the "first" outcome; the restart parks on gate2 with "second".
            val gate = if (attempt++ == 0) gate1 else gate2
            val outcome = if (gate === gate1) "first" else "second"
            gate.await()
            Outcome.Reachable(outcome)
        }

        board.probe("k")
        runCurrent()
        assertEquals(Status.Testing, board.statusOf("k"))

        // The declared RESTART policy: the second tap supersedes the in-flight probe.
        board.probe("k")
        runCurrent()
        assertEquals(Status.Testing, board.statusOf("k"))

        gate2.complete(Unit)
        advanceUntilIdle()
        assertEquals(Status.Connected("second"), board.statusOf("k"))

        // The superseded probe's gate opens afterwards — its stale outcome
        // ("first") must never land over the superseding result.
        gate1.complete(Unit)
        advanceUntilIdle()
        assertEquals(Status.Connected("second"), board.statusOf("k"))
    }

    @Test
    fun `a refusal supersedes an in-flight probe on the same key and its late outcome never lands`() = runTest {
        val gate = CompletableDeferred<Unit>()
        // One key for every request (the Seerr shape: all auth variants probe
        // the single connection).
        val board = ConnectionProbe<String, String, String>(
            scope = this,
            keyOf = { _ -> "k" },
            action = { gate.await(); Outcome.Reachable("late") },
            refused = { if (it == "refused") FallbackText.ServerUrlRequired else null },
        )

        board.probe("valid")
        runCurrent()
        assertEquals(Status.Testing, board.statusOf("k"))

        // The refusal supersedes: synchronous Error, no Testing frame for it.
        board.probe("refused")
        assertEquals(
            Status.Error(Failure.Declared(FallbackText.ServerUrlRequired)),
            board.statusOf("k"),
        )

        gate.complete(Unit)
        advanceUntilIdle()
        // The superseded probe settled "late" — it must not overwrite the refusal.
        assertEquals(
            Status.Error(Failure.Declared(FallbackText.ServerUrlRequired)),
            board.statusOf("k"),
        )
    }

    @Test
    fun `a cancelled probe leaves the state it had and never lands as Error`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val board = scope.probeBoard { gate.await(); Outcome.Reachable("never") }

        board.probe("k")
        runCurrent()
        assertEquals(Status.Testing, board.statusOf("k"))

        scope.cancel()
        gate.complete(Unit)
        runCurrent()

        assertEquals(Status.Testing, board.statusOf("k"))
    }

    @Test
    fun `retain cancels and reaps dropped keys whose late outcomes never land`() = runTest {
        val gateA = CompletableDeferred<Unit>()
        val gateB = CompletableDeferred<Unit>()
        val board = probeBoard { request ->
            (if (request == "a") gateA else gateB).await()
            Outcome.Reachable("from-$request")
        }

        board.probe("a")
        board.probe("b")
        runCurrent()
        assertEquals(Status.Testing, board.statusOf("a"))
        assertEquals(Status.Testing, board.statusOf("b"))

        board.retain(setOf("a"))
        assertEquals(Status.Idle, board.statusOf("b"))

        gateB.complete(Unit)
        gateA.complete(Unit)
        advanceUntilIdle()
        // b was dropped: its late outcome never lands; a's does.
        assertEquals(Status.Idle, board.statusOf("b"))
        assertEquals(Status.Connected("from-a"), board.statusOf("a"))
    }

    @Test
    fun `restoreConnected seeds without launching the action and reset clears the key`() = runTest {
        var calls = 0
        val board = probeBoard { calls++; Outcome.Reachable("v") }

        board.restoreConnected("k", "stored")
        advanceUntilIdle()
        assertEquals(Status.Connected("stored"), board.statusOf("k"))
        assertEquals(0, calls)

        board.reset("k")
        assertEquals(Status.Idle, board.statusOf("k"))
    }

    @Test
    fun `every fallback text maps to its declared resource and none may exist unpinned`() {
        val pinned = mapOf(
            FallbackText.ConnectionFailed to Res.string.settings_connection_failed,
            FallbackText.LoginFailed to Res.string.settings_probe_login_failed,
            FallbackText.UnexpectedError to Res.string.settings_probe_unexpected_error,
            FallbackText.ServerUrlRequired to Res.string.settings_probe_server_url_required,
            FallbackText.ApiKeyRequired to Res.string.settings_probe_api_key_required,
            FallbackText.UsernamePasswordRequired to Res.string.settings_probe_username_password_required,
            FallbackText.EmailPasswordRequired to Res.string.settings_probe_email_password_required,
            FallbackText.EnterCredentialsFirst to Res.string.settings_probe_enter_credentials,
            FallbackText.ProviderNotConfigured to Res.string.settings_probe_provider_not_configured,
        )
        assertEquals(pinned.keys.toSet(), FallbackText.entries.toSet())
        pinned.forEach { (text, resource) -> assertEquals(resource, text.resource()) }
    }
}
