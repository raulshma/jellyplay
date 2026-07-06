package com.raulshma.jellyplay.feature.settings

import android.content.Context
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LicensesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
                val jsonText = withContext(Dispatchers.IO) {
                    context.assets.open("aboutlibraries.json").bufferedReader().use { it.readText() }
                }
                val parsed = Libs.Builder().withJson(jsonText).build()
                libraries = parsed.libraries.sortedBy { it.name.lowercase() }
            } catch (_: Exception) {
                libraries = emptyList()
                error = "Could not load license information"
            }
            isLoading = false
        }
    }
}
