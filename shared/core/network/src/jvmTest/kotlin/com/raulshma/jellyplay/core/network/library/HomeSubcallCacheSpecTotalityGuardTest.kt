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
        if (!invalidateIteratesSpec.containsMatchIn(fetcherText)) {
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
     * TtlCache memo declaration in a scanned file. The type-annotation gap
     * may not cross a line or an `=`, so the spec lists
     * (`List<TtlCache<*>> = listOf(`) never match, and `TtlCacheGate(...)`
     * initializers match neither (the gate is a stamp, not a memo).
     */
    private val cacheDeclaration = Regex(
        """(?m)^[ \t]*(?:(?:private|internal|public)[ \t]+)*val[ \t]+(\w+)[ \t]*(?::[^=\n]+)?=[ \t]*TtlCache<""",
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

    /** `fun invalidateCaches() { … homeSubcallCaches …` — the drop walks the list. */
    private val invalidateIteratesSpec = Regex(
        """fun[ \t]+invalidateCaches[ \t]*\(\)[ \t]*\{.*?homeSubcallCaches""",
        RegexOption.DOT_MATCHES_ALL,
    )

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
