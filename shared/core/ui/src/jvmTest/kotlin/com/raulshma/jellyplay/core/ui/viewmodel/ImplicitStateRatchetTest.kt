package com.raulshma.jellyplay.core.ui.viewmodel

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The never-raise ratchet on the implicit-state vocabulary, in the
 * ControllerOwnershipTest idiom. The repo's sealed-state migration (the
 * ~25 implicit state machines folded into sealed phases/slots —
 * `DetailUiLoadState`, `HomeLoadPhase`, `AudioPlayerSheet`, `UpdateState`,
 * `QuickConnectUiState`, `LiveFallbackPhase`, …) left a RESIDUE:
 * declared-divergence booleans and the not-yet-migrated tail. This suite
 * freezes that residue repo-wide across the shared tree so it can only shrink.
 *
 * WHAT IS COUNTED — property declarations in CODE, not prose: comments are
 * stripped first (KDoc mentions of `isLoading` etc. are exempt, exactly as
 * ControllerOwnershipTest exempts its controllers' KDoc), and this suite's
 * stripper additionally blanks string/char literal CONTENTS so a code sample
 * riding in a raw-string KDoc cannot be counted. The scan is pure file
 * walking + regex over every shared module's `src/<source-set>/kotlin` tree
 * (main, platform and test sets alike; test files only ever matched through
 * the same name filters, never special-cased).
 *
 * 1. Spinner/progress flags on state carriers — `(val|var) (isLoading|
 *    isRefreshing|isSearching|isApplying|isSaving|isConnecting|isUploading)`
 *    in files named `*ViewModel.kt`, `*State.kt` (which subsumes
 *    `*UiState.kt`) and `*StateHolder*.kt`. Baseline [maxSpinnerFlagsOnStateCarriers]
 *    (2026-11-15): the not-yet-migrated boolean-soup state params
 *    (admin/newsletter/details/audio/livetv/music/requests/syncplay/calendar
 *    UiState + ViewModel data classes), the boolean-backing projections
 *    (`val isLoading: Boolean get() = _isLoading.value` and the
 *    `StateFlow<Boolean>`/`composeState` carriers), and — counted
 *    DELIBERATELY, not a false positive — HomeUiState.kt's two phase-verdict
 *    folds (`val isLoading/isRefreshing: Boolean get() = when (loadPhase)`):
 *    they are literal spinner-flag vocabulary on a state carrier even though
 *    derived over the sealed `HomeLoadPhase`. A new file of phase verdicts
 *    takes the dated-allowlist out below, like any other declared divergence.
 * 2. Parallel sheet/dialog/menu booleans — `var show…(Dialog|Sheet|Menu|
 *    Submenu)` composition flags in screens/components. Baseline
 *    [maxParallelSheetDialogMenuBooleans] (2026-11-15; 41 raw matches minus
 *    the two declared session/stack files allowlisted below). The regex is
 *    refined from the seed shape in two directions: the bare degenerate
 *    names (`showDialog`, `showMenu`) count too, and a trailing `\b` keeps
 *    mid-identifier rot (`showBottomSheetContent`) out. The sibling
 *    `show…Confirm` family is a DIFFERENT vocabulary and stays outside this
 *    ratchet.
 * 3. Parked one-shot message fields — the banned "nullable message field +
 *    clear-after-handling" shape, conventions §2, in state-carrier files.
 *    Widened (2026-11-17, the R4 finding): three alternations — (a)
 *    `(val|var) <x>Status|xMessage|xEvent|xFailure|xError` typed `String?`,
 *    (b) the same names delegated `by composeState`, (c) the helper-seeded
 *    nullable-slot shape (`= stateFlow<T?>(null)` / `composeState<T?>(null)`
 *    / `mutableStateOf<T?>(null)`) the old regex could not see. The
 *    2026-11-16 extinction pin (ceiling 0, floor 0) passed vacuously forever
 *    and the widened scan re-found 17 honest residue slots plus 8 immutable
 *    UiState data-class fields (declared divergences below). The
 *    [parkedMessageRegex_positiveAndNegativeControls] fixture is the
 *    shape-level rot guard the 0/0 pin lacked — a regex edit that stops
 *    recognizing any banned shape fails there outright. The sanctioned
 *    shapes (a Channel exposed as `receiveAsFlow()`, or a phase whose
 *    terminal face carries the message) still do not match — the ratchet
 *    bans reintroduction, not the migration targets.
 *
 * DECLARED DIVERGENCES ([declaredDivergences] — kept MINIMAL, populated only
 * where counting would be dishonest, each with file + reason + date):
 *  - DetailDialogSession.kt (2026-11-15): the media-detail screen's DECLARED
 *    dialog/sheet session — sheet-visibility two-state latches plus their
 *    open/dismiss cascades, pinned by DetailDialogSessionTest. It is the
 *    sanctioned HOME for detail-screen sheet flags (a plain class moved out
 *    of composition, not scattered `remember` flags); freezing its count
 *    would block the next sheet's sanctioned arrival there.
 *  - AudioPlayerSheetStack.kt (2026-11-15): the sealed `AudioPlayerSheet`
 *    slot's two DECLARED non-sheet latches (the persisted lyrics preference;
 *    the self-dismissing menu) — the stack REPLACED seven parallel booleans
 *    with one slot; its declared stragglers are the genuinely-two-state kind
 *    this allowlist exists for.
 *
 * THE OUTS when a count grows (each failure message says this):
 *  1. MIGRATE — fold the flags into one sealed phase/slot or route the
 *     one-shot through a Channel (StateManagementConventions.PREFERRED_PATTERN
 *     §1/§2/§3), then LOWER the ceiling: lower the baseline when a slice
 *     migrates; never raise it to admit growth.
 *  2. DECLARED DIVERGENCE — only if the new property is a genuinely
 *     two-state latch that must stay, extend [declaredDivergences] with a
 *     dated entry following ControllerOwnershipTest's raise-entry convention
 *     ("Raised to … (2026-11-15) for …").
 *
 * Scope: this suite scans OTHER modules' sources from core:ui's jvmTest
 * working directory, so the repo root is located by walking up to
 * settings.gradle.kts (the SharedModuleCiLaneGuardTest idiom —
 * ControllerOwnershipTest, by contrast, resolves its own module). No Gradle
 * invocations; deterministic on repeated runs.
 */
