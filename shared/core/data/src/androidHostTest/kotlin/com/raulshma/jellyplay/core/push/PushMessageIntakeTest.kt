package com.raulshma.jellyplay.core.push

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [PushMessageIntake] — the GLOSSARY "Receiver intake fold" verdict for
 * a UnifiedPush message — on the pure JVM (no Robolectric). This is the
 * first seam for [JellyPlayUnifiedPushReceiver], whose onMessage is now
 * fold + dispatch.
 *
 * Invariants:
 * - The SILENT `sync-nudge` kind answers [PushMessageVerdict.SyncNudge]
 *   through BOTH spellings (top-level `kind`, relayed ntfy `headers`
 *   `X-JellyPlay-Kind`) and REGARDLESS of the `push` gate — a nudge targets
 *   the SYNC toggle, never the push one.
 * - An ordinary payload rides the gate: open →
 *   [PushMessageVerdict.SurfaceInTray] (echoing the payload verbatim — the
 *   tray dispatcher owns the JSON decode and drops malformed bodies),
 *   closed → [PushMessageVerdict.GateClosed].
 * - Malformed JSON is simply not a nudge.
 */
class PushMessageIntakeTest {

    private fun payload(vararg pairs: Pair<String, String>): String =
        pairs.joinToString(",", prefix = "{", postfix = "}") { (k, v) -> "\"$k\":\"$v\"" }

    // ── nudge detection ──────────────────────────────────────────────────

    @Test
    fun `a top-level sync-nudge kind answers true`() {
        val body = payload("kind" to "sync-nudge", "title" to "ignored")
        assertEquals(true, PushMessageIntake.isSyncNudge(body))
    }

    @Test
    fun `a relayed ntfy header spelling answers true`() {
        val body = """{"headers":{"${PushMessageIntake.NTFY_KIND_HEADER}":"sync-nudge"}}"""
        assertEquals(true, PushMessageIntake.isSyncNudge(body))
    }

    @Test
    fun `malformed json is not a nudge`() {
        assertEquals(false, PushMessageIntake.isSyncNudge("not json at all"))
    }

    @Test
    fun `other kinds and non-json kinds are not nudges`() {
        assertEquals(false, PushMessageIntake.isSyncNudge(payload("kind" to "new-media")))
        assertEquals(false, PushMessageIntake.isSyncNudge(payload("kind" to "broadcast")))
        assertEquals(false, PushMessageIntake.isSyncNudge("{}"))
    }

    // ── verdict ──────────────────────────────────────────────────────────

    @Test
    fun `a nudge wins the verdict regardless of the gate`() {
        val body = payload("kind" to "sync-nudge")

        assertEquals(
            PushMessageVerdict.SyncNudge,
            PushMessageIntake.verdict(body, pushGateOpen = true),
        )
        assertEquals(
            PushMessageVerdict.SyncNudge,
            PushMessageIntake.verdict(body, pushGateOpen = false),
        )
    }

    @Test
    fun `an ordinary payload past the gate surfaces in the tray with its body echoed`() {
        val body = payload("kind" to "broadcast", "title" to "hello")

        assertEquals(
            PushMessageVerdict.SurfaceInTray(body),
            PushMessageIntake.verdict(body, pushGateOpen = true),
        )
    }

    @Test
    fun `an ordinary payload under a closed gate drops`() {
        val body = payload("kind" to "broadcast", "title" to "hello")

        assertEquals(
            PushMessageVerdict.GateClosed,
            PushMessageIntake.verdict(body, pushGateOpen = false),
        )
    }

    @Test
    fun `a malformed non-nudge payload with an open gate still surfaces — the tray dispatcher drops it`() {
        // The receiver's contract is unchanged: the fold routes, the tray
        // side owns the JSON decode (its malformed-payload guard returns
        // quietly). The gate was paid; the payload goes to the dispatcher.
        assertEquals(
            PushMessageVerdict.SurfaceInTray("not json"),
            PushMessageIntake.verdict("not json", pushGateOpen = true),
        )
    }
}
