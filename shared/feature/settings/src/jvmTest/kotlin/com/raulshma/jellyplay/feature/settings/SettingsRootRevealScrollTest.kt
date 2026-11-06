package com.raulshma.jellyplay.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The root browse pane's reveal-scroll contract — the runtime half of the
 * search-reveal choreography the expansion tests (section 9 of
 * `SettingsCatalogScreenContractTest`) cannot see.
 *
 * A search-reveal tap marks `lastClickedSettingId` and the pane must SCROLL
 * to the section hosting that id ([resolveRootRevealItemIndex] +
 * `HighlightScrollEffect` in SettingsBrowsePane): an off-screen section's
 * lazy item never composes, so the highlighted row's own focus choreography
 * (the 400ms-delayed focus grab + bring-into-view) can never fire — the row
 * stays invisible (or visible-but-unfocused when the card top merely clips
 * the fold) and the user reads it as "the item focus animation after
 * navigation is not working".
 *
 * Three layers pinned here:
 *  1. the declaration carries the reveal groups ([SETTINGS_ENTRANCE_SECTIONS]
 *     `revealGroup` ↔ [SettingsScreenGroups.rootRevealGroups] — one contract,
 *     two faces, pinned equal so neither drifts);
 *  2. the resolver's item-index arithmetic (declaration index minus the
 *     gated-off sections before the host, since the pane's `if`s skip those
 *     lazy items entirely — the stagger numbering tolerates the hole, the
 *     item index must not);
 *  3. the resolver's gate mirror against SettingsBrowsePane's emission `if`s
 *     (the same textual pin `SettingsSectionKeysGuardTest` runs for keys).
 */
class SettingsRootRevealScrollTest {

    // ── 1. declaration parity ───────────────────────────────────────────

    @Test
    fun `the entrance declaration hosts exactly the root reveal groups`() {
        val hosted = SETTINGS_ENTRANCE_SECTIONS.mapNotNull { it.revealGroup }
        // Set parity: the coverage contract is set-shaped (which groups reveal,
        // not where they sit — the declaration's order is the emission order,
        // rootRevealGroups' is the expansion-contract reading order).
        assertEquals(
            SettingsScreenGroups.rootRevealGroups.toSet(),
            hosted.toSet(),
            "SETTINGS_ENTRANCE_SECTIONS' revealGroup faces and SettingsScreenGroups" +
                ".rootRevealGroups disagree — the pane's reveal scroll and the groups' " +
                "auto-expansion read two different declarations; keep them one contract " +
                "(declare the group on the section's revealGroup and mirror it in " +
                "rootRevealGroups, or vice versa)",
        )
    }

    // ── 2. resolver arithmetic ──────────────────────────────────────────

    /** The four pane-relevant flags; the rest are irrelevant to the reveal scroll. */
    private fun capabilities(
        notifications: Boolean,
        idleAmbient: Boolean,
        discordPresence: Boolean,
        shellHooks: Boolean,
    ): SettingsCapabilities = SettingsCapabilities(
        supportsDynamicColor = false,
        supportsNotifications = notifications,
        supportsAppLocaleOverride = false,
        supportsAudioCache = false,
        supportsScreenOrientation = false,
        supportsTouchGestures = false,
        supportsAppLock = false,
        supportsBiometric = false,
        supportsSystemNotificationSettings = false,
        supportsLogSharing = false,
        supportsAudioDeviceSelection = false,
        supportsMpvRenderProfiles = false,
        supportsVolumeMemory = false,
        supportsIdleAmbientScreen = idleAmbient,
        supportsDiscordPresence = discordPresence,
        supportsShellHooks = shellHooks,
        supportsPip = false,
    )

    /** Android's actuals (phone + TV): notifications on, the desktop trio off. */
    private val androidCaps = capabilities(
        notifications = true, idleAmbient = false, discordPresence = false, shellHooks = false,
    )

    /** Desktop's actuals: notifications off, the desktop trio on. */
    private val desktopCaps = capabilities(
        notifications = false, idleAmbient = true, discordPresence = true, shellHooks = true,
    )

    private fun jellyplayRevealId(): String =
        SettingsScreenGroups.jellyplayFeatures.itemIdSet.first()

    @Test
    fun `every open-platform reveal id resolves to its hosting section's item index`() {
        // All gates open: the LazyColumn item index equals the declaration index.
        val tvAllOpen = capabilities(notifications = true, idleAmbient = true, discordPresence = true, shellHooks = true)
        for (section in SETTINGS_ENTRANCE_SECTIONS) {
            val group = section.revealGroup ?: continue
            val id = group.itemIdSet.first()
            val declarationIndex = SETTINGS_ENTRANCE_SECTIONS.indexOfFirst { it.key == section.key }
            assertEquals(
                declarationIndex,
                resolveRootRevealItemIndex(id, isTv = true, capabilities = tvAllOpen),
                "${section.key}: with every gate open the item index must equal the declaration index",
            )
        }
    }

    @Test
    fun `the jellyplay group reveals on every platform at its declaration index`() {
        // The composite sections before it are never gated, so nothing shifts.
        assertEquals(6, resolveRootRevealItemIndex(jellyplayRevealId(), isTv = false, capabilities = androidCaps))
        assertEquals(6, resolveRootRevealItemIndex(jellyplayRevealId(), isTv = true, capabilities = androidCaps))
        assertEquals(6, resolveRootRevealItemIndex(jellyplayRevealId(), isTv = false, capabilities = desktopCaps))
    }

