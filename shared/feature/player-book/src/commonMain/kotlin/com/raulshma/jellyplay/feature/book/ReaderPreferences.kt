package com.raulshma.jellyplay.feature.book

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.datastore.reader.PerBookAppearance
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.core.datastore.reader.ReaderFontFamily
import com.raulshma.jellyplay.core.datastore.reader.ReaderSlice
import com.raulshma.jellyplay.core.datastore.reader.ReaderStore
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the reader renders and routes writes against, folded once per emission:
 * the raw global [ReaderSlice], the loaded item's appearance override, the
 * per-item page-flow direction (and whether it is a persisted user choice),
 * and the item the commands route to (null = the reader holds no book).
 */
@Immutable
data class ReaderPrefsSnapshot(
    val itemId: String? = null,
    val global: ReaderSlice = ReaderSlice(),
    val perBook: PerBookAppearance? = null,
    val direction: ReadingDirection = ReadingDirection.LTR,
    val directionPinned: Boolean = false,
) {
    /** Per-book override axis ?: the global one — the single render truth. */
    val effective: EffectiveAppearance
        get() = effectiveAppearance(global, perBook)

    /** The "use for this book only" switch state the settings sheet renders. */
    val perBookActive: Boolean get() = perBook != null
}

/**
 * The reader preference choreography module: every knob write — global or
 * per-book-routed, clamped or stepped, seeded or cleared — lands here as one
 * command, and the reader reads one [snapshot].
 *
 * **Write-through**: a command updates [snapshot] synchronously and persists
 * through [ReaderStore] asynchronously, so rapid commands (font-size stepper
 * taps, back-to-back theme chips) always fold onto the freshest values — the
 * DataStore round-trip lag can no longer drop them. Until a persist lands in
 * the store's own flow, the store's emissions are not adopted (the in-flight
 * counter below), so a store emission carrying the FIRST write can never
 * clobber a second command issued before it propagated. External changes
 * (factory reset) adopt whenever no command is in flight.
 *
 * **Routing**: theme and font-size writes land in the item's
 * [PerBookAppearance] override when one is active ("use for this book only"),
 * else in the globals — the render truth is [ReaderPrefsSnapshot.effective]
 * either way. [setUsePerBookAppearance] seeds the override with the current
 * effective values (the look does not move), and copies them back into the
 * globals when switched off. Direction stays per-book-only by design (its own
 * map); the last-CFI exact-resume anchor rides [setLastCfi] (ADR-0003 point 4).
 *
 * The VM re-exposes [snapshot] and forwards the commands; ReaderStore stays
 * the storage seam (datastore module), this module its deep consumer.
 */
