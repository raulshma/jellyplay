package com.raulshma.jellyplay.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Desktop startup start() totality guard (source-scan on plain text, no PSI —
 * deliberately cheap, but executable; the `WorkerRegistrationTotalityGuardTest`
 * / [KoinModuleRegistrationGuardTest] shape).
 *
 * [launchDesktopStartup] hand-lists the app-lifetime `koinApp.koin.get<X>().start()`
 * arms. A NEW desktop subsystem exposing a zero-arg `fun start()` compiles
 * cleanly whether or not it joins that list — and an arm left out never runs.
 * This failure mode already shipped once: DesktopPlaybackSyncScheduler's
 * desktop actual was an honest no-op and "staged outbox rows sat forever"
 * (its KDoc records the defect). Compile gates are blind to it; the guard
 * converts it into a build failure by asserting every zero-arg `fun start()`
 * in the desktop-relevant sources is EITHER launched by [launchDesktopStartup]
 * OR carries a dated pin in [documentedNonStartupStarts] — and, reverse
 * direction, every launched arm resolves to a real zero-arg start().
 *
 * The universe is the zero-arg start() idiom ("the DesktopDownloadManager
 * idiom" — idempotent app-lifetime kickoff on the manager's own scope):
 * classes (indented member functions) declaring `fun start()` under
 * [scanRoots] — core:data's commonMain/jvmShared/jvmMain (where the shared
 * session/repository faces and the desktop schedulers live) plus the desktop
 * app's main sources. androidMain is excluded: the desktop never starts
 * Android actuals, and Android's own kickoff story is its lane's concern.
 * Parameterized `start(...)` calls are a different verb shape (session-scoped
 * wiring with explicit scopes/arguments, e.g. DesktopIdleMonitor) and stay
 * outside the universe.
 *
 * To add a startable subsystem: declare the class, land its
 * `koinApp.koin.get<X>().start()` arm WITH the class, and this scan finds both
 * halves — a class with no arm, an orphaned arm after a rename, or a stale pin
 * all fail here with the class named.
 */
class DesktopStartupTotalityGuardTest {

    private val scanRoots = listOf(
        "shared/core/data/src/commonMain",
        "shared/core/data/src/jvmShared",
        "shared/core/data/src/jvmMain",
        "apps/desktop/src/main/kotlin",
    )

    private val startupFile =
        "apps/desktop/src/main/kotlin/com/raulshma/jellyplay/desktop/DesktopStartup.kt"

    /**
     * Ratchet floor: zero-arg `fun start()` holders under [scanRoots]
     * (currently 18 — five jvmMain desktop subsystems, two commonMain session
     * faces + four collaborator classes, one jvmShared summary surface + three
     * collaborator classes, two desktop services + their IPC client). A
     * discovery-rot regression (regex stops matching, roots move) would
     * otherwise make the totality check vacuously green on an empty set.
     */
    private val minDiscoveredStartables = 18

    /**
     * The zero-arg start() holders that are NOT launchDesktopStartup arms,
     * each with its landing date + one-line reason. Adding an entry here is a
     * documented decision ("started by X, not at app startup"), never a
     * convenience. Pinned BOTH WAYS: a pin for a class launchDesktopStartup
     * actually starts — or that no longer exists — is stale and fails.
     */
    private val documentedNonStartupStarts: Map<String, String> = mapOf(
        "AudioProgressReporter" to
            "2026-11-20 — per-session audio progress reporting; held and started by " +
            "the audio playback stacks (AudioPlaybackManager / DesktopAudioQueueManager) " +
            "when a playback session opens, not at app startup",
        "PeriodicProgressReportLoop" to
            "2026-11-20 — the per-session progress ticker behind AudioProgressReporter " +
            "and player-video's PlaybackProgressReporter; starts with its session",
        "JellyPlayEventsRepository" to
            "2026-11-20 — the events SSE face runs for exactly one signed-in session; " +
            "JellyPlayEventsSessionController starts it on the session-open edge",
        "JellyPushRepository" to
            "2026-11-20 — the push registration flow; JellyPlayEventsSessionController " +
            "starts it on the session-open edge",
        "RemoteControlReceiver" to
            "2026-11-20 — WS remote-control intake; RealtimeSessionController " +
            "(feature/shell) wires `remoteControlReceiver::start` and starts it per " +
            "signed-in realtime session",
        "TimeSyncManager" to
            "2026-11-20 — SyncPlay clock sync; SyncPlayManager starts it when a " +
            "SyncPlay session starts",
        "ReconnectTrigger" to
            "2026-11-20 — the going-online edge machine; each desktop " +
            "scheduler/manager starts its own trigger inside its start()",
        "DiscordIpcClient" to
            "2026-11-20 — the Discord IPC transport loop; DiscordPresenceService " +
            "starts its client inside its own start()",
    )

