package com.raulshma.jellyplay.feature.settings

import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_licenses_load_error

class LicensesViewModel(
    private val jsonSource: AboutLibrariesJsonSource,
) : JellyPlayViewModel() {

    var libraries by composeState<List<Library>>(emptyList())
        private set

    var isLoading by composeState(true)
        private set

    var error by composeState<String?>(null)
        private set

    init {
        loadLicenses()
    }

    private fun loadLicenses() {
        launch {
            isLoading = true
            error = null
            try {
                val jsonText = withContext(Dispatchers.IO) { jsonSource.read() }
                if (jsonText == null) {
                    libraries = emptyList()
                    error = getString(Res.string.settings_licenses_load_error)
                } else {
                    // Parse + sort off the main thread — the aboutlibraries
                    // JSON is ~167 KB, heavy enough to drop a frame on open.
                    libraries = withContext(Dispatchers.Default) {
                        val parsed = Libs.Builder().withJson(jsonText).build()
                        parsed.libraries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                libraries = emptyList()
                error = getString(Res.string.settings_licenses_load_error)
            }
            isLoading = false
        }
    }
}
