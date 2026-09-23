package com.raulshma.jellyplay.core.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Surface ratchet for [SyncPlayRepository] (the PlaybackRepositorySurfaceTest
 * precedent): this narrow family seam is the feature-visible adapter the
 * MediaRepository union shrink left behind, and every member on it is
 * coupling its consumers (`SyncPlayViewModel`, `WatchPartyActions`) pay. This
 * test parses the interface's source, counts its members (fun/val
 * declarations, overloads counted separately — matching the impl's override
 * count), and pins that count so it can only move DOWN.
 *
 * Baseline 11 was the count right after the dead wire-command forwards were
 * retired from the seam: `joinSyncPlayGroup`, `leaveSyncPlayGroup`,
 * `syncPlayReady`, `syncPlayNextItem`, `syncPlayPreviousItem`,
 * `syncPlayRemoveFromPlaylist`, and `syncPlayMovePlaylistItem` had zero
 * repository-typed call sites (join/leave go through SyncPlayManager's
 * direct api-client use; the fire-and-forget reports go through
 * SyncPlayController), and the repository `syncPlayReady` silently dropped
 * `whenMs` — the impl fell back to an uncorrected wall clock, so deletion
 * killed that drift too. A re-added member under the count cap would
 * silently undo that retirement, so the second test pins their absence
 * explicitly.
 *
 * Baseline 4 is the count after the second (transport command) census: the
 * ignored-Result transport commands (`syncPlayPause`, `syncPlayUnpause`,
 * `syncPlaySeek`, `syncPlayStop`, `syncPlaySetRepeatMode`,
 * `syncPlaySetShuffleMode`, `syncPlaySetIgnoreWait` — their only
 * repository-typed caller, `SyncPlayViewModel`, launched them and dropped
 * the Result) retired from the seam and converged on SyncPlayController's
 * `safe()` via the feature-local `SyncPlaySession` seam. The nuance the
 * remaining surface records: the repository seam carries the commands its
 * UI consumers AWAIT — `syncPlaySetNewQueue` stays because
 * `WatchPartyActions.start` aborts its bootstrap on a failed Result.
 *
 * Lower [maxInterfaceMembers] when another member retires; never raise it. A
 * new SyncPlay capability that player plumbing fires without awaiting should
 * land on SyncPlayController (the fire-and-forget wrapper home), not here.
 */
class SyncPlayRepositorySurfaceTest {

    /** The maximum allowed member count of [SyncPlayRepository] (see class KDoc). */
    private val maxInterfaceMembers = 4

    /** Members retired from the interface; their re-addition must fail this suite. */
    private val retiredMembers = listOf(
        "joinSyncPlayGroup",
        "leaveSyncPlayGroup",
        "syncPlayReady",
        "syncPlayNextItem",
        "syncPlayPreviousItem",
        "syncPlayRemoveFromPlaylist",
        "syncPlayMovePlaylistItem",
        "syncPlayPause",
        "syncPlayUnpause",
        "syncPlaySeek",
        "syncPlayStop",
        "syncPlaySetRepeatMode",
        "syncPlaySetShuffleMode",
        "syncPlaySetIgnoreWait",
    )

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
            "src/commonMain/kotlin/com/raulshma/jellyplay/core/data/repository/SyncPlayRepository.kt",
        )
        assertTrue(file.isFile, "SyncPlayRepository.kt not found at ${file.path} — the ratchet is vacuous")
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

    /** The comment-stripped body of `interface SyncPlayRepository { … }`. */
    private fun interfaceBody(): String {
        val src = interfaceSource().readText(Charsets.UTF_8).stripCommentsAndStrings()
        val header = src.indexOf("interface SyncPlayRepository")
        assertTrue(header >= 0, "interface SyncPlayRepository declaration not found")
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
     * declaration. Default parameter values never start a line, so they
     * cannot inflate the count.
     */
    private fun countMembers(body: String): Int =
        Regex("""(?m)^\s*(?:suspend\s+)?(?:fun|val)\s""").findAll(body).count()

    @Test
    fun `interface member count never increases`() {
        val count = countMembers(interfaceBody())
        assertTrue(
            count <= maxInterfaceMembers,
            "SyncPlayRepository grew to $count members (ratchet baseline $maxInterfaceMembers). " +
                "Retire a member or route the command through SyncPlayController instead; lower the " +
                "baseline only when the surface shrinks — never raise it.",
        )
    }

    @Test
    fun `retired wire-command forwards stay retired`() {
        val body = interfaceBody()
        retiredMembers.forEach { member ->
            assertFalse(
                Regex("""\b$member\s*\(""").containsMatchIn(body),
                "$member reappeared on the SyncPlayRepository surface — it was retired (zero " +
                    "awaiting repository-typed callers; join/leave go through SyncPlayManager's api " +
                    "client, fire-and-forget reports and transport commands through " +
                    "SyncPlayController's safe()). Do not reintroduce it as an interface member.",
            )
        }
        // Sanity: the parse actually sees declarations, so an empty/false
        // body can never satisfy the ratchet above.
        assertTrue(
            countMembers(body) > 0 && body.contains("getSyncPlayGroups"),
            "interface body parse found no members — the ratchet is vacuous",
        )
    }
}
