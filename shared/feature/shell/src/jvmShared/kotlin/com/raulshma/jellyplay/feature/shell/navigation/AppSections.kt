package com.raulshma.jellyplay.feature.shell.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.admin.navigation.adminSection
import com.raulshma.jellyplay.feature.arrqueue.navigation.arrQueueSection
import com.raulshma.jellyplay.feature.auth.navigation.authSection
import com.raulshma.jellyplay.feature.calendar.navigation.calendarSection
import com.raulshma.jellyplay.feature.details.navigation.detailsSection
import com.raulshma.jellyplay.feature.downloads.navigation.downloadsSection
import com.raulshma.jellyplay.feature.editor.navigation.editorSection
import com.raulshma.jellyplay.feature.home.navigation.homeSection
import com.raulshma.jellyplay.feature.insights.navigation.insightsSection
import com.raulshma.jellyplay.feature.library.navigation.librarySection
import com.raulshma.jellyplay.feature.livetv.navigation.liveTvSection
import com.raulshma.jellyplay.feature.music.musichome.MusicHomeScreen
import com.raulshma.jellyplay.feature.music.navigation.musicSection
import com.raulshma.jellyplay.feature.newsletter.navigation.newsletterSection
import com.raulshma.jellyplay.feature.onboarding.navigation.onboardingSection
import com.raulshma.jellyplay.feature.player.audio.navigation.audioPlayerSection
import com.raulshma.jellyplay.feature.requests.navigation.requestsSection
import com.raulshma.jellyplay.feature.search.navigation.searchSection
import com.raulshma.jellyplay.feature.settings.navigation.settingsSection
import com.raulshma.jellyplay.feature.shortcuts.navigation.shortcutsSection
import com.raulshma.jellyplay.feature.syncplay.navigation.syncPlaySection

/**
 * The canonical shared section graph: the ~20 commonMain `*Section` builders
 * both shells used to restate (JellyPlayApp's entryProvider vs
 * DesktopAppRoot's), registered ONCE here, in the Android shell's order.
 * (nav3 resolves entries by key, so registration order is not routing
 * behaviour — the desktop order change is a structural delta only.)
 *
 * Shell-supplied inputs flow through [ShellHostHooks]; the MusicHomeScreen
 * wiring lives here because its seven navigate lambdas are identical per
 * shell, while the two audio-source lambdas come from the host. Routes that
 * cannot be registered from commonMain — the androidMain-only
 * livePlayerSection/subtitleTesterSection builders, the Android shell's
 * inline PlayOnCompanion entry, the desktop's conditional VideoPlayer entry —
 * stay in their shells and flow through [shellEntryProvider]'s
 * [ShellSections.extraSections] slot, so the ledger still sees them.
 */
fun EntryProviderScope<NavKey>.appSections(
    navigator: Navigator,
    host: ShellHostHooks,
) {
    homeSection(
        navigator = navigator,
        homeMode = host.homeMode,
        onModeChange = host.onHomeModeChange,
        playOnStrategy = host.playOnRedirect,
        surpriseRequests = host.surpriseRequests,
        musicContent = {
            MusicHomeScreen(
                onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
                onAlbumClick = { albumId -> navigator.navigate(Route.AlbumDetail(albumId)) },
                onArtistsClick = { navigator.navigate(Route.Artists) },
                onAlbumsClick = { navigator.navigate(Route.Albums) },
                onTracksClick = { navigator.navigate(Route.Tracks) },
                onGenresClick = { navigator.navigate(Route.Genres) },
                onPlaylistsClick = { navigator.navigate(Route.Playlists) },
                onNowPlayingClick = host.onNowPlayingClick,
                onAmbientClick = host.onAmbientClick,
            )
        },
    )
    librarySection(navigator)
    searchSection(navigator)
    liveTvSection(navigator)
    detailsSection(navigator)
    editorSection(navigator)
    audioPlayerSection(navigator)
    downloadsSection(navigator)
    authSection(navigator) { navigator.goBack() }
    settingsSection(
        navigator = navigator,
        onLogout = host.onLogout,
        onSetupWizard = { navigator.navigate(Route.Onboarding) },
        onCheckForUpdates = host.onCheckForUpdates,
    )
    adminSection(
        navigator = navigator,
        isAdmin = host.isAdmin,
        isRefreshingAdmin = host.isRefreshingAdmin,
        onRefreshAdmin = host.onRefreshAdmin,
    )
    musicSection(navigator)
    syncPlaySection(navigator)
    onboardingSection { navigator.goBack() }
    newsletterSection(navigator)
    insightsSection(navigator)
    requestsSection(navigator)
    arrQueueSection(navigator)
    calendarSection(navigator)
    shortcutsSection(navigator)
}

/**
 * What a shell consumes from [shellEntryProvider]: the entry provider the
 * shell's NavDisplay renders with, and the [ShellSectionRegistry] ledger over
 * the same graph.
 */
class ShellSections(
    val entryProvider: (NavKey) -> NavEntry<NavKey>,
    val registry: ShellSectionRegistry,
)

/**
 * Builds the shared shell graph in one call: [appSections] plus the shell's
 * own [extraSections], behind the sentinel fallback the registry ledger
 * distinguishes. Shells remember the result and pass `entryProvider` to
 * NavDisplay; a dead-end guard consults the registry.
 *
 * The default fallback only ever sees keys the guard should have swallowed
 * (shells without a guard register everything they push); it returns a
 * content-less [NavEntry] rather than nav3's throwing default so a stray
 * unregistered key renders blank instead of crashing the composition.
 */
fun shellEntryProvider(
    navigator: Navigator,
    host: ShellHostHooks,
    registry: ShellSectionRegistry = ShellSectionRegistry(),
    extraSections: EntryProviderScope<NavKey>.() -> Unit = {},
): ShellSections {
    val resolve: (NavKey) -> NavEntry<NavKey> = entryProvider(
        fallback = { key ->
            NavEntry(key, UnregisteredEntryContentKey, emptyMap<String, Any>(), {})
        },
    ) {
        appSections(navigator, host)
        extraSections()
    }
    registry.attach(resolve)
    return ShellSections(resolve, registry)
}
