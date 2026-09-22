package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable

/**
 * Platform file-picker seam for the client-certificate (mTLS) import flow —
 * the [BackupFilePicker] pattern, narrowed to READ-ONLY picks that deliver
 * the file's BYTES (plus a display name for the import sheet): SAF
 * content-URIs on Android and AWT `FileDialog` LOAD picks on desktop both
 * end as `readBytes` calls inside the platform actuals, so commonMain never
 * sees a URI or a stream.
 *
 * One launch surface with three roles — [CertificatePickRole.CERTIFICATE_OR_BUNDLE]
 * (`.p12`/`.pfx`/`.crt`/`.pem`), [CertificatePickRole.PRIVATE_KEY] (`.key`),
 * [CertificatePickRole.SERVER_CA] (`.pem`/`.crt`) — because Android's SAF
 * MIME handling for these extensions is unreliable (`application/x-pkcs12`
 * is rarely indexed); the actuals filter to what the role accepts where the
 * platform offers a filter at all, and the import parser re-validates the
 * content regardless (defense in depth — a `.crt` picked for the key role
 * fails the import with a clear message, never corrupts state).
 */
internal enum class CertificatePickRole {
    /** PKCS#12 bundle or PEM certificate chain. */
    CERTIFICATE_OR_BUNDLE,

    /** PEM private key (PKCS#8 or traditional RSA). */
    PRIVATE_KEY,

    /** Optional server CA override. */
    SERVER_CA,
}

/** One delivered pick: the file's bytes and a short display name. */
internal class CertificatePick(
    val bytes: ByteArray,
    val displayName: String,
)

internal interface CertificateFilePicker {

    /** Opens a read-only pick for [role]; calls back with null when cancelled. */
    fun launch(role: CertificatePickRole, onPicked: (CertificatePick?) -> Unit)
}

@Composable
internal expect fun rememberCertificateFilePicker(): CertificateFilePicker?