class ImplicitStateRatchetTest {

    // ── Baselines: the pinned residue. Lower when a slice migrates; never raise. ──

    /** Spinner/progress flags on state carriers (see class KDoc §1). */
    private val maxSpinnerFlagsOnStateCarriers = 62

    /** Parallel sheet/dialog/menu composition booleans (see class KDoc §2). */
    private val maxParallelSheetDialogMenuBooleans = 35

    /**
     * Parked one-shot message fields in state-carrier files (see class KDoc §3).
     *
     * History: lowered to 0 (2026-11-16) when the last parked field
     * (`ServerManagementViewModel.addressOperationMessage`) migrated into
     * `AddressOperationPhase` — then RAISED to 17 (2026-11-17), the R4 fix
     * from the regression analysis: the 0/0 ceiling+floor passed VACUOUSLY
     * forever (a rotted regex had nothing to re-find), and the vocabulary had
     * blind spots — `-Failure`/`-Error` names and the helper-seeded nullable
     * slot shape (`= stateFlow<LoginError?>(null)` / `composeState<UiMessage?>(
     * null)`) were invisible to the old regex, so genuine residue (the
     * analysis's `_loginError` / `_switchServerFailure` exemplars, plus the
     * music/settings tail) sat uncounted. The widened regex surfaced 25 raw
     * hits: 17 honest parked-slot residue (counted here — the ceiling is
     * exact-pinned by the rot floor below), and 8 immutable UiState
     * data-class fields (`val grabError: String? = null` constructor params
     * — rendered state, not parked-and-cleared slots) whose counting would
     * be dishonest; those files sit in [declaredDivergences] instead. The
     * rot-controls fixture (parkedMessageRegex_positiveAndNegativeControls)
     * guards the regex's shape coverage itself.
     */
    private val maxParkedOneShotMessageFields = 17

