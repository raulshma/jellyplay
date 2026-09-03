package com.raulshma.jellyplay.core.datastore.settings

import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.model.AppFontScale
import com.raulshma.jellyplay.core.model.ColorBlindMode
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.HandMode
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.UpdateDismissPeriod
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.StreamingQuality
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exercises the field-level [mergeWith] mergers for the six co-owned slices:
 * with no owning category selected the slice must come back unchanged, with a
 * single owning category only that category's fields may be replaced, and with
 * every owning category selected the incoming slice is adopted wholesale.
 */
class SliceCategoryMergersTest {

    // ------------------------------------------------------------------
    // AppearanceSlice — APPEARANCE + MISC_APP
    // ------------------------------------------------------------------

    private val appearanceIncoming = AppearanceSlice(
        dynamicTheming = false,
        themeMode = ThemeMode.DARK,
        contrastLevel = ContrastLevel.HIGH,
        oledMode = true,
        performanceMode = true,
        accentColorSwatch = "violet",
        colorStyle = ColorStyle.VIBRANT,
        themeVariant = "sakura",
        synthwaveAccent = "cyan",
        soothingAccent = "forest",
        vividAccent = "lime",
        auroraAccent = "ice",
        sakuraAccent = "peach",
        vectorPopAccent = "kelly",
        showAdvancedSettings = true,
        reduceMotionEnabled = true,
        blueLightFilterEnabled = true,
        blueLightFilterStrength = 0.75f,
        backdropThemeMusicEnabled = true,
        dateFormatPreference = DateFormatPreference.ISO,
        appFontScale = AppFontScale.LARGE,
        scheduledThemeStartHour = 20,
        scheduledThemeEndHour = 6,
        colorBlindMode = ColorBlindMode.DEUTERANOPIA,
        handMode = HandMode.LEFT,
        hapticsEnabled = true,
    )

    @Test
    fun `appearance slice is unchanged with no owning category`() {
        val current = AppearanceSlice()
        assertEquals(current, current.mergeWith(appearanceIncoming, emptySet()))
        assertEquals(current, current.mergeWith(appearanceIncoming, setOf(PreferenceResetCategory.PLAYBACK)))
    }

    @Test
    fun `appearance slice with APPEARANCE only replaces appearance fields but keeps haptics`() {
        val current = AppearanceSlice()
        val merged = current.mergeWith(appearanceIncoming, setOf(PreferenceResetCategory.APPEARANCE))
        assertEquals(appearanceIncoming.copy(hapticsEnabled = current.hapticsEnabled), merged)
    }

    @Test
    fun `appearance slice with MISC_APP only replaces haptics but keeps appearance fields`() {
        val current = AppearanceSlice()
        val merged = current.mergeWith(appearanceIncoming, setOf(PreferenceResetCategory.MISC_APP))
        assertEquals(current.copy(hapticsEnabled = true), merged)
    }

    @Test
    fun `appearance slice with both categories adopts incoming wholesale`() {
        val current = AppearanceSlice()
        val merged = current.mergeWith(
            appearanceIncoming,
            setOf(PreferenceResetCategory.APPEARANCE, PreferenceResetCategory.MISC_APP),
        )
        assertEquals(appearanceIncoming, merged)
    }

    // ------------------------------------------------------------------
    // PlaybackSlice — PLAYBACK + SUBTITLES_LANGUAGE + SYNCPLAY_CASTING + MISC_APP
    // ------------------------------------------------------------------

    private val playbackIncoming = PlaybackSlice(
        preferredPlayer = PlayerType.MPV,
        streamingQuality = StreamingQuality.FHD_1080P,
        audioPassthrough = true,
        frameRateMatching = true,
        keepScreenOnDuringVideo = false,
        pauseOnAudioFocusLoss = false,
        duckOnTransientFocusLoss = true,
        autoPlayCountdownSec = 42,
        backgroundVideoAudioEnabled = true,
        pgsSubtitleDirectPlay = true,
        liveStreamOption = LiveStreamOption.TRANSCODE,
        userDataSyncEnabled = false,
        androidTvWatchNextEnabled = false,
    )

