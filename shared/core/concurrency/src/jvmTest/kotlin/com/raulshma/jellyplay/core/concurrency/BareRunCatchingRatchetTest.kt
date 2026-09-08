package com.raulshma.jellyplay.core.concurrency

import kotlin.test.Test
import kotlin.test.assertTrue
import java.io.File

/**
 * Ratchet against reintroducing the cancellation-swallowing bug class: a bare
 * stdlib `runCatching` inside a `suspend fun` body captures
 * CancellationException like any other Throwable, masking structured
 * cancellation (a cancelled call reports as a failed Result instead of
 * stopping — broken worker retries, half-applied sync flips, zombie prewarms).
 * Five of the fifteen commits before 2026-09-07 were per-site fixes of exactly
 * this; the fix is [runCatchingRethrowingCancellation], and this test keeps
 * the converted set converted.
 *
 * Guarded module set: shared/core/data, shared/core/network,
 * shared/feature/home, shared/feature/player-video, the suspend-bearing
 * feature modules added by the 2026-09 folding wave (livetv, settings, shell,
 * downloads), the legacy core/data tree, core/notification, :app,
 * :apps:desktop, apps/web. Non-suspend bodies (pure JSON/enum/number parses
 * in mappers, framework glue) are legitimate stdlib `runCatching` territory
 * and simply don't count — the heuristic only counts occurrences inside
 * `suspend fun` bodies.
 *
 * Lower [maxBareRunCatchingInSuspendFuns] when another site converts; never
 * raise it. A legitimate NEW non-suspend use inside a suspend fun (parse
 * guards) should prefer extracting the parse into a non-suspend fun over
 * raising the baseline.
 */
class BareRunCatchingRatchetTest {

    private val maxBareRunCatchingInSuspendFuns = 27

    /** Module source roots guarded by the ratchet, relative to the repo root. */
    private val guardedRoots = listOf(
        "shared/core/data/src",
        "shared/core/network/src",
        "shared/feature/home/src",
        "shared/feature/player-video/src",
        "shared/feature/livetv/src",
        "shared/feature/settings/src",
        "shared/feature/shell/src",
        "shared/feature/downloads/src",
        "core/data/src/main",
        "core/notification/src/main",
        "app/src/main",
        "apps/desktop/src/main",
        "apps/web/src",
    )

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        assertTrue(dir != null, "could not locate settings.gradle.kts from ${System.getProperty("user.dir")}")
        return dir!!
    }

    private fun guardedSources(): List<File> {
        val root = repoRoot()
        return guardedRoots
            .map { File(root, it) }
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }
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

    data class Hit(val file: File, val line: Int)

    /** Counts bare `runCatching` occurrences inside `suspend fun` bodies of [file]. */
    private fun suspendFunsBareRunCatching(file: File): List<Hit> {
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
        val src = text.stripCommentsAndStrings()
        val hits = mutableListOf<Hit>()

        // Matches plain stdlib runCatching (with or without an explicit type
        // argument) but NOT runCatchingRethrowingCancellation.
        val pattern = Regex("""(?<!RethrowingCancellation\.)\brunCatching\s*(<[^>{]*>)?\s*\{""")
        var searchFrom = 0
        while (true) {
            val funRange = src.indexOf("suspend fun", searchFrom).let { if (it < 0) return hits else it }
            // Find the function's opening brace (first '{' after the signature's
            // ')' — expression-bodied suspend funs have no body and are skipped
            // by scanning to the next "suspend fun" if no brace appears first).
            var brace = -1
            var depthParen = 0
            var j = funRange
            var sawOpenParen = false
            while (j < src.length) {
                val ch = src[j]
                if (ch == '(') { sawOpenParen = true; depthParen++ }
                if (ch == ')') depthParen--
                if (ch == '{' && sawOpenParen && depthParen <= 0) { brace = j; break }
                if (ch == '\n' && !sawOpenParen) break // expression body / property
                j++
            }
            val nextFun = src.indexOf("suspend fun", funRange + 1).let { if (it < 0) src.length else it }
            val bodyEnd = if (brace >= 0) {
                // Match braces to the body's close.
                var depth = 0
                var k = brace
                while (k < src.length) {
                    when (src[k]) {
                        '{' -> depth++
                        '}' -> { depth--; if (depth == 0) break }
                    }
                    k++
                }
                k
            } else funRange
            if (brace >= 0) {
                val body = src.substring(brace, bodyEnd + 1)
                pattern.findAll(body).forEach { m ->
                    val line = src.substring(0, brace + m.range.first).count { it == '\n' } + 1
                    hits += Hit(file, line)
                }
            }
            // Advance past this body entirely: nested suspend funs inside the
            // body are covered by the outer window, so scanning them again
            // would double-count. Expression-bodied funs (no brace) jump to
            // the next declaration.
            searchFrom = if (brace >= 0) bodyEnd + 1 else maxOf(funRange + 1, nextFun)
        }
    }

    @Test
    fun `bare runCatching inside suspend funs never increases`() {
        val hits = guardedSources().flatMap { suspendFunsBareRunCatching(it) }
            .distinctBy { it.file to it.line }
        val report = hits.groupBy { it.file }.entries
            .sortedByDescending { it.value.size }
            .joinToString("\n") { (file, list) ->
                "  ${file.path}: ${list.size} (${list.joinToString(", ") { "${it.line}" }})"
            }
        assertTrue(
            hits.size <= maxBareRunCatchingInSuspendFuns,
            "bare runCatching inside suspend funs: ${hits.size} (baseline $maxBareRunCatchingInSuspendFuns). " +
                "Convert to runCatchingRethrowingCancellation (com.raulshma.jellyplay.core.concurrency), " +
                "or extract the non-suspend work out of the suspend body:\n$report",
        )
    }
}