    // ── Regex-rot floors: if a scan or a regex stops matching the known residue
    // the ceilings above would pass vacuously (SharedModuleCiLaneGuardTest's
    // "must not pass vacuously" law). Lower these TOGETHER with the ceilings
    // when slices migrate. ──
    private val spinnerRegexRotFloor = 50
    private val compositionRegexRotFloor = 30

    /**
     * Raised to 17 (2026-11-17) together with [maxParkedOneShotMessageFields]
     * when the vocabulary's blind spots closed: the widened regex re-finds
     * real production residue, so this floor is live again — the 0 floor that
     * followed the 2026-11-16 migration passed vacuously forever (the R4
     * finding). Equals the ceiling: the residue is exact-pinned, and any
     * slice migration must lower BOTH (the lower-when-migrated convention).
     */
    private val parkedMessageRegexRotFloor = 17

    /** The scan itself must not pass vacuously: shared/ holds at least this many kt files. */
    private val minScannedKotlinFiles = 3_500

    // ── The vocabulary (final regexes; derivation recorded in the class KDoc). ──

    private val spinnerFlagRegex =
        Regex("""(?:val|var)\s+(?:isLoading|isRefreshing|isSearching|isApplying|isSaving|isConnecting|isUploading)\b""")

    private val compositionFlagRegex =
        Regex("""var\s+show(?:[A-Z]\w*)?(?:Dialog|Sheet|Menu|Submenu)\b""")

    private val parkedMessageRegex =
        Regex(
            // A1/A2 — the original shape, widened (2026-11-17) with the
            // -Failure/-Error name suffixes the analysis's evading exemplars
            // (`_switchServerFailure`, `_loginError`) rode.
            """(?:val|var)\s+\w+(?:Status|Message|Event|Failure|Error)\s*:\s*String\??(?![A-Za-z])""" +
                """|(?:val|var)\s+\w+(?:Status|Message|Event|Failure|Error)\s+by\s+composeState""" +
                // A3 — the helper-seeded nullable-slot shape: a one-shot parked
                // as a nullable-typed, null-seeded stateFlow/composeState/
                // mutableStateOf slot (the `stateFlow<LoginError?>(null)` /
                // `composeState<UiMessage?>(null)` residue class). Non-nullable
                // seeds (`stateFlow<LoginPhase>(Idle)`) and suffix-less names
                // stay outside — the rot-controls fixture pins both edges.
                """|(?:val|var)\s+\w+(?:Status|Message|Event|Failure|Error)\s*=\s*""" +
                """(?:stateFlow|composeState|mutableStateOf)\s*<[^<>]*\?>\s*\(\s*null\s*\)""",
        )

    private val sourceSetRegex = Regex("""/src/[^/]+/kotlin/""")

    // ── Declared divergences: known, documented two-state latches the literal
    // vocabulary would otherwise count. Exempted PER PATTERN (an entry for one
    // ratchet never exempts another). ──

    private val patternSpinnerFlags = "spinnerFlags"
    private val patternCompositionFlags = "compositionFlags"
    private val patternParkedMessages = "parkedMessages"

    private data class DeclaredDivergence(
        /** Repo-relative path suffix of the exempted file. */
        val pathSuffix: String,
        /** Which ratchet's count this entry exempts. */
        val pattern: String,
        /** Why this file's hits are a DECLARED divergence, not residue. */
        val reason: String,
        /** The raise-entry date (ControllerOwnershipTest convention). */
        val date: String,
    )