    @Test
    fun `playback slice is unchanged with no owning category`() {
        val current = PlaybackSlice()
        assertEquals(current, current.mergeWith(playbackIncoming, emptySet()))
        assertEquals(current, current.mergeWith(playbackIncoming, setOf(PreferenceResetCategory.AUDIO)))
    }

    @Test
    fun `playback slice with PLAYBACK only replaces playback fields only`() {
        val current = PlaybackSlice()
        val merged = current.mergeWith(playbackIncoming, setOf(PreferenceResetCategory.PLAYBACK))
        assertEquals(
            playbackIncoming.copy(
                pgsSubtitleDirectPlay = false,
                liveStreamOption = LiveStreamOption.AUTO,
                userDataSyncEnabled = true,
                androidTvWatchNextEnabled = true,
            ),
            merged,
        )
    }

    @Test
    fun `playback slice with SUBTITLES_LANGUAGE only replaces pgs flag only`() {
        val current = PlaybackSlice()
        val merged = current.mergeWith(playbackIncoming, setOf(PreferenceResetCategory.SUBTITLES_LANGUAGE))
        assertEquals(current.copy(pgsSubtitleDirectPlay = true), merged)
    }

    @Test
    fun `playback slice with SYNCPLAY_CASTING only replaces live stream option only`() {
        val current = PlaybackSlice()
        val merged = current.mergeWith(playbackIncoming, setOf(PreferenceResetCategory.SYNCPLAY_CASTING))
        assertEquals(current.copy(liveStreamOption = LiveStreamOption.TRANSCODE), merged)
    }

    @Test
    fun `playback slice with MISC_APP only replaces the misc flags only`() {
        val current = PlaybackSlice()
        val merged = current.mergeWith(playbackIncoming, setOf(PreferenceResetCategory.MISC_APP))
        assertEquals(current.copy(userDataSyncEnabled = false, androidTvWatchNextEnabled = false), merged)
    }

    @Test
    fun `playback slice with all owning categories adopts incoming wholesale`() {
        val current = PlaybackSlice()
        val merged = current.mergeWith(
            playbackIncoming,
            setOf(
                PreferenceResetCategory.PLAYBACK,
                PreferenceResetCategory.SUBTITLES_LANGUAGE,
                PreferenceResetCategory.SYNCPLAY_CASTING,
                PreferenceResetCategory.MISC_APP,
            ),
        )
        assertEquals(playbackIncoming, merged)
    }

    // ------------------------------------------------------------------
    // AudioSlice — PLAYBACK (audioDelayMs) + AUDIO
    // ------------------------------------------------------------------

    private val audioIncoming = AudioSlice(
        audioDelayMs = 250L,
        audioDefaultSpeed = 1.25f,
        audioNightModeVolume = 0.8f,
        audioNightModeGain = 999,
        audioSkipPreviousThresholdMs = 12_345L,
        audioAutoplayNext = false,
        audioNormalizationEnabled = true,
        replayGainPreAmpDb = -3f,
        channelMixEnabled = true,
        audioGaplessEnabled = false,
        audioCrossfadeDurationMs = 2_000L,
        audioLyricsVisible = true,
        audioVisualizerEnabled = true,
        sleepTimerDurationMs = 900_000L,
        sleepTimerEndOfEpisode = true,
    )

    @Test
    fun `audio slice is unchanged with no owning category`() {
        val current = AudioSlice()
        assertEquals(current, current.mergeWith(audioIncoming, emptySet()))
        assertEquals(current, current.mergeWith(audioIncoming, setOf(PreferenceResetCategory.APPEARANCE)))
    }

