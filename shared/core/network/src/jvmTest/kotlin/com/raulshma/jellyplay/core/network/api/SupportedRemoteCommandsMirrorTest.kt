package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.network.auth.SUPPORTED_REMOTE_COMMANDS
import org.jellyfin.sdk.model.api.GeneralCommandType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The capabilities-mirror equality pin: the serial-name list the
 * shared auth client posts (`AuthWireDto.SUPPORTED_REMOTE_COMMANDS`) and the
 * SDK-typed list the JVM engine posts
 * (`JellyfinApiEngine.SUPPORTED_REMOTE_COMMANDS`) must carry the SAME
 * command set — server-side remote UIs key off this list, and the two
 * declarations live in different source sets precisely so they CAN drift.
 * This test is the one place that drift fails.
 */
class SupportedRemoteCommandsMirrorTest {

    @Test
    fun `the wire mirror and the engine list carry the same command set`() {
        val engineSerialNames = JellyfinApiEngine.SUPPORTED_REMOTE_COMMANDS
            .map { it.serialName }
            .toList()

        assertEquals(
            SUPPORTED_REMOTE_COMMANDS.toSet(),
            engineSerialNames.toSet(),
            "AuthWireDto's mirror and JellyfinApiEngine's list disagree — the capabilities " +
                "payloads the two clients post would advertise different remote-control surfaces",
        )
    }

    @Test
    fun `the wire mirror preserves the engine list's declaration order`() {
        val engineSerialNames = JellyfinApiEngine.SUPPORTED_REMOTE_COMMANDS
            .map { it.serialName }
            .toList()

        assertEquals(
            engineSerialNames,
            SUPPORTED_REMOTE_COMMANDS,
            "the mirror is documented as SAME-ORDER — diffs in capabilities payloads " +
                "should stay byte-stable",
        )
    }

    @Test
    fun `the navigation ladder is advertised`() {
        val advertised = SUPPORTED_REMOTE_COMMANDS.toSet()
        val ladder = setOf(
            "Back", "Select", "MoveUp", "MoveDown", "MoveLeft", "MoveRight",
            "GoHome", "GoToSettings", "GoToSearch", "ToggleContextMenu",
            "DisplayContent", "TakeScreenshot",
        )
        assertEquals(
            emptySet(),
            ladder - advertised,
            "the remote d-pad/screenshot controls render only when advertised",
        )
    }

    @Test
    fun `every advertised command exists in the SDK enum`() {
        // The mirror is hand-typed serial names — a typo would silently drop
        // the command from the SDK-typed list's vocabulary. Pin each name
        // resolves through GeneralCommandType's own parser.
        for (name in SUPPORTED_REMOTE_COMMANDS) {
            kotlin.test.assertNotNull(
                GeneralCommandType.fromNameOrNull(name),
                "no GeneralCommandType for '$name' — typo in the mirror?",
            )
        }
    }
}