    private val declaredDivergences = listOf(
        DeclaredDivergence(
            pathSuffix =
                "feature/details/src/jvmShared/kotlin/com/raulshma/jellyplay/feature/details/DetailDialogSession.kt",
            pattern = patternCompositionFlags,
            reason = "the media-detail screen's DECLARED dialog/sheet session: sheet-visibility " +
                "two-state latches plus their open/dismiss cascades, pinned by " +
                "DetailDialogSessionTest — the sanctioned HOME for detail-screen sheet " +
                "flags (moved out of composition, not scattered remember flags); freezing " +
                "its count would block the next sheet's sanctioned arrival there",
            date = "2026-11-15",
        ),
        DeclaredDivergence(
            pathSuffix =
                "feature/player-audio/src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/audio/AudioPlayerSheetStack.kt",
            pattern = patternCompositionFlags,
            reason = "the sealed AudioPlayerSheet slot's two DECLARED non-sheet latches (the " +
                "persisted lyrics preference; the self-dismissing menu) — the stack " +
                "REPLACED seven parallel booleans with one `current` slot; its declared " +
                "stragglers are the genuinely-two-state kind",
            date = "2026-11-15",
        ),
        // ── Parked-message exemptions (2026-11-17, the R4 widening): immutable
        // UiState data-class fields (`val xError: String? = null` constructor
        // params) the widened vocabulary matches but that are RENDERED STATE
        // replaced wholesale per state emission — not parked-and-cleared
        // one-shot slots. Counting them would be dishonest; each file still
        // matches the vocabulary (the hygiene check enforces it), and a TRUE
        // parked slot added to these files must migrate or raise honestly.

        DeclaredDivergence(
            pathSuffix =
                "feature/admin/src/jvmShared/kotlin/com/raulshma/jellyplay/feature/admin/users/detail/UserDetailViewModel.kt",
            pattern = patternParkedMessages,
            reason = "the UserEditUiState data class's `auxError: String? = null` constructor " +
                "field — rendered state on an immutable slice, not a parked one-shot",
            date = "2026-11-17",
        ),
        DeclaredDivergence(
            pathSuffix =
                "feature/arrqueue/src/commonMain/kotlin/com/raulshma/jellyplay/feature/arrqueue/ReleaseSearchState.kt",
            pattern = patternParkedMessages,
            reason = "the ReleaseSearchState data class's `grabError: String? = null` constructor " +
                "field — rendered state on an immutable slice, not a parked one-shot",
            date = "2026-11-17",
        ),
        DeclaredDivergence(
            pathSuffix =
                "feature/editor/src/commonMain/kotlin/com/raulshma/jellyplay/feature/editor/EditorViewModel.kt",
            pattern = patternParkedMessages,
            reason = "the editor UiState data class's `actionError: String? = null` constructor " +
                "field — the banner carrier rendered per state emission, not a parked one-shot",
            date = "2026-11-17",
        ),
        DeclaredDivergence(
            pathSuffix =
                "feature/player-audio/src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/audio/AudioPlayerUiState.kt",
            pattern = patternParkedMessages,
            reason = "the two audio UiState slices' `playbackError: String? = null` constructor " +
                "fields — rendered state on immutable slices, not parked one-shots",
            date = "2026-11-17",
        ),
        DeclaredDivergence(
            pathSuffix =
                "feature/player-video/src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/video/state/SubtitleState.kt",
            pattern = patternParkedMessages,
            reason = "the SubtitleSettingsState data class's `remoteSubtitlesError: String? = null` " +
                "constructor field — rendered state on an immutable slice, not a parked one-shot",
            date = "2026-11-17",
        ),
        DeclaredDivergence(
            pathSuffix =
                "feature/requests/src/commonMain/kotlin/com/raulshma/jellyplay/feature/requests/RequestsViewModel.kt",
            pattern = patternParkedMessages,
            reason = "the requests UiState data class's `actionError: String? = null` constructor " +
                "field — rendered state on an immutable slice, not a parked one-shot",
            date = "2026-11-17",
        ),
        DeclaredDivergence(
            pathSuffix =
                "feature/settings/src/commonMain/kotlin/com/raulshma/jellyplay/feature/settings/DiscoverRowsViewModel.kt",
            pattern = patternParkedMessages,
            reason = "the DiscoverRowDraft data class's `previewError: String? = null` constructor " +
                "field — rendered state on an immutable slice, not a parked one-shot",
            date = "2026-11-17",
        ),
    )

