package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.ui.platform.pickAwtFile
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_certificate_pick_ca_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_certificate_pick_certificate_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_certificate_pick_key_title
import java.io.File
import org.jetbrains.compose.resources.stringResource

/**
 * Desktop actual of the [CertificateFilePicker] seam: the shared AWT dialog
 * ([pickAwtFile]) in LOAD mode, one per role (the title names the role —
 * resolved through the module's compose-resources like every other
 * user-facing string, so the dialog follows the app language). Showing the
 * modal dialog straight from the click callback follows the
 * [DesktopBackupFilePicker] precedent — Compose desktop's UI thread is the
 * AWT EDT, so the dialog blocks the click handler while it is up and resumes
 * with the pick. Cancelling delivers null so the import sheet keeps its
 * state (the SAF launcher behaves the same way on Android).
 */
@Composable
internal actual fun rememberCertificateFilePicker(): CertificateFilePicker? {
    val certificateTitle = stringResource(Res.string.settings_certificate_pick_certificate_title)
    val keyTitle = stringResource(Res.string.settings_certificate_pick_key_title)
    val caTitle = stringResource(Res.string.settings_certificate_pick_ca_title)
    return remember(certificateTitle, keyTitle, caTitle) {
        object : CertificateFilePicker {
            override fun launch(role: CertificatePickRole, onPicked: (CertificatePick?) -> Unit) {
                val title = when (role) {
                    CertificatePickRole.CERTIFICATE_OR_BUNDLE -> certificateTitle
                    CertificatePickRole.PRIVATE_KEY -> keyTitle
                    CertificatePickRole.SERVER_CA -> caTitle
                }
                val picked: File? = pickAwtFile(title = title)
                if (picked == null) {
                    onPicked(null)
                    return
                }
                val bytes = try {
                    picked.readBytes()
                } catch (_: Exception) {
                    null
                }
                onPicked(bytes?.let { CertificatePick(bytes = it, displayName = picked.name) })
            }
        }
    }
}
