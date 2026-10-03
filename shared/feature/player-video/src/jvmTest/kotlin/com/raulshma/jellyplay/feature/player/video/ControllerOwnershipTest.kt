package com.raulshma.jellyplay.feature.player.video

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import java.io.File

/**
 * Ratchet against reintroducing the god-state wiring pattern.
 *
 * 1. The eighteen migrated controllers (`SleepTimerController`,
 *    `TrackSelectionHelper`, `SubtitleManager`, `VideoEffectsController`,
 *    `AbRepeatController`, `SyncPlayBridge`, `PlaybackSession`,
 *    `EpisodeNavigator`, `SubtitlePreviewController`,
 *    `SubtitleStyleController`, `MediaContentProjector`, `RenderControls`,
 *    `EpisodeContinuationController`, `PipTransportController`,
 *    `EngineAttachController`,
 *    `StillWatchingController`, `MediaDetailProjection`, `EngineConfigSync`)
 *    must not reference [VideoPlayerUiState] at all — their interface is
 *    their state class plus commands, never the state bag or a state
 *    transformer. (SubtitleFontController and BackgroundCastController left
 *    the list with their fold-backs: the font funs into
 *    SubtitleStyleController, the background-cast pair into the VM as two
 *    private funs. VideoSessionHost left the list with its deletion: the
 *    pass-through layer was dissolved by the [PlayerWiring] two-phase
 *    composition — the builder implements the session-facing seams itself
 *    and, being the VM's construction surface, is the one module besides the
 *    VM that legitimately names the ui state bag.)
 *    DECLARED EXCEPTION (the ratchet's ONE sanctioned state transformer):
 *    the prefs projection [SessionLoadOutputs.onPrefsProjected] forwards is
 *    spelled through the transparent `PrefsProjection` alias declared beside
 *    the outputs interface it exists to satisfy. The projection is the
 *    pipeline's own load-stage vocabulary (the seed the VM applies at the
 *    pipeline's behest, not state the implementer reads or owns); narrowing
 *    it to per-field setters would change the outputs interface every load
 *    fake implements. Exactly this one member — a second aliased transformer
 *    in a migrated file is a violation, not a precedent.
 * 2. The count of god-state wirings (`getUiState =` / `updateUiState =` /
 *    `uiState = _uiState`) in the module's src/main must never increase.
 *    Baseline: [SettingsProjector] (a deferred, prefs-mirror
 *    writer — 2 wirings) and [PlaybackProgressReporter] (raw handle, 1 wiring).
 * 3. [VideoPlayerViewModel.kt]'s total line count must never exceed the
 *    post-extraction ceiling (see [videoPlayerViewModel_totalLineCeiling]).
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
        "SubtitleStyleController.kt",
        "MediaContentProjector.kt",
        "RenderControls.kt",
        "EpisodeContinuationController.kt",
        "PipTransportController.kt",
        "EngineAttachController.kt",
        "StillWatchingController.kt",
        "MediaDetailProjection.kt",
        "EngineConfigSync.kt",
    )

    /** The maximum allowed god-state wirings in src/main (see class KDoc). */
    private val maxGodStateWirings = 3

    private fun mainSources(): List<File> {
        // KMP move: the module's main sources now live under
        // src/commonMain/kotlin + src/androidMain/kotlin (the ViewModel and
        // the session stack are commonMain; androidMain keeps only the
        // engine adapters + platform seams), not src/main/java.
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
    fun videoPlayerViewModel_totalLineCeiling() {
        // The size ratchet companion to the member-count ceiling in
        // VideoPlayerViewModelOwnershipTest: the VM shrinks only by moving
        // clusters into extracted modules (constructor-lambda controllers),
        // so its TOTAL line count is a one-way ratchet too. Baseline: 1_752
        // — the current 1_751 lines by this suite's lineSequence count plus
        // one line of slack, pinned EXACTLY like every other ratchet in
        // this suite. Lower the ceiling when a slice moves out; never raise
        // it to admit growth.
        val maxVideoPlayerViewModelLines = 1_752
        val vm = mainSources().first { it.name == "VideoPlayerViewModel.kt" }
        val lines = vm.sourceText().lineSequence().count()
        assertTrue(
            lines <= maxVideoPlayerViewModelLines,
            "VideoPlayerViewModel.kt grew to $lines lines (ceiling " +
                "$maxVideoPlayerViewModelLines) — new behaviour belongs in an " +
                "extracted module built from constructor lambdas, with the VM a " +
                "thin caller. Lower the ceiling when a slice moves out; never " +
                "raise it.",
        )
    }

    @Test
    fun constructionOrderConvention_wiringPhaseOneBeforeArm() {
        // The engine-flow collector launched from the VM's init used to call
        // into trackSelectionHelper, and the SessionEvent forwarder collected
        // playbackSession.events — so those properties had to be declared
        // before the VM's init block. Since the [PlayerWiring] move, the VM
        // declares ONE wiring property and init only calls [PlayerWiring.arm];
        // the same invariant is now enforced structurally by the builder and
        // pinned HERE at the builder's source: every collaborator the arm
        // phase's collectors drive is constructed in phase 1 (before the arm
        // function exists in the file), so no collector registration can run
        // against an uninitialized collaborator.
        val wiring = mainSources().first { it.name == "PlayerWiring.kt" }.sourceText()
        val armFun = wiring.indexOf("    fun arm() {")
        assertTrue(armFun >= 0, "PlayerWiring.arm not found")
        for ((collaborator, what) in listOf(
            "internal val playerSessionManager = PlayerSessionManager(" to
                "the engine-attach + preference collectors' session handle",
            "internal val playbackSession = PlaybackSession(" to
                "the session-event forwarder's events flow + the rearm callback",
            "internal val trackSelectionHelper = TrackSelectionHelper(" to
                "the engine-attach + resolver collectors' track helper",
            "private val playbackPreferenceResolver = ItemPlaybackPreferenceResolver(" to
                "the arm-phase preference collector's resolver",
            "private val engineAttachController = EngineAttachController(" to
                "the arm-phase engineFlow collector's choreography",
            "private val prefsFanout = PlayerPrefsFanout(" to
                "the aggregate collector's fan-out",
        )) {
            val decl = wiring.indexOf(collaborator)
            assertTrue(decl >= 0, "collaborator declaration not found: $collaborator")
            assertTrue(
                decl < armFun,
                "$what must be constructed in phase 1 (before fun arm) — the arm-phase " +
                    "collectors register against it and Kotlin initialises properties in " +
                    "declaration order",
            )
        }
    }
}
