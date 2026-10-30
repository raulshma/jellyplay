package com.raulshma.jellyplay.core.datastore.settings

import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.model.AppFontScale
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.AudioPassthroughCodec
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.ColorBlindMode
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.ExternalPlayerApp
import com.raulshma.jellyplay.core.model.HandMode
import com.raulshma.jellyplay.core.model.HdrFormatPreference
import com.raulshma.jellyplay.core.model.LanguageRuleSet
import com.raulshma.jellyplay.core.model.LayoutMode
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.MaxAudioChannelsEnum
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.model.OfflinePlaybackPreference
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.RefreshRateMode
import com.raulshma.jellyplay.core.model.SessionRestoreMode
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.SubtitleStylePreset
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.TrackSelectionPreset
import com.raulshma.jellyplay.core.model.TranscodeAudioCodecPreference
import com.raulshma.jellyplay.core.model.TranscodeVideoCodecPreference
import com.raulshma.jellyplay.core.model.TvOverscan
import com.raulshma.jellyplay.core.model.UpdateDismissPeriod
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the field-level [mergeWith] mergers for the six co-owned slices:
 * with no owning category selected the slice must come back unchanged, with a
 * single owning category only that category's fields may be replaced, and with
 * every owning category selected the incoming slice is adopted wholesale.
 *
 * The per-slice fixtures set EVERY declared field to a non-default value, and
 * `assertMergerAdoptsEveryDeclaredField` pins the wholesale arm field-by-field
 * via JVM reflection: a `copy()` line forgotten in a merger (which the wizard
 * diff would still advertise — the diff/apply drift of Issue #12) fails here
 * instead of silently keeping the device value. The fixture-quality check in
 * the same helper keeps the guard honest: a field left at its default proves
 * nothing.
 */
class SliceCategoryMergersTest {

    // ------------------------------------------------------------------
    // The coverage guard (JVM reflection — mirrors ResetCoverageGuard's stance)
    // ------------------------------------------------------------------

    /**
     * Asserts, over the JVM-declared fields of a slice class: (a) the fixture
     * differs from [current] on every field (fixture quality), and (b) the
     * full-category merge adopted [incoming] on every field (merger coverage).
     */
    private fun assertMergerAdoptsEveryDeclaredField(
        sliceClass: Class<*>,
        current: Any,
        incoming: Any,
        merged: Any,
    ) {
        val fields = sliceClass.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) }
            .onEach { it.isAccessible = true }
        val atDefault = fields.filter { it.get(current) == it.get(incoming) }.map { it.name }
        assertTrue(atDefault.isEmpty(), "test fixture left slice fields at default (they prove nothing): $atDefault")
        val dropped = fields.filter { it.get(incoming) != it.get(merged) }.map { it.name }
        assertTrue(
            dropped.isEmpty(),
            "mergeWith dropped slice fields — the restore wizard diffs them but never applies them: $dropped",
        )
    }

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
        detailsAwardsBadgesEnabled = false,
        dateFormatPreference = DateFormatPreference.ISO,
        appFontScale = AppFontScale.LARGE,
        scheduledThemeStartHour = 20,
        scheduledThemeEndHour = 6,
        colorBlindMode = ColorBlindMode.DEUTERANOPIA,
        handMode = HandMode.LEFT,
        layoutMode = LayoutMode.PHONE,
        tvOverscan = TvOverscan.TEN,
        hapticsEnabled = false,
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
        assertEquals(current.copy(hapticsEnabled = false), merged)
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

    @Test
    fun `appearance merger covers every declared slice field`() {
        val current = AppearanceSlice()
        val merged = current.mergeWith(
            appearanceIncoming,
            setOf(PreferenceResetCategory.APPEARANCE, PreferenceResetCategory.MISC_APP),
        )
        assertMergerAdoptsEveryDeclaredField(AppearanceSlice::class.java, current, appearanceIncoming, merged)
    }

    // ------------------------------------------------------------------
    // PlaybackSlice — PLAYBACK + SUBTITLES_LANGUAGE + SYNCPLAY_CASTING + MISC_APP
    // ------------------------------------------------------------------

    private val playbackIncoming = PlaybackSlice(
        preferredPlayer = PlayerType.MPV,
        preferredExternalPlayer = ExternalPlayerApp.VLC,
        streamingQuality = StreamingQuality.FHD_1080P,
        cellularStreamingQuality = StreamingQuality.HD_720P,
        hdrFormatPreference = HdrFormatPreference.SDR_ONLY,
        forceTranscodeAboveHeight = 1080,
        preferredTranscodeVideoCodec = TranscodeVideoCodecPreference.HEVC,
        preferredTranscodeAudioCodec = TranscodeAudioCodecPreference.AC3,
        playbackMode = PlaybackMode.FORCE_TRANSCODE,
        liveStreamOption = LiveStreamOption.TRANSCODE,
        offlinePlaybackPreference = OfflinePlaybackPreference.PREFER_STREAMING,
        decoderMode = DecoderMode.SW_ONLY,
        audioPassthrough = true,
        audioPassthroughCodecs = setOf(AudioPassthroughCodec.DTS),
        maxAudioChannels = MaxAudioChannelsEnum.STEREO,
        downmixBoostDb = 3f,
        spatialAudioEnabled = true,
        spatialAudioOnlyHeadphones = false,
        frameRateMatching = true,
        refreshRateMode = RefreshRateMode.FRAME_RATE_ONLY,
        keepScreenOnDuringVideo = false,
        pauseOnAudioFocusLoss = false,
        duckOnTransientFocusLoss = true,
        autoPlayCountdownSec = 42,
        backgroundVideoAudioEnabled = true,
        autoEnterPip = false,
        sessionRestoreMode = SessionRestoreMode.AUTO,
        offerDeleteAfterPlaybackMovies = true,
        offerDeleteAfterPlaybackEpisodes = true,
        customDeviceProfileJson = "{\"fixture\": true}",
        pgsSubtitleDirectPlay = true,
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

    @Test
    fun `playback merger covers every declared slice field`() {
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
        assertMergerAdoptsEveryDeclaredField(PlaybackSlice::class.java, current, playbackIncoming, merged)
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
        audioPreloadBufferSize = PreloadBufferSize.HIGH,
        audioNormalizationMode = AudioNormalizationMode.TRACK,
        audioNormalizationEnabled = true,
        replayGainPreAmpDb = -3f,
        channelMixMode = ChannelMixMode.SURROUND_UPMIX,
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

    @Test
    fun `audio merger covers every declared slice field`() {
        val current = AudioSlice()
        val merged = current.mergeWith(
            audioIncoming,
            setOf(PreferenceResetCategory.PLAYBACK, PreferenceResetCategory.AUDIO),
        )
        assertMergerAdoptsEveryDeclaredField(AudioSlice::class.java, current, audioIncoming, merged)
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

    @Test
    fun `notification merger covers every declared slice field`() {
        val current = NotificationSlice()
        val merged = current.mergeWith(
            notificationIncoming,
            setOf(PreferenceResetCategory.NOTIFICATIONS, PreferenceResetCategory.NEWSLETTER),
        )
        assertMergerAdoptsEveryDeclaredField(NotificationSlice::class.java, current, notificationIncoming, merged)
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

    @Test
    fun `experimental merger covers every declared slice field`() {
        val current = ExperimentalSlice()
        val merged = current.mergeWith(
            experimentalIncoming,
            setOf(PreferenceResetCategory.EXPERIMENTAL, PreferenceResetCategory.MISC_APP),
        )
        assertMergerAdoptsEveryDeclaredField(ExperimentalSlice::class.java, current, experimentalIncoming, merged)
    }

    // ------------------------------------------------------------------
    // SubtitleSlice — SUBTITLES_LANGUAGE + MISC_APP
    // ------------------------------------------------------------------

    private val subtitleIncoming = SubtitleSlice(
        preferredSubtitleLanguage = "de",
        subtitlesForcedOnly = true,
        showOnlyPreferredSubtitles = true,
        preferredAudioLanguage = "ja",
        subtitleDelayByItem = mapOf("item1" to 500L),
        languageRules = LanguageRuleSet(preset = TrackSelectionPreset.SUBBED_SHOWS),
        subtitleStyle = SubtitleStyle(applyCustomStyle = true, fontSize = 30),
        subtitlePreviewInSettings = false,
        highContrastSubtitles = true,
        hdrSubtitleStyleEnabled = true,
        hdrSubtitleStyle = SubtitleStyle(applyCustomStyle = true, fontSize = 32),
        userStylePresets = listOf(SubtitleStylePreset("Fixture", SubtitleStyle(applyCustomStyle = true))),
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

    @Test
    fun `subtitle merger covers every declared slice field`() {
        val current = SubtitleSlice()
        val merged = current.mergeWith(
            subtitleIncoming,
            setOf(PreferenceResetCategory.SUBTITLES_LANGUAGE, PreferenceResetCategory.MISC_APP),
        )
        assertMergerAdoptsEveryDeclaredField(SubtitleSlice::class.java, current, subtitleIncoming, merged)
    }
}