    // ── Scan plumbing (one pass over shared/ feeds every ratchet). ──

    private class Scan(
        val spinnerFlags: Map<String, Int>,
        val compositionFlags: Map<String, Int>,
        val parkedMessages: Map<String, Int>,
        val scannedFiles: Int,
    )

    private val scan: Scan by lazy { runScan() }

    private fun sharedRoot(): File {
        val shared = File(repoRoot(), "shared")
        assertTrue(shared.isDirectory, "missing shared/ under ${repoRoot().path} — repo layout changed?")
        return shared
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        fail("could not locate repo root (no settings.gradle.kts walking up from ${System.getProperty("user.dir")})")
    }

    /** Every .kt under a shared `src/<source-set>/kotlin` tree — the scan scope. */
    private fun sharedKotlinSources(): List<File> =
        sharedRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { sourceSetRegex.containsMatchIn(it.relativeTo(sharedRoot()).invariantSeparatorsPath) }
            .toList()

    /** State carriers: the file-name scope of ratchets 1 and 3 (see class KDoc). */
    private fun File.isStateCarrier(): Boolean =
        name.endsWith("ViewModel.kt") || name.endsWith("State.kt") || name.contains("StateHolder")

    private fun relativePath(file: File): String = file.relativeTo(sharedRoot()).invariantSeparatorsPath

    private fun File.sourceText(): String = readText(Charsets.UTF_8)

    /**
     * Removes line and block comments so the ratchets check *code*, not KDoc
     * prose — and (the extension over ControllerOwnershipTest's stripper)
     * blanks string/char literal CONTENTS too, raw strings included, so a
     * code sample riding in a raw-string KDoc cannot be counted as a
     * declaration. Quotes and newlines are preserved.
     */
    private fun String.stripComments(): String {
        val out = StringBuilder(length)
        var i = 0
        var inLine = false
        var inBlock = false
        var inString = false
        var inRawString = false
        var inChar = false
        while (i < length) {
            val c = this[i]
            val next = if (i + 1 < length) this[i + 1] else ' '
            val tripleQuote = c == '"' && next == '"' && i + 2 < length && this[i + 2] == '"'
            when {
                inLine -> if (c == '\n') { inLine = false; out.append(c) }
                inBlock -> if (c == '*' && next == '/') { inBlock = false; i++ } else if (c == '\n') out.append(c)
                inRawString -> when {
                    tripleQuote -> { inRawString = false; out.append("\"\"\""); i += 2 }
                    c == '\n' -> out.append(c)
                    else -> out.append(' ')
                }
                inString -> when {
                    c == '\\' -> { out.append(' '); i++ }
                    c == '\n' -> { inString = false; out.append(c) }
                    c == '"' -> { inString = false; out.append(c) }
                    else -> out.append(' ')
                }
                inChar -> when {
                    c == '\\' -> { out.append(' '); i++ }
                    c == '\'' -> { inChar = false; out.append(c) }
                    else -> out.append(' ')
                }
                else -> when {
                    c == '/' && next == '/' -> inLine = true
                    c == '/' && next == '*' -> inBlock = true
                    tripleQuote -> { inRawString = true; out.append("\"\"\""); i += 2 }
                    c == '"' -> { inString = true; out.append(c) }
                    c == '\'' -> { inChar = true; out.append(c) }
                    else -> out.append(c)
                }
            }
            i++
        }
        return out.toString()
    }

    private fun exemptPathSuffixes(pattern: String): Set<String> =
        declaredDivergences.filter { it.pattern == pattern }.map { it.pathSuffix }.toSet()

