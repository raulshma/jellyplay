package com.raulshma.jellyplay.feature.player.video

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import java.io.File

/**
 * Guard against reintroducing the god-state wiring pattern.
 *
 * 1. The twenty migrated controllers (`SleepTimerController`,
 *    `TrackSelectionHelper`, `SubtitleManager`, `VideoEffectsController`,
 *    `AbRepeatController`, `SyncPlayBridge`, `PlaybackSession`,
 *    `EpisodeNavigator`, `SubtitlePreviewController`,
 *    `SubtitleStyleController`, `MediaContentProjector`, `RenderControls`,
 *    `UpNextController`, `PipTransportController`,
 *    `EngineAttachController`,
 *    `StillWatchingController`, `MediaDetailProjection`, `EngineConfigSync`,
 *    `InputBindingToggleController`, `PlayerActionExecutor`)
 *    must not reference [VideoPlayerUiState] at all — their interface is
 *    their state class plus commands, never the state bag or a state
 *    transformer. (SubtitleFontController and BackgroundCastController left
 *    the list with their fold-backs: the font funs into
 *    SubtitleStyleController, the background-cast pair into the VM as two
 *    private funs. VideoSessionHost left the list with its deletion; the
 *    `PlayerWiring` builder inherited its composition-surface exemption and
 *    handed it to [PlaybackSession] — the composition
 *    root is the one module besides the VM that legitimately carries the
 *    sanctioned god-state wirings (the SettingsProjector pair; see
 *    godStateWiringCount_neverIncreases).)
 *    DECLARED EXCEPTION (the guard's ONE sanctioned state transformer):
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
        "UpNextController.kt",
        "PipTransportController.kt",
        "EngineAttachController.kt",
        "StillWatchingController.kt",
        "MediaDetailProjection.kt",
        "EngineConfigSync.kt",
        "InputBindingToggleController.kt",
        "PlayerActionExecutor.kt",
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
     * Removes line and block comments so the guard checks *code*, not KDoc
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
            // The composition root's sanctioned exception: the session carries
            // the SettingsProjector god-state wiring PAIR since the 
            // collapse (the former PlayerWiring builder's — the count is
            // pinned unchanged by godStateWiringCount_neverIncreases). A
            // controller taking a transformer is still a violation.
            if (controller == "PlaybackSession.kt") continue
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
        // The size guard companion to the member-count ceiling in
        // VideoPlayerViewModelOwnershipTest: the VM shrinks only by moving
        // clusters into extracted modules (constructor-lambda controllers),
        // so its TOTAL line count is a one-way guard too. Baseline: 1_752
        // — the then-current 1_751 lines plus one line of slack, pinned
        // EXACTLY like every other guard in this suite. Lowered to 1_637
        // when the 2026-10-14 session-slice migration deleted the eight
        // controller pass-throughs (their reads moved to the
        // session/SessionSlices.kt projections). Lowered to 1_133
        // (2026-10-28) when the five session construction bundles replaced
        // the ~19 hand-listed pass-through ctor params and the end-of-media
        // fold + segment-skip dispatch + speed/cadence engine commands moved
        // onto PlaybackSession (the VM's constructor dropped 41 → 22 params,
        // its playerSessionManager reach-throughs to zero). Lower the
        // ceiling when a slice moves out; never raise it to admit growth.
        val maxVideoPlayerViewModelLines = 1_110
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
    fun playbackSession_totalLineCeiling() {
        // The composition-root companion to videoPlayerViewModel_totalLineCeiling:
        // the former PlayerWiring builder folded INTO the
        // session, making it the player's largest surface, so its TOTAL line
        // count is a one-way guard too. Baseline: 3_022 — the current
        // line count by this suite's lineSequence count plus one line of
        // slack, pinned EXACTLY like every other guard in this suite.
        // Lowered from 3_054 when the pure resume resolver
        // (resolveResumeTicks + its staleness constant) moved out to
        // ResumeTicks.kt. Raised to 3_034 (2026-10-26) for the issue-#177
        // artwork threading: the session-scoped DefaultArtworkUrls + the
        // item-based media-session artwork lambda (tag-aware artwork URLs).
        // Raised to 3_132 (2026-10-28) for the end-of-media fold +
        // segment-skip dispatch moving in from the ViewModel (with the #14
        // PostPlaybackActionsController construction and the Skipped-segment
        // notice) plus the engine-commands funnel, the playbackFacts()
        // snapshot and the engine/warm aliases. Lower the ceiling when a
        // cluster moves out; never raise it to admit growth.
        val maxPlaybackSessionLines = 3_132
        val session = mainSources().first { it.name == "PlaybackSession.kt" }
        val lines = session.sourceText().lineSequence().count()
        assertTrue(
            lines <= maxPlaybackSessionLines,
            "PlaybackSession.kt grew to $lines lines (ceiling " +
                "$maxPlaybackSessionLines) — new behaviour belongs in an " +
                "extracted collaborator, with the session a thin composition " +
                "root. Lower the ceiling when a cluster moves out; never " +
                "raise it.",
        )
    }

    @Test
    fun constructionOrderConvention_wiringPhaseOneBeforeArm() {
        // The engine-flow collector launched from the VM's init used to call
        // into trackSelectionHelper, and the SessionEvent forwarder collected
        // playbackSession.events — so those properties had to be declared
        // before the VM's init block. Since the `PlayerWiring` fold INTO
        // [PlaybackSession] itself, the
        // invariant is pinned at the composition root's source: every
        // collaborator the arm phase's collectors drive is constructed in the
        // class body BEFORE the arm function exists in the file, so no
        // collector registration can run against an uninitialized
        // collaborator (Kotlin initialises properties in declaration order).
        val wiring = mainSources().first { it.name == "PlaybackSession.kt" }.sourceText()
        val armFun = wiring.indexOf("    internal fun arm() {")
        assertTrue(armFun >= 0, "PlaybackSession.arm not found")
        for ((collaborator, what) in listOf(
            "internal val playerSessionManager: PlayerSessionManager = playerSessionManagerOverride" to
                "the engine-attach + preference collectors' session handle",
            "private val engineEventShell = EngineSessionShell<SessionEvent>(" to
                "the session-event forwarder's events flow + the rearm callback",
            "internal val trackSelectionHelper: TrackSelectionHelper = TrackSelectionHelper(" to
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
                "$what must be constructed before fun arm() — the arm-phase collectors register " +
                    "against it and Kotlin initialises properties in declaration order",
            )
        }
    }
}
