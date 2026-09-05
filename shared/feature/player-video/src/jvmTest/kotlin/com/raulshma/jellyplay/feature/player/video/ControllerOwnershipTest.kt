package com.raulshma.jellyplay.feature.player.video

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import java.io.File

/**
 * Ratchet against reintroducing the god-state wiring pattern.
 *
 * 1. The nine migrated controllers (`SleepTimerController`,
 *    `TrackSelectionHelper`, `SubtitleManager`, `VideoEffectsController`,
 *    `AbRepeatController`, `SyncPlayBridge`, `PlaybackSession`,
 *    `EpisodeNavigator`, `SubtitlePreviewController`) must not
 *    reference [VideoPlayerUiState] at all — their interface is their state
 *    class plus commands, never the state bag or a state transformer.
 * 2. The count of god-state wirings (`getUiState =` / `updateUiState =` /
 *    `uiState = _uiState`) in the module's src/main must never increase.
 *    Baseline: [SettingsProjector] (a deferred, prefs-mirror
 *    writer — 2 wirings) and [PlaybackProgressReporter] (raw handle, 1 wiring).
 *
 * Lower the baseline when another slice migrates; never raise it.
 */
class ControllerOwnershipTest {

    private val migratedControllers = listOf(
        "SleepTimerController.kt",
        "TrackSelectionHelper.kt",
        "SubtitleManager.kt",
        "VideoEffectsController.kt",
        "AbRepeatController.kt",
        "SyncPlayBridge.kt",
        "PlaybackSession.kt",
        "EpisodeNavigator.kt",
        "SubtitlePreviewController.kt",
    )

    /** The maximum allowed god-state wirings in src/main (see class KDoc). */
    private val maxGodStateWirings = 3

    private fun mainSources(): List<File> {
        // KMP move (wave 7C): the module's main sources now live under
        // src/commonMain/kotlin + src/androidMain/kotlin (the monolith
        // ViewModel + session stack are androidMain), not src/main/java.
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var moduleRoot: File? = null
        while (dir != null && moduleRoot == null) {
            if (File(dir, "src/commonMain/kotlin").isDirectory) moduleRoot = dir else dir = dir.parentFile
        }
        assertTrue(moduleRoot != null, "could not locate src/commonMain/kotlin from ${System.getProperty("user.dir")}")
        val roots = listOf(
            File(moduleRoot!!, "src/commonMain/kotlin"),
            File(moduleRoot, "src/androidMain/kotlin"),
        ).filter { it.isDirectory }
        return roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
    }

    private fun File.sourceText(): String = readText(Charsets.UTF_8)

    /**
     * Removes line and block comments so the ratchet checks *code*, not KDoc
     * prose (the controllers legitimately mention VideoPlayerUiState in
     * documentation explaining what they do NOT take).
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
    fun migratedControllers_doNotReferenceTheUiStateBag() {
        val sources = mainSources().associateBy { it.name }
        for (controller in migratedControllers) {
            val file = sources[controller]
            assertTrue(file != null, "missing source file for $controller")
            val text = file!!.sourceText().stripComments()
            assertFalse(
                text.contains("VideoPlayerUiState"),
                "$controller must not reference VideoPlayerUiState (its interface is its state class + commands)",
            )
            assertFalse(text.contains("updateUiState"), "$controller must not take a god-state transformer")
            assertFalse(text.contains("getUiState"), "$controller must not read the god state")
        }
    }

    @Test
    fun godStateWiringCount_neverIncreases() {
        val sources = mainSources()
        val patterns = listOf("getUiState =", "updateUiState =", "uiState = _uiState")
        val wirings = buildMap {
            for (file in sources) {
                val text = file.sourceText()
                for (pattern in patterns) {
                    val count = Regex(Regex.escape(pattern)).findAll(text).count()
                    if (count > 0) put("${file.name}:$pattern", count)
                }
            }
        }
        val total = wirings.values.sum()
        assertEquals(
            maxGodStateWirings,
            total,
            "expected exactly the deferred god-state wirings: SettingsProjector's " +
                "getUiState/updateUiState pair (in its VM wiring + constructor) and " +
                "PlaybackProgressReporter's raw handle. Found: $wirings",
        )
    }

    @Test
    fun constructionOrderConvention_controllerPropertiesAboveInit() {
        // The engine-flow collector launched from the VM's init calls into
        // trackSelectionHelper; Kotlin initialises properties and init blocks
        // in declaration order, so the helpers must be declared before init.
        // The same holds for playbackSession: init's SessionEvent forwarder
        // collects playbackSession.events.
        val vm = mainSources().first { it.name == "VideoPlayerViewModel.kt" }.sourceText()
        val initBlock = vm.indexOf("\n    init {")
        val helperDecl = vm.indexOf("private val trackSelectionHelper = TrackSelectionHelper(")
        val resolverDecl = vm.indexOf("private val playbackPreferenceResolver = ItemPlaybackPreferenceResolver(")
        val sessionDecl = vm.indexOf("private val playbackSession = PlaybackSession(")
        assertTrue(initBlock >= 0, "VideoPlayerViewModel init block not found")
        assertTrue(helperDecl >= 0, "trackSelectionHelper declaration not found")
        assertTrue(resolverDecl >= 0, "playbackPreferenceResolver declaration not found")
        assertTrue(sessionDecl >= 0, "playbackSession declaration not found")
        assertTrue(
            helperDecl < initBlock && resolverDecl < initBlock,
            "trackSelectionHelper + playbackPreferenceResolver must be declared before the init block " +
                "(the engine-flow collector launched from init reaches into them)",
        )
        assertTrue(sessionDecl < initBlock, "playbackSession must be declared before the init block " +
                "(init's SessionEvent forwarder collects playbackSession.events)")
    }
}
