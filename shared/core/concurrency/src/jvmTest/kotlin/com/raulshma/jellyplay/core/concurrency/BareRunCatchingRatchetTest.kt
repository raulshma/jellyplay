package com.raulshma.jellyplay.core.concurrency

import kotlin.test.Test
import kotlin.test.assertTrue
import java.io.File

/**
 * Guard against reintroducing the cancellation-swallowing bug class: a bare
 * stdlib `runCatching` inside a `suspend fun` body captures
 * CancellationException like any other Throwable, masking structured
 * cancellation (a cancelled call reports as a failed Result instead of
 * stopping — broken worker retries, half-applied sync flips, zombie prewarms).
 * The fix is [runCatchingRethrowingCancellation], and this test keeps
 * the converted set converted.
 *
 * Guarded module set: DISCOVERED from the build itself:
 * every `include(":…")` module declared in settings.gradle.kts, plus every
 * directory under shared/ and apps/ that carries its own build.gradle.kts
 * (catches a module mid-wiring before its settings include lands), each
 * mapped to its source root — the whole `src` tree for KMP modules,
 * `src/main` for legacy single-variant modules (app, the legacy core tree,
 * baselineprofile, apps/desktop). The hand list is kept as
 * [legacyHandMaintainedRoots]; a canary test pins that discovery
 * still covers all of its on-disk roots. Non-suspend
 * bodies (pure JSON/enum/number parses in mappers, framework glue) are
 * legitimate stdlib `runCatching` territory and simply don't count — the
 * heuristic only counts occurrences inside `suspend fun` bodies, and
 * bodyless `suspend fun` declarations (interface members) are skipped,
 * not grafted onto the next function's body (a KDoc'd non-suspend site
 * like MultiConnectionDownloadStrategy's deletePartialQuietly must not be
 * swallowed into the preceding window).
 *
 * Known deliberate baseline entries (do not convert without a design note):
 * HomeDiscoveryStore.ensureNamespacedMigration (best-effort migration
 * swallow, KDoc'd) and PluginConfigViewModel.prepareBridgeScript's asset
 * read inside withContext(IO).
 *
 * Heuristic limitation, known and accepted: the scan matches literal
 * `suspend fun` declarations, so a bare `runCatching` inside a suspend
 * LAMBDA (e.g. a `fetch = { runCatching { … } }` argument) is invisible
 * to it. Keep suspend-lambda fetches on
 * [runCatchingRethrowingCancellation] by discipline; widening the
 * heuristic to suspend lambdas would need a fresh baseline census.
 *
 * Lower [maxBareRunCatchingInSuspendFuns] when another site converts; never
 * raise it. A legitimate NEW non-suspend use inside a suspend fun (parse
 * guards) should prefer extracting the parse into a non-suspend fun over
 * raising the baseline.
 *
 * Second arm — `catch (…: Exception)` swallowing CancellationException:
 * a plain `catch (…: Exception)` / `catch (…: Throwable)` (with no
 * CancellationException arm or in-body rethrow) whose try-block contains a
 * suspension point swallows structured cancellation the same way — a
 * cancelled collector keeps running as a zombie past `stop()`, a scope
 * release reports an in-flight load as a failure. The compliant shapes are
 * the rethrow arm (`catch (ce: CancellationException) { throw ce }` before
 * the `catch (…: Exception)`, as in TimeSyncManager.sync) or the in-body
 * rethrow (`catch (t: Throwable) { if (t is CancellationException) throw t … }`),
 * or the doctrine helper [runCatchingRethrowingCancellation]. Since plain
 * `catch` needs no `runCatching` literal to hide behind, this arm detects the
 * suspension point textually: the try-block must match one of an
 * unambiguous-marker list of coroutines/flow primitives (withContext, delay,
 * .collect, .await, this repo's suspend DAO `.upsert(…)` convention, …).
 * Ambiguous tokens that are suspend in one idiom and blocking in another are
 * deliberately NOT markers (OkHttp's blocking `.execute()`/`.enqueue()`,
 * stdlib `.toList()`/`.single()`/`.join()`), so a try-block whose only
 * suspend call is e.g. a bespoke repository function stays invisible;
 * keep new bespoke-callee try/catches
 * compliant by discipline. Like the first arm, only
 * `suspend fun` bodies are scanned, so suspend LAMBDAS inside non-suspend
 * functions (a `scope.launch { try { … } catch (…: Exception) { } }`) are
 * invisible; keep those compliant by discipline.
 *
 * Lower [maxCatchExceptionAroundSuspendCalls] when another site converts;
 * never raise it.
 */
