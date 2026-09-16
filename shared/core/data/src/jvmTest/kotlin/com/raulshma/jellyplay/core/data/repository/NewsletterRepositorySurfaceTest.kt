package com.raulshma.jellyplay.core.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Surface ratchet for [NewsletterRepository] (the PlaybackRepositorySurfaceTest
 * precedent): this family seam is the feature-visible adapter the
 * MediaRepository union shrink left behind, and every member on it is
 * coupling its consumer (the newsletter feature's view model) pays. This
 * test parses the interface's source, counts its members (fun/val
 * declarations, overloads counted separately — matching the impl's override
 * count), and pins that count so it can only move DOWN.
 *
 * Baseline 3 is the count at the MediaRepository facade split: the three
 * members moved verbatim from MediaRepositoryImpl to
 * [NewsletterRepositoryImpl] (one-line forwards over the MediaInfoApiClient
 * family seam — the family owns no cache state). Two of the three wait on
 * unimplemented backend routes (see the NOTEs kept on both the interface
 * and the impl); when those routes land or die, retire or keep the members
 * accordingly and lower the baseline if the surface shrinks.
 *
 * Lower [maxInterfaceMembers] when a member retires; never raise it. A new
 * newsletter capability should land as a narrow collaborator over the
 * MediaInfoApiClient family seam rather than growing this surface.
 */
class NewsletterRepositorySurfaceTest {

    /** The maximum allowed member count of [NewsletterRepository] (see class KDoc). */
    private val maxInterfaceMembers = 3

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
            "src/commonMain/kotlin/com/raulshma/jellyplay/core/data/repository/NewsletterRepository.kt",
        )
        assertTrue(file.isFile, "NewsletterRepository.kt not found at ${file.path} — the ratchet is vacuous")
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

    /** The comment-stripped body of `interface NewsletterRepository { … }`. */
    private fun interfaceBody(): String {
        val src = interfaceSource().readText(Charsets.UTF_8).stripCommentsAndStrings()
        val header = src.indexOf("interface NewsletterRepository")
        assertTrue(header >= 0, "interface NewsletterRepository declaration not found")
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
            "NewsletterRepository grew to $count members (ratchet baseline $maxInterfaceMembers). " +
                "Retire a member or add a narrow collaborator over the MediaInfoApiClient family " +
                "seam instead; lower the baseline only when the surface shrinks — never raise it.",
        )
    }

    @Test
    fun `interface body parse actually sees members`() {
        val body = interfaceBody()
        // Sanity: the parse actually sees declarations, so an empty/false
        // body can never satisfy the ratchet above. No members have been
        // retired from this surface yet, so there is no absence list to pin
        // (the PlaybackRepository precedent's second half) — this guard is
        // the whole vacuity defense until one retires.
        assertTrue(
            countMembers(body) > 0 && body.contains("getNewsletterData") && body.contains("sendTestNewsletter"),
            "interface body parse found no members — the ratchet is vacuous",
        )
    }
}
