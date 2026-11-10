package com.raulshma.jellyplay.shell

import android.content.Intent
import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The fdroid flavor's binding of [AppUpdateCoordinator]: a permanently-idle
 * no-op. On this distribution line self-updates come from the F-Droid client
 * itself — there is no GitHub download, no installer intent, and no
 * REQUEST_INSTALL_PACKAGES — so the coordinator exists only so the shell /
 * What's-New graph can resolve the seam uniformly: [updateState] stays
 * [UpdateState.Idle] forever (the update sheet never renders, the What's New
 * sheet is never gated), and [buildInstallIntent] always returns null (the
 * installer intent seam is store-only). Deliberately does NOT extend
 * [ShellCoordinator] — nothing here has commands to scope.
 */
class NoopAppUpdateCoordinator : AppUpdateCoordinator {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _selfUpdateDownloadEnabled = MutableStateFlow(false)
    override val selfUpdateDownloadEnabled: StateFlow<Boolean> = _selfUpdateDownloadEnabled.asStateFlow()

    override fun start(scope: CoroutineScope) = Unit

    override fun onSessionRestored() = Unit

    override fun manualCheckForUpdate() = Unit

    override fun setSelfUpdateDownloadEnabled(enabled: Boolean) = Unit

    override fun startUpdateDownload(info: AppUpdateInfo) = Unit

    override fun cancelDownload() = Unit

    override fun redownloadUpdate() = Unit

    override fun buildInstallIntent(): Intent? = null

    override fun dismissUpdate() = Unit
}