    private fun runScan(): Scan {
        val sources = sharedKotlinSources()
        val spinnerExempt = exemptPathSuffixes(patternSpinnerFlags)
        val compositionExempt = exemptPathSuffixes(patternCompositionFlags)
        val parkedExempt = exemptPathSuffixes(patternParkedMessages)

        val spinnerFlags = mutableMapOf<String, Int>()
        val compositionFlags = mutableMapOf<String, Int>()
        val parkedMessages = mutableMapOf<String, Int>()
        for (file in sources) {
            val path = relativePath(file)
            val text = file.sourceText().stripComments()
            if (file.isStateCarrier() && spinnerExempt.none { path.endsWith(it) }) {
                countInto(spinnerFlags, path, spinnerFlagRegex.findAll(text).count())
            }
            if (compositionExempt.none { path.endsWith(it) }) {
                countInto(compositionFlags, path, compositionFlagRegex.findAll(text).count())
            }
            if (file.isStateCarrier() && parkedExempt.none { path.endsWith(it) }) {
                countInto(parkedMessages, path, parkedMessageRegex.findAll(text).count())
            }
        }
        return Scan(spinnerFlags, compositionFlags, parkedMessages, sources.size)
    }

    private fun countInto(into: MutableMap<String, Int>, path: String, count: Int) {
        if (count > 0) into[path] = count
    }

    private fun Map<String, Int>.total(): Int = values.sum()

    private fun Map<String, Int>.formatted(): String =
        entries.sortedBy { it.key }.joinToString(separator = "\n      ") { "${it.key} (${it.value})" }

    // ── Failure messages: cite the convention, name the files, spell out the outs. ──

    private fun growthFailure(
        vocabulary: String,
        ceiling: Int,
        byFile: Map<String, Int>,
        conventionSection: String,
        migrateOut: String,
    ): String = buildString {
        append(vocabulary)
        append(" grew to ")
        append(byFile.total())
        append(" declarations (ceiling ")
        append(ceiling)
        append("). Offending files:\n      ")
        append(byFile.formatted())
        append("\n\nStateManagementConventions.PREFERRED_PATTERN ")
        append(conventionSection)
        append("\nTwo legitimate outs:\n")
        append("  1. MIGRATE: ")
        append(migrateOut)
        append(" — and LOWER this ceiling: lower the baseline when a slice migrates; never raise it to admit growth.\n")
        append("  2. DECLARED DIVERGENCE: only if the new property is a genuinely two-state latch that must " +
            "stay, add a dated entry to ImplicitStateRatchetTest.declaredDivergences (file + reason + date) " +
            "following the ControllerOwnershipTest raise-entry convention (\"Raised to … (2026-11-15) for …\").")
    }

    // ── The ratchets. ──

    @Test
    fun spinnerFlagsOnStateCarriers_neverIncrease() {
        val byFile = scan.spinnerFlags
        assertTrue(
            byFile.total() <= maxSpinnerFlagsOnStateCarriers,
            growthFailure(
                vocabulary = "the spinner/progress-flag vocabulary on state carriers " +
                    "(isLoading/isRefreshing/isSearching/isApplying/isSaving/isConnecting/isUploading " +
                    "declarations in *ViewModel/*State/*StateHolder files)",
                ceiling = maxSpinnerFlagsOnStateCarriers,
                byFile = byFile,
                conventionSection = "section 1 bans the parallel load-boolean choreography " +
                    "(\"NEVER mint parallel booleans (isLoading + isRefreshing + error)\"): nothing " +
                    "prevents two of them being true at once, and every render site must re-derive " +
                    "which combination wins",
                migrateOut = "fold the flags into one sealed load phase — one owner StateFlow with an " +
                    "exhaustive `when` at the render site (DetailUiLoadState / JellyPlayViewModel." +
                    "LoadingState exemplars) — so the booleans disappear",
            ),
        )
    }

    @Test
    fun parallelSheetDialogMenuBooleans_neverIncrease() {
        val byFile = scan.compositionFlags
        assertTrue(
            byFile.total() <= maxParallelSheetDialogMenuBooleans,
            growthFailure(
                vocabulary = "the parallel sheet/dialog/menu composition-flag vocabulary " +
                    "(var show…Dialog/Sheet/Menu/Submenu)",
                ceiling = maxParallelSheetDialogMenuBooleans,
                byFile = byFile,
                conventionSection = "section 1's \"NEVER mint parallel booleans\" covers these the same " +
                    "way: several open flags can coexist and every render site re-derives which one wins",
                migrateOut = "route the surface through a sealed sheet vocabulary with ONE open slot " +
                    "(AudioPlayerSheetStack's `current: AudioPlayerSheet?` exemplar) so the flags " +
                    "collapse into one mutually-exclusive state",
            ),
        )
    }

