package com.raulshma.jellyplay.feature.music

import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.ui.viewmodel.MutableComposeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The mood/smart generated-playlist snapshot holder. Owns what both routes
 * used to hand-roll — the generate pipeline (loading/error/selection/result
 * lifecycle around the suspend fetch), the `"custom-" + UUID` id convention
 * with its delete guard, [clearGenerated], [playAll] — so
 * `MoodPlaylistsViewModel` and `SmartPlaylistsViewModel` stop hand-syncing
 * loose compose fields through every generate call.
 *
 * Per-kind differences stay pure adapters passed to [generate]: the route's
 * [fetch] decides the server query (mood's random 300-item pull vs smart's
 * favorites shortcut / criteria filters), and the route's [process] runs the
 * client pipeline (mood's keyword/exclusion/rating filter + sort + take vs
 * smart's unplayed/criteria filter + client sort + take) on
 * [Dispatchers.Default] — the exact bodies the old ladders ran.
 *
 * State is Compose snapshot state ([MutableComposeState]), deliberately not
 * `StateFlow`: the screens read the ViewModels' plain getters inside
 * composition, and snapshot reads — unlike `StateFlow.value` — are
 * Compose-observed (the same split `PlaylistsViewModel` documents for its
 * dialog surface). A flow surface would have forced collectAsState rewrites
 * across every read-site for no behavior change. The generate `error` stays
 * `String?` for the same reason — the detail screens render it verbatim
 * (the module's typed `PlaylistCommandError` renders by resolving at the
 * call site, which these screens don't do).
 *
 * @param P the route's playlist type (`MoodPlaylist` / `SmartPlaylist`).
 *   The selected playlist is part of the pipeline — set at generate start,
 *   cleared by [clearGenerated]; the smart route never reads it.
 */
@OptIn(ExperimentalUuidApi::class)
class GeneratedPlaylistState<P : Any>(
    private val scope: CoroutineScope,
    private val audioQueueFacade: MusicQueuePlayer,
) {

    private val _generatedItems = MutableComposeState<List<MediaItem>>(emptyList())

    /** The last successful generate's tracks — kept on failure, cleared by [clearGenerated]. */
    val generatedItems: List<MediaItem> get() = _generatedItems.value

    private val _isLoading = MutableComposeState(false)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = MutableComposeState<String?>(null)

    /** The failed generate's message, or the fallback when the throwable had none. */
    val error: String? get() = _error.value

    private val _selectedPlaylist = MutableComposeState<P?>(null)
    val selectedPlaylist: P? get() = _selectedPlaylist.value

    /**
     * Runs the generate ladder exactly as the old hand-rolled ones did: raise
     * the loading flag, clear the stale error, select [playlist], fetch, hop
     * [process] onto [Dispatchers.Default], publish the result (or the failure
     * message), drop the loading flag — success or failure.
     */
    fun generate(
        playlist: P,
        fetch: suspend () -> Result<SearchResult>,
        process: (List<MediaItem>) -> List<MediaItem>,
    ) {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            _selectedPlaylist.value = playlist
            fetch().onSuccess { result ->
                withContext(Dispatchers.Default) {
                    process(result.items)
                }.also { items ->
                    _generatedItems.value = items
                }
            }.onFailure { throwable ->
                _error.value = UserErrorMessages.resolve(throwable, DEFAULT_ERROR_MESSAGE)
            }
            _isLoading.value = false
        }
    }

    /** Resets the generate snapshot — items and selection; the error stays until the next generate. */
    fun clearGenerated() {
        _generatedItems.value = emptyList()
        _selectedPlaylist.value = null
    }

    /** Clears a standing error without touching the generate snapshot (custom-playlist commands do this). */
    fun clearError() {
        _error.value = null
    }

    /** Plays the generated tracks as a fresh queue at [startIndex]. */
    fun playAll(startIndex: Int = 0) {
        scope.launch {
            audioQueueFacade.playTracks(generatedItems, startIndex = startIndex)
        }
    }

    companion object {
        const val CUSTOM_ID_PREFIX = "custom-"

        private const val DEFAULT_ERROR_MESSAGE = "Failed to generate playlist"

        /** The id convention for user-created mood/smart playlists. */
        fun newCustomId(): String = "$CUSTOM_ID_PREFIX${Uuid.random()}"

        /** The delete guard: only [newCustomId]-shaped ids may ever be deleted. */
        fun isCustomId(id: String): Boolean = id.startsWith(CUSTOM_ID_PREFIX)
    }
}
