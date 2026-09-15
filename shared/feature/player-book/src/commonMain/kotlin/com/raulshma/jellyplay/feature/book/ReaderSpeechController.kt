package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.feature.book.epub.EpubReaderHandle
import com.raulshma.jellyplay.feature.book.epub.EpubSpeechParagraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The read-aloud loop's live state. `spokenParagraphIndex` (the paragraph of
 * the sentence being spoken) is null while idle AND while between chapters
 * (a session is live, awaiting the next chapter's paragraphs); `active`
 * covers the whole session — started, playing OR paused — so the chrome
 * keeps its controls through a pause; `paused` is only meaningful while
 * active (engine stopped, position remembered).
 */
internal data class ReaderSpeechState(
    val spokenParagraphIndex: Int? = null,
    val active: Boolean = false,
    val paused: Boolean = false,
)

/**
 * One engine utterance: a sentence of paragraph [paragraphIndex] (a
 * paragraph that splits into no sentences still speaks as itself).
 */
private data class SpeechUnit(
    val paragraphIndex: Int,
    val cfi: String,
    val text: String,
)

/**
 * Splits a paragraph into speakable sentences at sentence-final punctuation
 * (`.` `!` `?` `…`) followed by a boundary (whitespace or end); trailing
 * quotes/brackets stay attached. Heuristic by design — abbreviations and
 * decimals may mis-split, which only coarsens skip granularity, never
 * correctness: the units concatenate back to the paragraph.
 */
internal fun splitSentences(text: String): List<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    val sentences = mutableListOf<String>()
    val current = StringBuilder()
    var i = 0
    while (i < trimmed.length) {
        val c = trimmed[i]
        current.append(c)
        if (c == '.' || c == '!' || c == '?' || c == '…') {
            var j = i + 1
            while (j < trimmed.length && (trimmed[j] == '"' || trimmed[j] == '”' || trimmed[j] == '’' ||
                    trimmed[j] == '\'' || trimmed[j] == ')' || trimmed[j] == ']')
            ) {
                current.append(trimmed[j])
                i = j
                j++
            }
            if (j >= trimmed.length || trimmed[j].isWhitespace()) {
                val sentence = current.toString().trim()
                if (sentence.isNotEmpty()) sentences.add(sentence)
                current.clear()
            }
        }
        i++
    }
    val rest = current.toString().trim()
    if (rest.isNotEmpty()) sentences.add(rest)
    return sentences.ifEmpty { listOf(trimmed) }
}

/**
 * Drives the sentence-by-sentence read-aloud loop over a
 * [BookSpeechEngine]: speak sentence *i* → its `onDone` → sentence *i+1* →
 * …; when the chapter's sentences exhaust, the controller does NOT finish —
 * it parks (index null, session live), turns the page itself through the
 * [host] seam and re-requests the next chapter's context once the turn's
 * relocation lands ([onRelocated]) or the [SPEECH_CHAPTER_ADVANCE_TIMEOUT_MS]
 * timeout fires (a book-end turn never relocates — the identity guard in
 * [onSpeechContext] finishes there). Skips move ±1 SENTENCE (forward past
 * the last sentence takes the chapter-end path); pause stops the engine and
 * keeps the position, resume re-speaks the current sentence from its start.
 * The engine itself stays text-agnostic — granularity lives here.
 *
 * Stale-utterance guard: every speak carries a generation counter, and
 * stop/pause/skip bump it — a late `onDone` from the utterance they
 * replaced can never advance the loop. Main-thread confined (the engine
 * contract posts `onDone` there; the ViewModel calls in from its scope).
 *
 * Compose-free by convention (the other reader controllers' rule): the
 * screen renders [state], this class owns the loop AND its chapter
 * continuation — controller → host → JS directly, no command channel (the
 * host accessor's shape and late-binding rationale are [ReaderAnnotationSync]'s).
 */
