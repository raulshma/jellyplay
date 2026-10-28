package com.raulshma.jellyplay.feature.downloads

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ratchet against the ViewModel regrowing into a god object (the
 * [com.raulshma.jellyplay.feature.details.DetailViewModelOwnershipTest]
 * pattern): the count of DownloadsViewModel's public + internal members
 * (properties and functions, primary-constructor parameters and private
 * members excluded) must never increase past the current ceiling. New
 * behaviour belongs in an extracted module built from constructor lambdas —
 * the resync/freshness family's [DownloadsResyncActions] seam (built from
 * the OfflineResync + OfflineRepository + DownloadQueue constructor deps) is
 * exactly that carve-out, and the read side (the state flows, the
 * confirmation hosts, the messages feed) stays public as queries.
 *
 * Unlike the detail/home models, this file carries two public top-level data
 * classes ([DownloadsUiState], and ForceResyncCandidate before it relocated
 * with its fold) AHEAD of the ViewModel, so the scan slices the class body
 * at `class DownloadsViewModel` instead of naively counting the whole file's
 * 4-space declarations (detail's file-tail state classes are private, so its
 * whole-file scan never faced this) — the VM is the file's last top-level
 * declaration, so the slice is the class body plus nothing else.
 *
 * Baseline (2026-10-28): 44 members — 12 of them the resync/freshness
 * cluster (the updatesAvailable/updateRows/resyncProgress/checking flows,
 * the checkAllForUpdates / resyncOne / resyncAll (both overloads) /
 * clearResyncProgress / forceResync verbs, the forceResyncCandidates query,
 * and the private forceResyncEligibleStatuses policy set behind it).
 *
 * 2026-10-28, ceiling 44 → 34 (documented, one-time): the resync/freshness
 * family moved OUT of the VM into [DownloadsResyncActions] (the
 * MetadataAdminActions seam shape) — the four flows, the six verbs, the
 * forceResyncCandidates query, and the private forceResyncEligibleStatuses
 * policy behind it (now single-sited in the holder, with
 * ForceResyncCandidate relocating alongside its fold). ONE VM accessor
 * (`resyncActions`) replaces the 11 public members + 1 private; the
 * post-action UI effects (the messages fold, selection clears) stayed
 * VM-side per the DownloadActions recorded rule (effects-fold-stays-VM-side
 * — resync is a read/refresh family, so the holder is fine, but post-action
 * UI effects do not move). This is a member RELOCATION, not accretion; the
 * ceiling stays 34 unless another relocation removes it.
 */
class DownloadsViewModelOwnershipTest {

    /** The maximum allowed public + internal members (see class KDoc). */
    private val maxPublicInternalMembers = 34

    /**
     * A class-body declaration line at the ViewModel's single level of
     * member indentation: optional visibility/modifier keywords, then
     * val/var/fun. Primary-constructor parameters share the indentation but
     * always carry a trailing comma; nested declarations (function bodies,
     * the init block) sit one level deeper — both are excluded.
     */
    private val memberDeclaration = Regex(
        "^ {4}(?:(?:public|internal|protected|open|override|suspend|inline|actual|expect|operator|infix|lateinit|const|abstract)\\s+)*(?:val|var|fun)\\s",
    )

    private fun viewModelSource(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "src/commonMain/kotlin").isDirectory) dir = dir.parentFile
        assertTrue(
            dir != null,
            "could not locate src/commonMain/kotlin from ${System.getProperty("user.dir")}",
        )
        return File(dir!!, "src/commonMain/kotlin/com/raulshma/jellyplay/feature/downloads/DownloadsViewModel.kt")
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

    /** The comment-stripped class body: from `class DownloadsViewModel` to EOF. */
    private fun viewModelBody(): String {
        val file = viewModelSource()
        assertTrue(file.isFile, "DownloadsViewModel.kt not found at ${file.path}")
        val code = file.readText(Charsets.UTF_8).stripComments()
        val start = Regex("(?m)^class DownloadsViewModel").find(code)
        assertTrue(start != null, "class DownloadsViewModel declaration not found")
        return code.substring(start!!.range.first)
    }

    @Test
    fun `downloadsViewModel public and internal member count never increases`() {
        val members = viewModelBody().lineSequence()
            .filter { line -> memberDeclaration.containsMatchIn(line) }
            .filter { line -> !line.trimEnd().endsWith(",") }
            .filter { line -> !line.contains(Regex("\\bprivate\\b")) }
            .toList()
        assertTrue(
            members.size <= maxPublicInternalMembers,
            "DownloadsViewModel grew to ${members.size} public+internal members (ceiling " +
                "$maxPublicInternalMembers):\n${members.joinToString("\n") { it.trim() }}\n" +
                "New behaviour belongs in an extracted module (like DownloadsResyncActions) " +
                "wired through constructor lambdas. Lower the ceiling when members are " +
                "deleted; never raise it.",
        )
    }

    @Test
    fun downloadsViewModel_totalLineCeiling() {
        // The size guard companion to the member-count ceiling (the
        // ControllerOwnershipTest convention): the VM shrinks only by moving
        // clusters into extracted modules (constructor-lambda holders), so
        // its TOTAL line count is a one-way guard too. Baseline: 726 — the
        // then-current 725 lines plus one line of slack, pinned EXACTLY like
        // every other guard in this suite. Lowered to 606 (2026-10-28) when
        // the resync/freshness family (the four flows, the six verbs, the
        // candidate query and the private eligibility policy) moved into
        // DownloadsResyncActions. Lower the ceiling when a slice moves out;
        // never raise it to admit growth.
        val maxDownloadsViewModelLines = 606
        val lines = viewModelSource().readText(Charsets.UTF_8).lineSequence().count()
        assertTrue(
            lines <= maxDownloadsViewModelLines,
            "DownloadsViewModel.kt grew to $lines lines (ceiling " +
                "$maxDownloadsViewModelLines) — new behaviour belongs in an " +
                "extracted module built from constructor lambdas, with the VM a " +
                "thin caller. Lower the ceiling when a slice moves out; never " +
                "raise it.",
        )
    }
}
