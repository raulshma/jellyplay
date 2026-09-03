package com.raulshma.jellyplay.feature.home

import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.model.UserInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the pure avatar helpers of [HomeUserSwitchMenu] — the seam both the
 * app-bar chip and the quick user-switcher share (the "THE avatar palette
 * helpers" single-home contract):
 *
 * 1. [userAvatarUrl] emits the bearer-less `/Users/{id}/Images/Primary` URL
 *    with the SDK param order (maxWidth then tag), normalizes compact 32-hex
 *    ids to dashed, trims a trailing server slash, and collapses the builder's
 *    "" refusals (no address / non-GUID id) to null so the initials avatar
 *    renders.
 * 2. [avatarColorsFor] picks the container AND matching on-container color at
 *    the SAME hash-derived index — the pairing (not the hash itself) is the
 *    invariant the initials avatar legibility depends on.
 * 3. [avatarColorPair] maps the M3 scheme triplet in canonical
 *    primary → secondary → tertiary order.
 */
class HomeUserSwitchMenuTest {

    private val dashedGuid = "d27f3684-c285-863a-c935-d847f16548d1"
    private val compactGuid = "d27f3684c285863ac935d847f16548d1"

    private fun user(
        id: String = dashedGuid,
        serverAddress: String = "http://server",
        primaryImageTag: String? = null,
    ) = UserInfo(
        id = id,
        name = "Tester",
        serverAddress = serverAddress,
        accessToken = "token",
        primaryImageTag = primaryImageTag,
    )

    // ── userAvatarUrl ───────────────────────────────────────────────────────

    @Test
    fun userAvatarUrl_dashedGuid_buildsBearinglessPrimaryUrlWithParamsInSdkOrder() {
        val url = userAvatarUrl(user(primaryImageTag = "tag1"))
        assertEquals(
            "http://server/Users/$dashedGuid/Images/Primary?maxWidth=96&tag=tag1",
            url,
        )
    }

    @Test
    fun userAvatarUrl_compactHexId_isNormalizedToDashed() {
        val url = userAvatarUrl(user(id = compactGuid))
        assertEquals(
            "http://server/Users/$dashedGuid/Images/Primary?maxWidth=96",
            url,
        )
    }

    @Test
    fun userAvatarUrl_taglessUser_omitsTagParam() {
        val url = userAvatarUrl(user())
        // A tag-less URL is fully valid (cache-busting only) — no empty tag param.
        assertEquals("http://server/Users/$dashedGuid/Images/Primary?maxWidth=96", url)
    }

    @Test
    fun userAvatarUrl_trailingSlashOnServer_isTrimmed() {
        val url = userAvatarUrl(user(serverAddress = "http://server/"))
        assertEquals("http://server/Users/$dashedGuid/Images/Primary?maxWidth=96", url)
    }

    @Test
    fun userAvatarUrl_customMaxWidth_isPassedThrough() {
        val url = userAvatarUrl(user(), maxWidth = 256)
        assertEquals("http://server/Users/$dashedGuid/Images/Primary?maxWidth=256", url)
    }

    @Test
    fun userAvatarUrl_blankServerAddress_collapsesToNull() {
        // "" → the initials avatar must render instead of a dead URL.
        assertNull(userAvatarUrl(user(serverAddress = "")))
        assertNull(userAvatarUrl(user(serverAddress = "   ")))
    }

    @Test
    fun userAvatarUrl_nonGuidId_collapsesToNull() {
        assertNull(userAvatarUrl(user(id = "local-user-1")))
    }

    // ── avatarColorsFor ─────────────────────────────────────────────────────

    private val containers = listOf(
        Color(0xFF000001),
        Color(0xFF000002),
        Color(0xFF000003),
    )
    private val onContainers = listOf(
        Color(0xFF100001),
        Color(0xFF100002),
        Color(0xFF100003),
    )

    @Test
    fun avatarColorsFor_picksContainerAndOnContainerAtTheSameIndex() {
        for (name in listOf("Tester", "Alice", "Bob", "", "Ø", "very-long-name-用户")) {
            val (bg, fg) = avatarColorsFor(name, containers, onContainers)
            val index = containers.indexOf(bg)
            assertTrue(index in containers.indices, "$name picked an out-of-list container")
            assertEquals(onContainers[index], fg, "$name's on-color must pair with its container index")
        }
    }

    @Test
    fun avatarColorsFor_isStablePerName() {
        val first = avatarColorsFor("Alice", containers, onContainers)
        val second = avatarColorsFor("Alice", containers, onContainers)
        assertEquals(first, second)
    }

    @Test
    fun avatarColorsFor_coversEveryBucketAcrossAUserSet() {
        // The hash-mod over a 3-triplet must not collapse the whole user list
        // onto one bucket — a diverse name set exercises more than one index.
        val indices = listOf("Alice", "Bob", "Carol", "Dave", "Eve").map { name ->
            containers.indexOf(avatarColorsFor(name, containers, onContainers).first)
        }
        assertTrue(indices.toSet().size > 1, "every name hashed to the same bucket: $indices")
    }

    // ── avatarColorPair ─────────────────────────────────────────────────────

    @Test
    fun avatarColorPair_mapsSchemeTripletInPrimarySecondaryTertiaryOrder() {
        val c1 = Color(0xFF200001)
        val c2 = Color(0xFF200002)
        val c3 = Color(0xFF200003)
        val o1 = Color(0xFF300001)
        val o2 = Color(0xFF300002)
        val o3 = Color(0xFF300003)
        val scheme = androidx.compose.material3.lightColorScheme(
            primaryContainer = c1,
            secondaryContainer = c2,
            tertiaryContainer = c3,
            onPrimaryContainer = o1,
            onSecondaryContainer = o2,
            onTertiaryContainer = o3,
        )

        val (containersOut, onContainersOut) = avatarColorPair(scheme)

        assertEquals(listOf(c1, c2, c3), containersOut)
        assertEquals(listOf(o1, o2, o3), onContainersOut)
    }
}
