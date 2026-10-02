package com.raulshma.jellyplay.feature.player.video

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Composition ratchet for [PlayerWiring], the two-phase builder that replaced
 * the VM's ~1,400 lines of in-class collaborator wiring AND the deleted
 * [VideoSessionHost] pass-through layer. [VideoPlayerViewModel] (like the
 * former host) is too heavy to construct in jvmTest — a 30+-dependency
 * constructor — so, the [SeekUserInitiatedWiringTest] /
 * [ControllerOwnershipTest] precedent, this pins the composition at the
 * source. The behavioral pins that COULD leave the builder moved to real
 * suites when their subjects moved: the load spine's offline-first segments
 * fetch, the cinema gate's vetoes and the remembered-muted
 * mirror-then-engine order are pinned in [SessionLoadPipelineTest] (they are
 * pipeline members now), and the server start report's incognito gate +
 * session-id resolution in PlaybackSessionReportingTest (a session member).
 * What this suite pins is what only the builder still owns:
 *
 *  1. the late-binding discipline — each of the five cycle slots is declared
 *     exactly once, bound exactly once, and bound inside [PlayerWiring.arm]
 *     (phase 2), so the six mutual-recursion construction cycles stay dead;
 *  2. the arm-phase hook-body ORDER that lost its behavioral test with the
 *     host: `resetForNewItem`'s synchronous prefix (autoplay reset →
 *     still-watching reset → the autoplay-cancelled mirror clear → the
 *     coordinator's new-item latch → the pending stream seed) and
 *     `onPlayheadSeeded`'s session pre-seed before the >0 chip gate;
 *  3. the deletion itself — no main source declares or references
 *     VideoSessionHost (a resurrection is the god-object regression this
 *     whole refactor unwinds).
 *
 * If a reformat trips a regex here, the assertion message names the contract
 * to restore — never delete an assertion to make a reformat pass.
 */
class PlayerWiringCompositionTest {

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

    private val wiringSource: String by lazy {
        mainSources().first { it.name == "PlayerWiring.kt" }.readText()
    }

    // ── the late-binding discipline ──────────────────────────────────────────

    @Test
    fun `each cycle slot is declared once and bound exactly once inside arm`() {
        val armIndex = wiringSource.indexOf("    fun arm() {")
        assertTrue(armIndex >= 0, "PlayerWiring.arm() not found — the two-phase entry point moved?")

        for (slot in listOf(
            "mediaDetailProjectionRef" to "mediaDetailProjection", // cycle 1
            "playbackSessionRef" to "playbackSession",             // cycles 2 + 3
            "episodeContinuationRef" to "episodeContinuation",     // cycle 3
            "playbackPreferenceWriterRef" to "playbackPreferenceWriter", // cycle 5
            "engineConfigSyncRef" to "engineConfigSync",           // cycle 6
        )) {
            val (name, collaborator) = slot
            val binding = "$name = $collaborator"
            assertEquals(
                1,
                Regex(Regex.escape("private lateinit var $name:")).findAll(wiringSource).count(),
                "cycle slot $name must be declared exactly once (a second declaration is a new cycle)",
            )
            val bindingIndex = wiringSource.indexOf(binding)
            assertTrue(
                bindingIndex >= 0,
                "cycle slot $name must be bound to $collaborator exactly once (the late-binding contract)",
            )
            assertTrue(
                wiringSource.indexOf(binding, bindingIndex + 1) < 0,
                "cycle slot $name is bound more than once — slots are write-once",
            )
            assertTrue(
                bindingIndex > armIndex,
                "cycle slot $name must be bound inside arm() (phase 2), not during phase 1 construction",
            )
        }
    }

    // ── the arm-phase hook-body order (ported from the deleted
    //    VideoSessionHostTest — the bodies became builder methods) ────────────

    @Test
    fun `resetForNewItem runs the synchronous prefix in the load-bearing order`() {
        val body = Regex("""override fun resetForNewItem\(selection: MediaStreamSelection\) \{[\s\S]*?\n    \}""")
            .find(wiringSource)?.value
        assertTrue(body != null, "resetForNewItem body not found in PlayerWiring")
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
        val preSeed = wiringSource.indexOf("playbackSessionRef.preSeedPlayhead(startPositionTicks)")
        val chip = wiringSource.indexOf("resumeReminder.tryEmit(startPositionTicks / 10_000)")
        assertTrue(preSeed >= 0, "the session pre-seed write is missing from onPlayheadSeeded")
        assertTrue(chip >= 0, "the Resumed—Restart chip emission is missing from onPlayheadSeeded")
        assertTrue(
            preSeed < chip,
            "the session playhead pre-seed must run before the resume chip gate (the mirror-then-" +
                "notify order the former host pinned)",
        )
        assertTrue(
            Regex("""if \(startPositionTicks > 0\) \{\s*resumeReminder\.tryEmit""").containsMatchIn(wiringSource),
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
                    "its seams are [PlayerWiring]'s SessionLoadOutputs/SessionLifecycleHooks " +
                    "implementations and its late-bound slots. Reintroducing it re-creates the " +
                    "mutual-recursion construction cycles this refactor unwound.",
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