class ReaderPreferences(
    private val store: ReaderStore,
    private val scope: CoroutineScope,
) {

    /** The item whose override/direction the commands route to (null = global-only). */
    private var itemId: String? = null

    private val _snapshot = MutableStateFlow(snapshotFrom(store.reader.value))
    val snapshot: StateFlow<ReaderPrefsSnapshot> = _snapshot.asStateFlow()

    /**
     * Commands issued whose persist has not landed in the store flow yet.
     * Cross-thread (commands come from main, the collector runs on [scope]),
     * hence the CAS loop. While nonzero, store emissions are not adopted.
     */
    private val inFlight = MutableStateFlow(0)

    init {
        scope.launch {
            store.reader.collect { slice ->
                if (inFlight.value == 0) _snapshot.value = snapshotFrom(slice)
            }
        }
    }

    /**
     * Re-points routing and the per-item folds at [itemId]. Called per load;
     * clears any optimistic per-book state from the previous book (an
     * override a load raced must not leak across items).
     */
    fun attach(itemId: String?) {
        this.itemId = itemId
        _snapshot.value = snapshotFrom(store.reader.value)
    }

    // -----------------------------------------------------------------
    // Appearance (theme + font size — the per-book-routable axes)
    // -----------------------------------------------------------------

    fun setTheme(theme: ReaderTheme) {
        val current = _snapshot.value
        val override = current.perBook
        if (current.itemId != null && override != null) {
            val next = override.copy(theme = theme)
            write(persist = { store.setPerBookAppearance(current.itemId, next) }) {
                it.copy(perBook = next)
            }
        } else {
            write(persist = { store.setReaderTheme(theme) }) {
                it.copy(global = it.global.copy(readerTheme = theme))
            }
        }
    }

    /**
     * Font-size stepper: steps from the last stepped value (the snapshot is
     * synchronous, so rapid taps accumulate instead of dropping) and clamps
     * into the store band. Routes like [setTheme].
     */
    fun adjustFontSize(delta: Int) {
        val next = (_snapshot.value.effective.fontSizePx + delta)
            .coerceIn(ReaderStore.MIN_FONT_SIZE_PX, ReaderStore.MAX_FONT_SIZE_PX)
        val current = _snapshot.value
        val override = current.perBook
        if (current.itemId != null && override != null) {
            val nextOverride = override.copy(fontSizePx = next)
            write(persist = { store.setPerBookAppearance(current.itemId, nextOverride) }) {
                it.copy(perBook = nextOverride)
            }
        } else {
            write(persist = { store.setReaderFontSizePx(next) }) {
                it.copy(global = it.global.copy(readerFontSizePx = next))
            }
        }
    }

    /**
     * The "use for this book only" switch. ON seeds the item's override with
     * the CURRENT effective values (the in-session look does not move); OFF
     * copies the effective values back into the globals and clears the
     * override — again visually seamless, and the next global change flows
     * normally.
     */
    fun setUsePerBookAppearance(enabled: Boolean) {
        val id = _snapshot.value.itemId ?: return
        val current = _snapshot.value
        val effectiveNow = current.effective
        if (enabled) {
            val seeded = PerBookAppearance(theme = effectiveNow.theme, fontSizePx = effectiveNow.fontSizePx)
            write(persist = { store.setPerBookAppearance(id, seeded) }) {
                it.copy(perBook = seeded)
            }
        } else {
            write(
                persist = {
                    store.setReaderTheme(effectiveNow.theme)
                    store.setReaderFontSizePx(effectiveNow.fontSizePx)
                    store.setPerBookAppearance(id, null)
                },
            ) {
                it.copy(
                    global = it.global.copy(
                        readerTheme = effectiveNow.theme,
                        readerFontSizePx = effectiveNow.fontSizePx,
                    ),
                    perBook = null,
                )
            }
        }
    }

    // -----------------------------------------------------------------
    // Typography + behavior (global-only axes; each clamps into its band)
    // -----------------------------------------------------------------

    fun setFontFamily(fontFamily: ReaderFontFamily) {
        write(persist = { store.setFontFamily(fontFamily) }) {
            it.copy(global = it.global.copy(fontFamily = fontFamily))
        }
    }

    fun setLineHeightPct(pct: Int) {
        val clamped = pct.coerceIn(ReaderStore.MIN_LINE_HEIGHT_PCT, ReaderStore.MAX_LINE_HEIGHT_PCT)
        write(persist = { store.setLineHeightPct(clamped) }) {
            it.copy(global = it.global.copy(lineHeightPct = clamped))
        }
    }

    fun setMarginPct(pct: Int) {
        val clamped = pct.coerceIn(ReaderStore.MIN_MARGIN_PCT, ReaderStore.MAX_MARGIN_PCT)
        write(persist = { store.setMarginPct(clamped) }) {
            it.copy(global = it.global.copy(marginPct = clamped))
        }
    }

    fun setJustify(justify: Boolean) {
        write(persist = { store.setJustify(justify) }) {
            it.copy(global = it.global.copy(justify = justify))
        }
    }

    fun setScrollMode(scrollMode: Boolean) {
        write(persist = { store.setScrollMode(scrollMode) }) {
            it.copy(global = it.global.copy(scrollMode = scrollMode))
        }
    }

    fun setBrightnessPct(pct: Int) {
        val clamped = pct.coerceIn(ReaderStore.MIN_BRIGHTNESS_PCT, ReaderStore.MAX_BRIGHTNESS_PCT)
        write(persist = { store.setBrightnessPct(clamped) }) {
            it.copy(global = it.global.copy(brightnessPct = clamped))
        }
    }

    fun setVolumeKeyPaging(enabled: Boolean) {
        write(persist = { store.setVolumeKeyPaging(enabled) }) {
            it.copy(global = it.global.copy(volumeKeyPaging = enabled))
        }
    }

    fun setAnimatedPageTurns(enabled: Boolean) {
        write(persist = { store.setAnimatedPageTurns(enabled) }) {
            it.copy(global = it.global.copy(animatedPageTurns = enabled))
        }
    }

    fun setTocRailVisible(enabled: Boolean) {
        write(persist = { store.setTocRailVisible(enabled) }) {
            it.copy(global = it.global.copy(tocRailVisible = enabled))
        }
    }

    fun setReadingSpeedWpm(wpm: Int) {
        val clamped = wpm.coerceIn(ReaderStore.MIN_READING_SPEED_WPM, ReaderStore.MAX_READING_SPEED_WPM)
        write(persist = { store.setReadingSpeedWpm(clamped) }) {
            it.copy(global = it.global.copy(readingSpeedWpm = clamped))
        }
    }

    /**
     * The settings sheets' commit diff, owned ONCE. The sheets deliberately
     * emit whole copy-on-change bundles ([ReaderTypographyState] /
     * [ReaderBehaviorState]) — the write surface stays the individual
     * setters, and THIS method is the single place that decides which axes
     * changed. The diff reads the live snapshot (write-through synchronous),
     * never a recomposition-captured stale slice; only changed axes write,
     * so an unchanged axis never fires a persist.
     */
    internal fun applyTypography(next: ReaderTypographyState) {
        val current = snapshot.value.global
        if (next.fontFamily != current.fontFamily) setFontFamily(next.fontFamily)
        if (next.lineHeightPct != current.lineHeightPct) setLineHeightPct(next.lineHeightPct)
        if (next.marginPct != current.marginPct) setMarginPct(next.marginPct)
        if (next.justify != current.justify) setJustify(next.justify)
        if (next.scrollMode != current.scrollMode) setScrollMode(next.scrollMode)
    }

    /** The behavior twin of [applyTypography] (see its KDoc). */
    internal fun applyBehavior(next: ReaderBehaviorState) {
        val current = snapshot.value.global
        if (next.volumeKeyPaging != current.volumeKeyPaging) setVolumeKeyPaging(next.volumeKeyPaging)
        if (next.animatedPageTurns != current.animatedPageTurns) setAnimatedPageTurns(next.animatedPageTurns)
        if (next.readingSpeedWpm != current.readingSpeedWpm) setReadingSpeedWpm(next.readingSpeedWpm)
        if (next.tocRailVisible != current.tocRailVisible) setTocRailVisible(next.tocRailVisible)
    }

    fun setAutoScrollSpeedPxPerSec(pxPerSec: Int) {
        val clamped = pxPerSec.coerceIn(
            ReaderStore.MIN_AUTO_SCROLL_SPEED_PX_PER_SEC,
            ReaderStore.MAX_AUTO_SCROLL_SPEED_PX_PER_SEC,
        )
        write(persist = { store.setAutoScrollSpeedPxPerSec(clamped) }) {
            it.copy(global = it.global.copy(autoScrollSpeedPxPerSec = clamped))
        }
    }

    // -----------------------------------------------------------------
    // Read aloud + direction + exact-resume anchor
    // -----------------------------------------------------------------

    fun setSpeechRate(pct: Int) {
        val clamped = pct.coerceIn(ReaderStore.MIN_SPEECH_RATE, ReaderStore.MAX_SPEECH_RATE)
        write(persist = { store.setSpeechRate(clamped) }) {
            it.copy(global = it.global.copy(speechRate = clamped))
        }
    }

    fun setSpeechPitch(pct: Int) {
        val clamped = pct.coerceIn(ReaderStore.MIN_SPEECH_PITCH, ReaderStore.MAX_SPEECH_PITCH)
        write(persist = { store.setSpeechPitch(clamped) }) {
            it.copy(global = it.global.copy(speechPitch = clamped))
        }
    }

    /**
     * Per-book page-flow direction — the persisted choice that outranks
     * whatever the EPUB reports about itself for this session's remainder
     * (the VM keeps the session gate).
     */
    fun setReadingDirection(direction: ReadingDirection) {
        val id = _snapshot.value.itemId ?: return
        write(persist = { store.setReadingDirection(id, direction) }) {
            it.copy(direction = direction, directionPinned = true)
        }
    }

    /** The exact-resume anchor read at open time (ADR-0003 point 4). */
    fun lastCfi(itemId: String): String? = store.lastCfi(itemId)

    /**
     * Records the book's exact resume CFI. Fire-and-persist: nothing reads
     * the anchor back through the snapshot mid-session, so no optimistic
     * write is needed (the flush path calls this on dispose, where a
     * synchronous snapshot update is pointless).
     */
    fun setLastCfi(itemId: String, cfi: String) {
        scope.launch { runCatchingRethrowingCancellation { store.setLastCfi(itemId, cfi) } }
    }

    // -----------------------------------------------------------------
    // Mechanics
    // -----------------------------------------------------------------

    /**
     * Synchronous snapshot update + async persist. The transform folds onto
     * the CURRENT snapshot (never the store flow), which is what makes
     * back-to-back commands accumulate; the in-flight counter is raised
     * BEFORE the optimistic write (and lowered when the persist lands), so
     * a store emission can never slip through the gap between the two and
     * revert a just-written value.
     */
    private fun write(
        persist: suspend () -> Unit,
        transform: (ReaderPrefsSnapshot) -> ReaderPrefsSnapshot,
    ) {
        incrementInFlight()
        _snapshot.value = transform(_snapshot.value)
        scope.launch {
            try {
                runCatchingRethrowingCancellation { persist() }
            } finally {
                decrementInFlight()
            }
        }
    }

    private fun snapshotFrom(slice: ReaderSlice): ReaderPrefsSnapshot {
        val id = itemId
        return ReaderPrefsSnapshot(
            itemId = id,
            global = slice,
            perBook = id?.let { slice.perBookAppearance[it] },
            direction = id?.let { slice.readingDirections[it] } ?: ReadingDirection.LTR,
            directionPinned = id != null && slice.readingDirections.containsKey(id),
        )
    }

    private fun incrementInFlight() {
        while (true) {
            val expected = inFlight.value
            if (inFlight.compareAndSet(expected, expected + 1)) return
        }
    }

    private fun decrementInFlight() {
        while (true) {
            val expected = inFlight.value
            if (inFlight.compareAndSet(expected, expected - 1)) return
        }
    }
}
