package com.raulshma.jellyplay.feature.player.video

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Composition ratchet for [PlaybackSession], the composition root that
 * replaced the deleted two-phase `PlayerWiring` builder (the builder had
 * itself replaced the VM's ~1,400 lines of in-class collaborator wiring AND
 * the deleted [VideoSessionHost] pass-through layer). [VideoPlayerViewModel]
 * is too heavy to construct in jvmTest — a 30+-dependency constructor — so,
 * the [SeekUserInitiatedWiringTest] / [ControllerOwnershipTest] precedent,
 * this pins the composition at the source. The behavioral pins that COULD
 * leave the builder moved to real suites when their subjects moved: the load
 * spine's offline-first segments fetch, the cinema gate's vetoes and the
 * remembered-muted mirror-then-engine order are pinned in
 * [SessionLoadPipelineTest] (they are pipeline members now), and the server
 * start report's incognito gate + session-id resolution in
 * PlaybackSessionReportingTest (a session member). What this suite pins is
 * what only the composition root still owns:
 *
 *  1. the post-collapse late-binding DISCIPLINE INVERSE — the former
 *     builder's five `lateinit` back-reference slots are GONE: the six
 *     mutual-recursion construction cycles dissolved by OWNERSHIP (the
 *     session KDoc's per-cycle map), so the composition root must declare
 *     zero of them (a reintroduced `lateinit` collaborator slot is a
 *     resurrected cycle);
 *  2. the hook-body ORDER that lost its behavioral test with the host:
 *     `resetForNewItem`'s synchronous prefix (autoplay reset → still-watching
 *     reset → the autoplay-cancelled mirror clear → the coordinator's
 *     new-item latch → the pending stream seed) and `onPlayheadSeeded`'s
 *     session pre-seed before the >0 chip gate;
 *  3. the deletion itself — no main source declares or references
 *     VideoSessionHost (a resurrection is the god-object regression this
 *     whole refactor unwinds).
 *
 * If a reformat trips a regex here, the assertion message names the contract
 * to restore — never delete an assertion to make a reformat pass.
 */
class PlaybackSessionCompositionTest {

    private fun mainSources(): List<File> {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var moduleRoot: File? = null
        while (dir != null && moduleRoot == null) {
            if (File(dir, "src/commonMain/kotlin").isDirectory) moduleRoot = dir else dir = dir.parentFile
        }
        assertTrue(moduleRoot != null, "could not locate src/commonMain/kotlin from ${System.getProperty("user.dir")}")
        return listOf(File(moduleRoot!!, "src/commonMain/kotlin"), File(moduleRoot, "src/androidMain/kotlin"))
            .filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
    }

    private val sessionSource: String by lazy {
        mainSources().first { it.name == "PlaybackSession.kt" }.readText()
    }

    // ── the post-collapse late-binding discipline ────────────────────────────

    @Test
    fun `the composition root declares no late-bound cycle slots`() {
        // The former builder bound five `…Ref` slots inside arm() (phase 2) to
        // break six mutual-recursion construction cycles. The collapse
        // dissolved the cycles by OWNERSHIP instead (the projector owns its
        // detail projection; the reporter/hooks/track-helper reach the session
        // through self-references; the forward references are compile-time
        // severed) — so a `lateinit` collaborator slot in the composition
        // root is a resurrected cycle, not a refactor tool.
        val slots = Regex("""private lateinit var \w+Ref:""").findAll(sessionSource).toList()
        assertEquals(
            0,
            slots.size,
            "the composition root must declare no late-bound back-reference slots (…Ref) — " +
                "the construction cycles dissolved by ownership; a new slot is a resurrected " +
                "cycle. Found: ${slots.map { it.value }}",
        )
    }

    // ── the hook-body order (ported from the deleted
    //    VideoSessionHostTest — the bodies became session methods) ─────────────

    @Test
    fun `resetForNewItem runs the synchronous prefix in the load-bearing order`() {
        val body = Regex("""override fun resetForNewItem\(selection: MediaStreamSelection\) \{[\s\S]*?\n    \}""")
            .find(sessionSource)?.value
        assertTrue(body != null, "resetForNewItem body not found in PlaybackSession")
        val anchor = body!!.withIndexSequence(
            "autoplayController.resetForNewItem()",
            "stillWatching.resetForItem()",
            "autoplayCancelled = false",
            "engineEventCoordinator.onNewItem()",
            "trackSelectionHelper.setPendingStreams(selection)",
        )
        assertTrue(
            anchor.zipWithNext().all { (a, b) -> a >= 0 && a < b },
            "resetForNewItem's synchronous prefix drifted: autoplay reset → still-watching reset → " +
                "autoplay-cancelled clear → coordinator new-item latch → pending stream seed (LAST). " +
                "Statement indices: $anchor",
        )
    }

    @Test
    fun `onPlayheadSeeded preSeedsTheSession before the positive-ticks chip gate`() {
        val preSeed = sessionSource.indexOf("preSeedPlayhead(startPositionTicks)")
        val chip = sessionSource.indexOf("resumeReminder.tryEmit(startPositionTicks / 10_000)")
        assertTrue(preSeed >= 0, "the session pre-seed write is missing from onPlayheadSeeded")
        assertTrue(chip >= 0, "the Resumed—Restart chip emission is missing from onPlayheadSeeded")
        assertTrue(
            preSeed < chip,
            "the session playhead pre-seed must run before the resume chip gate (the mirror-then-" +
                "notify order the former host pinned)",
        )
        assertTrue(
            Regex("""if \(startPositionTicks > 0\) \{\s*resumeReminder\.tryEmit""").containsMatchIn(sessionSource),
            "the resume chip must stay gated on non-zero resolved ticks (a fresh start raises no chip)",
        )
    }

    // ── the deletion itself ──────────────────────────────────────────────────

    @Test
    fun `videoSessionHost stays deleted`() {
        for (file in mainSources()) {
            val text = file.sourceTextStripComments()
            assertTrue(
                !text.contains("class VideoSessionHost") && !text.contains("videoSessionHost"),
                "${file.name} references VideoSessionHost — the pass-through layer was deleted; " +
                    "its seams are the composition root's SessionLoadOutputs/SessionLifecycleHooks " +
                    "implementations. Reintroducing it re-creates the mutual-recursion " +
                    "construction cycles this refactor unwound.",
            )
        }
    }

    private fun File.sourceTextStripComments(): String {
        val text = readText(Charsets.UTF_8)
        val out = StringBuilder()
        var i = 0
        var inBlock = false
        while (i < text.length) {
            val c = text[i]
            val next = if (i + 1 < text.length) text[i + 1] else ' '
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false
                    i++
                }
            } else if (c == '/' && next == '*') {
                inBlock = true
                i++
            } else {
                out.append(c)
            }
            i++
        }
        return out.toString()
    }
}

/** Indices of each needle in the source, in order — for ascending-order pins. */
private fun String.withIndexSequence(vararg needles: String): List<Int> =
    needles.map { indexOf(it) }
