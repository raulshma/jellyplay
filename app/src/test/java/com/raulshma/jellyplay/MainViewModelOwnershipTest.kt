package com.raulshma.jellyplay

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ratchet against the ViewModel regrowing into a god object (the shared
 * feature modules' [com.raulshma.jellyplay.feature.details.DetailViewModelOwnershipTest]
 * walker pattern): the set of MainViewModel's public members (val/var/fun,
 * primary-constructor parameters and private members excluded) must never
 * grow past the pinned baseline. The post-decoupling shape (baseline below)
 * is: the [com.raulshma.jellyplay.navigation.MainShellModel] seam it
 * implements (shell flows + commands), the intent/deep-link/shortcut entry
 * points MainActivity owns (handleDeepLink / handleShortcutIntent /
 * handleSharedText), and the small remainder the shell composition reads
 * (preferences, logout, markOnboardingCompleted) — session, update,
 * What's New and SyncPlay-open state all live in the coordinators the
 * composition resolves through ShellInfra, not here.
 *
 * Baseline: the 2026-09-27 post-decoupling state — 28 members (10 vals +
 * 18 funs), pinned exactly by name and kind below. Never add an entry:
 * new shell state belongs in a coordinator consumed via ShellInfra, new
 * shell commands in [MainShellModel], new intents in the existing
 * intent/shortcut folds. Shrink the baseline when members are deleted;
 * never raise it to admit new ones.
 */
class MainViewModelOwnershipTest {

    /**
     * The exact public surface at the 2026-09-27 post-decoupling baseline,
     * as "kind name" pairs. Remove entries when members are deleted; never
     * add entries.
     */
    private val baselineMembers = setOf(
        // MainShellModel flows (the seam the main shell renders through).
        "val offlineMode",
        "val activeDownloadCount",
        "val isGoingOnline",
        "val pendingRoute",
        "val pendingSearchQuery",
        "val surpriseRequests",
        "val isAdmin",
        "val isRefreshingAdmin",
        // MainShellModel commands.
        "fun consumeStateLossRestore",
        "fun toggleOfflineMode",
        "fun setHomeMode",
        "fun requestSurprise",
        "fun refreshAdminStatus",
        "fun consumePendingRoute",
        "fun consumePendingSearchQuery",
        "fun buildExternalPlayerLaunch",
        "fun reportExternalPlaybackStart",
        "fun reportExternalPlaybackStopped",
        // Read side beyond the interface: the merged preference pipeline and
        // the one-shot signals the shell composition consumes.
        "val preferences",
        "val surpriseOnLaunch",
        // Shell commands outside MainShellModel.
        "fun logout",
        "fun markOnboardingCompleted",
        // Intent / deep-link / shared-text surfaces MainActivity owns.
        "fun handleShortcutIntent",
        "fun consumeSurpriseOnLaunch",
        "fun handleDeepLink",
        "fun handleSharedText",
        // Shell-overlay navigation + the search entry the shell invokes.
        "fun navigateFromShell",
        "fun handleSearchQuery",
    )

    /**
     * A class-body declaration line at the ViewModel's single level of
     * member indentation: optional visibility/modifier keywords, then
     * val/var/fun. Primary-constructor parameters share the indentation but
     * always carry a trailing comma; nested declarations (companion,
     * function bodies, the private sealed class header) sit one level
     * deeper or carry `private` — all excluded.
     */
    private val memberDeclaration = Regex(
        "^ {4}(?:(?:public|internal|protected|open|override|suspend|inline|actual|expect|operator|infix|lateinit|const|abstract)\\s+)*(?:val|var|fun)\\s",
    )

    private fun viewModelSource(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        // MainViewModel lives in the app module's Android main source set
        // (src/main/java), not a shared module's commonMain/jvmShared — the
        // walk targets that directory.
        while (dir != null && !File(dir, "src/main/java").isDirectory) dir = dir.parentFile
        assertTrue(
            "could not locate src/main/java from ${System.getProperty("user.dir")}",
            dir != null,
        )
        return File(dir!!, "src/main/java/com/raulshma/jellyplay/MainViewModel.kt")
    }

    /**
     * Normalizes a matched declaration line to its "kind name" pair, so the
     * baseline pins names AND kinds (a val silently becoming a fun reads as
     * a new member and trips the ratchet).
     */
    private fun memberKey(line: String): String {
        val tokens = line.trim().split(Regex("\\s+")).filter { it !in MODIFIER_KEYWORDS }
        val kind = tokens.first()
        val name = tokens[1].substringBefore(":").substringBefore("(").substringBefore("=")
        return "$kind $name"
    }

    /**
     * Removes line and block comments so the ratchet checks *code*, not KDoc
     * prose (the ViewModel legitimately mentions its members in
     * documentation), while leaving string literals intact.
     */
    private fun String.stripComments(): String {
        val out = StringBuilder()
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
                inString -> {
                    out.append(c)
                    if (c == '\\') { out.append(next); i++ }
                    else if (c == '"') inString = false
                }
                inChar -> {
                    out.append(c)
                    if (c == '\\') { out.append(next); i++ }
                    else if (c == '\'') inChar = false
                }
                else -> when {
                    c == '/' && next == '/' -> inLine = true
                    c == '/' && next == '*' -> inBlock = true
                    c == '"' -> { inString = true; out.append(c) }
                    c == '\'' -> { inChar = true; out.append(c) }
                    else -> out.append(c)
                }
            }
            i++
        }
        return out.toString()
    }

    @Test
    fun `mainViewModel public member set never grows past the baseline`() {
        val file = viewModelSource()
        assertTrue("MainViewModel.kt not found at ${file.path}", file.isFile)
        val members = file.readText(Charsets.UTF_8).stripComments().lineSequence()
            .filter { line -> memberDeclaration.containsMatchIn(line) }
            .filter { line -> !line.trimEnd().endsWith(",") }
            .filter { line -> !line.contains(Regex("\\bprivate\\b")) }
            .map(::memberKey)
            .toList()
        val newMembers = members - baselineMembers
        assertTrue(
            "MainViewModel grew new public members (baseline ${baselineMembers.size}, " +
                "now ${members.size}):\n${newMembers.joinToString("\n")}\n" +
                "New shell state belongs in a coordinator consumed via ShellInfra; new shell " +
                "commands in MainShellModel; new intents in the existing intent/shortcut folds. " +
                "Shrink the baseline when members are deleted; never raise it.",
            newMembers.isEmpty(),
        )
    }

    private companion object {
        val MODIFIER_KEYWORDS = setOf(
            "public", "internal", "protected", "open", "override", "suspend",
            "inline", "actual", "expect", "operator", "infix", "lateinit",
            "const", "abstract",
        )
    }
}