    @Test
    fun `desktop shifts the desktop-backed groups up by the closed gates before them`() {
        // Desktop (isTv=false, notifications off): group_screensaver (18, TV-only)
        // and item_notifications (13, notifications gate) close before the trio.
        assertEquals(-1, resolveRootRevealItemIndex(SettingsScreenGroups.systemScreensaver.itemIdSet.first(), isTv = false, capabilities = desktopCaps))
        assertEquals(17, resolveRootRevealItemIndex(SettingsScreenGroups.systemIdleAmbient.itemIdSet.first(), isTv = false, capabilities = desktopCaps))
        assertEquals(18, resolveRootRevealItemIndex(SettingsScreenGroups.systemDiscordPresence.itemIdSet.first(), isTv = false, capabilities = desktopCaps))
        assertEquals(19, resolveRootRevealItemIndex(SettingsScreenGroups.systemHooks.itemIdSet.first(), isTv = false, capabilities = desktopCaps))
    }

    @Test
    fun `android phone closes the tv and desktop-backed hosts entirely`() {
        assertEquals(-1, resolveRootRevealItemIndex(SettingsScreenGroups.systemScreensaver.itemIdSet.first(), isTv = false, capabilities = androidCaps))
        assertEquals(-1, resolveRootRevealItemIndex(SettingsScreenGroups.systemIdleAmbient.itemIdSet.first(), isTv = false, capabilities = androidCaps))
        assertEquals(-1, resolveRootRevealItemIndex(SettingsScreenGroups.systemDiscordPresence.itemIdSet.first(), isTv = false, capabilities = androidCaps))
        assertEquals(-1, resolveRootRevealItemIndex(SettingsScreenGroups.systemHooks.itemIdSet.first(), isTv = false, capabilities = androidCaps))
    }

    @Test
    fun `android tv keeps the screensaver group at its declaration index`() {
        assertEquals(18, resolveRootRevealItemIndex(SettingsScreenGroups.systemScreensaver.itemIdSet.first(), isTv = true, capabilities = androidCaps))
    }

    @Test
    fun `navigation ids and unknown ids resolve to -1`() {
        // The entrance rows (and every other openSetting target) navigate away —
        // back-navigation must restore the pane's position, not scroll it.
        assertEquals(-1, resolveRootRevealItemIndex(PlaybackEntrance.rowId, isTv = false, capabilities = androidCaps))
        assertEquals(-1, resolveRootRevealItemIndex(AppearanceEntrance.rowId, isTv = true, capabilities = androidCaps))
        assertEquals(-1, resolveRootRevealItemIndex(null, isTv = false, capabilities = androidCaps))
        assertEquals(-1, resolveRootRevealItemIndex("id_no_group_hosts", isTv = false, capabilities = androidCaps))
    }

    // ── 3. pane gate mirror ─────────────────────────────────────────────

    /**
     * The resolver's [sectionGateOpen] mirror is private; the pane's emission
     * `if`s are the ground truth it must match. Scan the pane source: each
     * gated section's `settingsSection(…)` call site sits inside an `if` that
     * references the same axis the resolver keys on.
     */
    @Test
    fun `the pane's emission gates match the resolver's mirror`() {
        val root = repoRoot()
        val paneSource = stripComments(
            File(root, PANE_FILE).readText(),
        )
        assertTrue(
            paneSource.contains("settingsSection("),
            "no settingsSection call sites found in $PANE_FILE — the pane layout changed?",
        )

        // (call-site marker, the axis token the enclosing if must reference).
        // The notification section's call site is declaration-driven
        // (`settingsSection(NotificationEntrance.section.key, …)`), so its
        // marker is the key expression, not a literal.
        val gatedSections: List<Pair<String, String>> = listOf(
            """settingsSection("group_screensaver"""" to "if (isTv)",
            "settingsSection(NotificationEntrance.section.key" to "settingsCapabilities.supportsNotifications",
            """settingsSection("group_idle_ambient"""" to "settingsCapabilities.supportsIdleAmbientScreen",
            """settingsSection("group_discord_presence"""" to "settingsCapabilities.supportsDiscordPresence",
            """settingsSection("group_shell_hooks"""" to "settingsCapabilities.supportsShellHooks",
        )
        val drift = gatedSections.mapNotNull { (marker, axis) ->
            val callIdx = paneSource.indexOf(marker)
            if (callIdx < 0) {
                return@mapNotNull "$marker — no settingsSection call site found (renamed? moved?)"
            }
            // The emission ifs are single-line and sit immediately before their
            // call site; a small backward window keeps the check local.
            val window = paneSource.substring(maxOf(0, callIdx - 120), callIdx)
            val ifLine = window.lineSequence().lastOrNull { it.trimStart().startsWith("if (") }
            if (ifLine == null || axis !in ifLine) {
                "$marker — the if guarding its settingsSection call no longer references '$axis' " +
                    "(found: ${ifLine?.trim()}); update sectionGateOpen's mirror in SettingsEntranceSteps.kt"
            } else {
                null
            }
        }
        if (drift.isNotEmpty()) {
            fail(drift.joinToString("\n"))
        }
    }

    // ------------------------------------------------------------ plumbing

    private val PANE_FILE =
        "shared/feature/settings/src/commonMain/kotlin/com/raulshma/jellyplay/feature/settings/SettingsBrowsePane.kt"

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        fail("could not locate repo root (no settings.gradle.kts walking up from ${System.getProperty("user.dir")})")
    }

    /** Drops line and block comments so doc mentions don't count as call sites. */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")
}
