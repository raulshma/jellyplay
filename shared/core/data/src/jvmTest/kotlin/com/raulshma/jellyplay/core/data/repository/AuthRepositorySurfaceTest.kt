package com.raulshma.jellyplay.core.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Surface ratchet for [AuthRepository] (the MediaRepositorySurfaceTest
 * precedent): the interface is the feature-visible auth seam consumed by
 * login/settings/server-switching across every shell, so every member added
 * to it is coupling paid across all of them. This test parses the
 * interface's source, counts its members (fun/val declarations, overloads
 * counted separately — matching the impl's override count), and pins that
 * count so it can only move DOWN.
 *
 * Baseline 26 is the current count: 5 flow/StateFlow properties plus 21
 * suspend commands (server add/remove/switch/address management, the
 * password + QuickConnect login family, session restore/refresh/revoke,
 * user switching, capabilities posting).
 *
 * Lower [maxInterfaceMembers] when a member retires; never raise it. A
 * genuinely new auth capability should land as a narrow collaborator rather
 * than growing this surface.
 */
class AuthRepositorySurfaceTest {

    /** The maximum allowed member count of [AuthRepository] (see class KDoc). */
    private val maxInterfaceMembers = 26

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
            "src/commonMain/kotlin/com/raulshma/jellyplay/core/data/repository/AuthRepository.kt",
        )
        assertTrue(file.isFile, "AuthRepository.kt not found at ${file.path} — the ratchet is vacuous")
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

    /** The comment-stripped body of `interface AuthRepository { … }`. */
    private fun interfaceBody(): String {
        val src = interfaceSource().readText(Charsets.UTF_8).stripCommentsAndStrings()
        val header = src.indexOf("interface AuthRepository")
        assertTrue(header >= 0, "interface AuthRepository declaration not found")
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
        val body = interfaceBody()
        val count = countMembers(body)
        assertTrue(
            count <= maxInterfaceMembers,
            "AuthRepository grew to $count members (ratchet baseline $maxInterfaceMembers). " +
                "Retire a member or add a narrow collaborator instead; lower the baseline only " +
                "when the surface shrinks — never raise it.",
        )
        // Sanity: the parse actually sees declarations, so an empty/false
        // body can never satisfy the ratchet above.
        assertTrue(
            count > 0 && body.contains("restoreSession"),
            "interface body parse found no members — the ratchet is vacuous",
        )
    }
}
