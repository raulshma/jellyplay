@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.raulshma.jellyplay.feature.admin.users.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.UserEditorContext
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Desktop compose UI test for the user-detail tab switch: open the real
 * [UserDetailScreen] against a stubbed [AdminRepository], then drive each
 * tab click the way a user does. Any exception escaping the newly-composed
 * pager page fails the test — this is the red loop for the "crash when
 * switching to Access / Parental / Account" report.
 *
 * Each tab pins a sentinel string unique to its content, so the test
 * verifies the page actually composed (not merely that the pager moved):
 *
 *  - Access   → "Library access"
 *  - Parental → "Max parental rating"
 *  - Account  → "Sign-in"
 *
 * The ViewModel launches on [androidx.lifecycle.viewModelScope]
 * (Dispatchers.Main), which desktop ui tests don't provide — setMain to an
 * unconfined test dispatcher makes VM coroutines run eagerly on the test
 * thread (same lane's MainDispatcherRule precedent, without the JUnit rule).
 */
class UserDetailTabSwitchUiTest {

    private lateinit var adminRepository: AdminRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Compose the screen and wait until the user has loaded (title reads "Alice"). */
    private fun ComposeUiTest.openUserDetail() {
        setContent {
            MaterialTheme {
                UserDetailScreen(
                    userId = "u-admin",
                    onBack = {},
                    viewModel = UserDetailViewModel(adminRepository),
                )
            }
        }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Alice").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Click a tab and require the target page to actually compose. */
    private fun ComposeUiTest.clickTabAndAssertComposed(label: String, sentinel: String) {
        onNodeWithText(label).performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText(sentinel).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(sentinel).assertExists()
    }

    @Test
    fun `switching to Access tab composes without crashing`() = runComposeUiTest {
        openUserDetail()
        clickTabAndAssertComposed("Access", "Library access")
    }

    @Test
    fun `switching to Parental tab composes without crashing`() = runComposeUiTest {
        openUserDetail()
        clickTabAndAssertComposed("Parental", "Max parental rating")
    }

    @Test
    fun `switching to Account tab composes without crashing`() = runComposeUiTest {
        openUserDetail()
        clickTabAndAssertComposed("Account", "Sign-in")
    }

    @Test
    fun `switching to Access and back to Profile composes without crashing`() = runComposeUiTest {
        openUserDetail()
        clickTabAndAssertComposed("Access", "Library access")
        // Back to Profile: the Access page disposes once the pager settles
        // (beyondViewportPageCount defaults to 0), which pins the switch.
        onNodeWithText("Profile").performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Library access").fetchSemanticsNodes().isEmpty()
        }
        onNodeWithText("Library access").assertDoesNotExist()
    }
}
