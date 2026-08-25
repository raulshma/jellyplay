package com.raulshma.jellyplay.core.network.user

import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.SyncPlayAccessOption
import com.raulshma.jellyplay.core.model.UnratedItemOption
import com.raulshma.jellyplay.core.model.UserAccessSchedule
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the wasm user-management wire DTOs' PascalCase contract and the
 * DTO→core.model mapping semantics (mirrors the jvmShared JellyfinDtoMappers
 * / UserApiClientImpl behavior these tests substitute for): managed-user
 * mapping field-for-field, the policy overlay's merge rules, the
 * parental-rating grouping, and the password null→reset semantics. Decoding
 * runs through the same lenient Json configuration the wasm client uses.
 */
class UserWireMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun fullPolicyJson() = """
        {
          "IsAdministrator": true,
          "IsHidden": false,
          "IsDisabled": false,
          "EnableUserPreferenceAccess": true,
          "EnableCollectionManagement": true,
          "EnableSubtitleManagement": true,
          "EnableLyricManagement": true,
          "EnableSharedDeviceControl": true,
          "ForceRemoteSourceTranscoding": true,
          "RemoteClientBitrateLimit": 20000000,
          "SyncPlayAccess": "CreateAndJoinGroups",
          "EnableRemoteControlOfOtherUsers": true,
          "EnableRemoteAccess": true,
          "EnableLiveTvManagement": true,
          "EnableLiveTvAccess": true,
          "EnableMediaPlayback": true,
          "EnableAudioPlaybackTranscoding": true,
          "EnableVideoPlaybackTranscoding": true,
          "EnablePlaybackRemuxing": true,
          "EnableContentDeletion": true,
          "EnableContentDownloading": true,
          "MaxParentalRating": 13,
          "MaxParentalSubRating": 2,
          "MaxActiveSessions": 3,
          "LoginAttemptsBeforeLockout": 5,
          "EnableAllFolders": false,
          "EnabledFolders": ["folder-1", "folder-2"],
          "EnableAllChannels": false,
          "EnabledChannels": ["ch-1"],
          "EnableAllDevices": false,
          "EnabledDevices": ["dev-1"],
          "EnableContentDeletionFromFolders": ["df-1"],
          "BlockUnratedItems": ["Movie", "LiveTvProgram", "WeirdFutureItem"],
          "AllowedTags": ["tag-a"],
          "BlockedTags": ["tag-b"],
          "AccessSchedules": [
            {"Id": 7, "UserId": "old-owner", "DayOfWeek": "Wednesday",
             "StartHour": 8.0, "EndHour": 22.5}
          ],
          "InvalidLoginAttemptCount": 2,
          "EnablePublicSharing": true,
          "BlockedMediaFolders": ["bmf-1"],
          "BlockedChannels": ["bc-1"],
          "EnableSyncTranscoding": true,
          "EnableMediaConversion": true,
          "AuthenticationProviderId": "auth-provider",
          "PasswordResetProviderId": "reset-provider",
          "UnknownFutureField": 42
        }
    """.trimIndent()

    private fun fullUserJson() = """
        {
          "Name": "alice",
          "ServerId": "srv-1",
          "ServerName": "media",
          "Id": "a1b2c3d4",
          "PrimaryImageTag": "img-tag",
          "HasPassword": true,
          "HasConfiguredPassword": true,
          "HasConfiguredEasyPassword": false,
          "EnableAutoLogin": false,
          "LastLoginDate": "2026-08-01T10:00:00.0000000Z",
          "LastActivityDate": "2026-08-02T11:30:00.0000000Z",
          "Configuration": {"GroupedFolders": ["f1"], "PreferredLanguage": "en"},
          "Policy": ${fullPolicyJson()},
          "PrimaryImageAspectRatio": 0.67,
          "UnknownFutureField": {"ignored": true}
        }
    """.trimIndent()

    // ── UserDto wire + toManagedUser ───────────────────────────────────────

    @Test
    fun `full user dto decodes PascalCase wire and ignores unknown keys`() {
        val user = json.decodeFromString<ManagedUserDtoWire>(fullUserJson())

        assertEquals("a1b2c3d4", user.id)
        assertEquals("alice", user.name)
        assertEquals("img-tag", user.primaryImageTag)
        assertEquals(true, user.hasPassword)
        assertEquals(true, user.hasConfiguredPassword)
        assertEquals("2026-08-01T10:00:00.0000000Z", user.lastLoginDate)
        assertEquals("2026-08-02T11:30:00.0000000Z", user.lastActivityDate)
        assertEquals(true, user.policy?.isAdministrator)
        assertEquals(2, user.policy?.invalidLoginAttemptCount)
    }

    @Test
    fun `toManagedUser maps field-for-field like the jvmShared mapper`() {
        val managed = json.decodeFromString<ManagedUserDtoWire>(fullUserJson()).toManagedUser()

        assertEquals("a1b2c3d4", managed.id)
        assertEquals("alice", managed.name)
        assertEquals("img-tag", managed.primaryImageTag)
        assertEquals(true, managed.hasPassword)
        assertEquals(true, managed.hasConfiguredPassword)
        assertEquals("2026-08-01T10:00:00.0000000Z", managed.lastLoginDate)
        assertEquals("2026-08-02T11:30:00.0000000Z", managed.lastActivityDate)
        // Policy spot checks — full parity pinned by the policy tests below.
        assertEquals(true, managed.policy.isAdministrator)
        assertEquals(listOf("folder-1", "folder-2"), managed.policy.enabledFolders)
        assertEquals(SyncPlayAccessOption.CREATE_AND_JOIN, managed.policy.syncPlayAccess)
    }

    @Test
    fun `missing policy falls back to the model default`() {
        val managed = json.decodeFromString<ManagedUserDtoWire>(
            """{"Id":"u1"}""",
        ).toManagedUser()

        assertEquals(ManagedUserPolicy(), managed.policy, "null Policy maps to ManagedUserPolicy()")
        assertEquals("", managed.name, "null name maps to empty string (JVM: name ?: \"\")")
    }

    // ── Policy mapping (toManagedPolicy) ───────────────────────────────────

    @Test
    fun `toManagedPolicy maps every field with the SDK enum semantics`() {
        val policy = json.decodeFromString<ManagedUserPolicyDtoWire>(fullPolicyJson()).toManagedPolicy()

        assertEquals(true, policy.isAdministrator)
        assertEquals(false, policy.isHidden)
        assertEquals(false, policy.isDisabled)
        assertEquals(true, policy.enableUserPreferenceAccess)
        assertEquals(false, policy.enableAllFolders)
        assertEquals(listOf("folder-1", "folder-2"), policy.enabledFolders)
        assertEquals(true, policy.enableMediaPlayback)
        assertEquals(true, policy.enableAudioPlaybackTranscoding)
        assertEquals(true, policy.enableVideoPlaybackTranscoding)
        assertEquals(true, policy.enablePlaybackRemuxing)
        assertEquals(true, policy.enableContentDeletion)
        assertEquals(true, policy.enableContentDownloading)
        assertEquals(true, policy.enableLiveTvAccess)
        assertEquals(true, policy.enableLiveTvManagement)
        assertEquals(true, policy.enableRemoteControlOfOtherUsers)
        assertEquals(true, policy.enableRemoteAccess)
        assertEquals(13, policy.maxParentalRating)
        assertEquals(2, policy.maxParentalSubRating)
        assertEquals(3, policy.maxActiveSessions)
        assertEquals(5, policy.loginAttemptsBeforeLockout)
        assertEquals(true, policy.enableCollectionManagement)
        assertEquals(true, policy.enableSubtitleManagement)
        assertEquals(true, policy.forceRemoteSourceTranscoding)
        assertEquals(true, policy.enableSharedDeviceControl)
        assertEquals(20_000_000, policy.remoteClientBitrateLimit)
        assertEquals(SyncPlayAccessOption.CREATE_AND_JOIN, policy.syncPlayAccess)
        assertEquals(false, policy.enableAllChannels)
        assertEquals(listOf("ch-1"), policy.enabledChannels)
        assertEquals(false, policy.enableAllDevices)
        assertEquals(listOf("dev-1"), policy.enabledDevices)
        assertEquals(listOf("df-1"), policy.enableContentDeletionFromFolders)
        assertEquals(listOf("tag-a"), policy.allowedTags)
        assertEquals(listOf("tag-b"), policy.blockedTags)
        // LiveTvProgram (not in the app enum) and unknown names take the
        // jvmShared MOVIE safe default; Movie maps 1:1.
        assertEquals(
            listOf(UnratedItemOption.MOVIE, UnratedItemOption.MOVIE, UnratedItemOption.MOVIE),
            policy.blockUnratedItems,
        )
        assertEquals(
            listOf(UserAccessSchedule(id = 7, dayOfWeek = "Wednesday", startHour = 8.0, endHour = 22.5)),
            policy.accessSchedules,
        )
    }

    @Test
    fun `empty wire policy equals the jvmShared fallback construction`() {
        // The all-null server-policy fallback UserApiClientImpl builds: every
        // permission off, lockout -1, provider ids empty, SyncPlayAccess NONE.
        val fallback = json.decodeFromString<ManagedUserPolicyDtoWire>("{}")

        assertEquals(false, fallback.isAdministrator)
        assertEquals(false, fallback.enableUserPreferenceAccess)
        assertEquals(false, fallback.enableAllFolders)
        assertEquals(-1, fallback.loginAttemptsBeforeLockout)
        assertEquals(0, fallback.maxActiveSessions)
        assertEquals(0, fallback.remoteClientBitrateLimit)
        assertEquals("", fallback.authenticationProviderId)
        assertEquals("", fallback.passwordResetProviderId)
        assertEquals("None", fallback.syncPlayAccess)

        val mapped = fallback.toManagedPolicy()
        assertEquals(SyncPlayAccessOption.NONE, mapped.syncPlayAccess)
        assertEquals(-1, mapped.loginAttemptsBeforeLockout)
        assertEquals(emptyList(), mapped.enabledFolders)
    }

    @Test
    fun `policy wire round-trips through PascalCase encoding`() {
        val decoded = json.decodeFromString<ManagedUserPolicyDtoWire>(fullPolicyJson())
        val reDecoded = json.decodeFromString<ManagedUserPolicyDtoWire>(json.encodeToString(decoded))

        assertEquals(decoded, reDecoded, "decode → encode → decode must be lossless")
    }

    // ── Policy overlay (updateUserPolicy merge) ────────────────────────────

    @Test
    fun `overlayWith replaces every edited field and keeps bookkeeping`() {
        val serverPolicy = json.decodeFromString<ManagedUserPolicyDtoWire>(fullPolicyJson())
        val edited = ManagedUserPolicy(
            isAdministrator = false,
            enableAllFolders = true,
            maxParentalRating = 7,
            syncPlayAccess = SyncPlayAccessOption.JOIN_ONLY,
            loginAttemptsBeforeLockout = 0,
            enabledFolders = listOf("f-new"),
            blockUnratedItems = listOf(UnratedItemOption.BOOK, UnratedItemOption.SERIES),
            accessSchedules = listOf(
                UserAccessSchedule(id = 1, dayOfWeek = "Everyday", startHour = 6.0, endHour = 23.0),
            ),
        )

        val merged = serverPolicy.overlayWith(edited, userId = "target-user")

        assertEquals(false, merged.isAdministrator, "edited isAdministrator wins")
        assertEquals(true, merged.enableAllFolders, "edited enableAllFolders wins")
        assertEquals(7, merged.maxParentalRating)
        assertEquals("JoinGroups", merged.syncPlayAccess)
        assertEquals(0, merged.loginAttemptsBeforeLockout, "edited lockout wins over server's 5")
        assertEquals(listOf("f-new"), merged.enabledFolders)
        assertEquals(listOf("Book", "Series"), merged.blockUnratedItems)
        assertEquals(
            listOf(
                AccessScheduleDtoWire(
                    id = 1, userId = "target-user", dayOfWeek = "Everyday",
                    startHour = 6.0, endHour = 23.0,
                ),
            ),
            merged.accessSchedules,
            "schedules re-stamp the target user id",
        )
        // Bookkeeping survives untouched.
        assertEquals(2, merged.invalidLoginAttemptCount)
        assertEquals("auth-provider", merged.authenticationProviderId)
        assertEquals("reset-provider", merged.passwordResetProviderId)
        assertEquals(true, merged.enablePublicSharing)
        assertEquals(listOf("bmf-1"), merged.blockedMediaFolders)
        assertEquals(listOf("bc-1"), merged.blockedChannels)
        assertEquals(true, merged.enableSyncTranscoding)
        assertEquals(true, merged.enableMediaConversion)
        assertEquals(true, merged.enableLyricManagement)
    }

    @Test
    fun `overlaying the model-default policy flips editable fields to the defaults`() {
        val serverPolicy = json.decodeFromString<ManagedUserPolicyDtoWire>(fullPolicyJson())
        val merged = serverPolicy.overlayWith(ManagedUserPolicy(), userId = "u1")

        assertEquals(true, merged.enableUserPreferenceAccess, "model default true beats server value")
        assertEquals(true, merged.enableAllFolders)
        assertEquals(true, merged.enableMediaPlayback)
        assertEquals("CreateAndJoinGroups", merged.syncPlayAccess)
        assertEquals(-1, merged.loginAttemptsBeforeLockout)
        assertNull(merged.maxParentalRating)
        assertEquals(emptyList(), merged.enabledFolders)
        assertEquals(2, merged.invalidLoginAttemptCount, "bookkeeping still preserved")
    }

    @Test
    fun `overlay on the missing-policy fallback matches the JVM null-policy path`() {
        val merged = ManagedUserPolicyDtoWire().overlayWith(ManagedUserPolicy(), userId = "u9")

        assertEquals(true, merged.enableUserPreferenceAccess)
        assertEquals(-1, merged.loginAttemptsBeforeLockout, "edited default (-1) overlays the fallback's own -1")
        assertEquals("CreateAndJoinGroups", merged.syncPlayAccess)
        assertEquals("", merged.authenticationProviderId)
        assertEquals(emptyList(), merged.accessSchedules)
    }

    // ── Parental ratings ────────────────────────────────────────────────────

    @Test
    fun `parental ratings keep the schema's lowercase score serial names`() {
        val dto = json.decodeFromString<ParentalRatingDtoWire>(
            """{"Name":"PG-13","Value":13,"RatingScore":{"score":13,"subScore":1}}""",
        )

        assertEquals("PG-13", dto.name)
        assertEquals(13, dto.value)
        assertEquals(13, dto.ratingScore?.score)
        assertEquals(1, dto.ratingScore?.subScore)
    }

    @Test
    fun `ratings group by score and subScore, join names and sort by score`() {
        val options = listOf(
            ParentalRatingDtoWire(name = "TV-MA", ratingScore = ParentalRatingScoreDtoWire(score = 17)),
            ParentalRatingDtoWire(name = "PG-13", ratingScore = ParentalRatingScoreDtoWire(score = 13, subScore = 1)),
            ParentalRatingDtoWire(name = "TV-14", ratingScore = ParentalRatingScoreDtoWire(score = 13, subScore = 1)),
            ParentalRatingDtoWire(name = "G", ratingScore = ParentalRatingScoreDtoWire(score = 0)),
            ParentalRatingDtoWire(name = "Bulgarian Unrated", ratingScore = null),
            ParentalRatingDtoWire(name = "PG", ratingScore = ParentalRatingScoreDtoWire(score = 7)),
        ).toParentalRatingOptions()

        assertEquals(
            listOf(
                com.raulshma.jellyplay.core.model.ParentalRatingOption(name = "G", score = 0, subScore = null),
                com.raulshma.jellyplay.core.model.ParentalRatingOption(name = "PG", score = 7, subScore = null),
                com.raulshma.jellyplay.core.model.ParentalRatingOption(name = "PG-13/TV-14", score = 13, subScore = 1),
                com.raulshma.jellyplay.core.model.ParentalRatingOption(name = "TV-MA", score = 17, subScore = null),
            ),
            options,
            "null-score entries drop, same (score, subScore) collapse, sorted ascending",
        )
    }

    @Test
    fun `same score with different subScore stays separate`() {
        val options = listOf(
            ParentalRatingDtoWire(name = "A", ratingScore = ParentalRatingScoreDtoWire(score = 10, subScore = 1)),
            ParentalRatingDtoWire(name = "B", ratingScore = ParentalRatingScoreDtoWire(score = 10, subScore = 2)),
        ).toParentalRatingOptions()

        assertEquals(2, options.size)
        assertEquals(1, options[0].subScore)
        assertEquals(2, options[1].subScore)
    }

    // ── Request bodies ──────────────────────────────────────────────────────

    @Test
    fun `create-user request serializes to PascalCase wire`() {
        assertEquals(
            """{"Name":"carol","Password":"secret"}""",
            json.encodeToString(CreateUserByNameRequestDtoWire(name = "carol", password = "secret")),
        )
        assertEquals(
            """{"Name":"no-pw"}""",
            json.encodeToString(CreateUserByNameRequestDtoWire(name = "no-pw", password = null)),
            "null password is omitted (SDK default-null field, encodeDefaults=false)",
        )
    }

    @Test
    fun `password null means reset and non-null means set`() {
        // UpdateUserPassword(currentPw = null, newPw = newPassword,
        // resetPassword = newPassword == null) — the jvmShared semantics.
        val setBody = json.encodeToString(
            UpdateUserPasswordRequestDtoWire(currentPw = null, newPw = "new-secret", resetPassword = false),
        )
        assertEquals("""{"NewPw":"new-secret","ResetPassword":false}""", setBody)

        val resetBody = json.encodeToString(
            UpdateUserPasswordRequestDtoWire(currentPw = null, newPw = null, resetPassword = true),
        )
        assertEquals("""{"ResetPassword":true}""", resetBody)
    }

    // ── Rename flow (GET-modify-POST, DTO level) ───────────────────────────

    @Test
    fun `rename copy changes only the name and preserves the rest of the DTO`() {
        val current = json.decodeFromString<ManagedUserDtoWire>(fullUserJson())
        val renamed = current.copy(name = "alicia")
        val reposted = json.decodeFromString<ManagedUserDtoWire>(json.encodeToString(renamed))

        assertEquals("alicia", reposted.name, "the POST body carries the new name")
        assertEquals(current.id, reposted.id)
        assertEquals(current.policy, reposted.policy, "full policy round-trips untouched")
        assertEquals(current.configuration, reposted.configuration, "raw Configuration element survives")
        assertEquals(current.hasPassword, reposted.hasPassword)
        assertEquals(current.lastLoginDate, reposted.lastLoginDate)
        assertTrue(
            json.encodeToString(renamed).contains("\"GroupedFolders\":[\"f1\"]"),
            "the server's own configuration bytes are re-POSTed verbatim",
        )
    }
}
