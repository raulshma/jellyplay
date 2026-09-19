package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.download.ActiveDownloadCount
import com.raulshma.jellyplay.core.data.download.DownloadQueue
import com.raulshma.jellyplay.core.data.download.SeriesEpisodeDownloads
import com.raulshma.jellyplay.core.data.download.TrackDownloadStatusWindow
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Surface ratchet for [DownloadRepository] + the four promoted read seams its
 * engine implements directly (the PlaybackRepositorySurfaceTest precedent).
 *
 * Two surfaces are pinned:
 *
 *  1. **[DownloadRepository] itself (27)** — the downloads lifecycle/status/
 *     series-batch seam. Its inherited write port
 *     ([OfflineDownloadWriter], the 9-member artifact bundle surface the
 *     delegate consumes) is deliberately NOT counted here: that port has its
 *     own narrow contract and its own consumer, so its member count is a
 *     different budget, not part of this interface's.
 *  2. **The four promoted read seams (+4)** — [DownloadQueue] (11),
 *     [TrackDownloadStatusWindow] (3), [ActiveDownloadCount] (1),
 *     [SeriesEpisodeDownloads] (1): the DownloadIntake-precedent surfaces
 *     `DownloadRepositoryImpl` implements DIRECTLY (commonMain-crossing reads
 *     with no per-read adapters). They are the feature-facing downloads
 *     surface, so they ratchet here too.
 *
 * Baselines are the counts at the ratchet's introduction. Lower a baseline
 * when its surface shrinks; never raise it. A genuinely new download read
 * should land as a new narrow commonMain seam (the DownloadIntake precedent)
 * rather than growing any of these.
 */
class DownloadRepositorySurfaceTest {

    /** The maximum allowed member count of [DownloadRepository] (see class KDoc). */
    private val maxInterfaceMembers = 27

    /**
     * The maximum allowed member counts of the four promoted read seams the
     * engine implements directly (see class KDoc).
     */
    private val maxSeamMembers = mapOf(
        "DownloadQueue" to 11,
        "TrackDownloadStatusWindow" to 3,
        "ActiveDownloadCount" to 1,
        "SeriesEpisodeDownloads" to 1,
    )

    /** Which source file declares each ratcheted interface (relative to the module root). */
    private val interfaceFiles = mapOf(
        "DownloadRepository" to "src/jvmShared/kotlin/com/raulshma/jellyplay/core/data/repository/DownloadRepository.kt",
        "DownloadQueue" to "src/commonMain/kotlin/com/raulshma/jellyplay/core/data/download/DownloadQueue.kt",
        "TrackDownloadStatusWindow" to "src/commonMain/kotlin/com/raulshma/jellyplay/core/data/download/TrackDownloadActions.kt",
        "ActiveDownloadCount" to "src/commonMain/kotlin/com/raulshma/jellyplay/core/data/download/ActiveDownloadCount.kt",
        "SeriesEpisodeDownloads" to "src/commonMain/kotlin/com/raulshma/jellyplay/core/data/download/SeriesEpisodeDownloads.kt",
    )

    /** Walks up from the working dir to the module root that owns src/commonMain/kotlin. */
    private fun moduleRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "src/commonMain/kotlin").isDirectory) dir = dir.parentFile
        assertTrue(dir != null, "could not locate src/commonMain/kotlin from ${System.getProperty("user.dir")}")
        return dir
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

    /** The comment-stripped body of `interface <name> { … }` in its declaring file. */
    private fun interfaceBody(name: String): String {
        val file = File(moduleRoot(), interfaceFiles.getValue(name))
        assertTrue(file.isFile, "$name not found at ${file.path} — the ratchet is vacuous")
        val src = file.readText(Charsets.UTF_8).stripCommentsAndStrings()
        val header = src.indexOf("interface $name")
        assertTrue(header >= 0, "interface $name declaration not found")
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
     * Counts member declarations: fun/val at the start of a line (the files'
     * style puts every member on its own line), overloads counted per
     * declaration. Default parameter values never start a line, so they
     * cannot inflate the count.
     */
    private fun countMembers(body: String): Int =
        Regex("""(?m)^\s*(?:suspend\s+)?(?:fun|val)\s""").findAll(body).count()

    @Test
    fun `interface member count never increases`() {
        val count = countMembers(interfaceBody("DownloadRepository"))
        assertTrue(
            count <= maxInterfaceMembers,
            "DownloadRepository grew to $count members (ratchet baseline $maxInterfaceMembers). " +
                "Retire a member or add a narrow collaborator instead; lower the baseline only when " +
                "the surface shrinks — never raise it.",
        )
    }

    @Test
    fun `promoted read seams never grow`() {
        maxSeamMembers.forEach { (name, cap) ->
            val count = countMembers(interfaceBody(name))
            assertTrue(
                count <= cap,
                "$name grew to $count members (ratchet baseline $cap). These are the promoted " +
                    "feature-facing read seams; retire or narrow a member instead — lower the " +
                    "baseline only when the surface shrinks, never raise it.",
            )
        }
        // Sanity: the parse actually sees declarations, so an empty/false
        // body can never satisfy the ratchets above.
        assertTrue(
            countMembers(interfaceBody("DownloadRepository")) > 0 &&
                interfaceBody("DownloadRepository").contains("getAllDownloads") &&
                interfaceBody("TrackDownloadStatusWindow").contains("downloadsFor"),
            "interface body parse found no members — the ratchet is vacuous",
        )
    }
}
