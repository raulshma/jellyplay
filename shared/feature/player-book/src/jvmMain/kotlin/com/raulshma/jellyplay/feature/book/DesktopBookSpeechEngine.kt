package com.raulshma.jellyplay.feature.book

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop actual of [BookSpeechEngine]: permanently UNAVAILABLE — desktop
 * TTS is roadmap-future, so the reader honestly degrades (no read-aloud
 * controls in the chrome, "unavailable" caption in the settings sheet)
 * instead of shipping a play button that cannot speak. Bound in
 * [com.raulshma.jellyplay.feature.book.di.desktopBookPlayerModule] so the
 * degradation is a platform decision, not a missing binding.
 */
internal class DesktopBookSpeechEngine : BookSpeechEngine {
    private val unavailable = MutableStateFlow(BookSpeechAvailability.UNAVAILABLE)
    override val availability: StateFlow<BookSpeechAvailability> = unavailable.asStateFlow()
    override fun configure(ratePercent: Int, pitchPercent: Int) {}
    override fun speak(text: String, onDone: () -> Unit) {}
    override fun stop() {}
    override fun shutdown() {}
}
