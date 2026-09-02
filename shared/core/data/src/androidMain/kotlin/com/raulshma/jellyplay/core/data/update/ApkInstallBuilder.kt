package com.raulshma.jellyplay.core.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android seam left behind when [AppUpdateRepository] moved to
 * :shared:core:data jvmShared (Wave xB): the install intent needs
 * `android.content.Intent` + `FileProvider`, so it cannot live in the shared
 * interface. The shell's UpdateCoordinator injects this instead of calling the
 * old repository method. Impl body is byte-identical to the legacy
 * AppUpdateRepositoryImpl.buildInstallIntent.
 */
fun interface ApkInstallBuilder {

    /**
     * Builds a launchable [Intent] that asks the system package installer to
     * install [apkFile] via its FileProvider content URI. The caller
     * `startActivity`s it.
     */
    fun buildInstallIntent(apkFile: File): Intent
}

/** Android actual: ACTION_VIEW over the app's `${packageName}.fileprovider` authority. */
class ApkInstallBuilderImpl(
    private val context: Context,
) : ApkInstallBuilder {

    override fun buildInstallIntent(apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    companion object {
        private const val MIME_APK = "application/vnd.android.package-archive"
    }
}
