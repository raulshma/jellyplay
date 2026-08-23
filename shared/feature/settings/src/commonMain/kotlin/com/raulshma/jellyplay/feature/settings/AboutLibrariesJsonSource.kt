package com.raulshma.jellyplay.feature.settings

/**
 * Platform seam feeding [LicensesViewModel] (V3 settings conveyor): only the
 * raw `aboutlibraries.json` read is platform-shaped — Android opens the
 * bundled asset, desktop reads the classpath resource; null means the file
 * could not be read, and the ViewModel routes that into its existing
 * load-error state. Parsing stays in the ViewModel (aboutlibraries core).
 */
fun interface AboutLibrariesJsonSource {
    fun read(): String?
}