    @Test
    fun parkedOneShotMessageFields_neverIncrease() {
        val byFile = scan.parkedMessages
        assertTrue(
            byFile.total() <= maxParkedOneShotMessageFields,
            growthFailure(
                vocabulary = "the parked one-shot message vocabulary " +
                    "(*Status/*Message/*Event fields typed String? or delegated by composeState " +
                    "in state-carrier files)",
                ceiling = maxParkedOneShotMessageFields,
                byFile = byFile,
                conventionSection = "section 2 bans parking a one-shot as a nullable field plus a " +
                    "clear-it-after-handling call: \"a StateFlow replays its current value to every " +
                    "new collector, so the event re-fires on state restoration, process recreation, " +
                    "or a re-subscribing collector\"",
                migrateOut = "move the one-shot onto a Channel exposed as receiveAsFlow() and consumed " +
                    "exactly once (VideoPlayerViewModel.closePlayer / ChannelDetailViewModel.messages / " +
                    "undoActionChannel() exemplars)",
            ),
        )
    }

    @Test
    fun parkedMessageRegex_positiveAndNegativeControls() {
        // The R4 rot control (2026-11-17): the production rot floor
        // (parkedMessageRegexRotFloor) only proves the regex still finds the
        // TOLERATED residue — the 2026-11-16 0/0 ceiling+floor could pass
        // vacuously forever if the regex rotted off the vocabulary entirely,
        // and the -Failure/-Error names plus the helper-seeded nullable-slot
        // shape were invisible to the old regex without any test noticing.
        // This fixture plants EVERY banned shape (all three alternations) and
        // the shapes that must stay OUTSIDE the vocabulary, so a regex edit
        // that stops recognizing any banned shape — or one that starts
        // counting sanctioned state — fails here outright.
        val fixtureText = javaClass.getResourceAsStream(
            "/ratchet/parked-message-rot-controls.kt.txt",
        )?.bufferedReader()?.readText()
        assertTrue(
            !fixtureText.isNullOrEmpty(),
            "the rot-controls fixture (ratchet/parked-message-rot-controls.kt.txt) is missing " +
                "from jvmTest resources — the parked-message vocabulary has no shape-level " +
                "rot guard without it",
        )

        val positiveMarker = "── POSITIVE CONTROLS"
        val negativeMarker = "── NEGATIVE CONTROLS"
        assertTrue(
            fixtureText.contains(positiveMarker) && fixtureText.contains(negativeMarker) &&
                fixtureText.indexOf(positiveMarker) < fixtureText.indexOf(negativeMarker),
            "fixture lost its POSITIVE/NEGATIVE CONTROLS markers",
        )
        // Everything before the negative marker (header + positive section):
        // the header plants no val/var lines, so the line filter below picks
        // exactly the positive declarations.
        val positiveSection = fixtureText.substringBefore(negativeMarker)
        val negativeSection = fixtureText.substringAfter(negativeMarker)

        val positives = positiveSection.lineSequence()
            .filter { it.startsWith("val ") || it.startsWith("var ") }
            .toList()
        val negatives = negativeSection.lineSequence()
            .filter { it.startsWith("val ") || it.startsWith("var ") }
            .toList()
        assertTrue(
            positives.size >= 11,
            "the fixture's positive controls shrank (${positives.size} lines) — every banned " +
                "shape (A1/A2 names incl. -Failure/-Error, A2 by-composeState, A3 " +
                "helper-seeded nullable slots) must stay planted",
        )
        assertTrue(
            negatives.size >= 7,
            "the fixture's negative controls shrank (${negatives.size} lines) — the sanctioned " +
                "shapes the vocabulary must keep excluding must stay planted",
        )

        for (line in positives) {
            assertTrue(
                parkedMessageRegex.containsMatchIn(line),
                "positive control no longer matches the parked-message vocabulary: `$line` — " +
                    "a regex/scope edit broke shape coverage; fix the regex, not the fixture",
            )
        }
        for (line in negatives) {
            assertTrue(
                !parkedMessageRegex.containsMatchIn(line),
                "negative control started matching the parked-message vocabulary: `$line` — " +
                    "the regex now counts sanctioned state; tighten it (the fixture is the spec)",
            )
        }
    }

