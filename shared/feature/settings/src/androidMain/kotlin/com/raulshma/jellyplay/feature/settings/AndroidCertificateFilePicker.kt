package com.raulshma.jellyplay.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android actual of the [CertificateFilePicker] seam: one SAF open-document
 * launcher whose result is routed to the pending (role, callback) pair —
 * SAF launchers are registered at composition, so the `launch(role, cb)`
 * shape (vs. BackupFilePicker's fixed callbacks) needs that indirection.
 *
 * MIME types for .p12/.pfx/.key are inconsistently indexed by SAF, so the
 * launcher opens with the any-MIME filter and the import parser validates
 * content (see the expect's KDoc). Bytes are read on Dispatchers.IO; cancel
 * (null uri) delivers null so the import sheet keeps its state.
 */
@Composable
internal actual fun rememberCertificateFilePicker(): CertificateFilePicker? {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var pending by remember {
        mutableStateOf<Pair<CertificatePickRole, ((CertificatePick?) -> Unit)>?>(null)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val callback = pending?.second
        pending = null
        if (callback == null) return@rememberLauncherForActivityResult
        if (uri == null) {
            callback(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val pick = withContext(Dispatchers.IO) {
                readPick(context, uri)
            }
            callback(pick)
        }
    }

    return remember {
        object : CertificateFilePicker {
            override fun launch(role: CertificatePickRole, onPicked: (CertificatePick?) -> Unit) {
                pending = role to onPicked
                launcher.launch(arrayOf("*/*"))
            }
        }
    }
}

private fun readPick(context: Context, uri: Uri): CertificatePick? = try {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return null
    CertificatePick(bytes = bytes, displayName = queryDisplayName(context, uri) ?: "certificate")
} catch (_: Exception) {
    null
}

private fun queryDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
} catch (_: Exception) {
    null
}