    @Test
    fun everyZeroArgStart_isLaunchedByDesktopStartupOrPinned() {
        val root = repoRoot()
        val discovered = discoverStartables(root)
        assertTrue(
            discovered.size >= minDiscoveredStartables,
            "discovered only ${discovered.size} zero-arg fun start() holders under " +
                "${scanRoots.joinToString()}, expected >= $minDiscoveredStartables — discovery is " +
                "broken (moved package? renamed declarations?), fix the scan in " +
                javaClass.simpleName,
        )

        val startupText = root.resolve(startupFile).let { file ->
            assertTrue(file.isFile, "missing $startupFile — repo layout changed?")
            stripComments(file.readText())
        }
        val launched = startupArms.findAll(startupText).map { it.groupValues[1] }.toSet()
        assertTrue(
            launched.isNotEmpty(),
            "launchDesktopStartup launches no start() arms anymore? Restore the list.",
        )

        val problems = mutableListOf<String>()

        // Forward: a discovered startable neither launched nor pinned compiles
        // cleanly and never starts — the shipped defect shape.
        val unlaunched = (discovered.keys - launched - documentedNonStartupStarts.keys).sorted()
        if (unlaunched.isNotEmpty()) {
            problems += unlaunched.map { name ->
                "  - '$name' (${discovered.getValue(name).name}) declares a zero-arg fun start() " +
                    "but launchDesktopStartup never starts it — the subsystem compiles cleanly " +
                    "and silently stays dead (DesktopPlaybackSyncScheduler shipped exactly this: " +
                    "staged outbox rows sat forever). Add " +
                    "`koinApp.koin.get<$name>().start()` to launchDesktopStartup, or — if it is " +
                    "genuinely started elsewhere — add a dated pin to documentedNonStartupStarts."
            }
        }

        // Reverse: every launched arm resolves to a discovered startable (a
        // rename that orphaned an arm fails here).
        val orphaned = (launched - discovered.keys).sorted()
        if (orphaned.isNotEmpty()) {
            problems += orphaned.map { name ->
                "  - launchDesktopStartup starts '$name' but no zero-arg fun start() exists " +
                    "for it under ${scanRoots.joinToString()} — renamed or removed? " +
                    "Fix or drop the arm in $startupFile."
            }
        }

        if (problems.isNotEmpty()) {
            fail(
                "the desktop startup start() arms are not total (every zero-arg fun start() " +
                    "in the desktop-relevant sources is either launched by launchDesktopStartup " +
                    "or carries a dated pin):\n" +
                    problems.joinToString("\n")
            )
        }
    }

    @Test
    fun everyPin_namesALiveNotLaunchedStartable() {
        val root = repoRoot()
        val discovered = discoverStartables(root)
        val launched = startupArms
            .findAll(stripComments(root.resolve(startupFile).readText()))
            .map { it.groupValues[1] }
            .toSet()

        // A pin whose class was renamed/removed is dead documentation; a pin
        // for an arm launchDesktopStartup actually starts claims the opposite
        // of the code.
        documentedNonStartupStarts.forEach { (name, reason) ->
            assertTrue(
                name in discovered,
                "pin for '$name' matches no zero-arg fun start() under " +
                    "${scanRoots.joinToString()} — renamed or removed? Update or drop the pin " +
                    "(was: $reason).",
            )
            assertTrue(
                name !in launched,
                "'$name' is pinned as NOT a startup arm ($reason) but launchDesktopStartup " +
                    "starts it — drop the stale pin.",
            )
        }
    }

