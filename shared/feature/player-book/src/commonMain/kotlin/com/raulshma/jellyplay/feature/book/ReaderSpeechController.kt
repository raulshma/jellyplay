package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.feature.book.epub.EpubSpeechParagraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * it parks (index null, session live) and hands continuation to
 * [onChapterEnd], whose owner advances the reader and feeds the next
 * chapter back through [start]. Skips move ±1 SENTENCE (forward past the
 * last sentence takes the chapter-end path); pause stops the engine and
 * keeps the position, resume re-speaks the current sentence from its start.
 * The engine itself stays text-agnostic — granularity lives here.
 *
 * Stale-utterance guard: every speak carries a generation counter, and
 * stop/pause/skip bump it — a late `onDone` from the utterance they
 * replaced can never advance the loop. Main-thread confined (the engine
 * contract posts `onDone` there; the ViewModel calls in from its scope).
 *
 * Compose-free by convention (the other reader controllers' rule): the
 * screen renders [state] and executes [com.raulshma.jellyplay.feature.book.ReaderHostCommand]s,
 * this class owns only the loop.
 */
internal class ReaderSpeechController(
    private val engine: BookSpeechEngine,
    private val onSpeakParagraph: (index: Int, cfi: String) -> Unit,
    private val onChapterEnd: () -> Unit,
    private val onFinished: () -> Unit,
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

    /**
     * Marks the session live without paragraphs yet (the context request is
     * in flight) — the chrome can already show the active/paused state and
     * a late context still lands because [onSpeechContext] guards on active.
     */
    fun awaitContext() {
        hardStopEngine()
        units = emptyList()
        unitIndex = -1
        _state.value = ReaderSpeechState(active = true)
    }

    /**
     * Speaks [paragraphs] from the first sentence. An empty chapter is not an
     * error: it takes the chapter-end path immediately so the owner can
     * advance (an empty context at book end terminates through the owner's
     * same-chapter/percent guard, never through an infinite loop here).
     */
    fun start(paragraphs: List<EpubSpeechParagraph>) {
        hardStopEngine()
        units = paragraphs.flatMapIndexed { index, paragraph ->
            splitSentences(paragraph.text).map { SpeechUnit(index, paragraph.cfi, it) }
        }
        unitIndex = -1
        if (units.isEmpty()) {
            _state.value = ReaderSpeechState(active = true)
            onChapterEnd()
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
            onChapterEnd()
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
            onChapterEnd()
        } else {
            speakUnit(next)
        }
    }

    private fun hardStopEngine() {
        generation++
        engine.stop()
    }
}
