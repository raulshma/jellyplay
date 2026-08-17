package com.raulshma.jellyplay.feature.player.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ratchet against reintroducing the god-state wiring pattern.
 *
 * 1. The six migrated controllers (`SleepTimerController`,
 *    `TrackSelectionHelper`, `SubtitleManager`, `VideoEffectsController`,
 *    `AbRepeatController`, `SyncPlayBridge`) must not reference
 *    [VideoPlayerUiState] at all — their interface is their state class plus
 *    commands, never the state bag or a state transformer.
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
    )

    /** The maximum allowed god-state wirings in src/main (see class KDoc). */
    private val maxGodStateWirings = 3

    private fun mainSources(): List<File> {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var sources: File? = null
        while (dir != null && sources == null) {
            val candidate = File(dir, "src/main/java")
            if (candidate.isDirectory) sources = candidate else dir = dir.parentFile
        }
        assertTrue(
            "could not locate src/main/java from ${System.getProperty("user.dir")}",
            sources != null
        )
        return sources!!.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
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
            assertTrue("missing source file for $controller", file != null)
            val text = file!!.sourceText().stripComments()
            assertFalse(
                "$controller must not reference VideoPlayerUiState (its interface is its state class + commands)",
                text.contains("VideoPlayerUiState")
            )
            assertFalse(
                "$controller must not take a god-state transformer",
                text.contains("updateUiState")
            )
            assertFalse(
                "$controller must not read the god state",
                text.contains("getUiState")
            )
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
            "expected exactly the deferred god-state wirings: SettingsProjector's " +
                "getUiState/updateUiState pair (in its VM wiring + constructor) and " +
                "PlaybackProgressReporter's raw handle. Found: $wirings",
            maxGodStateWirings,
            total,
        )
    }

    @Test
    fun constructionOrderConvention_controllerPropertiesAboveInit() {
        // The engine-flow collector launched from the VM's init calls into
        // trackSelectionHelper; Kotlin initialises properties and init blocks
        // in declaration order, so the helpers must be declared before init.
        val vm = mainSources().first { it.name == "VideoPlayerViewModel.kt" }.sourceText()
        val initBlock = vm.indexOf("\n    init {")
        val helperDecl = vm.indexOf("private val trackSelectionHelper = TrackSelectionHelper(")
        val resolverDecl = vm.indexOf("private val playbackPreferenceResolver = ItemPlaybackPreferenceResolver(")
        assertTrue("VideoPlayerViewModel init block not found", initBlock >= 0)
        assertTrue("trackSelectionHelper declaration not found", helperDecl >= 0)
        assertTrue("playbackPreferenceResolver declaration not found", resolverDecl >= 0)
        assertTrue(
            "trackSelectionHelper + playbackPreferenceResolver must be declared before the init block " +
                "(the engine-flow collector launched from init reaches into them)",
            helperDecl < initBlock && resolverDecl < initBlock,
        )
    }
}
