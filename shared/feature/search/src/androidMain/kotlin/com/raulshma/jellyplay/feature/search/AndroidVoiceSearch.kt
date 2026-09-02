package com.raulshma.jellyplay.feature.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
internal actual fun rememberVoiceSearchLauncher(
    prompt: String,
    onResult: (String?) -> Unit,
): (() -> Unit)? {
    val context = LocalContext.current
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                onResult(spokenText)
            }
        }
    }
    return remember(prompt) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        // Guard against devices/TVs without a speech-recognition activity.
        val activityAvailable = intent.resolveActivity(context.packageManager) != null
        if (activityAvailable) {
            { speechLauncher.launch(intent) }
        } else {
            null
        }
    }
}