internal class ReaderSpeechController(
    private val engine: BookSpeechEngine,
    /**
     * Owns the chapter-advance timeout: the continuation waits for the turn's
     * relocation, and a book-end turn never delivers one — the timeout
     * re-requests the context anyway so the identity guard can finish.
     */
    private val scope: CoroutineScope,
    /**
     * The host seam — the ViewModel's attached [ReaderSessionPort] handle:
     * chapter turns (`next`) and context requests (`requestSpeechContext`)
     * execute DIRECTLY against it. Late-bound (`() -> Handle?`) because the
     * host is session-owned and created after this controller; a null host
     * (detached screen) degrades the continuation to a no-op, which the
     * parked session simply outlives.
     */
    private val host: () -> EpubReaderHandle?,
    private val onSpeakParagraph: (index: Int, cfi: String) -> Unit,
    private val onFinished: () -> Unit = {},
    private val onError: () -> Unit = {},
) {

    private val _state = MutableStateFlow(ReaderSpeechState())
    val state: StateFlow<ReaderSpeechState> = _state.asStateFlow()

    /** The chapter's sentences flattened in speak order; empty while idle. */
    private var units: List<SpeechUnit> = emptyList()

    /** Index of the live (or, while paused, remembered) sentence; -1 = none. */
    private var unitIndex = -1

    /** Bumped by every loop interruption; utterance completions must match it. */
    private var generation = 0

    // -----------------------------------------------------------------
    // Chapter continuation state (the protocol the VM used to keep as four
    // hand-synced fields).
    // -----------------------------------------------------------------

    /**
     * First-paragraph CFI of the chapter being spoken — the book-end identity
     * guard. The RAW first paragraph, not the first SENTENCE: a paragraph
     * that splits into no sentences still anchors the chapter identity.
     */
    private var chapterKey: String? = null

    /** True between "chapter end" and "context re-requested" (relocation wait window). */
    private var advancing = false

    /** Percent snapshot at the chapter turn — the empty-chapter continuation guard. */
    private var percentAtAdvance = 0.0

    /** Live percent, kept current by the owner via [reportPercent]. */
    private var currentPercent = 0.0

    private var advanceTimeout: Job? = null

    /**
     * The owner keeps this current from the relocated/percent events — the
     * stall guard compares it against [percentAtAdvance].
     */
    fun reportPercent(percent: Double) {
        currentPercent = percent
    }

    /**
     * Chapter-end continuation: remember the chapter identity + percent,
     * turn the page through the host seam, then re-request the context once
     * the turn's relocation arrives ([onRelocated]) or the timeout fires (a
     * book-end turn never relocates — the identity guard finishes there).
     */
    fun advanceChapter() {
        // chapterKey already holds the current chapter's raw first-paragraph
        // CFI (set by [start]); nothing between start and the turn rewrites it.
        percentAtAdvance = currentPercent
        advancing = true
        host()?.next()
        advanceTimeout?.cancel()
        advanceTimeout = scope.launch {
            delay(SPEECH_CHAPTER_ADVANCE_TIMEOUT_MS)
            requestContextAfterAdvance()
        }
    }

    /** The host relocated — exactly the event the turn's context request waits for. */
    fun onRelocated() {
        if (!advancing) return
        advanceTimeout?.cancel()
        advanceTimeout = null
        requestContextAfterAdvance()
    }

    private fun requestContextAfterAdvance() {
        if (!advancing) return
        advancing = false
        host()?.requestSpeechContext(null)
    }

    /**
     * Marks the session live without paragraphs yet and asks the host for the
     * chapter's speakable context at [cfi] (null = the current chapter) —
     * the answer lands in [onSpeechContext], guarded on active, so the
     * chrome can already show the active/paused state while it is in flight.
     */
    fun awaitContext(cfi: String? = null) {
        hardStopEngine()
        units = emptyList()
        unitIndex = -1
        clearContinuation()
        _state.value = ReaderSpeechState(active = true)
        host()?.requestSpeechContext(cfi)
    }

    /**
     * Speaks [paragraphs] from the first sentence. An empty chapter is not an
     * error: it takes the chapter-end path immediately so the loop advances
     * itself (an empty context at book end terminates through the
     * same-chapter/percent guard in [onSpeechContext], never through an
     * infinite loop here).
     */
    fun start(paragraphs: List<EpubSpeechParagraph>) {
        hardStopEngine()
        chapterKey = paragraphs.firstOrNull()?.cfi
        units = paragraphs.flatMapIndexed { index, paragraph ->
            splitSentences(paragraph.text).map { SpeechUnit(index, paragraph.cfi, it) }
        }
        unitIndex = -1
        if (units.isEmpty()) {
            _state.value = ReaderSpeechState(active = true)
            advanceChapter()
            return
        }
        speakUnit(0)
    }

    fun pause() {
        val current = _state.value
        if (!current.active || current.paused) return
        generation++
        engine.stop()
        _state.value = current.copy(paused = true)
    }

    fun resume() {
        val current = _state.value
        if (!current.active || !current.paused) return
        _state.value = current.copy(paused = false)
        val index = unitIndex
        if (index < 0) return
        speakUnit(index)
    }

    /** +1 sentence; forward past the last takes the chapter-end continuation. */
    fun skipForward() {
        if (!_state.value.active || units.isEmpty()) return
        val next = unitIndex + 1
        if (next >= units.size) {
            generation++
            engine.stop()
            unitIndex = -1
            _state.value = ReaderSpeechState(active = true)
            advanceChapter()
        } else {
            speakUnit(next)
        }
    }

    /** -1 sentence, clamped at the chapter's first (re-speaks it). */
    fun skipBack() {
        if (!_state.value.active || units.isEmpty()) return
        val target = (unitIndex - 1).coerceAtLeast(0)
        speakUnit(target)
    }

    /** User stop: clears the session silently (no [onFinished] — that is the natural end). */
    fun stop() {
        hardStopEngine()
        units = emptyList()
        unitIndex = -1
        clearContinuation()
        _state.value = ReaderSpeechState()
    }

    /**
     * Natural end (the owner determined no chapter follows): clears the
     * session and fires [onFinished].
     */
    fun finish() {
        hardStopEngine()
        units = emptyList()
        unitIndex = -1
        _state.value = ReaderSpeechState()
        onFinished()
    }

    /**
     * The engine went away mid-session (availability dropped to
     * UNAVAILABLE): clears the session and fires [onError] instead of
     * [onFinished] — the two end differently in the owning ViewModel.
     */
    fun engineLost() {
        if (!_state.value.active) return
        stop()
        onError()
    }

    /**
     * Host answer to a speech-context request, guards folded in (moved
     * verbatim from the owning ViewModel): a context whose first paragraph
     * repeats the chapter being spoken means the chapter turn did not
     * relocate (book end) → finish; an empty context mid-book means a
     * chapter with nothing speakable → the caller keeps advancing (the
     * percent snapshot taken at the turn breaks the loop once the position
     * stops moving); anything else starts the loop on the new chapter.
     */
    fun onSpeechContext(paragraphs: List<EpubSpeechParagraph>, currentPercent: Double) {
        if (!_state.value.active) return // late answer to a stopped session
        val firstCfi = paragraphs.firstOrNull()?.cfi
        if (firstCfi != null && firstCfi == chapterKey) {
            // The turn did not relocate — the same chapter came back. Book end.
            finish()
            return
        }
        if (paragraphs.isEmpty() && currentPercent == percentAtAdvance) {
            // Nothing speakable AND the position never moved: advancing again
            // cannot help (book end, or a static rendition) — finish instead
            // of looping.
            finish()
            return
        }
        start(paragraphs)
    }

    /** Clears the continuation bookkeeping (NOT the loop state — callers drive that). */
    private fun clearContinuation() {
        chapterKey = null
        advancing = false
        advanceTimeout?.cancel()
        advanceTimeout = null
    }

    private fun speakUnit(index: Int) {
        val unit = units.getOrNull(index) ?: return
        generation++
        unitIndex = index
        _state.value = ReaderSpeechState(spokenParagraphIndex = unit.paragraphIndex, active = true)
        onSpeakParagraph(unit.paragraphIndex, unit.cfi)
        val spokenGeneration = generation
        engine.speak(unit.text) {
            if (spokenGeneration != generation) return@speak
            advance()
        }
    }

    private fun advance() {
        val next = unitIndex + 1
        if (next >= units.size) {
            unitIndex = -1
            _state.value = ReaderSpeechState(active = true)
            advanceChapter()
        } else {
            speakUnit(next)
        }
    }

    private fun hardStopEngine() {
        generation++
        engine.stop()
    }
}

/** How long the continuation waits for the chapter turn's relocation before re-requesting the context anyway. */
private const val SPEECH_CHAPTER_ADVANCE_TIMEOUT_MS = 1_500L
