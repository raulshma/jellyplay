package com.raulshma.jellyplay.core.network.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Home sub-call cache-spec totality guard (source-scan on plain text, no PSI
 * — deliberately cheap, but executable; the repo's established totality-guard
 * shape: `WorkerRegistrationTotalityGuardTest`, `DesktopStartupTotalityGuardTest`).
 *
 * The wholesale drop of the home sub-call memos walks ONE hand-list:
 * [HomeSectionsFetcher] `homeSubcallCaches`, fed by the plugin sibling's own
 * `subcallCaches` (the glossary's "Fetcher cache spec" rule — "a forgotten
 * clear serves stale badges for a TTL"). The sibling transport registration
 * is pinned (HomeRowArmsTest) and the CURRENT plugin memos' wiring is pinned
 * behaviorally (PluginHomeContentTest), but the lists' totality was enforced
 * only by declaration discipline: a NEW `TtlCache` field on the fetcher or
 * the plugin module compiles and passes every suite while `invalidateCaches`
 * silently skips it — a watched/favorite/progress write then serves the
 * pre-write rows for the sub-call TTL. Compile gates are blind to this; the
 * guard converts it into a build failure by asserting, over the two library
 * files: every `val x = TtlCache<…>` declaration is a member of its file's
 * spec list (forward), every spec-list member resolves to a declared memo
 * (reverse — a rename that orphaned an entry, or discovery rot), the plugin
 * family still reaches the drop through the `+ pluginHomeContent.subcallCaches`
 * delegation, and `invalidateCaches` still walks the list.
 *
 * Why a source scan and not a runtime pin: `TtlCache` carries no registration
 * a test could walk, commonMain has no reflection over private properties,
 * and the spec lists themselves are private — the cheap executable
 * alternative is the text, in the lane file IO lives in (ADR-0006 rule 2:
 * jvmTest). To add a memo: declare the `TtlCache`, join the spec list in the
 * same change, and this scan finds both halves — an unlisted memo, a stale
 * entry, a broken delegation or a hand-rolled drop body all fail here with
 * the cache named.
 *
 * [documentedNonSpecMembers] carries the deliberate non-members, each with
 * its landing date + reason, pinned both ways like the startup guard's pins:
 * a pin whose declaration vanished, or whose subject BECAME a `TtlCache`
 * (it must join the spec list instead), fails.
 */
class HomeSubcallCacheSpecTotalityGuardTest {

    private val fetcherFile =
        "shared/core/network/src/commonMain/kotlin/com/raulshma/jellyplay/core/network/library/HomeSectionsFetcher.kt"

    private val pluginFile =
        "shared/core/network/src/commonMain/kotlin/com/raulshma/jellyplay/core/network/library/PluginHomeContent.kt"

    /**
     * Ratchet floors: `= TtlCache<` memos per file (currently 3 — latest,
     * similar, discover — and 2 — plugin rows, curated hero). A
     * discovery-rot regression (declaration shape drifts past the regex)
     * would otherwise make the totality check vacuously green on an empty
     * set.
     */
    private val minFetcherCaches = 3
    private val minPluginCaches = 2

    /**
     * The freshness-bearing members of [HomeSectionsFetcher] that are
     * deliberately NOT sub-call memos and stay off `homeSubcallCaches`, each
     * with its landing date + one-line reason. Adding an entry here is a
     * documented decision ("holds no Jellyfin per-item UserData — no
     * stale-badge mode"), never a convenience. Pinned both ways: a pin whose
     * declaration was renamed/removed — or whose subject now declares a
     * `TtlCache` and so belongs on the spec list — is stale and fails.
     */
    private val documentedNonSpecMembers: Map<String, String> = mapOf(
        "seerrRowsGate" to
            "2026-11-20 — a TtlCacheGate (freshness stamp only, holds no rows), not a memo: " +
            "the Seerr-sourced discover rows carry raw seerrItems with no per-item UserData, " +
            "so a watched/favorite/progress write cannot stale-badge them; pull-to-refresh " +
            "already invalidates it through the force path",
        "lastKnownSeerrRows" to
            "2026-11-20 — the Seerr rows' last-known-good memo (a @Volatile var), same " +
            "no-per-item-UserData rationale as seerrRowsGate, and deliberately not " +
            "identity-scoped (the Seerr connection is app-wide state — see its comment)",
    )

    @Test
    fun everySubcallCache_declaresItselfOnItsCacheSpec() {
        val root = repoRoot()
        val fetcherText = strippedSource(root, fetcherFile)
        val pluginText = strippedSource(root, pluginFile)

        val fetcherCaches = discoveredCaches(fetcherText)
        val pluginCaches = discoveredCaches(pluginText)
        assertTrue(
            fetcherCaches.size >= minFetcherCaches,
            "discovered only ${fetcherCaches.sorted()} in HomeSectionsFetcher.kt, expected " +
                ">= $minFetcherCaches — discovery is broken (declaration shape changed?), " +
                "fix the scan in " + javaClass.simpleName,
        )
        assertTrue(
            pluginCaches.size >= minPluginCaches,
            "discovered only ${pluginCaches.sorted()} in PluginHomeContent.kt, expected " +
                ">= $minPluginCaches — discovery is broken (declaration shape changed?), " +
                "fix the scan in " + javaClass.simpleName,
        )

        val problems = mutableListOf<String>()

        // The fetcher's spec initializer keeps its pinned shape: a listOf of
        // members PLUS the plugin delegation (the plugin family's memos reach
        // the wholesale drop only through it).
        val fetcherSpec = fetcherSpecInitializer.find(fetcherText) ?: fail(
            "HomeSectionsFetcher.kt's homeSubcallCaches initializer no longer matches the " +
                "pinned shape (a listOf of members + `+ pluginHomeContent.subcallCaches`) — " +
                "the wholesale drop lost its members or its plugin delegation. Restore the " +
                "shape, or fix the scan in " + javaClass.simpleName + ".",
        )
        val fetcherSpecMembers = identifiers(fetcherSpec.groupValues[1])
        assertTrue(
            fetcherSpecMembers.isNotEmpty(),
            "homeSubcallCaches lists no members — the wholesale drop would clear nothing.",
        )

        // Forward: every fetcher memo is on the spec (the defect shape — a new
        // TtlCache compiles, serves memoised rows, and is never cleared).
        (fetcherCaches - fetcherSpecMembers).sorted().forEach { name ->
            problems +=
                "  - '$name' (HomeSectionsFetcher.kt) declares a TtlCache sub-call memo that " +
                    "never joins homeSubcallCaches — the wholesale drop never clears it, so a " +
                    "watched/favorite/progress write serves its stale badges until the TTL " +
                    "expires (the glossary 'Fetcher cache spec' defect). Add it to the " +
                    "homeSubcallCaches list, or — if it genuinely is not a sub-call memo — " +
                    "add a dated pin to documentedNonSpecMembers in " + javaClass.simpleName + "."
        }
        // Reverse: every spec member resolves to a discovered memo.
        (fetcherSpecMembers - fetcherCaches).sorted().forEach { name ->
            problems +=
                "  - homeSubcallCaches lists '$name' but no `val $name = TtlCache<…>` " +
                    "declaration exists in HomeSectionsFetcher.kt — renamed or removed? " +
                    "Fix or drop the list entry."
        }

        // The drop walks the spec list — the list IS the totality contract
        // this guard pins; a hand-rolled per-cache body re-opens the defect.
        if (!invalidateCachesWalksTheSpec(fetcherText)) {
            problems +=
                "  - invalidateCaches() in HomeSectionsFetcher.kt no longer walks " +
                    "homeSubcallCaches — restore the forEach over the spec list, or fix the " +
                    "scan in " + javaClass.simpleName + "."
        }

        // The plugin family: same totality over its own public spec (the
        // delegation the fetcher's list consumes).
        val pluginSpec = pluginSpecInitializer.find(pluginText) ?: fail(
            "PluginHomeContent.kt's subcallCaches initializer no longer matches the pinned " +
                "shape (a listOf of members) — the fetcher's delegation resolves to nothing. " +
                "Restore the shape, or fix the scan in " + javaClass.simpleName + ".",
        )
        val pluginSpecMembers = identifiers(pluginSpec.groupValues[1])
        assertTrue(
            pluginSpecMembers.isNotEmpty(),
            "PluginHomeContent.subcallCaches lists no members — the delegated drop would " +
                "clear nothing.",
        )
        (pluginCaches - pluginSpecMembers).sorted().forEach { name ->
            problems +=
                "  - '$name' (PluginHomeContent.kt) declares a TtlCache memo that never joins " +
                    "the module's subcallCaches — the fetcher's wholesale drop (which " +
                    "delegates to that list) never clears it, so a watched/favorite/progress " +
                    "write serves its stale badges until the TTL expires. Add it to the " +
                    "subcallCaches list in PluginHomeContent.kt, or — if it genuinely is not " +
                    "a sub-call memo — add a dated pin to documentedNonSpecMembers in " +
                    javaClass.simpleName + "."
        }
        (pluginSpecMembers - pluginCaches).sorted().forEach { name ->
            problems +=
                "  - PluginHomeContent.subcallCaches lists '$name' but no `val $name = " +
                    "TtlCache<…>` declaration exists in PluginHomeContent.kt — renamed or " +
                    "removed? Fix or drop the list entry."
        }

        if (problems.isNotEmpty()) {
            fail(
                "the home sub-call cache specs are not total (every TtlCache memo in " +
                    "HomeSectionsFetcher.kt / PluginHomeContent.kt must declare itself on its " +
                    "file's spec list):\n" +
                    problems.joinToString("\n")
            )
        }
    }

    @Test
    fun everyNonMemberPin_namesALiveNonCacheDeclaration() {
        val root = repoRoot()
        val fetcherText = strippedSource(root, fetcherFile)
        val discovered = discoveredCaches(fetcherText)

        documentedNonSpecMembers.forEach { (name, reason) ->
            assertTrue(
                memberDeclaration.findAll(fetcherText).any { it.groupValues[1] == name },
                "pin for '$name' matches no val/var declaration in HomeSectionsFetcher.kt — " +
                    "renamed or removed? Update or drop the pin (was: $reason).",
            )
            assertTrue(
                name !in discovered,
                "'$name' is pinned as NOT a cache-spec member ($reason) but it now declares " +
                    "a TtlCache — move it into homeSubcallCaches and drop the stale pin.",
            )
        }
    }

    // ------------------------------------------------------------ plumbing

    /**
     * `private val homeLatestMediaCache = TtlCache<…>` — the universe: every
     * TtlCache memo declaration in a scanned file, in BOTH constructor
     * spellings — explicit type arguments (`TtlCache<…>(`) and inferred ones
     * (`TtlCache(`) — so a memo that drops its type args never falls out of
     * discovery. The initializer gap after the `=` may cross a line break (a
     * constructor starting on the next line is the same memo), but the
     * type-annotation gap may not cross a line or an `=`, so the spec lists
     * (`List<TtlCache<*>> = listOf(`) never match; `TtlCacheGate(...)`
     * initializers match neither (the gate is a stamp, not a memo — the
     * identifier continues past `TtlCache`, so neither `<` nor `(` follows).
     */
    private val cacheDeclaration = Regex(
        """(?m)^[ \t]*(?:(?:private|internal|public)[ \t]+)*val[ \t]+(\w+)[ \t]*(?::[^=\n]+)?=[ \t\r\n]*TtlCache[ \t]*[<(]""",
    )

    /** Every `val`/`var` declaration, for the non-member pins' liveness check. */
    private val memberDeclaration = Regex(
        """(?m)^[ \t]*(?:@Volatile[ \t]*\n[ \t]*)?(?:(?:private|internal|public|override)[ \t]+)*(?:val|var)[ \t]+(\w+)""",
    )

    /**
     * The fetcher's spec initializer — the member listOf plus the plugin
     * delegation, both part of the pinned shape.
     */
    private val fetcherSpecInitializer = Regex(
        """val[ \t]+homeSubcallCaches\b.*?listOf[ \t]*\(([^)]*)\)[ \t]*\+[ \t]*pluginHomeContent\.subcallCaches""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** The plugin module's spec initializer. */
    private val pluginSpecInitializer = Regex(
        """val[ \t]+subcallCaches\b.*?listOf[ \t]*\(([^)]*)\)""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** The `fun invalidateCaches()` head — the body scan starts at its `{`. */
    private val invalidateCachesHead = Regex(
        """fun[ \t]+invalidateCaches[ \t]*\(\)[ \t]*\{""",
    )

    /**
     * The traversal half of the drop body: the spec list walked with forEach,
     * up to and including the lambda head — group 1 is an explicit lambda
     * parameter (`cache ->`), blank for the implicit `it`.
     */
    private val invalidateTraversal = Regex(
        """homeSubcallCaches[ \t]*\.[ \t]*forEach[ \t]*(?:\([ \t]*)?\{[ \t]*(?:(\w+)[ \t]*->[ \t]*)?""",
    )

    /**
     * The invalidation half of the drop body: the ITERATED element cleared —
     * `<it|param>[?].clear(` — inside the traversal's own lambda. A `.clear(`
     * elsewhere in the body (an unrelated cache, a metrics map) is not the
     * drop; the walked caches are exactly what must be cleared. The match
     * runs on the MASKED body ([maskLiteralContents]) so a logged
     * `"it.clear("` string is prose, not evidence — and a `it.clear(` bound
     * to a nested lambda inside an interpolation (the nested binding shadows
     * the iterated element) is masked too.
     */
    private fun traversalClearsTheElement(lambdaBody: String, element: String): Boolean =
        Regex("""\b${Regex.escape(element)}[ \t]*(\?)?\.[ \t]*clear[ \t]*\(""")
            .containsMatchIn(maskLiteralContents(lambdaBody))

    /**
     * [text] with every string/char literal's TEMPLATE text blanked to spaces
     * (delimiters kept, positions preserved) — the regex-only twin of the
     * brace scan's literal skipping: code that merely SAYS `it.clear(` inside
     * a literal must not satisfy the clear-call check. A literal's `${…}`
     * expression interiors stay VERBATIM: they are executable code the
     * clear-call check must see (a wholesale drop expressed as a log-line
     * interpolation still clears), while the expressions' own nested
     * literals — and their comments — mask recursively (a quoted `it.clear(`
     * inside an expression branch is prose, not evidence), and a `{` inside
     * an expression opens a NESTED lambda whose own implicit `it` shadows the
     * traversal's element — those nested bindings blank (only the
     * expression's own depth-1 `it` stays verbatim; the trade: a
     * receiver-lambda body like `run { it.clear() }` is masked too, which
     * fails a valid drop loudly instead of passing a hollow one). The brace
     * scanners ([balancedBraceEnd], the literal helpers) keep reading the raw
     * text — the boundaries this preserves are exactly those scanners'.
     */
    private fun maskLiteralContents(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                // The dispatch splits on the DELIMITER: maskLiteral only walks
                // double-quoted literals (it hunts a `"` closer), so a `'`-opened
                // char literal routed there would blank the rest of its line and
                // leave a later real string's contents verbatim.
                '"' -> i = maskLiteral(text, i, out)
                '\'' -> i = maskCharLiteral(text, i, out)
                else -> {
                    out.append(text[i])
                    i++
                }
            }
        }
        return out.toString()
    }

    /**
     * Masks ONE literal opened at [start] into [out] — template text as
     * spaces, `${…}` expression interiors verbatim minus their own nested
     * literals/comments — and returns the index past the literal. The walk
     * mirrors [stringLiteralEnd] boundary-for-boundary: masking never moves
     * the scan, so a naturally-closed literal keeps both delimiters and an
     * unterminated bail ends the mask where the raw-text scanners bail.
     */
    private fun maskLiteral(text: String, start: Int, out: StringBuilder): Int {
        if (!text.startsWith("\"\"\"", start)) return maskLineString(text, start, out)
        val close = text.indexOf("\"\"\"", start + 3)
        val end = if (close == -1) text.length else close + 3
        val closed = close != -1
        out.append("\"\"\"")
        var i = start + 3
        var templateDepth = 0
        while (i < end) {
            val c = text[i]
            when {
                c == '$' && text.startsWith("{", i + 1) -> {
                    templateDepth++
                    out.append("${'$'}{")
                    i += 2
                }
                templateDepth > 0 && text.startsWith("//", i) -> {
                    val newline = text.indexOf('\n', i)
                    val commentEnd = minOf(if (newline == -1) text.length else newline, end)
                    repeat(commentEnd - i) { out.append(' ') }
                    i = commentEnd
                }
                templateDepth > 0 && text.startsWith("/*", i) -> {
                    var commentDepth = 1
                    val open = i
                    i += 2
                    while (i < end && commentDepth > 0) {
                        when {
                            text.startsWith("/*", i) -> {
                                commentDepth++
                                i += 2
                            }
                            text.startsWith("*/", i) -> {
                                commentDepth--
                                i += 2
                            }
                            else -> i++
                        }
                    }
                    repeat(i - open) { out.append(' ') }
                }
                templateDepth > 0 && text[i] == '{' -> {
                    templateDepth++
                    out.append('{')
                    i++
                }
                templateDepth > 0 && text[i] == '}' -> {
                    templateDepth--
                    out.append('}')
                    i++
                }
                templateDepth > 1 && text.startsWith("it", i) && isStandaloneIt(text, i) -> {
                    // From a `{`'s depth on, an `it` binds the NESTED lambda, not
                    // the traversal's element — blank the token so its clear call
                    // cannot read as drop evidence (the direct depth-1 `it` stays).
                    out.append("  ")
                    i += 2
                }
                templateDepth > 0 && text[i] == '"' -> i = maskLiteral(text, i, out)
                templateDepth > 0 && text[i] == '\'' -> i = maskCharLiteral(text, i, out)
                else -> {
                    out.append(if (templateDepth > 0) c else ' ')
                    i++
                }
            }
        }
        if (closed) out.append("\"\"\"")
        return end
    }

    /** The [maskLiteral] walk for a line string (escapes and the EOL bail). */
    private fun maskLineString(text: String, start: Int, out: StringBuilder): Int {
        out.append('"')
        var i = start + 1
        var templateDepth = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' -> {
                    // The escape pair is template text: blank both chars (the
                    // loop guard absorbs an input-final backslash).
                    out.append("  ")
                    i += 2
                }
                templateDepth == 0 && c == '"' -> {
                    out.append('"')
                    return i + 1
                }
                templateDepth == 0 && c == '\n' -> return i // unterminated — no closer to keep
                templateDepth > 0 && text.startsWith("//", i) -> {
                    val newline = text.indexOf('\n', i)
                    val commentEnd = if (newline == -1) text.length else newline
                    repeat(commentEnd - i) { out.append(' ') }
                    i = commentEnd
                }
                templateDepth > 0 && text.startsWith("/*", i) -> {
                    var commentDepth = 1
                    val open = i
                    i += 2
                    while (i < text.length && commentDepth > 0) {
                        when {
                            text.startsWith("/*", i) -> {
                                commentDepth++
                                i += 2
                            }
                            text.startsWith("*/", i) -> {
                                commentDepth--
                                i += 2
                            }
                            else -> i++
                        }
                    }
                    repeat(i - open) { out.append(' ') }
                }
                c == '$' && text.startsWith("{", i + 1) -> {
                    templateDepth++
                    out.append("${'$'}{")
                    i += 2
                }
                templateDepth > 0 && c == '{' -> {
                    templateDepth++
                    out.append('{')
                    i++
                }
                templateDepth > 0 && c == '}' -> {
                    templateDepth--
                    out.append('}')
                    i++
                }
                templateDepth > 1 && text.startsWith("it", i) && isStandaloneIt(text, i) -> {
                    // The nested-lambda blanking above: same rule, line-string walk.
                    out.append("  ")
                    i += 2
                }
                templateDepth > 0 && c == '"' -> i = maskLiteral(text, i, out)
                templateDepth > 0 && c == '\'' -> i = maskCharLiteral(text, i, out)
                else -> {
                    out.append(if (templateDepth > 0) c else ' ')
                    i++
                }
            }
        }
        return text.length
    }

    /** True when the `it` at [index] is a whole token, not a longer identifier's tail. */
    private fun isStandaloneIt(text: String, index: Int): Boolean {
        val prev = text[index - 1] // call sites never open a walk before index 1
        val next = text.getOrNull(index + 2)
        return !(prev.isLetterOrDigit() || prev == '_') &&
            (next == null || !(next.isLetterOrDigit() || next == '_'))
    }

    /**
     * The [maskLiteral] walk for a char literal (interior blanked, delimiters kept).
     */
    private fun maskCharLiteral(text: String, start: Int, out: StringBuilder): Int {
        out.append('\'')
        var i = start + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> {
                    out.append("  ")
                    i += 2
                }
                '\'' -> {
                    out.append('\'')
                    return i + 1
                }
                '\n' -> return i // not a literal after all — bail at EOL
                else -> {
                    out.append(' ')
                    i++
                }
            }
        }
        return text.length
    }

    /**
     * The drop walks the spec list: `fun invalidateCaches()`'s OWN body (the
     * balanced-brace region after its head) must carry BOTH halves — the
     * `homeSubcallCaches.forEach` traversal and a `clear` on each walked
     * cache INSIDE the traversal's own lambda. A body that only mentions the
     * list (a size read) passes neither, a traversal that never clears its
     * element (a log/size walk beside an unrelated `.clear(`) fails the
     * second half, and a `homeSubcallCaches` reference that lives past the
     * method's closing brace (another member's) is outside the scanned
     * region — a hand-rolled or hollowed drop must not borrow a later
     * reference to pass. The brace scan skips string/char literals
     * (escaped delimiters included), so a quoted brace cannot end or extend
     * a method body.
     */
    private fun invalidateCachesWalksTheSpec(text: String): Boolean {
        val head = invalidateCachesHead.find(text) ?: return false
        val bodyEnd = balancedBraceEnd(text, head.range.last)
        if (bodyEnd == -1) return false // unterminated body — not a walkable drop
        val body = text.substring(head.range.last + 1, bodyEnd)
        val traversal = invalidateTraversal.find(body) ?: return false
        // The lambda body: balanced braces from the traversal's own `{` (the
        // first brace at/after the match — nothing between the list reference
        // and the lambda opens a brace).
        val lambdaOpen = body.indexOf('{', traversal.range.first)
        val lambdaEnd = balancedBraceEnd(body, lambdaOpen)
        if (lambdaEnd == -1) return false
        val lambdaBody = body.substring(lambdaOpen + 1, lambdaEnd)
        val element = traversal.groupValues[1].ifBlank { "it" }
        return traversalClearsTheElement(lambdaBody, element)
    }

    /**
     * The index past the `}` balancing the `{` at [openBraceIndex] — braces
     * inside string/char literals don't count. Returns -1 when the braces
     * never balance (an unterminated body/lambda is not a walkable drop).
     */
    private fun balancedBraceEnd(text: String, openBraceIndex: Int): Int {
        var depth = 1
        var i = openBraceIndex + 1
        while (i < text.length) {
            when (text[i]) {
                // The literal helpers return EXCLUSIVE ends (already past the
                // literal) — resume there directly; the shared increment below
                // is not theirs, or the first character after each literal
                // (a `}`, say, from `it == ','}`) would be skipped and the
                // body scan would overrun into the next method.
                '"' -> i = stringLiteralEnd(text, i)
                '\'' -> i = charLiteralEnd(text, i)
                '{' -> {
                    depth++
                    i++
                }
                '}' -> {
                    depth--
                    if (depth == 0) return i + 1
                    i++
                }
                else -> i++
            }
        }
        return -1
    }

    private fun discoveredCaches(strippedText: String): Set<String> =
        cacheDeclaration.findAll(strippedText).map { it.groupValues[1] }.toSet()

    private fun identifiers(region: String): Set<String> =
        Regex("""\w+""").findAll(region).map { it.value }.toSet()

    /** Walks up from the test working dir (shared/core/network) to settings.gradle.kts. */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("could not locate repo root (no settings.gradle.kts walking up from ${System.getProperty("user.dir")})")
    }

    private fun strippedSource(root: File, path: String): String {
        val file = root.resolve(path)
        assertTrue(file.isFile, "missing $path — repo layout changed?")
        return stripComments(file.readText())
    }

    /**
     * Drops comments. Kotlin block comments NEST, so a non-greedy
     * `/*…*/` removal stops at the FIRST closer and would resurrect the
     * disabled code between an inner closer and the outer one as "live"
     * source (a memo commented out that way would be discovered); this scan
     * tracks depth instead, removing each complete OUTER comment (nested
     * ones included) before cache discovery. Line comments whitespace-fill
     * with the newline KEPT (an opener inside one cannot open a block) —
     * eating the newline would fuse a trailing comment's line into the next
     * one and the (?m)^-anchored [cacheDeclaration] would miss the memo that
     * followed it. Block comments whitespace-fill too (newlines kept, so
     * line anchors and line numbers survive), which also retains token
     * boundaries a bare removal would fuse (`val/*c*/x` → `val x`, not
     * `valx`). String/char literals are copied verbatim so executable code
     * carrying an opener never opens a comment — everything outside comments
     * is preserved.
     */
    private fun stripComments(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("//", i) -> {
                    val newline = text.indexOf('\n', i)
                    val commentEnd = if (newline == -1) text.length else newline
                    repeat(commentEnd - i) { out.append(' ') }
                    if (newline != -1) out.append('\n')
                    i = if (newline == -1) text.length else newline + 1
                }
                text.startsWith("/*", i) -> {
                    var depth = 1
                    var j = i + 2
                    while (j < text.length && depth > 0) {
                        when {
                            text.startsWith("/*", j) -> {
                                depth++
                                j += 2
                            }
                            text.startsWith("*/", j) -> {
                                depth--
                                j += 2
                            }
                            else -> j++
                        }
                    }
                    // Whitespace-fill the whole outer comment: each non-newline
                    // char becomes a space, newlines stay newlines.
                    for (k in i until j) out.append(if (text[k] == '\n') '\n' else ' ')
                    i = j
                }
                text[i] == '"' -> {
                    val end = stringLiteralEnd(text, i)
                    out.append(text, i, end)
                    i = end
                }
                text[i] == '\'' -> {
                    val end = charLiteralEnd(text, i)
                    out.append(text, i, end)
                    i = end
                }
                else -> {
                    out.append(text[i])
                    i++
                }
            }
        }
        return out.toString()
    }

    /**
     * The index past the quoted literal opened at [start] (raw or escaped).
     * A `${…}` template expression is tracked to its balancing `}` (inner
     * braces count), string/char literals INSIDE the expression recurse (a
     * quote in a fallback branch does not terminate the outer string), and
     * comments inside the expression are skipped (line comments to the
     * newline, block comments to their depth-matched closer) so a quoted or
     * braced comment body can neither open a fake string nor alter the
     * template depth. The escape branch consumes BOTH the backslash and the
     * escaped character; the loop guard absorbs an input-final backslash.
     */
    private fun stringLiteralEnd(text: String, start: Int): Int {
        if (text.startsWith("\"\"\"", start)) {
            val close = text.indexOf("\"\"\"", start + 3)
            return if (close == -1) text.length else close + 3
        }
        var i = start + 1
        var templateDepth = 0
        while (i < text.length) {
            when {
                text[i] == '\\' -> i += 2
                templateDepth == 0 && text[i] == '"' -> return i + 1
                templateDepth == 0 && text[i] == '\n' -> return i // unterminated line string — bail at EOL
                text[i] == '$' && text.startsWith("{", i + 1) -> {
                    templateDepth++
                    i += 2
                }
                templateDepth > 0 && text.startsWith("//", i) -> {
                    val newline = text.indexOf('\n', i)
                    i = if (newline == -1) text.length else newline
                }
                templateDepth > 0 && text.startsWith("/*", i) -> {
                    var commentDepth = 1
                    i += 2
                    while (i < text.length && commentDepth > 0) {
                        when {
                            text.startsWith("/*", i) -> {
                                commentDepth++
                                i += 2
                            }
                            text.startsWith("*/", i) -> {
                                commentDepth--
                                i += 2
                            }
                            else -> i++
                        }
                    }
                }
                templateDepth > 0 && text[i] == '{' -> {
                    templateDepth++
                    i++
                }
                templateDepth > 0 && text[i] == '}' -> {
                    templateDepth--
                    i++
                }
                templateDepth > 0 && text[i] == '"' -> i = stringLiteralEnd(text, i)
                templateDepth > 0 && text[i] == '\'' -> i = charLiteralEnd(text, i)
                else -> i++
            }
        }
        return text.length
    }

    /** The index past the char literal opened at [start]. */
    private fun charLiteralEnd(text: String, start: Int): Int {
        var i = start + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i++
                '\'' -> return i + 1
                '\n' -> return i // not a literal after all — bail at EOL
            }
            i++
        }
        return text.length
    }

    // ------------------------------------------------------------ scanner fixtures

    /**
     * A memo commented out inside a NESTED block comment is not discovered
     * (the non-greedy removal this scan replaced would resurrect it after the
     * inner closer); the live memo beside it still is.
     */
    @Test
    fun scanner_skipsCacheDeclarationsInsideNestedBlockComments() {
        val discovered = discoveredCaches(
            stripComments(
                """
                private val liveCache = TtlCache<List<MediaItem>>(ttlMs = 1)

                /* disabled memo:
                /* inner note */
                private val commentedOutCache = TtlCache<List<MediaItem>>(ttlMs = 1)
                */
                """.trimIndent(),
            ),
        )
        assertTrue("liveCache" in discovered)
        assertTrue(
            "commentedOutCache" !in discovered,
            "a declaration inside a nested block comment must not be discovered, got: $discovered",
        )
    }

    /** A multiline initializer (`= TtlCache<` on the next line) is a memo. */
    @Test
    fun scanner_discoversMultilineCacheInitializers() {
        val discovered = discoveredCaches(
            """
            private val sameLineCache = TtlCache<List<MediaItem>>(ttlMs = 1)
            private val multilineCache =
                TtlCache<List<MediaItem>>(ttlMs = 1)
            """.trimIndent(),
        )
        assertTrue(
            "multilineCache" in discovered,
            "an unlisted multiline cache declaration must be discovered (and so fail the " +
                "guard's totality check), got: $discovered",
        )
        assertTrue("sameLineCache" in discovered)
    }

    /** A size-only invalidateCaches body is a mention, not a wholesale drop. */
    @Test
    fun scanner_rejectsASizeOnlyInvalidateCachesBody() {
        val walks = invalidateCachesWalksTheSpec(
            """
            fun invalidateCaches() {
                log(homeSubcallCaches.size)
            }
            """.trimIndent(),
        )
        assertTrue(
            !walks,
            "a body that reads homeSubcallCaches.size without traversing and clearing is not a drop",
        )
    }

    /**
     * A homeSubcallCaches reference AFTER invalidateCaches's closing brace
     * (another member's) must not lend its body the traversal evidence.
     */
    @Test
    fun scanner_rejectsAReferenceOutsideTheInvalidateCachesBody() {
        val walks = invalidateCachesWalksTheSpec(
            """
            fun invalidateCaches() {
            }

            fun elsewhere() {
                homeSubcallCaches.forEach { it.clear() }
            }
            """.trimIndent(),
        )
        assertTrue(
            !walks,
            "the traversal must live inside invalidateCaches's own braces, not a later member's",
        )
    }

    /**
     * A quoted closing brace must not end the method-body scan: the brace
     * balance skips string/char literals (escaped delimiters included), so a
     * body that logs `"}"` before the valid walk still reads as a walkable
     * drop.
     */
    @Test
    fun scanner_acceptsADropWhoseBodyCarriesABraceInAStringLiteral() {
        val walks = invalidateCachesWalksTheSpec(
            """
            fun invalidateCaches() {
                log("}")
                homeSubcallCaches.forEach { it.clear() }
            }
            """.trimIndent(),
        )
        assertTrue(
            walks,
            "a brace inside a string literal must not terminate the body scan before the drop",
        )
    }

    /**
     * The invalidation half must clear the ITERATED element: a traversal that
     * only reads it (a log walk) beside an unrelated `.clear(` call is a
     * mention, not a wholesale drop — the shape `invalidateTraversal` +
     * any-`.clear(` loose pairing accepted.
     */
    @Test
    fun scanner_rejectsATraversalThatNeverClearsItsElement() {
        val walks = invalidateCachesWalksTheSpec(
            """
            fun invalidateCaches() {
                homeSubcallCaches.forEach { log(it) }
                metrics.clear()
            }
            """.trimIndent(),
        )
        assertTrue(
            !walks,
            "a traversal that never clears its walked element is not a drop, even with an unrelated clear",
        )
    }

    /** An inferred-type constructor (`= TtlCache(`) is a memo; `TtlCacheGate(` never is. */
    @Test
    fun scanner_discoversInferredTypeCacheInitializers() {
        val discovered = discoveredCaches(
            """
            private val inferredCache = TtlCache(ttlMs = 1)
            private val gate = TtlCacheGate(ttlMs = 1)
            """.trimIndent(),
        )
        assertTrue(
            "inferredCache" in discovered,
            "an inferred-type TtlCache constructor must be discovered (and so fail the " +
                "guard's totality check until listed), got: $discovered",
        )
        assertTrue(
            "gate" !in discovered,
            "TtlCacheGate is a freshness stamp, not a memo — never discovered, got: $discovered",
        )
    }

    /**
     * The literal helpers return EXCLUSIVE ends, so the brace scan must
     * resume AT the returned index: resuming past it (the old shared
     * increment) skips the first character after each literal — here the `}`
     * closing the hollow body's lambda (`it == ';'}`) — and the scan would
     * overrun into the later method, borrowing its traversal to pass a
     * hollow drop. Class-wrapped because the overrun only reads as evidence
     * once a real traversal exists later in the same type.
     */
    @Test
    fun scanner_rejectsAHollowBodyWhoseLiteralSkipsItsClosingBrace() {
        val walks = invalidateCachesWalksTheSpec(
            """
            class CacheHost {
                fun invalidateCaches() {
                    tags.filterNot { it == ';'}
                }

                fun elsewhere() {
                    homeSubcallCaches.forEach { it.clear() }
                }
            }
            """.trimIndent(),
        )
        assertTrue(
            !walks,
            "the body scan must end at invalidateCaches's own closing brace even when a " +
                "char literal directly abuts it — a traversal in a later method is not evidence",
        )
    }

    /**
     * The clear-call half runs on the MASKED body: a traversal that only
     * logs a string MENTIONING the clear shape is prose, not a wholesale
     * drop (the positive `it.clear()` fixtures above stay the evidence).
     */
    @Test
    fun scanner_rejectsATraversalThatOnlyLogsAClearMention() {
        val walks = invalidateCachesWalksTheSpec(
            """
            fun invalidateCaches() {
                homeSubcallCaches.forEach { log("it.clear(") }
            }
            """.trimIndent(),
        )
        assertTrue(
            !walks,
            "a quoted it.clear( inside the traversal's lambda is a log line, not a drop",
        )
    }

    /**
     * A cache-clearing call inside a `${…}` interpolation is EXECUTABLE code,
     * not literal text: the masked body must keep expression interiors
     * visible so the clear-call half finds it — a wholesale drop expressed as
     * a log-line interpolation is still a drop — while a quoted mention
     * NESTED inside the same expression class stays masked prose.
     */
    @Test
    fun scanner_acceptsADropExpressedAsAClearCallInsideAnInterpolation() {
        val walks = invalidateCachesWalksTheSpec(
            """
            fun invalidateCaches() {
                homeSubcallCaches.forEach { log("cleared ${'$'}{it.clear()}") }
            }
            """.trimIndent(),
        )
        assertTrue(
            walks,
            "it.clear() inside a template expression executes — the mask must leave " +
                "interpolated expressions visible to the clear-call check",
        )
        val quotedInsideExpression = invalidateCachesWalksTheSpec(
            """
            fun invalidateCaches() {
                homeSubcallCaches.forEach { log("${'$'}{"it.clear("}") }
            }
            """.trimIndent(),
        )
        assertTrue(
            !quotedInsideExpression,
            "a quoted it.clear( nested inside an interpolation is still literal text — " +
                "the expression's own literals must stay masked",
        )
    }

    /**
     * The quote dispatch routes a `'`-opened literal to the CHAR masker: a
     * char literal misrouted into the string masker hunts a `"` closer,
     * blanking the code after it on that line (here the clear itself) — and
     * the real string it then lands on opens with its own `"` treated as the
     * bogus closer, leaving its contents verbatim evidence. The valid drop
     * with a char literal in its traversal stays accepted; the log-only one
     * whose quoted mention follows a char literal stays rejected.
     */
    @Test
    fun scanner_handlesCharLiteralsInsideTheTraversalLambda() {
        val walks = invalidateCachesWalksTheSpec(
            """
            fun invalidateCaches() {
                homeSubcallCaches.forEach { cache -> if (cache.sep == ';') cache.clear() }
            }
            """.trimIndent(),
        )
        assertTrue(
            walks,
            "a char literal in the traversal's lambda must not blank the clear call sharing " +
                "its line — the drop is valid",
        )
        val quotedAfterCharLiteral = invalidateCachesWalksTheSpec(
            """
            fun invalidateCaches() {
                homeSubcallCaches.forEach { log('x'); log("it.clear(") }
            }
            """.trimIndent(),
        )
        assertTrue(
            !quotedAfterCharLiteral,
            "a quoted it.clear( following a char literal is still literal text, not evidence",
        )
    }

    /**
     * Inside an interpolation a `{` opens a NESTED lambda whose own implicit
     * `it` shadows the traversal's element: `${…also { it.clear() }}` clears
     * the nested lambda's list, never the walked cache — the mask blanks the
     * nested binding so it cannot read as drop evidence — while the direct
     * `${it.clear()}` at the expression's own depth stays verbatim evidence.
     */
    @Test
    fun scanner_rejectsAClearCallBoundToANestedLambdaInsideAnInterpolation() {
        val walks = invalidateCachesWalksTheSpec(
            """
            fun invalidateCaches() {
                homeSubcallCaches.forEach {
                    log("${'$'}{mutableListOf<String>().also { it.clear() }}")
                }
            }
            """.trimIndent(),
        )
        assertTrue(
            !walks,
            "an it.clear( bound to a nested lambda inside an interpolation does not clear " +
                "the walked cache",
        )
        val direct = invalidateCachesWalksTheSpec(
            """
            fun invalidateCaches() {
                homeSubcallCaches.forEach { log("${'$'}{it.clear()}") }
            }
            """.trimIndent(),
        )
        assertTrue(
            direct,
            "the interpolation's own-depth it.clear( is the walked cache clearing — still evidence",
        )
    }

    /**
     * The line-comment half of [stripComments] (the discovery side of the
     * newline fix): a TRAILING comment must not swallow its newline, or the
     * memo on the next line fuses into the comment's line and the
     * (?m)^-anchored [cacheDeclaration] never sees it. The third declaration
     * pins the block-comment whitespace-fill the same way: a mid-token
     * comment removed bare would fuse `val` into the name (`valblockFused`).
     */
    @Test
    fun scanner_discoversUnlistedCachesPastTrailingAndInlineComments() {
        val discovered = discoveredCaches(
            stripComments(
                """
                private val commentedNeighbor = TtlCache<List<MediaItem>>(ttlMs = 1) // refreshed hourly
                private val belowLineComment = TtlCache<List<MediaItem>>(ttlMs = 1)
                private val/* mid-token */blockFused = TtlCache<List<MediaItem>>(ttlMs = 1)
                """.trimIndent(),
            ),
        )
        assertTrue(
            "belowLineComment" in discovered,
            "a trailing line comment must keep its newline so the next line's memo stays " +
                "line-anchored and discovered, got: $discovered",
        )
        assertTrue(
            "blockFused" in discovered,
            "a block comment must whitespace-fill so the tokens it split stay separated, got: $discovered",
        )
    }
}