    // ------------------------------------------------------------ plumbing

    /** Walks up from the test working dir (apps/desktop) to settings.gradle.kts. */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("could not locate repo root (no settings.gradle.kts walking up from ${System.getProperty("user.dir")})")
    }

    /**
     * `koinApp.koin.get<X>().start()` — the launchDesktopStartup arm keys.
     * The gaps admit any whitespace including newlines: a chain formatted
     * across lines (`get<X>()` closing one line, `.start()` opening the
     * next) is the same arm, and an unrecognized layout would report its
     * startable unlaunched and fail the guard on valid source.
     */
    private val startupArms = Regex("""get<(\w+)>\(\)\s*\.\s*start\s*\(""")

    /**
     * A zero-arg member `fun start()` (the app-lifetime kickoff idiom),
     * crossing visibility/suspend/override modifiers AND the expect/actual
     * pair (an `actual fun start()` implementation without `override`, or an
     * `expect fun start()` face, is as much a startable as a plain one);
     * group 1 is the indent. The leading-whitespace requirement keeps
     * top-level functions out — a start() that belongs to no class cannot be
     * a Koin-startable arm.
     */
    private val startDeclaration = Regex(
        """(?m)^([ \t]+)(?:(?:public|internal|private|override|open|suspend|expect|actual)[ \t]+)*fun[ \t]+start[ \t]*\([ \t]*\)""",
    )

    /**
     * `class X` / `interface X` / `object X`, including their `expect`/`actual`
     * declarations (an `expect object`'s members belong to the same holder as
     * its `actual object`); group 1 is the indent, 2 the name.
     */
    private val typeDeclaration = Regex(
        """(?m)^([ \t]*)(?:(?:public|internal|private|open|abstract|sealed|data|enum|value|inner|expect|actual)[ \t]+)*(?:class|interface|object)[ \t]+(\w+)""",
    )

    /** The declaring file's `package …` clause — the merge's qualifier source. */
    private val packageDeclaration = Regex("""(?m)^package[ \t]+([\w.]+)""")

    /**
     * Every zero-arg start() holder under [scanRoots], mapped to its declaring
     * file. Each start() belongs to the nearest PRECEDING type declaration
     * OUTDENTED relative to it (a member function is indented past its class
     * head, so a nested `private class` head inside the body must not steal
     * the enclosing class's later members — TimeSyncManager's Measurement and
     * DesktopDownloadManager's TransferHandle both precede their outer class's
     * start()). Strings that merely mention `class X` can only mis-attribute
     * when they textually precede a start() in the same file — the idiom's
     * accepted cheapness.
     */
    private fun discoverStartables(root: File): Map<String, File> {
        // simple name → (qualified type id → declaring file). The qualified
        // key (package + simple name — the textual identity closest to the
        // compiler's) dedups the expect/actual pair, which is ONE type with
        // the same id across two source roots; [mergeBySimpleName] rejects
        // distinct ids collapsing onto one launch name.
        val qualifiedByName = mutableMapOf<String, MutableMap<String, File>>()
        for (rootName in scanRoots) {
            val dir = root.resolve(rootName)
            assertTrue(dir.isDirectory, "missing $rootName — repo layout changed?")
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { File.separator + "build" + File.separator !in it.absolutePath }
                .forEach { file ->
                    val text = stripComments(file.readText())
                    val pkg = packageDeclaration.find(text)?.groupValues?.get(1).orEmpty()
                    discoverInText(text).forEach { name ->
                        qualifiedByName.getOrPut(name) { mutableMapOf() }[
                            if (pkg.isEmpty()) name else "$pkg.$name"
                        ] = file
                    }
                }
        }
        return mergeBySimpleName(qualifiedByName)
    }

    /**
     * Collapses qualified type identities onto the simple launch-arm names —
     * `get<X>()` keys the arms by X's simple name (imports resolve the rest),
     * so the discovered map must too. ONE qualified id per simple name merges
     * (the expect/actual pair is the same type); TWO distinct ids
     * (`com.a.Reporter` + `com.b.Reporter`) mean the arm names only the
     * imported type and this scan cannot tell which — silently merging would
     * let the unlaunched same-named type pass the totality check, so fail
     * with both identities named instead.
     */
    private fun mergeBySimpleName(qualifiedByName: Map<String, Map<String, File>>): Map<String, File> = buildMap {
        qualifiedByName.forEach { (name, qualified) ->
            if (qualified.size > 1) {
                error(
                    "two distinct types share the start() holder name '$name' — " +
                        qualified.entries.joinToString(" vs ") { (id, file) -> "$id ($file)" } +
                        ". get<$name>() names only the imported one, so the totality scan cannot " +
                        "tell which type an arm launches. Rename one of them, or teach the scan " +
                        "qualified matching.",
                )
            }
            put(name, qualified.values.single())
        }
    }

    /**
     * One stripped source text's zero-arg start() holders (simple names) —
     * the per-file body of [discoverStartables], split out so the scanner's
     * own discovery patterns have a direct fixture surface.
     */
    private fun discoverInText(text: String): Set<String> = buildSet {
        val types = typeDeclaration.findAll(text)
            .map { TypeMark(it.range.first, indentWidth(it.groupValues[1]), it.groupValues[2]) }
            .toList()
        startDeclaration.findAll(text).forEach { match ->
            val startIndent = indentWidth(match.groupValues[1])
            types.lastOrNull {
                it.position < match.range.first && it.indent < startIndent
            }?.let { (_, _, owner) ->
                add(owner)
            }
        }
    }

    /** A type head's position: offset in the stripped text, indent, simple name. */
    private data class TypeMark(val position: Int, val indent: Int, val name: String)

    /** Tabs expand to the repo's 4-space indent unit. */
    private fun indentWidth(whitespace: String): Int =
        whitespace.sumOf { if (it == '\t') 4 else 1 }

    /**
     * Drops comments. Kotlin block comments NEST, so a non-greedy
     * `/*…*/` removal stops at the FIRST closer and would resurrect the
     * disabled code between an inner closer and the outer one as "live"
     * source; this scan tracks depth instead, removing each complete OUTER
     * comment (nested ones included). Line comments whitespace-fill with the
     * newline kept (an opener inside one cannot open a block) — eating the
     * newline would fuse a trailing comment's line into the next one, and the
     * (?m)^-anchored [startDeclaration] would miss the fun start() that
     * followed it; and string/char literals are copied
     * verbatim so executable code carrying an opener (a DLNA MIME wildcard,
     * a regex) never opens a comment — everything outside comments is
     * preserved.
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
                    i += 2
                    while (i < text.length && depth > 0) {
                        when {
                            text.startsWith("/*", i) -> {
                                depth++
                                i += 2
                            }
                            text.startsWith("*/", i) -> {
                                depth--
                                i += 2
                            }
                            else -> i++
                        }
                    }
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
     * braces count — a lambda body inside the expression is common), and
     * string/char literals INSIDE the expression recurse, so quotes in a
     * fallback branch (`"${if (ok) "a" else "b"}"`) do not terminate the
     * outer string and spill its tail — including any comment marker it
     * carries — into the code path. The escape branch consumes BOTH the
     * backslash and the escaped character (a `\"` must not read as the
     * literal's terminator; the loop guard absorbs an input-final backslash).
     * COMMENTS inside the expression are skipped before quotes/braces are
     * interpreted — line comments to (not past) the newline, block comments
     * to their depth-matched closer (Kotlin nests them) — so a quoted or
     * braced comment body can neither open a fake string nor alter the
     * template depth.
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
     * Discovery recognizes the expect/actual pair: an `expect class` face, its
     * `actual class` implementation, and an `actual fun start()` without
     * `override` are all startable shapes — a regex that stops at the plain
     * modifier list would exclude them and rot the totality set silently.
     * Each form is scanned SEPARATELY: one combined fixture would collapse
     * both into a single set entry, and a regex that lost one half would
     * still pass off the other.
     */
    @Test
    fun scanner_discoversExpectAndActualStartHolders() {
        val expectFace = discoverInText(
            stripComments(
                """
                expect class SessionProbe {
                    fun start()
                }
                """.trimIndent(),
            ),
        )
        assertTrue(
            "SessionProbe" in expectFace,
            "expected the expect class face to be discovered, got: $expectFace",
        )
        val actualFace = discoverInText(
            stripComments(
                """
                actual class SessionProbe {
                    actual fun start() {
                        probe()
                    }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(
            "SessionProbe" in actualFace,
            "expected the actual class implementation to be discovered, got: $actualFace",
        )
    }

    /**
     * The line-comment half of [stripComments]: a TRAILING comment must not
     * eat its newline — the fused line would hide the following `fun start()`
     * from the (?m)^-anchored [startDeclaration] (a silently lost startable,
     * the discovery-rot shape the ratchet floor guards against only
     * in aggregate).
     */
    @Test
    fun scanner_keepsTheLineStartPastATrailingLineComment() {
        val holders = discoverInText(
            stripComments(
                """
                class TrailingCommentHolder {
                    val probe = 1 // kickoff note
                    fun start() {
                    }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(
            "TrailingCommentHolder" in holders,
            "a fun start() below a trailing line comment must still be discovered, got: $holders",
        )
    }

    /**
     * The nested-comment half of [stripComments]: disabled code between an
     * inner closer and the outer comment's own closer must NOT surface as a
     * live declaration (the non-greedy removal shape this scan replaced).
     */
    @Test
    fun scanner_ignoresDisabledCodeInsideNestedBlockComments() {
        val holders = discoverInText(
            stripComments(
                """
                class LiveHolder {
                    fun start() {
                    }
                }

                /* disabled experiment:
                /* phase one */
                class DeadHolder {
                    fun start() {
                    }
                }
                */
                """.trimIndent(),
            ),
        )
        assertTrue("LiveHolder" in holders)
        assertTrue(
            "DeadHolder" !in holders,
            "a class inside a nested block comment must not count as a startable, got: $holders",
        )
    }

    /**
     * The arm chain split across lines (`get<X>()` closing one, `.start()`
     * opening the next) is the same launch arm — a whitespace gap that only
     * admitted spaces/tabs would report the startable unlaunched and fail
     * the guard on valid source.
     */
    @Test
    fun scanner_recognizesAStartupArmSplitAcrossLines() {
        val arms = startupArms.findAll(
            """
            private fun launchDesktopStartup() {
                koinApp.koin
                    .get<DesktopDownloadManager>()
                    .start()
            }
            """.trimIndent(),
        ).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf("DesktopDownloadManager"),
            arms,
            "an arm chain formatted across lines must still be recognized",
        )
    }

    /**
     * The string-template half of [stripComments]: quotes INSIDE a `${…}`
     * expression (the fallback-string shape) must not terminate the outer
     * string — the spilled tail would hand its comment marker to the
     * stripper, which would eat everything past it (the declaration below
     * included). The nested literal's comment opener is deliberately
     * unterminated on its line so the mis-parse cannot recover.
     */
    @Test
    fun scanner_keepsDeclarationsVisiblePastATemplateCarryingQuotedCommentMarkers() {
        val holders = discoverInText(
            stripComments(
                """
                class TemplateHolder {
                    val fallback = "pick ${'$'}{if (ok) "open /* marker" else "closed"}"
                    fun start() {
                    }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(
            "TemplateHolder" in holders,
            "a fun start() below a string template with nested quoted literals must still be " +
                "discovered — the template's quotes must not expose its content to comment " +
                "stripping, got: $holders",
        )
    }

    /**
     * The escape half of [stringLiteralEnd]: the branch must consume BOTH the
     * backslash and the escaped character. A single-char advance re-examines
     * the escaped character as if unescaped — an escaped `\"` would terminate
     * the literal early and spill its tail (the comment-like markers below)
     * into the code path, where the block-comment opener among them eats the
     * live declaration after it.
     */
    @Test
    fun scanner_keepsDeclarationsVisiblePastAnEscapedQuoteCarryingCommentMarkers() {
        val holders = discoverInText(
            stripComments(
                """
                class EscapedQuoteHolder {
                    val shout = "say \"open /* marker\" loudly"
                    fun start() {
                    }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(
            "EscapedQuoteHolder" in holders,
            "a fun start() below a string with an escaped quote followed by comment-like " +
                "markers must still be discovered — the escape must consume the escaped " +
                "character, got: $holders",
        )
    }

    /**
     * Comments inside a `${…}` expression are prose: a quote in one must not
     * open a fake string (which would swallow the template's real closer and,
     * with it, the rest of the file as "string" text — resurrecting the
     * commented-out startable below), and a brace in one must not alter the
     * template depth.
     */
    @Test
    fun scanner_ignoresACommentedStartablePastAQuoteInsideATemplateExpressionComment() {
        val holders = discoverInText(
            stripComments(
                """
                class TemplateCommentHolder {
                    val probe = "${'$'}{ /* " } */ 0 }"
                    fun start() {
                    }
                }

                /* disabled experiment:
                class GhostHolder {
                    fun start() {
                    }
                }
                */
                """.trimIndent(),
            ),
        )
        assertTrue("TemplateCommentHolder" in holders)
        assertTrue(
            "GhostHolder" !in holders,
            "a quote inside a template-expression comment must not expose the text after " +
                "the template to discovery — the commented-out startable below must stay " +
                "dead, got: $holders",
        )
    }

    /**
     * The discovery merge: two DISTINCT qualified types sharing a simple
     * launch name (`com.a.Reporter` vs `com.b.Reporter`) must fail the merge
     * — the arm keys are simple names, so a silent merge would let the
     * unlaunched same-named type pass totality — while the expect/actual
     * pair (the SAME qualified identity across two source roots) still
     * dedups to one entry.
     */
    @Test
    fun scanner_rejectsDistinctQualifiedTypesSharingALaunchName() {
        val collision = assertFailsWith<IllegalStateException> {
            mergeBySimpleName(
                mapOf(
                    "Reporter" to mapOf(
                        "com.a.Reporter" to File("shared/core/data/src/jvmMain/kotlin/com/a/Reporter.kt"),
                        "com.b.Reporter" to File("shared/core/data/src/jvmMain/kotlin/com/b/Reporter.kt"),
                    ),
                ),
            )
        }
        assertTrue(
            "com.a.Reporter" in collision.message!! && "com.b.Reporter" in collision.message!!,
            "the collision must name both identities, got: ${collision.message}",
        )
        val deduped = mergeBySimpleName(
            mapOf(
                "SessionProbe" to mapOf(
                    "com.raulshma.jellyplay.core.data.SessionProbe" to
                        File("shared/core/data/src/commonMain/kotlin/com/raulshma/jellyplay/core/data/SessionProbe.kt"),
                    "com.raulshma.jellyplay.core.data.SessionProbe" to
                        File("shared/core/data/src/jvmMain/kotlin/com/raulshma/jellyplay/core/data/SessionProbe.kt"),
                ),
            ),
        )
        assertTrue(
            "SessionProbe" in deduped,
            "the expect/actual pair is one qualified type — it must merge to one launch name",
        )
    }
}
