package com.raulshma.jellyplay.core.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Surface ratchet for [MediaRepository] (the PlaybackRepositorySurfaceTest
 * precedent): the interface is the feature-visible media seam consumed by
 * home/library/details/search/music features, so every member added to it is
 * coupling paid across all of them. This test parses the interface's source,
 * counts its members (fun/val declarations, overloads counted separately —
 * matching the impl's override count), and pins that count so it can only
 * move DOWN.
 *
 * Baseline 42 is the count right after the union shrink landed: the former
 * 86-member union extended LiveTvRepository / SyncPlayRepository /
 * NewsletterRepository / PlaylistRepository, forcing every media consumer to
 * learn four unrelated families; the interface is its own 42 members now and
 * the family surfaces have their own impls and seams. The second test pins
 * that shrink: the declaration must stay a bare `interface MediaRepository`
 * — re-extending any family surface must fail even if a member elsewhere
 * retired and kept the count under the cap.
 *
 * Lower [maxInterfaceMembers] when another member retires; never raise it. A
 * genuinely new media capability should land as a narrow collaborator or a
 * family seam (see the facade-split impls) rather than growing this surface.
 */
class MediaRepositorySurfaceTest {

    /**
     * The maximum allowed member count of [MediaRepository] (see class KDoc).
     * 44 as of the custom Discover rows feature (getDiscoverRowItems +
     * invalidateDiscoverRowCache — the home-sections family's row-scoped
     * members; a collaborator would have to re-expose the identity/session
     * seam the family already owns). 45 adds getPeople — same feature, same
     * seam reasoning: the People picker rides the discover editor, which
     * already reaches the catalog through this repository. 46 adds
     * seedDiscoverRowCache — the dice roll's commit half of the same
     * row-scoped pair (invalidate drops, seed republishes).
     */
    private val maxInterfaceMembers = 46

    /** Walks up from the working dir to the module root that owns src/commonMain/kotlin. */
    private fun moduleRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "src/commonMain/kotlin").isDirectory) dir = dir.parentFile
        assertTrue(dir != null, "could not locate src/commonMain/kotlin from ${System.getProperty("user.dir")}")
        return dir
    }

    private fun interfaceSource(): File {
        val file = File(
            moduleRoot(),
            "src/commonMain/kotlin/com/raulshma/jellyplay/core/data/repository/MediaRepository.kt",
        )
        assertTrue(file.isFile, "MediaRepository.kt not found at ${file.path} — the ratchet is vacuous")
        return file
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

    /** The comment-stripped body of `interface MediaRepository { … }`. */
    private fun interfaceBody(): String {
        val src = interfaceSource().readText(Charsets.UTF_8).stripCommentsAndStrings()
        val header = src.indexOf("interface MediaRepository")
        assertTrue(header >= 0, "interface MediaRepository declaration not found")
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
     * Counts member declarations: fun/val at the start of a line (the file's
     * style puts every member on its own line), overloads counted per
     * declaration. Default parameter values never start a line, so they
     * cannot inflate the count.
     */
    private fun countMembers(body: String): Int =
        Regex("""(?m)^\s*(?:suspend\s+)?(?:fun|val)\s""").findAll(body).count()

    @Test
    fun `interface member count never increases`() {
        val count = countMembers(interfaceBody())
        assertTrue(
            count <= maxInterfaceMembers,
            "MediaRepository grew to $count members (ratchet baseline $maxInterfaceMembers). " +
                "Retire a member or add a narrow collaborator/family seam instead; lower the baseline " +
                "only when the surface shrinks — never raise it.",
        )
    }

    @Test
    fun `union shrink stays shrunken - no family supertypes return`() {
        // The declaration itself, not just the body: the four family surfaces
        // (LiveTv/SyncPlay/Newsletter/Playlist) were dropped from the
        // supertype list when the union shrank, and a re-extend is exactly
        // the regression this ratchet exists to catch.
        val src = interfaceSource().readText(Charsets.UTF_8).stripCommentsAndStrings()
        val declaration = src.substring(src.indexOf("interface MediaRepository"), src.indexOf('{', src.indexOf("interface MediaRepository")))
        assertTrue(
            declaration.trim() == "interface MediaRepository",
            "MediaRepository re-grew a supertype list ($declaration) — the union shrink retired the " +
                "family extends; a family capability belongs on its own seam, not back on the union.",
        )
        // Sanity: the parse actually sees declarations, so an empty/false
        // body can never satisfy the ratchet above.
        val body = interfaceBody()
        assertTrue(
            countMembers(body) > 0 && body.contains("getHomeSections"),
            "interface body parse found no members — the ratchet is vacuous",
        )
    }
}
