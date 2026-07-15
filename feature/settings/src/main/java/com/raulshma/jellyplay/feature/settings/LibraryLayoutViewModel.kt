package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.PreferencesJson
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.HomeLayoutConfig
import com.raulshma.jellyplay.core.model.HomeLayoutPreset
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.PinnedSectionType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A browseable, pinnable option surfaced in the "Add pinned section" picker. */
@Immutable
data class PinnableOption(
    val sourceId: String,
    val title: String,
    val subtitle: String? = null,
)

@HiltViewModel
class LibraryLayoutViewModel @Inject constructor(
    private val store: UserPreferencesStore,
    private val editor: PreferencesEditor,
    private val mediaRepository: MediaRepository,
) : JellyPlayViewModel() {

    /**
     * Narrow slice of just the pinned home-sections list. Collecting this (rather
     * than the whole [com.raulshma.jellyplay.core.model.UserPreferences]) means
     * the pinned-sections screen recomposes only when the pinned list changes.
     * One-shot reads of other preference fields use [store].preferences.value.
     */
    val pinnedHomeSectionsFlow: StateFlow<List<PinnedHomeSection>> = store.pinnedHomeSectionsFlow

    // ----- Pinned home sections -------------------------------------------

    var pinnedBrowseOptions by composeState<List<PinnableOption>>(emptyList())
        private set

    var pinnedBrowseLoading by composeState(false)
        private set

    var pinnedBrowseError by composeState<String?>(null)
        private set

    private var pinnedBrowseJob: Job? = null

    val pinnedHomeSections: List<PinnedHomeSection>
        get() = store.preferences.value.pinnedHomeSections

    fun addPinnedHomeSection(section: PinnedHomeSection) {
        editor.edit { addPinnedHomeSection(section) }
    }

    fun removePinnedHomeSection(sectionId: String) {
        editor.edit { removePinnedHomeSection(sectionId) }
    }

    fun setPinnedHomeSections(sections: List<PinnedHomeSection>) {
        editor.edit { setPinnedHomeSections(sections) }
    }

    /** Moves a pinned section from [from] to [to], clamped to valid bounds. */
    fun movePinnedHomeSection(from: Int, to: Int) {
        val current = store.preferences.value.pinnedHomeSections.toMutableList()
        if (from !in current.indices || to !in current.indices) return
        val moved = current.removeAt(from)
        current.add(to, moved)
        setPinnedHomeSections(current)
    }

    /**
     * Loads the browseable list of pinnable sources for the given [type]. For
     * FAVORITES the list is a single sentinel option (favorites is a server-side
     * filter, not a discrete item) so the picker can confirm in one tap.
     */
    fun loadPinnableOptions(type: PinnedSectionType) {
        pinnedBrowseJob?.cancel()
        pinnedBrowseJob = launch {
            pinnedBrowseLoading = true
            pinnedBrowseError = null
            val result = runCatching {
                when (type) {
                    PinnedSectionType.COLLECTION ->
                        mediaRepository.getMediaItems(
                            mediaTypes = listOf(MediaType.COLLECTION),
                            limit = 100,
                        ).getOrDefault(SearchResult(emptyList(), 0, 0))
                            .items.map { PinnableOption(it.id, it.name) }

                    PinnedSectionType.PLAYLIST ->
                        mediaRepository.getPlaylists(limit = 100).getOrDefault(emptyList())
                            .map { PinnableOption(it.id, it.name, "${it.itemCount} items") }

                    PinnedSectionType.GENRE ->
                        mediaRepository.getGenres().getOrDefault(emptyList())
                            .map { PinnableOption(it.id, it.name) }

                    PinnedSectionType.STUDIO ->
                        mediaRepository.getStudios().getOrDefault(emptyList())
                            .map { PinnableOption(it.id, it.name) }

                    PinnedSectionType.FAVORITES ->
                        listOf(PinnableOption(
                            PinnedHomeSection.FAVORITES_SOURCE_ID,
                            "Favorites",
                            "All your favorited items",
                        ))
                }
            }
            result.onSuccess {
                pinnedBrowseOptions = it
                pinnedBrowseLoading = false
            }.onFailure { throwable ->
                pinnedBrowseOptions = emptyList()
                pinnedBrowseError = throwable.message ?: throwable::class.simpleName
                pinnedBrowseLoading = false
            }
        }
    }

    fun clearPinnedBrowse() {
        pinnedBrowseJob?.cancel()
        pinnedBrowseOptions = emptyList()
        pinnedBrowseLoading = false
        pinnedBrowseError = null
    }

    // ----- Home layout presets (save / load / import / export / reset) ----

    val homeLayoutPresets: List<HomeLayoutPreset>
        get() = store.preferences.value.homeLayoutPresets

    var presetImportError by composeState<String?>(null)
        private set

    /** Snapshots the current home-screen layout into a named preset and saves it. */
    fun saveCurrentLayoutAsPreset(name: String, idOverride: String? = null) {
        val config = currentLayoutConfig()
        val preset = HomeLayoutPreset(
            id = idOverride ?: java.util.UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Preset" },
            config = config,
        )
        editor.edit { saveHomeLayoutPreset(preset) }
    }

    /** Applies a preset's layout to the current preferences. */
    fun applyPreset(config: HomeLayoutConfig) {
        editor.edit {
            setEnabledHomeSectionTypes(config.enabledHomeSectionTypes)
            setHomeSectionOrder(config.homeSectionOrder)
            setHiddenLibrarySectionIds(config.hiddenLibrarySectionIds)
            setMergeContinueWatchingAndNextUp(config.mergeContinueWatchingAndNextUp)
            setNextUpMaxDays(config.nextUpMaxDays)
            setNextUpRewatching(config.nextUpRewatching)
            setPinnedHomeSections(config.pinnedHomeSections)
            setHomeHeroEnabled(config.homeHeroEnabled)
            setContinueWatchingClickBehavior(config.continueWatchingClickBehavior)
        }
    }

    fun deleteHomeLayoutPreset(presetId: String) {
        editor.edit { deleteHomeLayoutPreset(presetId) }
    }

    /** Serializes a preset to a shareable pretty-printed JSON string. */
    fun exportPresetJson(preset: HomeLayoutPreset): String =
        PreferencesJson.export.encodeToString(HomeLayoutPreset.serializer(), preset)

    /** Serializes the *current* layout (without saving) for quick sharing. */
    fun exportCurrentLayoutJson(): String {
        val config = currentLayoutConfig()
        return PreferencesJson.export.encodeToString(HomeLayoutConfig.serializer(), config)
    }

    /**
     * Parses pasted/imported JSON. Accepts either a full [HomeLayoutPreset] or
     * a bare [HomeLayoutConfig]. Returns the parsed config (and optional name
     * when a full preset was supplied).
     */
    fun importPresetFromJson(
        raw: String,
        onResult: (Result<Pair<HomeLayoutConfig, String?>>) -> Unit,
    ) {
        launch {
            val result = runCatching {
                val text = raw.trim()
                val parser = PreferencesJson.import
                if (text.contains("\"config\"")) {
                    val preset = parser.decodeFromString<HomeLayoutPreset>(text)
                    preset.config to preset.name
                } else {
                    val config = parser.decodeFromString<HomeLayoutConfig>(text)
                    config to null
                }
            }
            presetImportError = result.exceptionOrNull()?.message
            onResult(result)
        }
    }

    fun clearPresetImportError() {
        presetImportError = null
    }

    /** Resets the home layout to factory defaults. */
    fun resetHomeLayout() {
        applyPreset(HomeLayoutConfig.DEFAULT)
    }

    private fun currentLayoutConfig(): HomeLayoutConfig {
        val prefs = store.preferences.value
        return HomeLayoutConfig(
            enabledHomeSectionTypes = prefs.enabledHomeSectionTypes,
            homeSectionOrder = prefs.homeSectionOrder,
            hiddenLibrarySectionIds = prefs.hiddenLibrarySectionIds,
            mergeContinueWatchingAndNextUp = prefs.mergeContinueWatchingAndNextUp,
            nextUpMaxDays = prefs.nextUpMaxDays,
            nextUpRewatching = prefs.nextUpRewatching,
            pinnedHomeSections = prefs.pinnedHomeSections,
            homeHeroEnabled = prefs.homeHeroEnabled,
            continueWatchingClickBehavior = prefs.continueWatchingClickBehavior,
        )
    }
}
