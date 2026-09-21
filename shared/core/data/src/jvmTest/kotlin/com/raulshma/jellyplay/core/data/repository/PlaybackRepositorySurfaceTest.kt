package com.raulshma.jellyplay.core.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Surface ratchet for [PlaybackRepository]: the interface is consumed by a
 * wide fan of player/detail/insight features, so every member added to it is
 * coupling paid across all of them. This test parses the interface's source,
 * counts its members (fun/val declarations, overloads counted separately —
 * matching the impl's override count), and pins that count so it can only
 * move DOWN.
 *
 * Baseline 25 is the count right after the session-credential readers
 * (`getServerUrl` / `getAccessToken`) were retired from the interface into the
 * narrow [com.raulshma.jellyplay.core.data.playback.PlaybackIdentity] module
 * (their four readers inject that instead), which itself followed the dead
 * intro/credit timestamp readers (`getIntroTimestamps` / `getCreditTimestamps`)
 * retirement: those had zero external callers, and the impl kept the
 * getMediaSegments legacy fallback by calling [com.raulshma.jellyplay.core.network.api.PlaybackApiClient]
 * directly. They must stay retired — a re-added member under the count cap
 * would silently undo that retirement, so the second test pins their absence
 * explicitly.
 *
 * Lower [maxInterfaceMembers] when another member retires; never raise it. A
 * genuinely new playback capability should land as a narrow collaborator
 * (see PlaybackRepositoryImpl's family-seam constructor) rather than growing
 * this surface.
 */
class PlaybackRepositorySurfaceTest {

    /** The maximum allowed member count of [PlaybackRepository] (see class KDoc). */
    private val maxInterfaceMembers = 25

    /** Members retired from the interface; their re-addition must fail this suite. */
    private val retiredMembers = listOf(
        "getIntroTimestamps",
        "getCreditTimestamps",
        "getServerUrl",
        "getAccessToken",
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
            "src/commonMain/kotlin/com/raulshma/jellyplay/core/data/repository/PlaybackRepository.kt",
        )
        assertTrue(file.isFile, "PlaybackRepository.kt not found at ${file.path} — the ratchet is vacuous")
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

    /** The comment-stripped body of `interface PlaybackRepository { … }`. */
    private fun interfaceBody(): String {
        val src = interfaceSource().readText(Charsets.UTF_8).stripCommentsAndStrings()
        val header = src.indexOf("interface PlaybackRepository")
        assertTrue(header >= 0, "interface PlaybackRepository declaration not found")
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
            "PlaybackRepository grew to $count members (ratchet baseline $maxInterfaceMembers). " +
                "Retire a member or add a narrow collaborator instead; lower the baseline only when " +
                "the surface shrinks — never raise it.",
        )
    }

    @Test
    fun `retired intro and credit timestamp members stay retired`() {
        val body = interfaceBody()
        retiredMembers.forEach { member ->
            assertFalse(
                Regex("""\b$member\s*\(""").containsMatchIn(body),
                "$member reappeared on the PlaybackRepository surface — it was retired (zero external " +
                    "callers; the impl's getMediaSegments fallback reads PlaybackApiClient directly). " +
                    "Do not reintroduce it as an interface member.",
            )
        }
        // Sanity: the parse actually sees declarations, so an empty/false
        // body can never satisfy the ratchet above.
        assertTrue(
            countMembers(body) > 0 && body.contains("reportPlaybackStart"),
            "interface body parse found no members — the ratchet is vacuous",
        )
    }
}
