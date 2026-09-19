package com.raulshma.jellyplay.core.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Surface ratchet for [SeerrRepository] (the PlaybackRepositorySurfaceTest
 * precedent): the interface is consumed by the requests/settings/details/
 * search/discover feature family plus the Arr server-discovery merge, so
 * every member added to it is coupling paid across all of them. This test
 * parses the interface's source, counts its members (fun/val declarations,
 * overloads counted separately — matching the impl's override count), and
 * pins that count so it can only move DOWN.
 *
 * Baseline 43 is the count after the `/settings/` detail pair retired: the
 * `getRadarrServiceDetail`/`getSonarrServiceDetail` forwards had zero
 * callers (the kind-parameterized `getServiceDetail(id, kind)` over the
 * `SeerrServiceDetail` sealed parent is the only consumer path; the
 * api-client pair stays for its own surface). Earlier, the
 * `getServiceRadarrDetail`/`getServiceSonarrDetail` kind pair (two members
 * differing only in kind, payloads mirrored field-for-field under the
 * sealed parent) folded into that single member. Both retirements must
 * stay — a re-added twin under the count cap would silently undo them, so
 * the second test pins their absence explicitly. The remaining
 * Radarr/Sonarr pairs (`getRadarrSettings`/`getSonarrSettings` with
 * differing list element types, the servers pair) are the known next folds
 * where a clean shape exists.
 *
 * Lower [maxInterfaceMembers] when a pair folds or a member retires; never
 * raise it. A genuinely new Seerr capability should land as a narrow
 * collaborator rather than growing this surface.
 */
class SeerrRepositorySurfaceTest {

    /** The maximum allowed member count of [SeerrRepository] (see class KDoc). */
    private val maxInterfaceMembers = 43

    /** Members folded away from the interface; their re-addition must fail this suite. */
    private val retiredMembers = listOf(
        "getServiceRadarrDetail",
        "getServiceSonarrDetail",
        "getRadarrServiceDetail",
        "getSonarrServiceDetail",
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
            "src/commonMain/kotlin/com/raulshma/jellyplay/core/data/repository/SeerrRepository.kt",
        )
        assertTrue(file.isFile, "SeerrRepository.kt not found at ${file.path} — the ratchet is vacuous")
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

    /** The comment-stripped body of `interface SeerrRepository { … }`. */
    private fun interfaceBody(): String {
        val src = interfaceSource().readText(Charsets.UTF_8).stripCommentsAndStrings()
        val header = src.indexOf("interface SeerrRepository")
        assertTrue(header >= 0, "interface SeerrRepository declaration not found")
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
            "SeerrRepository grew to $count members (ratchet baseline $maxInterfaceMembers). " +
                "Fold a kind pair, retire a member, or add a narrow collaborator instead; lower the " +
                "baseline only when the surface shrinks — never raise it.",
        )
    }

    @Test
    fun `folded kind-pair members stay folded`() {
        val body = interfaceBody()
        retiredMembers.forEach { member ->
            assertFalse(
                Regex("""\b$member\s*\(""").containsMatchIn(body),
                "$member reappeared on the SeerrRepository surface — it was folded into the " +
                    "kind-parameterized getServiceDetail(id, kind) over the SeerrServiceDetail " +
                    "sealed parent. Do not reintroduce the kind twin as an interface member.",
            )
        }
        // Sanity: the parse actually sees declarations, so an empty/false
        // body can never satisfy the ratchet above.
        assertTrue(
            countMembers(body) > 0 && body.contains("requestMedia"),
            "interface body parse found no members — the ratchet is vacuous",
        )
    }
}