    @Test
    fun `audio slice with PLAYBACK only replaces audioDelayMs only`() {
        val current = AudioSlice()
        val merged = current.mergeWith(audioIncoming, setOf(PreferenceResetCategory.PLAYBACK))
        assertEquals(current.copy(audioDelayMs = 250L), merged)
    }

    @Test
    fun `audio slice with AUDIO only replaces audio fields but keeps audioDelayMs`() {
        val current = AudioSlice()
        val merged = current.mergeWith(audioIncoming, setOf(PreferenceResetCategory.AUDIO))
        assertEquals(audioIncoming.copy(audioDelayMs = current.audioDelayMs), merged)
    }

    @Test
    fun `audio slice with both categories adopts incoming wholesale`() {
        val current = AudioSlice()
        val merged = current.mergeWith(
            audioIncoming,
            setOf(PreferenceResetCategory.PLAYBACK, PreferenceResetCategory.AUDIO),
        )
        assertEquals(audioIncoming, merged)
    }

    // ------------------------------------------------------------------
    // NotificationSlice — NOTIFICATIONS + NEWSLETTER
    // ------------------------------------------------------------------

    private val notificationIncoming = NotificationSlice(
        notificationPreferences = NotificationPreferences(enabled = true, soundEnabled = false),
        newsletterEnabled = false,
        newsletterDayOfWeek = 3,
        newsletterLastViewedMs = 555L,
        enabledNewsletterSections = emptySet(),
        newsletterSectionOrder = emptyList(),
    )

    @Test
    fun `notification slice is unchanged with no owning category`() {
        val current = NotificationSlice()
        assertEquals(current, current.mergeWith(notificationIncoming, emptySet()))
        assertEquals(current, current.mergeWith(notificationIncoming, setOf(PreferenceResetCategory.AUDIO)))
    }

    @Test
    fun `notification slice with NOTIFICATIONS only replaces notification prefs only`() {
        val current = NotificationSlice()
        val merged = current.mergeWith(notificationIncoming, setOf(PreferenceResetCategory.NOTIFICATIONS))
        assertEquals(
            notificationIncoming.copy(
                newsletterEnabled = true,
                newsletterDayOfWeek = 7,
                newsletterLastViewedMs = 0L,
                enabledNewsletterSections = NewsletterSectionType.entries.toSet(),
                newsletterSectionOrder = NewsletterSectionType.DEFAULT_ORDER,
            ),
            merged,
        )
    }

    @Test
    fun `notification slice with NEWSLETTER only replaces newsletter fields only`() {
        val current = NotificationSlice()
        val merged = current.mergeWith(notificationIncoming, setOf(PreferenceResetCategory.NEWSLETTER))
        assertEquals(
            current.copy(
                newsletterEnabled = false,
                newsletterDayOfWeek = 3,
                newsletterLastViewedMs = 555L,
                enabledNewsletterSections = emptySet(),
                newsletterSectionOrder = emptyList(),
            ),
            merged,
        )
    }

    @Test
    fun `notification slice with both categories adopts incoming wholesale`() {
        val current = NotificationSlice()
        val merged = current.mergeWith(
            notificationIncoming,
            setOf(PreferenceResetCategory.NOTIFICATIONS, PreferenceResetCategory.NEWSLETTER),
        )
        assertEquals(notificationIncoming, merged)
    }

    // ------------------------------------------------------------------
    // ExperimentalSlice — EXPERIMENTAL + MISC_APP
    // ------------------------------------------------------------------

    private val experimentalIncoming = ExperimentalSlice(
        enabledExperimentalFeatures = setOf(ExperimentalFeature.HOME_CARD_CLIPPING),
        selfUpdateCheckEnabled = false,
        selfUpdateDownloadEnabled = true,
        appLanguage = "de",
        showShareMediaOption = false,
        hideSearchHistory = true,
        preferAudioDescription = true,
        dismissedUpdateVersion = "9.9.9",
        dismissedUpdateAtMs = 777L,
        updateDismissPeriod = UpdateDismissPeriod.NEVER,
    )

