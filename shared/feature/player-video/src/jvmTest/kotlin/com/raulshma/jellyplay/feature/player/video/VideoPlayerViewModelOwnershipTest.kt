package com.raulshma.jellyplay.feature.player.video

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ratchet against the ViewModel regrowing into a god object (the
 * player-audio module's AudioPlayerViewModelOwnershipTest pattern): the count
 * of VideoPlayerViewModel's public + internal members (properties and
 * functions, primary-constructor parameters and private members excluded)
 * must never increase past the current ceiling. The VM is a flows +
 * `onEvent` facade (the AudioPlayerUiEvent intent fold): commands arrive as
 * [VideoPlayerUiEvent]s through the single funnel, the former command funs
 * are private handlers, and the only public members beyond the funnel are
 * the state flows/getters, the result-returning queries
 * (`getImageUrl` / `loadTrickplayThumbnail` / `verifyPlayerLockPin` /
 * `useDownloadedSubtitle`), the `release` dispose hook, and the internal
 * controller slices the screen drives directly
 * (cast/syncPlay/subtitles/sleepTimer/abRepeat/effects/render/sessionRender/
 * globalMpvConfig + the session's `resolveOfflineResumeTicks` hook). New
 * behaviour belongs in an extracted module built from constructor lambdas —
 * the SettingsProjector / PlayerPrefsFanout / MediaContentProjector seam
 * shape — with the VM left a thin caller that owns the uiState writes.
 *
 * Baseline: 35 members (104 before the VideoPlayerUiEvent intent fold: the
 * same flows/getters/slices plus ~60 per-action command funs the funnel
 * replaced and 13 dead ones deleted outright — `loadActiveSubtitleCues` /
 * `setPreviewSheetVisible` / `clearActiveSubtitleCues` / `subtitlePreviewState`
 * (the cue-preview sheet has no remaining external driver),
 * `bufferedPositionMs` (no reader — the multi-band `bufferedRanges` feed the
 * same leaves), `setSecondarySubtitleTrack`, `skipCredits`,
 * `toggleEqualizer` / `setEqualizerSettings` / `setFrameRateMatching` /
 * `setRefreshRateMode` (the settings screens own those preference stores
 * directly) and `setInterpolationTscale` / `setCustomShaderFiles` (the
 * Rendering sheet only exposes the pickers with callers)). Lower the ceiling
 * when a slice moves out and deletes members; never raise it to admit new
 * ones.
 */
class VideoPlayerViewModelOwnershipTest {

    /** The maximum allowed public + internal members (see class KDoc). */
    private val maxPublicInternalMembers = 35

    /**
     * A class-body declaration line at the ViewModel's single level of
     * member indentation: optional visibility/modifier keywords, then
     * val/var/fun. Primary-constructor parameters share the indentation but
     * always carry a trailing comma; nested declarations (companion,
     * function bodies) sit one level deeper — both are excluded. The scan
     * starts at the class declaration (not the top of the file) so the
     * file-level private helper's locals are never miscounted as members.
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
        return File(dir!!, "src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/video/VideoPlayerViewModel.kt")
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
    fun `videoPlayerViewModel public and internal member count never increases`() {
        val file = viewModelSource()
        assertTrue(file.isFile, "VideoPlayerViewModel.kt not found at ${file.path}")
        val text = file.readText(Charsets.UTF_8).stripComments()
        val classBody = text.substringAfter("\nclass VideoPlayerViewModel(", text)
        val members = classBody.lineSequence()
            .filter { line -> memberDeclaration.containsMatchIn(line) }
            .filter { line -> !line.trimEnd().endsWith(",") }
            .filter { line -> !line.contains(Regex("\\bprivate\\b")) }
            .toList()
        assertTrue(
            members.size <= maxPublicInternalMembers,
            "VideoPlayerViewModel grew to ${members.size} public+internal members (ceiling " +
                "$maxPublicInternalMembers):\n${members.joinToString("\n") { it.trim() }}\n" +
                "New behaviour belongs in an extracted module (the SettingsProjector / PlayerPrefsFanout shape) wired " +
                "through constructor lambdas, with the VM a thin caller that owns the uiState writes. " +
                "New user intents belong on VideoPlayerUiEvent, routed once in onEvent. " +
                "Lower the ceiling when members are deleted; never raise it.",
        )
    }
}