class BareRunCatchingRatchetTest {

    private val maxBareRunCatchingInSuspendFuns = 2

    /** Sites where a plain `catch (…: Exception/Throwable)` wraps a suspension point. Zero-tolerance. */
    private val maxCatchExceptionAroundSuspendCalls = 0

    /** `catch (name: Type)` clauses; group 1 is the caught type. */
    private val catchClause = Regex("""catch\s*\(\s*\w+\s*:\s*([\w.]+)\s*\)""")

    /**
     * Unambiguous suspension-point markers (see the class KDoc for what is
     * deliberately absent). `.upsert(…)` is this repo's suspend-DAO convention.
     */
    private val suspendCallMarker = Regex(
        """withContext\s*\(|withTimeout(?:OrNull)?\s*\(|\bdelay\s*\(|\byield\s*\(|awaitAll\s*\(|""" +
            """coroutineScope\s*\{|supervisorScope\s*\{|suspendCancellableCoroutine|""" +
            """runCatchingRethrowingCancellation\s*\(|\.collect\s*[({]|\.collectLatest\s*[({]|""" +
            """\.emit\s*\(|\.emitAll\s*\(|\.await\s*\(|\.first\s*\(\s*\)|\.cancelAndJoin|""" +
            """\.onSubscription|\.upsert\s*\(""",
    )

    /** Catch types that capture CancellationException when unqualified by a rethrow arm. */
    private val swallowingCatchTypes = setOf("Exception", "RuntimeException", "Throwable")

