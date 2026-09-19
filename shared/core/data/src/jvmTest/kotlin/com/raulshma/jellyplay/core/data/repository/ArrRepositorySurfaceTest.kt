package com.raulshma.jellyplay.core.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Surface ratchet for [ArrRepository] (the PlaybackRepositorySurfaceTest
 * precedent): the interface is the feature-visible Radarr+Sonarr aggregate
 * consumed by the requests/calendar/settings features behind the
 * DIRECT_ARR_INTEGRATION flag, so every member added to it is coupling paid
 * across all of them. This test parses the interface's source, counts its
 * members (fun/val declarations, overloads counted separately — matching the
 * impl's override count), and pins that count so it can only move DOWN.
 *
 * Baseline 27 is the count at the ratchet's introduction (the companion
 * object's `SERVER_CACHE_TTL_MS` const is not a member and does not count).
 *
 * Lower [maxInterfaceMembers] when another member retires; never raise it.
 * A genuinely new *arr capability should land as a narrow collaborator or a
 * new [com.raulshma.jellyplay.core.network.api.ArrServiceClient] method
 * behind the existing dispatch rather than growing this surface.
 */
class ArrRepositorySurfaceTest {

    /** The maximum allowed member count of [ArrRepository] (see class KDoc). */
    private val maxInterfaceMembers = 27

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
            "src/commonMain/kotlin/com/raulshma/jellyplay/core/data/repository/ArrRepository.kt",
        )
        assertTrue(file.isFile, "ArrRepository.kt not found at ${file.path} — the ratchet is vacuous")
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

    /** The comment-stripped body of `interface ArrRepository { … }`. */
    private fun interfaceBody(): String {
        val src = interfaceSource().readText(Charsets.UTF_8).stripCommentsAndStrings()
        val header = src.indexOf("interface ArrRepository")
        assertTrue(header >= 0, "interface ArrRepository declaration not found")
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
     * cannot inflate the count; the companion const is a `const val` at line
     * start and is excluded by the same convention the interface-body
     * extraction already applies (it lives outside the body).
     */
    private fun countMembers(body: String): Int =
        Regex("""(?m)^\s*(?:suspend\s+)?(?:fun|val)\s""").findAll(body).count()

    @Test
    fun `interface member count never increases`() {
        val count = countMembers(interfaceBody())
        assertTrue(
            count <= maxInterfaceMembers,
            "ArrRepository grew to $count members (ratchet baseline $maxInterfaceMembers). " +
                "Retire a member or add a narrow collaborator/client method instead; lower the " +
                "baseline only when the surface shrinks — never raise it.",
        )
    }

    @Test
    fun `ratchet parses real members`() {
        // Sanity: the parse actually sees declarations, so an empty/false
        // body can never satisfy the ratchet above.
        val body = interfaceBody()
        assertTrue(
            countMembers(body) > 0 && body.contains("resolveServers"),
            "interface body parse found no members — the ratchet is vacuous",
        )
    }
}
