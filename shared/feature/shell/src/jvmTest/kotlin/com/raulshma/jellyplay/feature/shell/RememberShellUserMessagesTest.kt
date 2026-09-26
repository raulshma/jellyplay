package com.raulshma.jellyplay.feature.shell

import com.raulshma.jellyplay.core.ui.message.UiText
import com.raulshma.jellyplay.core.ui.message.UserMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the JVM-testable half of the [rememberShellUserMessages] seam — the
 * [shellUserMessageHost] kernel both overloads build their host from: the
 * shell's present adapter rides behind the SHARED resolver ([resolveUiText])
 * and the shared severity→duration policy, the wiring the Android and
 * desktop shells used to hand-copy into their own host remembers. The
 * composition half (host remember + collector-effect keys +
 * rememberUpdatedState refresh of the adapter/projections) is compose-only —
 * this module's jvmTest carries no compose ui-test harness — and the
 * collection choreography underneath is already pinned by UserMessageHostTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RememberShellUserMessagesTest {

    @Test
    fun `the shell host kernel resolves through the shared resolver into the shell's surface`() = runTest {
        val presented = mutableListOf<Pair<String, UserMessageDuration>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            shellUserMessageHost { text, duration -> presented.add(text to duration) }
                .host(
                    flowOf(UserMessage.Info(UiText.Raw("saved")), UserMessage.Error(UiText.Raw("failed"))),
                )
        }
        advanceUntilIdle()

        // Raw payloads pass through the shared resolver verbatim; the
        // severity→duration table is the host policy's.
        assertEquals(
            listOf("saved" to UserMessageDuration.Short, "failed" to UserMessageDuration.Long),
            presented,
        )
    }

    private fun <M> flowOf(vararg messages: M): Flow<M> = messages.asFlow()
}
