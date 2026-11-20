package com.raulshma.jellyplay.desktop

import java.io.File
import kotlin.test.Test
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

    /** `koinApp.koin.get<X>().start()` — the launchDesktopStartup arm keys. */
    private val startupArms = Regex("""get<(\w+)>\(\)[ \t]*\.[ \t]*start[ \t]*\(""")

    /**
     * A zero-arg member `fun start()` (the app-lifetime kickoff idiom),
     * crossing visibility/suspend/override modifiers; group 1 is the indent.
     * The leading-whitespace requirement keeps top-level functions out — a
     * start() that belongs to no class cannot be a Koin-startable arm.
     */
    private val startDeclaration = Regex(
        """(?m)^([ \t]+)(?:(?:public|internal|private|override|open|suspend)[ \t]+)*fun[ \t]+start[ \t]*\([ \t]*\)""",
    )

    /** `class X` / `interface X` / `object X`; group 1 is the indent, 2 the name. */
    private val typeDeclaration = Regex(
        """(?m)^([ \t]*)(?:(?:public|internal|private|open|abstract|sealed|data|enum|value|inner)[ \t]+)*(?:class|interface|object)[ \t]+(\w+)""",
    )

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
    private fun discoverStartables(root: File): Map<String, File> = buildMap {
        for (rootName in scanRoots) {
            val dir = root.resolve(rootName)
            assertTrue(dir.isDirectory, "missing $rootName — repo layout changed?")
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { File.separator + "build" + File.separator !in it.absolutePath }
                .forEach { file ->
                    val text = stripComments(file.readText())
                    val types = typeDeclaration.findAll(text)
                        .map { TypeMark(it.range.first, indentWidth(it.groupValues[1]), it.groupValues[2]) }
                        .toList()
                    startDeclaration.findAll(text).forEach { match ->
                        val startIndent = indentWidth(match.groupValues[1])
                        types.lastOrNull {
                            it.position < match.range.first && it.indent < startIndent
                        }?.let { (_, _, owner) ->
                            put(owner, file)
                        }
                    }
                }
        }
    }

    /** A type head's position: offset in the stripped text, indent, simple name. */
    private data class TypeMark(val position: Int, val indent: Int, val name: String)

    /** Tabs expand to the repo's 4-space indent unit. */
    private fun indentWidth(whitespace: String): Int =
        whitespace.sumOf { if (it == '\t') 4 else 1 }

    /** Drops comments (Kotlin block comments NEST — loop until stable). */
    private fun stripComments(text: String): String {
        var stripped = text
        while (true) {
            val next = stripped.replace(
                Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL),
                "",
            )
            if (next == stripped) break
            stripped = next
        }
        return stripped.replace(Regex("//[^\n]*"), "")
    }
}