    /**
     * The hand-maintained guard list this test used before root discovery, kept as
     * a canary: root discovery must cover every one of these paths that still
     * exists on disk (see the `discovered guard roots cover the hand list
     * they replaced` test). Roots whose module has since been deleted are
     * naturally absent and therefore exempt.
     */
    private val legacyHandMaintainedRoots = listOf(
        "shared/core/data/src",
        "shared/core/network/src",
        "shared/core/datastore/src",
        "shared/core/database/src",
        "shared/core/ui/src",
        "shared/core/model/src",
        "shared/core/designsystem/src",
        "shared/core/player-contract/src",
        "shared/core/concurrency/src",
        "core/testing/src/main",
        "shared/feature/home/src",
        "shared/feature/player-video/src",
        "shared/feature/livetv/src",
        "shared/feature/settings/src",
        "shared/feature/shell/src",
        "shared/feature/downloads/src",
        "shared/feature/details/src",
        "shared/feature/music/src",
        "shared/feature/admin/src",
        "shared/feature/editor/src",
        "shared/feature/search/src",
        "shared/feature/library/src",
        "shared/feature/arrqueue/src",
        "shared/feature/auth/src",
        "shared/feature/calendar/src",
        "shared/feature/insights/src",
        "shared/feature/newsletter/src",
        "shared/feature/onboarding/src",
        "shared/feature/player-audio/src",
        "shared/feature/player-live/src",
        "shared/feature/requests/src",
        "shared/feature/shortcuts/src",
        "shared/feature/subtitle-tester/src",
        "shared/feature/syncplay/src",
        "core/data/src/main",
        "core/ui/src/main",
        "core/notification/src/main",
        "app/src/main",
        "apps/desktop/src/main",
    )

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        assertTrue(dir != null, "could not locate settings.gradle.kts from ${System.getProperty("user.dir")}")
        return dir!!
    }

    /**
     * Discovers the module source roots to guard, straight from the build:
     * (1) every `include(":a:b")` module path declared in settings.gradle.kts,
     * and (2) every directory under shared/ or apps/ that carries its own
     * build.gradle.kts (a module mid-wiring whose settings include has not
     * landed yet). The legacy core tree + app is intentionally
     * settings-only — its dead sibling dirs (core/database, core/model, …)
     * are not modules and must stay unscanned. Each module directory maps to
     * its source root: `src/main` for legacy single-variant modules, the
     * whole `src` tree for KMP modules (which fold their test sources in, as
     * the hand list always did).
     */
    private fun discoverGuardedRoots(root: File): Set<File> {
        val moduleDirs = linkedSetOf<File>()

        // (1) the settings-declared module graph
        val settings = File(root, "settings.gradle.kts")
        if (settings.isFile) {
            val includeCall = Regex("""include\s*\(([^)]*)\)""")
            val quoted = Regex("\"([^\"]+)\"")
            settings.readText().lineSequence()
                .map { it.substringBefore("//") } // ignore commented-out includes
                .forEach { line ->
                    includeCall.findAll(line).forEach { call ->
                        quoted.findAll(call.groupValues[1])
                            .map { it.groupValues[1] }
                            .filter { it.startsWith(":") }
                            .forEach { modulePath ->
                                val dir = File(root, modulePath.removePrefix(":").replace(':', '/'))
                                if (dir.isDirectory) moduleDirs += dir
                            }
                    }
                }
        }

        // (2) modules on disk under shared/ or apps/ regardless of settings
        val pending = ArrayDeque<File>()
        listOf("shared", "apps").forEach { top ->
            val topDir = File(root, top)
            if (topDir.isDirectory) pending += topDir
        }
        while (pending.isNotEmpty()) {
            val dir = pending.removeFirst()
            val children = dir.listFiles() ?: continue
            if (children.any { it.isFile && it.name == "build.gradle.kts" }) moduleDirs += dir
            children.filter { it.isDirectory && it.name != "build" }.forEach { pending += it }
        }

        return moduleDirs.mapNotNullTo(linkedSetOf()) { module ->
            when {
                File(module, "src/main").isDirectory -> File(module, "src/main")
                File(module, "src").isDirectory -> File(module, "src")
                else -> null // module with no sources yet — nothing to guard
            }
        }
    }

    private fun guardedSources(): List<File> {
        val roots = discoverGuardedRoots(repoRoot())
        assertTrue(roots.isNotEmpty(), "guard-root discovery found no module source roots — ratchet would be vacuous")
        return roots.flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }
    }

    /**
     * Strips line/block comments and string/char literals so the scan reads
     * code, not prose. Also returns, per kept character, its original line
     * number — block-comment removal collapses lines, so positions in the
     * stripped text alone would report skewed [Hit] lines.
     */
    private fun stripCommentsAndStrings(text: String): Pair<String, IntArray> {
        val out = StringBuilder(text.length)
        val lineOf = IntArray(text.length)
        var line = 1
        var i = 0
        var inLine = false
        var inBlock = false
        var inString = false
        var inChar = false
        while (i < text.length) {
            val c = text[i]
            val next = if (i + 1 < text.length) text[i + 1] else ' '
            when {
                inLine -> if (c == '\n') {
                    inLine = false
                    out.append(c)
                    lineOf[out.length - 1] = line
                    line++
                }
                inBlock -> {
                    if (c == '\n') line++
                    if (c == '*' && next == '/') { inBlock = false; i++ }
                }
                inString -> {
                    if (c == '\n') line++
                    if (c == '\\') i++
                    else if (c == '"') inString = false
                }
                inChar -> {
                    if (c == '\\') i++
                    else if (c == '\'') inChar = false
                }
                c == '/' && next == '/' -> { inLine = true; i++ }
                c == '/' && next == '*' -> { inBlock = true; i++ }
                c == '"' -> { inString = true; out.append(' '); lineOf[out.length - 1] = line }
                c == '\'' -> { inChar = true; out.append(' '); lineOf[out.length - 1] = line }
                else -> {
                    out.append(c)
                    lineOf[out.length - 1] = line
                    if (c == '\n') line++
                }
            }
            i++
        }
        return out.toString() to lineOf
    }

    data class Hit(val file: File, val line: Int)

    /** Counts bare `runCatching` occurrences inside `suspend fun` bodies of [file]. */
    private fun suspendFunsBareRunCatching(file: File): List<Hit> {
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
        val (src, lineOf) = stripCommentsAndStrings(text)
        val hits = mutableListOf<Hit>()

        // Matches plain stdlib runCatching (with or without an explicit type
        // argument) but NOT runCatchingRethrowingCancellation.
        val pattern = Regex("""(?<!RethrowingCancellation\.)\brunCatching\s*(<[^>{]*>)?\s*\{""")
        var searchFrom = 0
        while (true) {
            val funRange = src.indexOf("suspend fun", searchFrom).let { if (it < 0) return hits else it }
            // Bodyless declarations (interface/abstract members with no
            // braces) must not graft the NEXT function's body onto this
            // window — stop the body search at the next `fun` keyword, which
            // a real body brace always precedes.
            val nextDecl = Regex("""\bfun\b""").find(src, funRange + "suspend fun".length)
                ?.range?.first ?: src.length
            // Find the function's opening brace (first '{' after the signature's
            // ')' — expression-bodied suspend funs have no body and are skipped
            // by scanning to the next "suspend fun" if no brace appears first).
            var brace = -1
            var depthParen = 0
            var j = funRange
            var sawOpenParen = false
            while (j < src.length && j < nextDecl) {
                val ch = src[j]
                if (ch == '(') { sawOpenParen = true; depthParen++ }
                if (ch == ')') depthParen--
                if (ch == '{' && sawOpenParen && depthParen <= 0) { brace = j; break }
                // A '}' outside the parameter parens closes the declaration
                // (the interface's own brace after a bodyless member): no body
                // brace can follow, so stop here. Without this, the ')' of the
                // bodyless signature leaves depthParen at -1 and the NEXT
                // declaration's opening brace (a class body, e.g.) reads as
                // this fun's body — swallowing every following top-level
                // declaration into the window (observed: a companion object's
                // non-suspend parse helper counted against the baseline).
                if (ch == '}' && depthParen <= 0) break
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
                    hits += Hit(file, lineOf[brace + m.range.first])
                }
            }
            // Advance past this body entirely: nested suspend funs inside the
            // body are covered by the outer window, so scanning them again
            // would double-count. Bodyless/expression-bodied funs (no brace)
            // jump to the next declaration.
            searchFrom = if (brace >= 0) bodyEnd + 1 else maxOf(funRange + 1, nextFun)
        }
    }

    /**
     * Flags `catch (…: Exception|RuntimeException|Throwable)` clauses inside
     * `suspend fun` bodies whose try-block contains a suspension-point marker
     * and which carry no CancellationException compliance anywhere between the
     * try open brace and the end of that catch clause's block (so both the
     * rethrow-arm shape and the in-body `if (t is CancellationException) throw t`
     * shape read as compliant).
     */
    private fun suspendFunsCatchExceptionAroundSuspendCalls(file: File): List<Hit> {
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
        val (src, lineOf) = stripCommentsAndStrings(text)
        val hits = mutableListOf<Hit>()
        val windows = suspendFunWindows(src)
        if (windows.isEmpty()) return hits
        val tryStarts = Regex("""\btry\b""").findAll(src).map { it.range.first }.toList()
        for (catchMatch in catchClause.findAll(src)) {
            val cm = catchMatch.range.first
            val window = windows.firstOrNull { it.first < cm && cm < it.second } ?: continue
            val excType = catchMatch.groupValues[1].substringAfterLast('.')
            if (excType !in swallowingCatchTypes) continue
            // Innermost try whose block encloses this catch: the last try
            // keyword before it whose matched block is still open at the catch.
            val tryOpen = tryStarts.lastOrNull { it in window.first until cm } ?: continue
            val blockOpen = src.indexOf('{', tryOpen)
            if (blockOpen < 0 || blockOpen > cm) continue
            val blockClose = matchBraces(src, blockOpen)
            if (blockClose < 0 || blockClose > cm) continue
            // Compliance region: try open brace → end of this catch block.
            var catchBodyOpen = catchMatch.range.last + 1
            while (catchBodyOpen < src.length && src[catchBodyOpen] != '{') catchBodyOpen++
            val regionEnd = if (catchBodyOpen < src.length) {
                val close = matchBraces(src, catchBodyOpen)
                if (close > cm) close else cm
            } else {
                cm
            }
            if (src.subSequence(blockOpen, regionEnd).contains("CancellationException")) continue
            val tryBody = src.subSequence(blockOpen, blockClose)
            if (suspendCallMarker.containsMatchIn(tryBody)) {
                hits += Hit(file, lineOf[cm])
            }
        }
        return hits
    }

    /** All `suspend fun` body windows, reusing the first arm's windowing rules. */
    private fun suspendFunWindows(src: String): List<Pair<Int, Int>> {
        val windows = mutableListOf<Pair<Int, Int>>()
        var searchFrom = 0
        while (true) {
            val funRange = src.indexOf("suspend fun", searchFrom).let { if (it < 0) return windows else it }
            // Same bodyless-declaration guard as the first arm: stop the
            // signature walk at the next `fun` keyword.
            val nextDecl = Regex("""\bfun\b""").find(src, funRange + "suspend fun".length)
                ?.range?.first ?: src.length
            var brace = -1
            var depthParen = 0
            var j = funRange
            var sawOpenParen = false
            while (j < src.length && j < nextDecl) {
                val ch = src[j]
                if (ch == '(') { sawOpenParen = true; depthParen++ }
                if (ch == ')') depthParen--
                if (ch == '{' && sawOpenParen && depthParen <= 0) { brace = j; break }
                if (ch == '}' && depthParen <= 0) break
                if (ch == '\n' && !sawOpenParen) break
                j++
            }
            if (brace >= 0) {
                val bodyEnd = matchBraces(src, brace)
                if (bodyEnd > 0) {
                    windows += brace to bodyEnd
                    searchFrom = bodyEnd + 1
                    continue
                }
            }
            searchFrom = maxOf(funRange + 1, nextDecl)
        }
    }

    /** Index of the brace matching the one at [open], or -1. */
    private fun matchBraces(src: String, open: Int): Int {
        var depth = 0
        var k = open
        while (k < src.length) {
            when (src[k]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return k }
            }
            k++
        }
        return -1
    }

    @Test
    fun `bare runCatching inside suspend funs never increases`() {        val hits = guardedSources().flatMap { suspendFunsBareRunCatching(it) }
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

    @Test
    fun `catch Exception around suspend calls never increases`() {
        val hits = guardedSources().flatMap { suspendFunsCatchExceptionAroundSuspendCalls(it) }
            .distinctBy { it.file to it.line }
        val report = hits.groupBy { it.file }.entries
            .sortedByDescending { it.value.size }
            .joinToString("\n") { (file, list) ->
                "  ${file.path}: ${list.size} (${list.joinToString(", ") { "${it.line}" }})"
            }
        assertTrue(
            hits.size <= maxCatchExceptionAroundSuspendCalls,
            "catch(…Exception) swallowing CancellationException around suspend calls: ${hits.size} " +
                "(baseline $maxCatchExceptionAroundSuspendCalls). Add a CancellationException rethrow " +
                "arm (shape: TimeSyncManager.sync) or wrap the work in " +
                "runCatchingRethrowingCancellation (com.raulshma.jellyplay.core.concurrency):\n$report",
        )
    }

    @Test
    fun `discovered guard roots cover the hand list they replaced`() {
        val root = repoRoot()
        val discovered = discoverGuardedRoots(root).map { it.canonicalFile }
        val uncovered = legacyHandMaintainedRoots
            .map { File(root, it) }
            .filter { it.isDirectory } // deleted modules are naturally exempt
            .filter { legacy ->
                discovered.none { rootDir -> legacy.canonicalFile == rootDir || legacy.toPath().startsWith(rootDir.toPath()) }
            }
        assertTrue(
            uncovered.isEmpty(),
            "guard-root discovery no longer covers ${uncovered.map { it.path }} — a module the ratchet " +
                "used to guard has escaped; fix discoverGuardedRoots, do not just re-add the path",
        )
    }
}
