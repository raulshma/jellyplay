package com.raulshma.jellyplay.feature.admin.users.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.UserEditorContext
import com.raulshma.jellyplay.core.testfixtures.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric regression for the Android crash on the user-detail tab switch
 * (Access / Parental / Account). The pager's prefetcher composes the adjacent
 * pages during ANY tab animation, so one poisoned page killed every switch:
 * `MaxParentalRatingField` called the deprecated no-arg
 * `Modifier.menuAnchor()` inside `ExposedDropdownMenuBox`, which this module
 * compiled against androidx material3 1.5.0-alpha22 (via the pinned JB
 * compose-multiplatform redirect) but the app ships material3 1.5.0-beta02 —
 * the overload was removed in between, so the page threw NoSuchMethodError on
 * first composition. Desktop never sees it (JB material3 artifacts are
 * consistent there; the desktop twin of this suite, UserDetailTabSwitchUiTest,
 * was green throughout).
 *
 * KmpLibraryComposePlugin's material3 alignment constraint resolves this
 * lane's classpath to the androidx material3 the :app actually ships
 * (catalog composeMaterial3) — the same version the APK carries — so this
 * test remains the link check across any future compile/runtime drift, not
 * another composition smoke test.
 *
 * Each tab pins a sentinel string unique to its content, so the test
 * verifies the page actually composed (not merely that the pager moved):
 *
 *  - Access   → "Library access"
 *  - Parental → "Max parental rating"
 *  - Account  → "Sign-in"
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UserDetailTabSwitchHostTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** UserDetailViewModel launches on viewModelScope (Dispatchers.Main). */
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private lateinit var adminRepository: AdminRepository

    @Before
    fun setUp() {
        adminRepository = mockk(relaxed = true)
        coEvery { adminRepository.getUserEditorContext("u-admin") } returns Result.success(
            UserEditorContext(
                user = ManagedUser(id = "u-admin", name = "Alice", policy = ManagedUserPolicy(isAdministrator = true)),
                libraries = listOf(LibraryFolder(id = "lib-1", name = "Movies")),
                currentUserId = "someone-else",
                adminCount = 1,
            )
        )
        coEvery { adminRepository.getDevices() } returns Result.success(emptyList())
        coEvery { adminRepository.getLiveTvChannels(any()) } returns Result.success(emptyList())
        coEvery { adminRepository.getParentalRatings() } returns Result.success(emptyList())
        coEvery { adminRepository.getTags(any()) } returns Result.success(emptyList())
    }

    /** Compose the screen and wait until the user has loaded (title reads "Alice"). */
    private fun openUserDetail() {
        composeTestRule.setContent {
            MaterialTheme {
                UserDetailScreen(
                    userId = "u-admin",
                    onBack = {},
                    viewModel = UserDetailViewModel(adminRepository),
                )
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Alice").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Click a tab and require the target page to actually compose. */
    private fun clickTabAndAssertComposed(label: String, sentinel: String) {
        composeTestRule.onNodeWithText(label).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(sentinel).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(sentinel).assertExists()
    }

    @Test
    fun `switching to Access tab composes without crashing`() {
        openUserDetail()
        clickTabAndAssertComposed("Access", "Library access")
    }

    @Test
    fun `switching to Parental tab composes without crashing`() {
        openUserDetail()
        clickTabAndAssertComposed("Parental", "Max parental rating")
    }

    @Test
    fun `switching to Account tab composes without crashing`() {
        openUserDetail()
        clickTabAndAssertComposed("Account", "Sign-in")
    }
}
