package com.raulshma.jellyplay

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.feature.home.KidsHomeScreen
import org.junit.Rule
import org.junit.Test

class KidsHomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun kidsHomeScreen_showsSections() {
        val sections = listOf(
            HomeSection(
                title = "Continue Watching",
                type = HomeSectionType.CONTINUE_WATCHING,
                items = listOf(
                    MediaItem(id = "1", name = "Movie One", mediaType = MediaType.MOVIE),
                    MediaItem(id = "2", name = "Movie Two", mediaType = MediaType.MOVIE),
                ),
            ),
            HomeSection(
                title = "Next Up",
                type = HomeSectionType.NEXT_UP,
                items = listOf(
                    MediaItem(id = "3", name = "Show One", mediaType = MediaType.SERIES),
                ),
            ),
        )

        composeTestRule.setContent {
            KidsHomeScreen(
                sections = sections,
                favorites = emptyList(),
                isLoading = false,
                error = null,
                imageUrlBuilder = { "" },
                onItemClick = {},
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithText("Kids Corner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue Watching").assertIsDisplayed()
        composeTestRule.onNodeWithText("Movie One").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next Up").assertIsDisplayed()
        composeTestRule.onNodeWithText("Show One").assertIsDisplayed()
    }

    @Test
    fun kidsHomeScreen_showsFavorites() {
        val favorites = listOf(
            MediaItem(id = "10", name = "Favorite Movie", mediaType = MediaType.MOVIE, isFavorite = true),
        )

        composeTestRule.setContent {
            KidsHomeScreen(
                sections = emptyList(),
                favorites = favorites,
                isLoading = false,
                error = null,
                imageUrlBuilder = { "" },
                onItemClick = {},
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithText("My Favorites").assertIsDisplayed()
        composeTestRule.onNodeWithText("Favorite Movie").assertIsDisplayed()
    }

    @Test
    fun kidsHomeScreen_showsSurpriseMe() {
        val sections = listOf(
            HomeSection(
                title = "Latest",
                type = HomeSectionType.LATEST_MEDIA,
                items = listOf(
                    MediaItem(id = "1", name = "Surprise Movie", mediaType = MediaType.MOVIE, genres = listOf("Animation")),
                ),
            ),
        )

        composeTestRule.setContent {
            KidsHomeScreen(
                sections = sections,
                favorites = emptyList(),
                isLoading = false,
                error = null,
                imageUrlBuilder = { "" },
                onItemClick = {},
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Surprise Me").performClick()
        composeTestRule.onNodeWithText("Surprise Me!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Surprise Movie").assertIsDisplayed()
    }

    @Test
    fun kidsHomeScreen_showsGenreChips() {
        val sections = listOf(
            HomeSection(
                title = "Latest",
                type = HomeSectionType.LATEST_MEDIA,
                items = listOf(
                    MediaItem(id = "1", name = "Anim Movie", mediaType = MediaType.MOVIE, genres = listOf("Animation")),
                    MediaItem(id = "2", name = "Adv Movie", mediaType = MediaType.MOVIE, genres = listOf("Adventure")),
                ),
            ),
        )

        composeTestRule.setContent {
            KidsHomeScreen(
                sections = sections,
                favorites = emptyList(),
                isLoading = false,
                error = null,
                imageUrlBuilder = { "" },
                onItemClick = {},
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithText("Animation").assertIsDisplayed()
        composeTestRule.onNodeWithText("Adventure").assertIsDisplayed()
    }

    @Test
    fun kidsHomeScreen_showsError() {
        composeTestRule.setContent {
            KidsHomeScreen(
                sections = emptyList(),
                favorites = emptyList(),
                isLoading = false,
                error = "Something went wrong",
                imageUrlBuilder = { "" },
                onItemClick = {},
                onRefresh = {},
            )
        }

        composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }
}
