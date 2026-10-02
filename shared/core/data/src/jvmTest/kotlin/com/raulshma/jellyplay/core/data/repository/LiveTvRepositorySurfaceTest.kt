package com.raulshma.jellyplay.core.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Surface ratchet for [LiveTvRepository] (the PlaybackRepositorySurfaceTest
 * precedent): this family seam is the feature-visible adapter the
 * MediaRepository union shrink left behind, and every member on it is
 * coupling its consumers (the livetv feature's channel/program/EPG/
 * recording/schedule hosts, the live player) pay. This test parses the
 * interface's source, counts its members (fun/val declarations, overloads
 * counted separately — matching the impl's override count), and pins that
 * count so it can only move DOWN.
 *
 * Baseline 1 since the pass-through mirror retired: the fourteen forwarded
 * members live on the extended [com.raulshma.jellyplay.core.network.api.LiveTvApiClient]
 * (interface inheritance — the interface re-declares nothing), and the one
 * member declared here is the family's real routing decision
 * ([LiveTvRepository.deleteRecording] → MediaInfoApiClient.deleteItem).
 *
 * Lower [maxInterfaceMembers] when another member retires; never raise it.
 * A genuinely new Live capability should land as a narrow collaborator over
 * the API family client rather than growing this surface.
 */
class LiveTvRepositorySurfaceTest {

    /** The maximum allowed member count of [LiveTvRepository] (see class KDoc). */
    private val maxInterfaceMembers = 1

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
            "src/commonMain/kotlin/com/raulshma/jellyplay/core/data/repository/LiveTvRepository.kt",
        )
        assertTrue(file.isFile, "LiveTvRepository.kt not found at ${file.path} — the ratchet is vacuous")
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

    /** The comment-stripped body of `interface LiveTvRepository { … }`. */
    private fun interfaceBody(): String {
        val src = interfaceSource().readText(Charsets.UTF_8).stripCommentsAndStrings()
        val header = src.indexOf("interface LiveTvRepository")
        assertTrue(header >= 0, "interface LiveTvRepository declaration not found")
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
            "LiveTvRepository grew to $count members (ratchet baseline $maxInterfaceMembers). " +
                "Retire a member or add a narrow collaborator over the API family client instead; " +
                "lower the baseline only when the surface shrinks — never raise it.",
        )
    }

    @Test
    fun `interface body parse actually sees members`() {
        val body = interfaceBody()
        // Sanity: the parse actually sees the routing member, so an
        // empty/false body can never satisfy the ratchet above — and the
        // routing decision must stay ON this surface (the client-inherited
        // fourteen ride the supertype, not this file).
        assertTrue(
            countMembers(body) > 0 && body.contains("deleteRecording"),
            "interface body parse found no members — the ratchet is vacuous",
        )
    }
}