    @Test
    fun `experimental slice is unchanged with no owning category`() {
        val current = ExperimentalSlice()
        assertEquals(current, current.mergeWith(experimentalIncoming, emptySet()))
        assertEquals(current, current.mergeWith(experimentalIncoming, setOf(PreferenceResetCategory.AUDIO)))
    }

    @Test
    fun `experimental slice with EXPERIMENTAL only replaces the feature set only`() {
        val current = ExperimentalSlice()
        val merged = current.mergeWith(experimentalIncoming, setOf(PreferenceResetCategory.EXPERIMENTAL))
        assertEquals(current.copy(enabledExperimentalFeatures = setOf(ExperimentalFeature.HOME_CARD_CLIPPING)), merged)
    }

    @Test
    fun `experimental slice with MISC_APP only replaces misc fields only`() {
        val current = ExperimentalSlice()
        val merged = current.mergeWith(experimentalIncoming, setOf(PreferenceResetCategory.MISC_APP))
        assertEquals(
            current.copy(
                selfUpdateCheckEnabled = false,
                selfUpdateDownloadEnabled = true,
                appLanguage = "de",
                showShareMediaOption = false,
                hideSearchHistory = true,
                preferAudioDescription = true,
                dismissedUpdateVersion = "9.9.9",
                dismissedUpdateAtMs = 777L,
                updateDismissPeriod = UpdateDismissPeriod.NEVER,
            ),
            merged,
        )
    }

    @Test
    fun `experimental slice with both categories adopts incoming wholesale`() {
        val current = ExperimentalSlice()
        val merged = current.mergeWith(
            experimentalIncoming,
            setOf(PreferenceResetCategory.EXPERIMENTAL, PreferenceResetCategory.MISC_APP),
        )
        assertEquals(experimentalIncoming, merged)
    }

    // ------------------------------------------------------------------
    // SubtitleSlice — SUBTITLES_LANGUAGE + MISC_APP
    // ------------------------------------------------------------------

    private val subtitleIncoming = SubtitleSlice(
        preferredSubtitleLanguage = "de",
        subtitlesForcedOnly = true,
        preferredAudioLanguage = "ja",
        subtitleDelayByItem = mapOf("item1" to 500L),
        subtitlePreviewInSettings = false,
        highContrastSubtitles = true,
        hdrSubtitleStyleEnabled = true,
        appLanguage = "fr",
        preferAudioDescription = true,
    )

    @Test
    fun `subtitle slice is unchanged with no owning category`() {
        val current = SubtitleSlice()
        assertEquals(current, current.mergeWith(subtitleIncoming, emptySet()))
        assertEquals(current, current.mergeWith(subtitleIncoming, setOf(PreferenceResetCategory.PLAYBACK)))
    }

    @Test
    fun `subtitle slice with SUBTITLES_LANGUAGE only replaces subtitle fields only`() {
        val current = SubtitleSlice()
        val merged = current.mergeWith(subtitleIncoming, setOf(PreferenceResetCategory.SUBTITLES_LANGUAGE))
        assertEquals(
            subtitleIncoming.copy(appLanguage = null, preferAudioDescription = false),
            merged,
        )
    }

    @Test
    fun `subtitle slice with MISC_APP only replaces language fields only`() {
        val current = SubtitleSlice()
        val merged = current.mergeWith(subtitleIncoming, setOf(PreferenceResetCategory.MISC_APP))
        assertEquals(current.copy(appLanguage = "fr", preferAudioDescription = true), merged)
    }

    @Test
    fun `subtitle slice with both categories adopts incoming wholesale`() {
        val current = SubtitleSlice()
        val merged = current.mergeWith(
            subtitleIncoming,
            setOf(PreferenceResetCategory.SUBTITLES_LANGUAGE, PreferenceResetCategory.MISC_APP),
        )
        assertEquals(subtitleIncoming, merged)
    }
}
