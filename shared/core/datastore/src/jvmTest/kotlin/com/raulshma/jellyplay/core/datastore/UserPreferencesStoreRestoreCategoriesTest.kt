package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies [UserPreferencesStore.restoreV2Categories] — the per-category import
 * path — and the [UserPreferencesStore.slicesForCategory] routing table it is
 * built on:
 *
 *  - every category maps to its documented slice keys, with co-owned slices
 *    (audio, appearance, subtitle, playback, notification, experimental) shared
 *    by the categories that touch them;
 *  - shared slices merge FIELD-LEVEL: importing one category must apply only
 *    that category's fields and must not bleed into a co-owner's fields
 *    (e.g. PLAYBACK applies audioDelayMs but not the AUDIO-owned default speed);
 *  - exclusive slices restore wholesale only when their owning category is
 *    selected;
 *  - the security split (lock config gated behind restoreSecuritySensitive)
 *    holds on the per-category path too;
 *  - an empty selection with includeExtras=false is a full no-op — even the
 *    extras block of the backup must not land;
 *  - [UserPreferencesStore.restoreExtras] writes extras with clearNullIds=true
 *    so stale ids are cleared by an all-null extras import.
 */
class UserPreferencesStoreRestoreCategoriesTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var graph: PreferenceSliceGraph
    private lateinit var store: UserPreferencesStore

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            graph = createPreferenceSliceGraph(scope, dataStore)
            store = createUserPreferencesStore(scope, dataStore)
            // Drain the Eagerly-cached slice flows so the cleared state is
            // observed before each test restores into them.
            drain()
        }
    }

    private suspend fun drain() {
        store.snapshotForBackup()
    }

    // ------------------------------------------------------------------
    // Backup construction: synthesize each slice directly (no setter
    // round-trips) so a test states exactly which incoming fields differ.
    // ------------------------------------------------------------------

    private fun <T> sliceElement(serializer: KSerializer<T>, value: T): JsonElement =
        PreferencesJson.export.encodeToJsonElement(serializer, value)

    private fun backup(
        appearance: AppearanceSlice? = null,
        playback: PlaybackSlice? = null,
        videoPlayer: VideoPlayerSlice? = null,
        audio: AudioSlice? = null,
        subtitle: SubtitleSlice? = null,
        security: SecuritySlice? = null,
        experimental: ExperimentalSlice? = null,
        extras: AppRuntimeState = AppRuntimeState(),
    ): SettingsBackup {
        val slices = buildMap<String, JsonElement> {
            appearance?.let { put(BackupSliceKey.APPEARANCE, sliceElement(AppearanceSlice.serializer(), it)) }
            playback?.let { put(BackupSliceKey.PLAYBACK, sliceElement(PlaybackSlice.serializer(), it)) }
            videoPlayer?.let { put(BackupSliceKey.VIDEO_PLAYER, sliceElement(VideoPlayerSlice.serializer(), it)) }
            audio?.let { put(BackupSliceKey.AUDIO, sliceElement(AudioSlice.serializer(), it)) }
            subtitle?.let { put(BackupSliceKey.SUBTITLE, sliceElement(SubtitleSlice.serializer(), it)) }
            security?.let { put(BackupSliceKey.SECURITY, sliceElement(SecuritySlice.serializer(), it)) }
            experimental?.let { put(BackupSliceKey.EXPERIMENTAL, sliceElement(ExperimentalSlice.serializer(), it)) }
        }
        return SettingsBackup(slices = slices, extras = extras)
    }

    private suspend fun <T> decodedSlice(key: String, serializer: KSerializer<T>): T? =
        store.snapshotForBackup().slices[key]?.let { element ->
            PreferencesJson.import.decodeFromJsonElement(serializer, element)
        }

    private suspend fun appearanceAfter(): AppearanceSlice =
        decodedSlice(BackupSliceKey.APPEARANCE, AppearanceSlice.serializer()) ?: AppearanceSlice()

    private suspend fun playbackAfter(): PlaybackSlice =
        decodedSlice(BackupSliceKey.PLAYBACK, PlaybackSlice.serializer()) ?: PlaybackSlice()

    private suspend fun videoAfter(): VideoPlayerSlice =
        decodedSlice(BackupSliceKey.VIDEO_PLAYER, VideoPlayerSlice.serializer()) ?: VideoPlayerSlice()

    private suspend fun audioAfter(): AudioSlice =
        decodedSlice(BackupSliceKey.AUDIO, AudioSlice.serializer()) ?: AudioSlice()

    private suspend fun subtitleAfter(): SubtitleSlice =
        decodedSlice(BackupSliceKey.SUBTITLE, SubtitleSlice.serializer()) ?: SubtitleSlice()

    private suspend fun securityAfter(): SecuritySlice =
        decodedSlice(BackupSliceKey.SECURITY, SecuritySlice.serializer()) ?: SecuritySlice()

    private suspend fun extrasAfter(): AppRuntimeState = store.snapshotForBackup().extras

    // ------------------------------------------------------------------
    // slicesForCategory routing table
    // ------------------------------------------------------------------

    @Test
    fun `every category maps to its documented slice keys`() = runTest {
        val expected = mapOf(
            PreferenceResetCategory.APPEARANCE to setOf(BackupSliceKey.APPEARANCE),
            PreferenceResetCategory.PLAYBACK to setOf(
                BackupSliceKey.PLAYBACK, BackupSliceKey.VIDEO_PLAYER, BackupSliceKey.AUDIO,
            ),
            PreferenceResetCategory.AUDIO to setOf(BackupSliceKey.AUDIO, BackupSliceKey.AUDIO_EFFECTS),
            PreferenceResetCategory.SUBTITLES_LANGUAGE to setOf(BackupSliceKey.SUBTITLE, BackupSliceKey.PLAYBACK),
            PreferenceResetCategory.DOWNLOADS_NETWORK to setOf(BackupSliceKey.DOWNLOADS, BackupSliceKey.NETWORK_OFFLINE),
            PreferenceResetCategory.HOME_DISCOVERY to setOf(
                BackupSliceKey.HOME_DISCOVERY, BackupSliceKey.LIBRARY, BackupSliceKey.NAVIGATION,
            ),
            PreferenceResetCategory.AUDIO_CACHE to setOf(BackupSliceKey.AUDIO_CACHE),
            PreferenceResetCategory.SECURITY to setOf(BackupSliceKey.SECURITY),
            PreferenceResetCategory.NOTIFICATIONS to setOf(BackupSliceKey.NOTIFICATION),
            PreferenceResetCategory.SCREENSAVER to setOf(BackupSliceKey.SCREENSAVER),
            PreferenceResetCategory.NEWSLETTER to setOf(BackupSliceKey.NOTIFICATION),
            PreferenceResetCategory.SYNCPLAY_CASTING to setOf(BackupSliceKey.SYNC_PLAY_CAST),
            PreferenceResetCategory.PLAYER_ENGINES to setOf(BackupSliceKey.PLAYER_ENGINE),
            PreferenceResetCategory.EXPERIMENTAL to setOf(BackupSliceKey.EXPERIMENTAL),
            PreferenceResetCategory.MISC_APP to setOf(
                BackupSliceKey.APPEARANCE, BackupSliceKey.PLAYBACK,
                BackupSliceKey.SUBTITLE, BackupSliceKey.EXPERIMENTAL,
            ),
        )
        assertEquals(PreferenceResetCategory.entries.size, expected.size, "every category must be routed")
        expected.forEach { (category, slices) ->
            assertEquals(slices, store.slicesForCategory(category), "slices for $category")
        }
    }

    @Test
    fun `co-owned slices are shared by exactly the categories documented in the mergers`() = runTest {
        // audio: PLAYBACK + AUDIO; appearance: APPEARANCE + MISC_APP;
        // subtitle: SUBTITLES_LANGUAGE + MISC_APP; notification: NOTIFICATIONS + NEWSLETTER.
        assertTrue(BackupSliceKey.AUDIO in store.slicesForCategory(PreferenceResetCategory.PLAYBACK))
        assertTrue(BackupSliceKey.AUDIO in store.slicesForCategory(PreferenceResetCategory.AUDIO))
        assertTrue(BackupSliceKey.APPEARANCE in store.slicesForCategory(PreferenceResetCategory.APPEARANCE))
        assertTrue(BackupSliceKey.APPEARANCE in store.slicesForCategory(PreferenceResetCategory.MISC_APP))
        assertTrue(BackupSliceKey.SUBTITLE in store.slicesForCategory(PreferenceResetCategory.SUBTITLES_LANGUAGE))
        assertTrue(BackupSliceKey.SUBTITLE in store.slicesForCategory(PreferenceResetCategory.MISC_APP))
        assertTrue(BackupSliceKey.NOTIFICATION in store.slicesForCategory(PreferenceResetCategory.NOTIFICATIONS))
        assertTrue(BackupSliceKey.NOTIFICATION in store.slicesForCategory(PreferenceResetCategory.NEWSLETTER))
    }

    // ------------------------------------------------------------------
    // Field-level merges for shared slices
    // ------------------------------------------------------------------

    @Test
    fun `appearance category import applies theme fields but not the misc-owned haptics`() = runTest {
        val incoming = AppearanceSlice(themeMode = ThemeMode.DARK, hapticsEnabled = false)

        store.restoreV2Categories(backup(appearance = incoming), categories = setOf(PreferenceResetCategory.APPEARANCE))
        drain()

        val after = appearanceAfter()
        assertEquals(ThemeMode.DARK, after.themeMode)
        assertEquals(true, after.hapticsEnabled, "haptics is MISC_APP-owned; APPEARANCE import must not bleed")
    }

    @Test
    fun `misc import applies haptics but not the appearance-owned theme`() = runTest {
        val incoming = AppearanceSlice(themeMode = ThemeMode.DARK, hapticsEnabled = false)

        store.restoreV2Categories(backup(appearance = incoming), categories = setOf(PreferenceResetCategory.MISC_APP))
        drain()

        val after = appearanceAfter()
        assertEquals(ThemeMode.SYSTEM, after.themeMode, "themeMode is APPEARANCE-owned; MISC import must not bleed")
        assertEquals(false, after.hapticsEnabled)
    }

    @Test
    fun `playback import applies delay but not the audio-owned default speed`() = runTest {
        // audioDelayMs is PLAYBACK-owned within the shared AudioSlice;
        // audioDefaultSpeed is AUDIO-owned.
        val incoming = AudioSlice(audioDelayMs = 250L, audioDefaultSpeed = 1.5f)

        store.restoreV2Categories(backup(audio = incoming), categories = setOf(PreferenceResetCategory.PLAYBACK))
        drain()

        val after = audioAfter()
        assertEquals(250L, after.audioDelayMs)
        assertEquals(1.0f, after.audioDefaultSpeed, "audioDefaultSpeed is AUDIO-owned; PLAYBACK import must not bleed")
    }

    @Test
    fun `audio import applies default speed but not the playback-owned delay`() = runTest {
        val incoming = AudioSlice(audioDelayMs = 250L, audioDefaultSpeed = 1.5f)

        store.restoreV2Categories(backup(audio = incoming), categories = setOf(PreferenceResetCategory.AUDIO))
        drain()

        val after = audioAfter()
        assertEquals(0L, after.audioDelayMs, "audioDelayMs is PLAYBACK-owned; AUDIO import must not bleed")
        assertEquals(1.5f, after.audioDefaultSpeed)
    }

    @Test
    fun `subtitles import applies the subtitle-owned playback field but not playback core fields`() = runTest {
        val incomingPlayback = PlaybackSlice(preferredPlayer = PlayerType.MPV, pgsSubtitleDirectPlay = true)

        store.restoreV2Categories(
            backup(playback = incomingPlayback),
            categories = setOf(PreferenceResetCategory.SUBTITLES_LANGUAGE),
        )
        drain()

        val after = playbackAfter()
        assertEquals(true, after.pgsSubtitleDirectPlay, "pgsSubtitleDirectPlay is SUBTITLES_LANGUAGE-owned")
        assertEquals(PlayerType.EXO_PLAYER, after.preferredPlayer, "preferredPlayer is PLAYBACK-owned; must not bleed")
    }

    @Test
    fun `subtitle misc import applies app language but not the language preference`() = runTest {
        val incoming = SubtitleSlice(preferredSubtitleLanguage = "ger", appLanguage = "fr")

        store.restoreV2Categories(backup(subtitle = incoming), categories = setOf(PreferenceResetCategory.MISC_APP))
        drain()

        val after = subtitleAfter()
        assertEquals("fr", after.appLanguage)
        assertNull(after.preferredSubtitleLanguage, "preferredSubtitleLanguage is SUBTITLES_LANGUAGE-owned")
    }

    // ------------------------------------------------------------------
    // Exclusive slices + multi-category selection
    // ------------------------------------------------------------------

    @Test
    fun `exclusive video-player slice is untouched when no selected category owns it`() = runTest {
        val incoming = VideoPlayerSlice(videoGesturesEnabled = false)

        store.restoreV2Categories(backup(videoPlayer = incoming), categories = setOf(PreferenceResetCategory.APPEARANCE))
        drain()

        assertEquals(true, videoAfter().videoGesturesEnabled, "default survives — APPEARANCE does not own videoPlayer")
    }

    @Test
    fun `exclusive video-player slice restores wholesale when PLAYBACK is selected`() = runTest {
        val incoming = VideoPlayerSlice(videoGesturesEnabled = false)

        store.restoreV2Categories(backup(videoPlayer = incoming), categories = setOf(PreferenceResetCategory.PLAYBACK))
        drain()

        assertEquals(false, videoAfter().videoGesturesEnabled)
    }

    @Test
    fun `multiple selected categories merge their fields in one pass`() = runTest {
        val incoming = backup(
            appearance = AppearanceSlice(themeMode = ThemeMode.DARK, hapticsEnabled = false),
            playback = PlaybackSlice(preferredPlayer = PlayerType.MPV, pgsSubtitleDirectPlay = true),
        )

        store.restoreV2Categories(
            incoming,
            categories = setOf(PreferenceResetCategory.APPEARANCE, PreferenceResetCategory.PLAYBACK),
        )
        drain()

        assertEquals(ThemeMode.DARK, appearanceAfter().themeMode)
        // Incoming hapticsEnabled=false must NOT apply (MISC owns it): the
        // current default (true) survives the combined import.
        assertEquals(true, appearanceAfter().hapticsEnabled, "MISC-owned field stays put even in a combined import")
        assertEquals(PlayerType.MPV, playbackAfter().preferredPlayer)
        assertEquals(false, playbackAfter().pgsSubtitleDirectPlay, "SUBTITLES-owned field stays put")
    }

    // ------------------------------------------------------------------
    // Security split on the per-category path
    // ------------------------------------------------------------------

    @Test
    fun `security category import without opt-in preserves the lock config`() = runTest {
        seedLockedSecurity()

        store.restoreV2Categories(
            backup(security = SecuritySlice()),
            categories = setOf(PreferenceResetCategory.SECURITY),
            restoreSecuritySensitive = false,
        )
        drain()

        val after = securityAfter()
        assertTrue(after.pinLockEnabled)
        assertEquals("existing-hash", after.pinHash)
    }

    @Test
    fun `security category import with opt-in overwrites the lock config`() = runTest {
        seedLockedSecurity()

        store.restoreV2Categories(
            backup(security = SecuritySlice()),
            categories = setOf(PreferenceResetCategory.SECURITY),
            restoreSecuritySensitive = true,
        )
        drain()

        val after = securityAfter()
        assertEquals(false, after.pinLockEnabled)
        // Hash is only WRITTEN when the incoming slice carries one — a null
        // incoming hash leaves the previously stored hash in place (dormant
        // while the lock is off). Pins the `slice.pinHash?.let` semantics.
        assertEquals("existing-hash", after.pinHash)
    }

    // ------------------------------------------------------------------
    // No-op early return + extras handling
    // ------------------------------------------------------------------

    @Test
    fun `empty categories without extras is a full no-op`() = runTest {
        val incoming = backup(
            appearance = AppearanceSlice(themeMode = ThemeMode.DARK),
            playback = PlaybackSlice(preferredPlayer = PlayerType.MPV),
            extras = AppRuntimeState(watchLaterPlaylistId = "pl-9", onboardingCompleted = true),
        )

        store.restoreV2Categories(incoming, categories = emptySet(), includeExtras = false)
        drain()

        assertEquals(PlayerType.EXO_PLAYER, playbackAfter().preferredPlayer)
        assertEquals(ThemeMode.SYSTEM, appearanceAfter().themeMode)
        assertNull(extrasAfter().watchLaterPlaylistId, "extras must not land when the selection is empty")
        assertEquals(false, extrasAfter().onboardingCompleted)
    }

    @Test
    fun `includeExtras writes the app-runtime state even with no categories`() = runTest {
        val incoming = backup(extras = AppRuntimeState(watchLaterPlaylistId = "pl-9", onboardingCompleted = true))

        store.restoreV2Categories(incoming, categories = emptySet(), includeExtras = true)
        drain()

        val extras = extrasAfter()
        assertEquals("pl-9", extras.watchLaterPlaylistId)
        assertTrue(extras.onboardingCompleted)
    }

    @Test
    fun `restoreExtras overwrites ids and clears them on an all-null import`() = runTest {
        store.restoreExtras(backup(extras = AppRuntimeState(watchLaterPlaylistId = "pl-1", favoriteChannels = setOf("c1"))))
        drain()
        assertEquals("pl-1", extrasAfter().watchLaterPlaylistId)

        // An all-null extras import must CLEAR the stale ids (clearNullIds=true
        // on the restoreExtras wrapper), not leave the previous binding.
        store.restoreExtras(backup())
        drain()

        assertNull(extrasAfter().watchLaterPlaylistId)
        assertEquals(emptySet(), extrasAfter().favoriteChannels)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Seeds pinLockEnabled + pinHash through the owning store's setters. */
    private suspend fun seedLockedSecurity() {
        graph.securityStore.setPinLockEnabled(true)
        graph.securityStore.setPinHash("existing-hash")
        drain()
    }
}
