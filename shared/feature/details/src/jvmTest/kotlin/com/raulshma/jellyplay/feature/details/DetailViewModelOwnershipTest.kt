package com.raulshma.jellyplay.feature.details

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ratchet against the ViewModel regrowing into a god object (the
 * [ManageSeriesViewModelOwnershipTest] pattern): the count of
 * DetailViewModel's public + internal members (properties and functions,
 * primary-constructor parameters and private members excluded) must never
 * increase past the current ceiling. New user intents belong in
 * [DetailUiEvent] routed through the single onEvent funnel (the home
 * feature's `HomeViewModel` precedent); new behaviour belongs in an
 * extracted module built from constructor lambdas — the eight deep helper
 * seams ([DetailViewModel.downloads], playlists, watchLater, collections,
 * resync, offline, watchParty, seerrRequests) are exactly that carve-out,
 * and the read side (the state flows, the image-URL getters, the click-time
 * selected-*-index reads, the storage probe) stays public as queries.
 *
 * Baseline: 22 members after the DetailUiEvent intent fold. Before the fold
 * the VM exposed ~48 members: the same 22 read/helper/funnel surfaces minus
 * onEvent, plus 27 public command funs (loadItem, forceRefresh,
 * loadEpisodesForSeason, selectSubtitle, selectAudio, selectLocalSubtitle,
 * setEpisodesDescending, setCompactEpisodeList, playAlbum, playLocalTrack,
 * startInstantMix, toggleFavorite (both the no-arg and per-item variants),
 * markPlayed, markUnplayed, markEpisodePlayed, markRowItemPlayed,
 * markSeasonPlayed, markSeasonUnplayed, downloadRowItem,
 * removeRowItemDownload, hideFromNextUp, showFromNextUp,
 * hideFromContinueWatching, showFromContinueWatching, setLastViewedSeason,
 * setShowDetailUpNext) that went private behind the funnel. Three of them
 * (playLocalTrack, the per-item toggleFavorite, markEpisodePlayed) had no
 * production dispatcher and were deleted outright instead of gaining event
 * arms — playLocalTrack took the then-orphaned DetailAudioPlayback DI seam
 * with it. Lower the ceiling when a slice moves out and deletes members;
 * never raise it to admit new ones.
 */
class DetailViewModelOwnershipTest {

    /** The maximum allowed public + internal members (see class KDoc). */
    private val maxPublicInternalMembers = 22

    /**
     * A class-body declaration line at the ViewModel's single level of
     * member indentation: optional visibility/modifier keywords, then
     * val/var/fun. Primary-constructor parameters share the indentation but
     * always carry a trailing comma; nested declarations (companion,
     * function bodies, the file-tail private data classes' headers) sit one
     * level deeper — both are excluded.
     */
    private val memberDeclaration = Regex(
        "^ {4}(?:(?:public|internal|protected|open|override|suspend|inline|actual|expect|operator|infix|lateinit|const|abstract)\\s+)*(?:val|var|fun)\\s",
    )

    private fun viewModelSource(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        // DetailViewModel lives in the jvmShared source set (its dependency
        // closure reaches the jvmShared halves of core:data), not commonMain
        // like ManageSeriesViewModel — the walk targets that directory.
        while (dir != null && !File(dir, "src/jvmShared/kotlin").isDirectory) dir = dir.parentFile
        assertTrue(
            dir != null,
            "could not locate src/jvmShared/kotlin from ${System.getProperty("user.dir")}",
        )
        return File(dir!!, "src/jvmShared/kotlin/com/raulshma/jellyplay/feature/details/DetailViewModel.kt")
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
    fun `detailViewModel public and internal member count never increases`() {
        val file = viewModelSource()
        assertTrue(file.isFile, "DetailViewModel.kt not found at ${file.path}")
        val members = file.readText(Charsets.UTF_8).stripComments().lineSequence()
            .filter { line -> memberDeclaration.containsMatchIn(line) }
            .filter { line -> !line.trimEnd().endsWith(",") }
            .filter { line -> !line.contains(Regex("\\bprivate\\b")) }
            .toList()
        assertTrue(
            members.size <= maxPublicInternalMembers,
            "DetailViewModel grew to ${members.size} public+internal members (ceiling " +
                "$maxPublicInternalMembers):\n${members.joinToString("\n") { it.trim() }}\n" +
                "New intents belong in DetailUiEvent routed through the onEvent funnel; new " +
                "behaviour belongs in an extracted module wired through constructor lambdas. " +
                "Lower the ceiling when members are deleted; never raise it.",
        )
    }
}
