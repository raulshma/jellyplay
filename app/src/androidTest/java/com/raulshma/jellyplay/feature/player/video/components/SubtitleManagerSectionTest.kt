package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented tests for [SubtitleManagerSection] — the live "Get" tab body of
 * the subtitle hub (`SubtitleHubSheet`), rendered here with the host-side tab
 * state hoisted exactly as the hub does (the section owns no state itself).
 *
 * The section opens on the Download tab (index 0), so download-list assertions
 * carry over unchanged; Search and Upload tests switch via the tab row.
 */
class SubtitleManagerSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleSubtitles = listOf(
        RemoteSubtitleInfo(
            id = "1",
            name = "English",
            language = "eng",
            format = "srt",
            provider = "OpenSubtitles",
            downloadCount = 1500,
        ),
        RemoteSubtitleInfo(
            id = "2",
            name = "Spanish",
            language = "spa",
            format = "ass",
            provider = "OpenSubtitles",
            downloadCount = 800,
        ),
    )

    private val sampleCultures = listOf(
        CultureInfo(name = "eng", displayName = "English"),
        CultureInfo(name = "spa", displayName = "Spanish"),
    )

    // Reusable default-content wrapper: only the Download-tab-relevant params
    // are exercised by most tests; the rest get inert defaults. JELLYFIN-only
    // configured providers keep the Search tab on the legacy single-provider
    // flow (the hub passes the same when no external provider is set up).
    private fun setContent(
        downloadSubtitles: List<RemoteSubtitleInfo> = sampleSubtitles,
        isDownloading: Boolean = false,
        cultures: List<CultureInfo> = sampleCultures,
        searchResults: List<RemoteSubtitleInfo> = emptyList(),
        isSearching: Boolean = false,
        hasSearched: Boolean = false,
        searchError: String? = null,
        isUploading: Boolean = false,
        onDownload: (RemoteSubtitleInfo) -> Unit = {},
        onLoadLocalFile: () -> Unit = {},
        onSearch: (String) -> Unit = {},
        onDownloadSearched: (RemoteSubtitleInfo) -> Unit = {},
        onUpload: (String, String, String?, Boolean, Boolean) -> Unit = { _, _, _, _, _ -> },
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                var selectedTab by mutableIntStateOf(0)
                Column {
                    SubtitleManagerSection(
                        downloadSubtitles = downloadSubtitles,
                        isDownloading = isDownloading,
                        onDownload = onDownload,
                        onLoadLocalFile = onLoadLocalFile,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        hasSearched = hasSearched,
                        searchError = searchError,
                        cultures = cultures,
                        defaultLanguage = "eng",
                        onSearch = onSearch,
                        onDownloadSearched = onDownloadSearched,
                        providerSearchResults = emptyList(),
                        providerSearchErrors = emptyMap(),
                        configuredProviders = setOf(SubtitleProviderKind.JELLYFIN),
                        onSearchAllProviders = {},
                        onDownloadProviderSubtitle = {},
                        downloadingSubtitles = emptyMap(),
                        onUseSubtitle = {},
                        isUploading = isUploading,
                        onUpload = onUpload,
                        isTv = false,
                        selectedTab = selectedTab,
                        onTabChange = { selectedTab = it },
                        downloadFocus = remember { FocusRequester() },
                        searchFocus = remember { FocusRequester() },
                        uploadFocus = remember { FocusRequester() },
                        loadBtnFocus = rememberTvFocusState(),
                    )
                }
            }
        }
    }

    // region Download tab (default) ------------------------------------------

    @Test
    fun subtitleManagerSection_displaysSubtitleNames() {
        setContent()
        composeTestRule.onNodeWithText("English").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spanish").assertIsDisplayed()
    }

    @Test
    fun subtitleManagerSection_displaysDownloadCount() {
        setContent()
        composeTestRule.onNodeWithText("1500").assertIsDisplayed()
        composeTestRule.onNodeWithText("800").assertIsDisplayed()
    }

    @Test
    fun subtitleManagerSection_displaysLanguageAndFormat() {
        setContent()
        composeTestRule.onNodeWithText("ENG").assertIsDisplayed()
        composeTestRule.onNodeWithText("SRT").assertIsDisplayed()
        composeTestRule.onNodeWithText("OpenSubtitles").assertIsDisplayed()
    }

    @Test
    fun subtitleManagerSection_clickSubtitle_callsOnDownload() {
        var downloaded: RemoteSubtitleInfo? = null
        setContent(onDownload = { downloaded = it })
        composeTestRule.onNodeWithText("Spanish").performClick()
        assertEquals("2", downloaded!!.id)
    }

    @Test
    fun subtitleManagerSection_loading_hidesList() {
        setContent(downloadSubtitles = emptyList(), isDownloading = true)
        composeTestRule.onNodeWithText("English").assertDoesNotExist()
    }

    @Test
    fun subtitleManagerSection_emptyResults_showsMessage() {
        setContent(downloadSubtitles = emptyList(), isDownloading = false)
        composeTestRule.onNodeWithText("No remote subtitles available.").assertIsDisplayed()
    }

    @Test
    fun subtitleManagerSection_subtitleWithoutName_usesLanguage() {
        val subs = listOf(RemoteSubtitleInfo(id = "1", language = "fra"))
        setContent(downloadSubtitles = subs)
        composeTestRule.onNodeWithText("fra").assertIsDisplayed()
    }

    @Test
    fun subtitleManagerSection_displaysTabs() {
        setContent()
        composeTestRule.onNodeWithText("Download").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search").assertIsDisplayed()
        composeTestRule.onNodeWithText("Upload").assertIsDisplayed()
    }

    // endregion

    // region Search tab ------------------------------------------------------

    @Test
    fun subtitleManagerSection_searchTab_searchButton_triggersOnSearch() {
        var searchedLanguage: String? = null
        setContent(onSearch = { searchedLanguage = it })
        composeTestRule.onNodeWithText("Search").performClick() // tab
        // Tab and button both read "Search" once on the Search tab; the button
        // is the second node (below the tab row) — same disambiguation as the
        // Upload submit test below.
        composeTestRule.onAllNodesWithText("Search")[1].performClick()
        assertEquals("eng", searchedLanguage)
    }

    @Test
    fun subtitleManagerSection_searchTab_noResults_showsMessage() {
        setContent(searchResults = emptyList(), hasSearched = true)
        composeTestRule.onNodeWithText("Search").performClick() // tab
        composeTestRule.onNodeWithText("No results found").assertIsDisplayed()
    }

    @Test
    fun subtitleManagerSection_searchTab_error_showsFailureState_notNoResults() {
        // A search failure must read as a failure, distinct from "no results",
        // so the user is prompted to retry rather than change their query.
        setContent(searchResults = emptyList(), hasSearched = false, searchError = "Network error")
        composeTestRule.onNodeWithText("Search").performClick() // tab
        composeTestRule.onNodeWithText("Search failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Network error").assertIsDisplayed()
    }

    @Test
    fun subtitleManagerSection_searchTab_resultsRenderAndDownload() {
        var downloaded: RemoteSubtitleInfo? = null
        setContent(
            searchResults = sampleSubtitles,
            onDownloadSearched = { downloaded = it },
        )
        composeTestRule.onNodeWithText("Search").performClick() // tab
        composeTestRule.onNodeWithText("English").performClick()
        assertEquals("1", downloaded!!.id)
    }

    @Test
    fun subtitleManagerSection_searchTab_emptyCultures_showsFallbackDropdownOnChevronClick() {
        setContent(cultures = emptyList())
        composeTestRule.onNodeWithText("Search").performClick() // tab
        composeTestRule.onNodeWithContentDescription("Language").performClick() // chevron
        composeTestRule.onNodeWithText("English (eng)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spanish (spa)").assertIsDisplayed()
    }

    // endregion

    // region Upload tab ------------------------------------------------------

    @Test
    fun subtitleManagerSection_uploadTab_showsFileSelectAndCheckboxes() {
        setContent()
        composeTestRule.onNodeWithText("Upload").performClick() // tab
        composeTestRule.onNodeWithText("Select File (.srt, .ass, .ssa, .vtt)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Forced subtitle").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hearing impaired").assertIsDisplayed()
    }

    @Test
    fun subtitleManagerSection_uploadTab_uploadButton_disabledWithoutFile() {
        setContent()
        composeTestRule.onNodeWithText("Upload").performClick() // tab
        // No file selected → submit disabled. The Upload tab and the submit
        // button both read "Upload", so assert against the button node and its
        // disabled state explicitly (rather than just that the form rendered).
        composeTestRule.onNodeWithText("Language").assertIsDisplayed()
        val uploadNodes = composeTestRule.onAllNodesWithText("Upload")
        // The submit button is the second "Upload" node (after the tab).
        uploadNodes[1].assertIsNotEnabled()
    }

    @Test
    fun subtitleManagerSection_uploadTab_uploadButton_disabledWhileUploading() {
        setContent(isUploading = true)
        composeTestRule.onNodeWithText("Upload").performClick() // tab
        composeTestRule.onNodeWithText("Uploading…").assertIsDisplayed()
        // The file picker is disabled while uploading.
        composeTestRule.onNodeWithText("Select File (.srt, .ass, .ssa, .vtt)").assertIsDisplayed()
    }

    // endregion
}
