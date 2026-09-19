package com.raulshma.jellyplay.desktop.update

import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.feature.shell.ShellSessionController
import com.raulshma.jellyplay.feature.shell.UpdateCheckMessage
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The About row's "Check for updates" controller (extracted from
 * DesktopAppRoot; docs/adr/desktop-auto-update.md). One command —
 * [checkForUpdate] — running the exact flow the scaffold used to inline:
 * repository check → shared message mapping
 * ([ShellSessionController.updateCheckMessage], ADR 0001's split) →, on
 * UpdateAvailable ONLY, [DesktopUpdateLinks.pick] + browser handoff
 * ([DesktopUpdateBrowser.openOrNull], off the event thread) → snackbar
 * wording through [desktopUpdateText].
 *
 * ADR CONTRACT (pinned by DesktopUpdateCheckControllerTest): there is NEVER
 * a silent download or install on desktop — this type has NO download/install
 * surface at all (it holds the check call only; `downloadUpdate` /
 * `getPendingUpdate` / `cleanupDownloadedUpdate` are unreachable from here
 * by construction), and the ONLY handoff is the user's browser opening the
 * release page. The user downloads and runs the installer themselves
 * (Windows: the MSI's fixed upgradeUuid major-upgrades in place); when no
 * browser is reachable the snackbar names the releases page instead.
 *
 * @param scope the shell's composition scope — the launched job dies with
 *   the scaffold, exactly like the inline `scope.launch` it replaces.
 * @param repository the Koin-resolved single (the desktopAppUpdateModule
 *   override: release-lane builds report genuine releases, dev builds stay
 *   "up to date" by construction).
 * @param showMessage the snackbar sink.
 * @param openInBrowser the browser handoff; injected so tests pin the
 * handoff WITHOUT an AWT desktop.
 * @param ioDispatcher where the browser handoff runs (off the event
 * thread); tests pass [EmptyCoroutineContext] to stay on the test
 * scheduler.
 */
internal class DesktopUpdateCheckController(
    private val scope: CoroutineScope,
    private val repository: AppUpdateRepository,
    private val showMessage: suspend (String) -> Unit,
    private val openInBrowser: (String) -> Boolean = DesktopUpdateBrowser::openOrNull,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) {
    fun checkForUpdate() {
        scope.launch { showMessage(runCheck()) }
    }

    private suspend fun runCheck(): String {
        val result = repository.checkForUpdate()
        val message = ShellSessionController.updateCheckMessage(result)
        val opened = message is UpdateCheckMessage.UpdateAvailable &&
            withContext(ioDispatcher) {
                DesktopUpdateLinks.pick(result.getOrNull())?.let(openInBrowser) == true
            }
        return message.desktopUpdateText(opened)
    }
}

/**
 * Desktop rendering of the shared [UpdateCheckMessage] — ADR 0001's split:
 * the check→message mapping lives in ShellSessionController (commonMain),
 * the WORDING is this shell's own surface. The available branch is LIVE
 * (ADR desktop-auto-update: desktopAppUpdateModule replaced the sentinel, so
 * a release-lane build can genuinely report a newer release) and pairs the
 * version announcement with the browser-handoff outcome:
 * [openedReleasePage] means AWT already opened the release page, otherwise
 * the message points at the releases page the user can visit manually. Dev
 * builds are suppressed repository-side and always read "up to date".
 */
internal fun UpdateCheckMessage.desktopUpdateText(openedReleasePage: Boolean = false): String = when (this) {
    is UpdateCheckMessage.UpdateAvailable ->
        if (openedReleasePage) {
            "Version $latestVersion is available — opening the release page in your browser."
        } else {
            "Version $latestVersion is available — get the installer from ${DesktopUpdateLinks.RELEASES_PAGE_URL}"
        }
    UpdateCheckMessage.UpToDate -> "You're up to date"
    is UpdateCheckMessage.Failed -> "Update check failed: ${reason ?: "unknown error"}"
}
