package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.whatsnew.WhatsNewRepository
import com.raulshma.jellyplay.core.model.WhatsNewRelease
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.launch

/**
 * The What's New archive screen's model: the assembled feed (cached ∪
 * fetched, newest first) plus a refresh fired on open so the archive is
 * fresh whenever the user browses it.
 */
class WhatsNewViewModel(
    private val whatsNewRepository: WhatsNewRepository,
) : JellyPlayViewModel() {

    var releases by composeState<List<WhatsNewRelease>>(emptyList())
        private set

    init {
        launch {
            // Serve the offline state immediately, then fold in whatever a
            // refresh brings — the repository never degrades the list on
            // failure, so no loading/error states exist here.
            whatsNewRepository.releases.collect { releases = it }
        }
        launch { whatsNewRepository.refresh() }
    }
}