    @Test
    fun scanScope_doesNotPassVacuously() {
        val sources = sharedKotlinSources()
        assertTrue(
            sources.size >= minScannedKotlinFiles,
            "the ratchet scanned only ${sources.size} shared kt files (floor $minScannedKotlinFiles) — " +
                "the walk-up to settings.gradle.kts or the /src/*/kotlin/ scope broke; the ceilings " +
                "must not pass vacuously",
        )

        // Sentinels: the stable convention artifact the ratchet cites, and the
        // ViewModel base class — both must sit inside the scanned scope.
        for (sentinel in listOf(
            "core/ui/src/commonMain/kotlin/com/raulshma/jellyplay/core/ui/viewmodel/StateManagementConventions.kt",
            "core/ui/src/commonMain/kotlin/com/raulshma/jellyplay/core/ui/viewmodel/JellyPlayViewModel.kt",
        )) {
            assertTrue(
                sources.any { relativePath(it).endsWith(sentinel) },
                "sentinel $sentinel is outside the scan — the scope broke and the ceilings would pass vacuously",
            )
        }

        // Regex-rot floors: each vocabulary must still find its known residue.
        assertTrue(
            scan.spinnerFlags.total() >= spinnerRegexRotFloor,
            "spinner-flag ratchet found only ${scan.spinnerFlags.total()} hits (floor $spinnerRegexRotFloor) — " +
                "either the residue migrated (lower the floor AND maxSpinnerFlagsOnStateCarriers together, " +
                "the ControllerOwnershipTest lower-when-migrated convention) or the regex/scope rotted",
        )
        assertTrue(
            scan.compositionFlags.total() >= compositionRegexRotFloor,
            "composition-flag ratchet found only ${scan.compositionFlags.total()} hits (floor $compositionRegexRotFloor) — " +
                "either the residue migrated (lower the floor AND maxParallelSheetDialogMenuBooleans together) " +
                "or the regex/scope rotted",
        )
        assertTrue(
            scan.parkedMessages.total() >= parkedMessageRegexRotFloor,
            "parked-message ratchet found only ${scan.parkedMessages.total()} hits (floor $parkedMessageRegexRotFloor) — " +
                "either the last parked field migrated (lower the floor AND maxParkedOneShotMessageFields " +
                "together) or the regex/scope rotted",
        )

        // Allowlist hygiene: an entry whose declared divergence migrated away is
        // stale — remove it (and lower the ceiling) instead of leaving dead text.
        for (entry in declaredDivergences) {
            val file = sources.firstOrNull { relativePath(it).endsWith(entry.pathSuffix) }
            assertTrue(
                file != null,
                "stale allowlist entry (${entry.date}): ${entry.pathSuffix} no longer resolves — " +
                    "remove the entry from declaredDivergences",
            )
            val stillCounted = when (entry.pattern) {
                patternSpinnerFlags -> spinnerFlagRegex.findAll(file.sourceText().stripComments()).count()
                patternCompositionFlags -> compositionFlagRegex.findAll(file.sourceText().stripComments()).count()
                patternParkedMessages -> parkedMessageRegex.findAll(file.sourceText().stripComments()).count()
                else -> fail("declaredDivergences entry names an unknown pattern: ${entry.pattern}")
            }
            assertTrue(
                stillCounted > 0,
                "stale allowlist entry (${entry.date}): ${entry.pathSuffix} no longer matches the " +
                    "${entry.pattern} vocabulary — the divergence migrated away; remove the entry " +
                    "(and lower the ceiling) instead of leaving dead text",
            )
        }
    }
}
