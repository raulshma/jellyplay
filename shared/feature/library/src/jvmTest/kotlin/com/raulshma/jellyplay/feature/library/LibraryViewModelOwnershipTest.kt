package com.raulshma.jellyplay.feature.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ratchet against the ViewModel regrowing into a god object (the reader
 * module's BookReaderViewModelOwnershipTest pattern): the count of
 * LibraryViewModel's public + internal members (properties and functions,
 * primary-constructor parameters and private members excluded) must never
 * increase past the current ceiling. New behaviour belongs in an extracted
 * module built from constructor lambdas — the QuickDownloadActions / DeferredUserDataRefresher / PhotoFolderChildUrlsStore seam shape — with the VM left a thin
 * caller that owns the uiState writes — or, for user intents, in the
 * `LibraryUiEvent` fold routed through the single `onEvent` funnel (the
 * HomeViewModel precedent) instead of new per-action command members.
 *
 * Baseline: 18 members (was 37 before the LibraryUiEvent intent fold deleted
 * the per-command publics in favour of `onEvent`). Lower the ceiling when a
 * slice moves out and deletes members; never raise it to admit new ones.
 */
class LibraryViewModelOwnershipTest {

    /** The maximum allowed public + internal members (see class KDoc). */
    private val maxPublicInternalMembers = 18

    /**
     * A class-body declaration line at the ViewModel's single level of
     * member indentation: optional visibility/modifier keywords, then
     * val/var/fun. Primary-constructor parameters share the indentation but
     * always carry a trailing comma; nested declarations (companion,
     * function bodies) sit one level deeper — both are excluded.
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
        return File(dir!!, "src/commonMain/kotlin/com/raulshma/jellyplay/feature/library/LibraryViewModel.kt")
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
    fun `libraryViewModel public and internal member count never increases`() {
        val file = viewModelSource()
        assertTrue(file.isFile, "LibraryViewModel.kt not found at ${file.path}")
        val members = file.readText(Charsets.UTF_8).stripComments().lineSequence()
            .filter { line -> memberDeclaration.containsMatchIn(line) }
            .filter { line -> !line.trimEnd().endsWith(",") }
            .filter { line -> !line.contains(Regex("\\bprivate\\b")) }
            .toList()
        assertTrue(
            members.size <= maxPublicInternalMembers,
            "LibraryViewModel grew to ${members.size} public+internal members (ceiling " +
                "$maxPublicInternalMembers):\n${members.joinToString("\n") { it.trim() }}\n" +
                "New behaviour belongs in an extracted module (the QuickDownloadActions / DeferredUserDataRefresher shape) wired through " +
                "constructor lambdas, with the VM a thin caller that owns the uiState writes. " +
                "Lower the ceiling when members are deleted; never raise it.",
        )
    }
}
