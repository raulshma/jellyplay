package com.raulshma.jellyplay.core.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Surface ratchet for [PluginAdminRepository] (the LiveTvRepositorySurfaceTest
 * precedent): this family seam is what the AdminRepository facade split left
 * behind for the plugins screens (list, detail, config), and every member on
 * it is coupling its consumers pay. This test parses the interface's source,
 * counts its members (fun/val declarations, overloads counted separately —
 * matching the impl's override count), and pins that count so it can only
 * move DOWN.
 *
 * Baseline 12 is the count at the split: the twelve members moved verbatim
 * from AdminRepository to [PluginAdminRepositoryImpl] (stateless forwards
 * over the PluginApiClient family seam plus the family's two compositions —
 * config-page resolution and the WebView bridge session). The interface
 * lives in jvmShared — the plugin screens are a jvm/android feature, so the
 * seam never crossed to commonMain.
 *
 * Lower [maxInterfaceMembers] when another member retires; never raise it.
 * A genuinely new plugin capability should land as a narrow collaborator
 * over the API family client rather than growing this surface.
 */
class PluginAdminRepositorySurfaceTest {

    /** The maximum allowed member count of [PluginAdminRepository] (see class KDoc). */
    private val maxInterfaceMembers = 12

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
            "src/jvmShared/kotlin/com/raulshma/jellyplay/core/data/repository/PluginAdminRepository.kt",
        )
        assertTrue(file.isFile, "PluginAdminRepository.kt not found at ${file.path} — the ratchet is vacuous")
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

    /** The comment-stripped body of `interface PluginAdminRepository { … }`. */
    private fun interfaceBody(): String {
        val src = interfaceSource().readText(Charsets.UTF_8).stripCommentsAndStrings()
        val header = src.indexOf("interface PluginAdminRepository")
        assertTrue(header >= 0, "interface PluginAdminRepository declaration not found")
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
            "PluginAdminRepository grew to $count members (ratchet baseline $maxInterfaceMembers). " +
                "Retire a member or add a narrow collaborator instead; lower the baseline only when " +
                "the surface shrinks — never raise it.",
        )
    }

    @Test
    fun `ratchet parses real members`() {
        // Sanity: the parse actually sees declarations, so an empty/false
        // body can never satisfy the ratchet above.
        val body = interfaceBody()
        assertTrue(
            countMembers(body) > 0 && body.contains("getInstalledPlugins"),
            "interface body parse found no members — the ratchet is vacuous",
        )
    }
}
