package com.raulshma.jellyplay.feature.settings

import android.content.Context
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
data class LicenseEntry(
    val name: String,
    val license: String = "",
    val version: String = "",
    val description: String = "",
    val url: String = "",
)

@Serializable
data class LicensesFile(
    val licenses: Map<String, String> = emptyMap(),
    val dependencies: List<LicenseEntry> = emptyList(),
)

@HiltViewModel
class LicensesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : JellyPlayViewModel() {

    var licenses by composeState<List<LicenseEntry>>(emptyList())
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
                val json = withContext(Dispatchers.IO) {
                    context.assets.open("licenses.json").bufferedReader().use { it.readText() }
                }
                val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<LicensesFile>(json)
                licenses = parsed.dependencies.sortedBy { it.name.lowercase() }
            } catch (_: Exception) {
                licenses = emptyList()
                error = "Could not load license information"
            }
            isLoading = false
        }
    }
}
