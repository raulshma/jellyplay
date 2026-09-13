package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.feature.book.epub.EpubSpeechParagraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The read-aloud loop's live state. `paragraphIndex` is null while idle AND
 * while between chapters (a session is live, awaiting the next chapter's
 * paragraphs); `active` covers the whole session — started, playing OR
 * paused — so the chrome keeps its controls through a pause; `paused` is
 * only meaningful while active (engine stopped, index remembered).
 */
internal data class ReaderSpeechState(
    val paragraphIndex: Int? = null,
    val active: Boolean = false,
    val paused: Boolean = false,
)

/**
 * Drives the paragraph-by-paragraph read-aloud loop over a
 * [BookSpeechEngine]: speak paragraph *i* → its `onDone` → paragraph *i+1*
 * → …; when the chapter's paragraphs exhaust, the controller does NOT
 * finish — it parks (index null, session live) and hands continuation to
 * [onChapterEnd], whose owner advances the reader and feeds the next
 * chapter back through [start]. Skips move ±1 paragraph (forward past the
 * last paragraph takes the chapter-end path); pause stops the engine and
 * keeps the index, resume re-speaks the current paragraph from its start.
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

    private var paragraphs: List<EpubSpeechParagraph> = emptyList()

    /** Bumped by every loop interruption; utterance completions must match it. */
    private var generation = 0

    /**
     * Marks the session live without paragraphs yet (the context request is
     * in flight) — the chrome can already show the active/paused state and
     * a late context still lands because [onSpeechContext] guards on active.
     */
    fun awaitContext() {
        hardStopEngine()
        paragraphs = emptyList()
        _state.value = ReaderSpeechState(active = true)
    }

    /**
     * Speaks [paragraphs] from the first one. An empty chapter is not an
     * error: it takes the chapter-end path immediately so the owner can
     * advance (an empty context at book end terminates through the owner's
     * same-chapter/percent guard, never through an infinite loop here).
     */
    fun start(paragraphs: List<EpubSpeechParagraph>) {
        hardStopEngine()
        this.paragraphs = paragraphs
        if (paragraphs.isEmpty()) {
            _state.value = ReaderSpeechState(active = true)
            onChapterEnd()
            return
        }
        speakIndex(0)
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
        val index = current.paragraphIndex ?: return
        speakIndex(index)
    }

    /** +1 paragraph; forward past the last takes the chapter-end continuation. */
    fun skipForward() {
        if (!_state.value.active || paragraphs.isEmpty()) return
        val next = (_state.value.paragraphIndex ?: -1) + 1
        if (next >= paragraphs.size) {
            generation++
            engine.stop()
            _state.value = ReaderSpeechState(active = true)
            onChapterEnd()
        } else {
            speakIndex(next)
        }
    }

    /** -1 paragraph, clamped at the chapter's first (re-speaks it). */
    fun skipBack() {
        if (!_state.value.active || paragraphs.isEmpty()) return
        val target = ((_state.value.paragraphIndex ?: 0) - 1).coerceAtLeast(0)
        speakIndex(target)
    }

    /** User stop: clears the session silently (no [onFinished] — that is the natural end). */
    fun stop() {
        hardStopEngine()
        paragraphs = emptyList()
        _state.value = ReaderSpeechState()
    }

    /**
     * Natural end (the owner determined no chapter follows): clears the
     * session and fires [onFinished].
     */
    fun finish() {
        hardStopEngine()
        paragraphs = emptyList()
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

    private fun speakIndex(index: Int) {
        val paragraph = paragraphs.getOrNull(index) ?: return
        generation++
        _state.value = ReaderSpeechState(paragraphIndex = index, active = true)
        onSpeakParagraph(index, paragraph.cfi)
        val spokenGeneration = generation
        engine.speak(paragraph.text) {
            if (spokenGeneration != generation) return@speak
            advance()
        }
    }

    private fun advance() {
        val next = (_state.value.paragraphIndex ?: -1) + 1
        if (next >= paragraphs.size) {
            _state.value = ReaderSpeechState(active = true)
            onChapterEnd()
        } else {
            speakIndex(next)
        }
    }

    private fun hardStopEngine() {
        generation++
        engine.stop()
    }
}
