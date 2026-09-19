package com.raulshma.jellyplay.core.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Surface ratchet for [PlaybackOutboxRepository] (the MediaRepositorySurfaceTest
 * precedent): the interface is the offline-staging seam consumed by the
 * playback report path, the sync worker and the played-state reconcile, so
 * every member added to it is coupling paid across all of them. This test
 * parses the interface's source, counts its members (fun/val declarations,
 * overloads counted separately — matching the impl's override count,
 * default-body members included), and pins that count so it can only move
 * DOWN.
 *
 * Baseline 18 is the current count: 6 enqueue commands (START/PROGRESS/STOP,
 * book progress, played/favorite state flips), the drain + intent probes
 * and deletes (drain, hasUnsyncedPlayedIntent/hasUnsyncedUnplayedIntent,
 * deletePlayedStateIntents, the default-body isPlayedStateIntentDelivered,
 * delete, markDeadLetter, deleteForItem, deletePlaybackTelemetryForItem),
 * and the count + reactive pair (count, countFlow, getAllFlow).
 *
 * Lower [maxInterfaceMembers] when a member retires; never raise it. A
 * genuinely new outbox capability should land as a narrow collaborator
 * rather than growing this surface.
 */
class PlaybackOutboxRepositorySurfaceTest {

    /** The maximum allowed member count of [PlaybackOutboxRepository] (see class KDoc). */
    private val maxInterfaceMembers = 18

    /** Walks up from the working dir to the module root that owns src/commonMain/kotlin. */
    private fun moduleRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "src/commonMain/kotlin").isDirectory) dir = dir.parentFile
        assertTrue(dir != null, "could not locate src/commonMain/kotlin from ${System.getProperty("user.dir")}")
        return dir
    }

    private fun interfaceSource(): File {
        val file = File(
            moduleRoot(),
            "src/commonMain/kotlin/com/raulshma/jellyplay/core/data/repository/PlaybackOutboxRepository.kt",
        )
        assertTrue(file.isFile, "PlaybackOutboxRepository.kt not found at ${file.path} — the ratchet is vacuous")
        return file
    }

    /** Strips line/block comments and string/char literals so the scan reads code, not prose. */
    private fun String.stripCommentsAndStrings(): String {
        val out = StringBuilder(length)
        var i = 0
        var inLine = false
        var inBlock = false
        var inString = false
        var inChar = false
        while (i < length) {
            val c = this[i]
            val next = if (i + 1 < length) this[i + 1] else ' '
            when {
                inLine -> if (c == '\n') { inLine = false; out.append(c) }
                inBlock -> if (c == '*' && next == '/') { inBlock = false; i++ }
                inString -> when {
                    c == '\\' -> i++
                    c == '"' -> inString = false
                }
                inChar -> when {
                    c == '\\' -> i++
                    c == '\'' -> inChar = false
                }
                c == '/' && next == '/' -> { inLine = true; i++ }
                c == '/' && next == '*' -> { inBlock = true; i++ }
                c == '"' -> { inString = true; out.append(' ') }
                c == '\'' -> { inChar = true; out.append(' ') }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    /** The comment-stripped body of `interface PlaybackOutboxRepository { … }`. */
    private fun interfaceBody(): String {
        val src = interfaceSource().readText(Charsets.UTF_8).stripCommentsAndStrings()
        val header = src.indexOf("interface PlaybackOutboxRepository")
        assertTrue(header >= 0, "interface PlaybackOutboxRepository declaration not found")
        val open = src.indexOf('{', header)
        assertTrue(open >= 0, "interface body opening brace not found")
        var depth = 0
        var i = open
        while (i < src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return src.substring(open + 1, i) }
            }
            i++
        }
        error("interface body closing brace not found")
    }

    /**
     * Counts member declarations: fun/val at the start of a line (the file's
     * style puts every member on its own line), overloads counted per
     * declaration. Default parameter values and default-body members'
     * continuation lines never start a line, so they cannot inflate the
     * count.
     */
    private fun countMembers(body: String): Int =
        Regex("""(?m)^\s*(?:suspend\s+)?(?:fun|val)\s""").findAll(body).count()

    @Test
    fun `interface member count never increases`() {
        val body = interfaceBody()
        val count = countMembers(body)
        assertTrue(
            count <= maxInterfaceMembers,
            "PlaybackOutboxRepository grew to $count members (ratchet baseline $maxInterfaceMembers). " +
                "Retire a member or add a narrow collaborator instead; lower the baseline only " +
                "when the surface shrinks — never raise it.",
        )
        // Sanity: the parse actually sees declarations, so an empty/false
        // body can never satisfy the ratchet above.
        assertTrue(
            count > 0 && body.contains("enqueueStart"),
            "interface body parse found no members — the ratchet is vacuous",
        )
    }
}
