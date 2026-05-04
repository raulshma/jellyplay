package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onServerManagement: () -> Unit = {},
    onUserManagement: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val userName = viewModel.currentUserName

    var showPinDialog by remember { mutableStateOf(false) }
    var showPlayerPicker by remember { mutableStateOf(false) }
    var showAudioLanguagePicker by remember { mutableStateOf(false) }
    var showSubtitleLanguagePicker by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (userName.isNotBlank()) {
                Text(
                    text = "Signed in as $userName",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                HorizontalDivider()
            }

            SettingsSectionHeader("Servers")

            SettingItem(
                icon = Icons.Default.Dns,
                title = "Server Management",
                subtitle = "Switch between servers or add new ones",
                onClick = onServerManagement,
            )

            SettingItem(
                icon = Icons.Default.Person,
                title = "Switch User",
                subtitle = if (userName.isNotBlank()) "Signed in as $userName" else "Manage users on this server",
                onClick = onUserManagement,
            )

            HorizontalDivider()

            SettingsSectionHeader("Playback")

            SettingItem(
                icon = Icons.Default.PlayCircle,
                title = "Preferred Player",
                subtitle = preferences.preferredPlayer.displayName,
                onClick = { showPlayerPicker = true },
            ) {
                Text(
                    preferences.preferredPlayer.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider()

            SettingItem(
                icon = Icons.Default.RecordVoiceOver,
                title = "Dialogue Boost",
                subtitle = if (preferences.dialogueBoostEnabled) "Enhanced vocal clarity" else "Disabled",
            ) {
                Switch(
                    checked = preferences.dialogueBoostEnabled,
                    onCheckedChange = { viewModel.setDialogueBoostEnabled(it) },
                )
            }

            HorizontalDivider()

            SettingItem(
                icon = Icons.Default.Tune,
                title = "Equalizer",
                subtitle = if (preferences.equalizerEnabled) "Custom 10-band" else "Disabled",
            ) {
                Switch(
                    checked = preferences.equalizerEnabled,
                    onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                )
            }

            HorizontalDivider()

            SettingItem(
                icon = Icons.Default.Speed,
                title = "Night Mode Audio",
                subtitle = if (preferences.nightModeEnabled) "Dynamic range compression" else "Disabled",
            ) {
                Switch(
                    checked = preferences.nightModeEnabled,
                    onCheckedChange = { viewModel.setNightModeEnabled(it) },
                )
            }

            HorizontalDivider()

            SettingItem(
                icon = Icons.Default.HighQuality,
                title = "Decoder Mode",
                subtitle = preferences.decoderMode.displayName,
                onClick = {
                    val modes = DecoderMode.entries
                    val currentIndex = modes.indexOf(preferences.decoderMode)
                    val nextIndex = (currentIndex + 1) % modes.size
                    viewModel.setDecoderMode(modes[nextIndex])
                },
            ) {
                Text(
                    preferences.decoderMode.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider()

            SettingItem(
                icon = Icons.Default.PlayCircle,
                title = "Audio Passthrough",
                subtitle = if (preferences.audioPassthrough) "Direct audio to receiver" else "Disabled",
            ) {
                Switch(
                    checked = preferences.audioPassthrough,
                    onCheckedChange = { viewModel.setAudioPassthrough(it) },
                )
            }

            HorizontalDivider()

            SettingItem(
                icon = Icons.Default.Speed,
                title = "Frame Rate Matching",
                subtitle = if (preferences.frameRateMatching) "Match display to content" else "Disabled",
            ) {
                Switch(
                    checked = preferences.frameRateMatching,
                    onCheckedChange = { viewModel.setFrameRateMatching(it) },
                )
            }

            HorizontalDivider()

            SettingItem(
                icon = Icons.Default.HighQuality,
                title = "Streaming Quality",
                subtitle = when (preferences.streamingQuality) {
                    StreamingQuality.AUTO -> "Auto (adaptive)"
                    StreamingQuality.LOW_360P -> "360p (Low)"
                    StreamingQuality.SD_480P -> "480p (SD)"
                    StreamingQuality.HD_720P -> "720p (HD)"
                    StreamingQuality.FHD_1080P -> "1080p (Full HD)"
                    StreamingQuality.UHD_4K -> "4K (Ultra HD)"
                },
                onClick = {
                    val qualities = StreamingQuality.entries
                    val currentIndex = qualities.indexOf(preferences.streamingQuality)
                    val nextIndex = (currentIndex + 1) % qualities.size
                    viewModel.setStreamingQuality(qualities[nextIndex])
                },
            ) {
                Text(
                    when (preferences.streamingQuality) {
                        StreamingQuality.AUTO -> "Auto"
                        StreamingQuality.LOW_360P -> "360p"
                        StreamingQuality.SD_480P -> "480p"
                        StreamingQuality.HD_720P -> "720p"
                        StreamingQuality.FHD_1080P -> "1080p"
                        StreamingQuality.UHD_4K -> "4K"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider()

            SettingsSectionHeader("Language")

            SettingItem(
                icon = Icons.Default.Language,
                title = "Preferred Audio Language",
                subtitle = preferences.preferredAudioLanguage ?: "Default",
                onClick = { showAudioLanguagePicker = true },
            )

            HorizontalDivider()

            SettingItem(
                icon = Icons.Default.ClosedCaption,
                title = "Preferred Subtitle Language",
                subtitle = preferences.preferredSubtitleLanguage ?: "Default",
                onClick = { showSubtitleLanguagePicker = true },
            )

            HorizontalDivider()

            SettingsSectionHeader("Storage")

            SettingItem(
                icon = Icons.Default.Storage,
                title = "Cache Size",
                subtitle = "${viewModel.cacheSizeMb} MB used",
            )

            SettingItem(
                icon = Icons.Default.Delete,
                title = "Clear Cache",
                subtitle = "Free up storage space",
                onClick = { viewModel.clearCache() },
            )

            HorizontalDivider()

            SettingItem(
                icon = Icons.Default.Cached,
                title = "Auto-delete Cache",
                subtitle = "Automatically clear cache when storage is low",
            ) {
                Switch(
                    checked = preferences.autoDeleteCache,
                    onCheckedChange = { viewModel.setAutoDeleteCache(it) },
                )
            }

            SettingItem(
                icon = Icons.Default.Speed,
                title = "Max Cache Size",
                subtitle = "${preferences.maxCacheSizeMb} MB",
                onClick = {
                    val sizes = listOf(250, 500, 1000, 2000, 5000)
                    val currentIndex = sizes.indexOf(preferences.maxCacheSizeMb)
                    val nextIndex = (currentIndex + 1) % sizes.size
                    viewModel.setMaxCacheSize(sizes[nextIndex])
                },
            ) {
                Text(
                    "${preferences.maxCacheSizeMb} MB",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider()

            SettingsSectionHeader("Appearance")

            SettingItem(
                title = "Dynamic Theming",
                subtitle = "Color theme from media artwork",
            ) {
                Switch(
                    checked = preferences.dynamicTheming,
                    onCheckedChange = { viewModel.setDynamicTheming(it) },
                )
            }

            HorizontalDivider()

            SettingsSectionHeader("Security")

            SettingItem(
                icon = if (preferences.pinLockEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                title = "PIN Lock",
                subtitle = if (preferences.pinLockEnabled) "Enabled" else "Disabled",
                onClick = {
                    if (preferences.pinLockEnabled) {
                        viewModel.clearPin()
                    } else {
                        showPinDialog = true
                    }
                },
            ) {
                Switch(
                    checked = preferences.pinLockEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showPinDialog = true
                        } else {
                            viewModel.clearPin()
                        }
                    },
                )
            }

            HorizontalDivider()

            SettingItem(
                icon = Icons.Default.ChildCare,
                title = "Kids Mode",
                subtitle = if (preferences.kidsModeEnabled) "Enabled (${preferences.kidsModeMaxRating})" else "Disabled",
                onClick = {
                    val ratings = listOf("G", "PG", "PG-13", "TV-Y", "TV-Y7", "TV-G", "TV-PG")
                    val nextRating = ratings[(ratings.indexOf(preferences.kidsModeMaxRating) + 1) % ratings.size]
                    viewModel.setKidsModeMaxRating(nextRating)
                },
            ) {
                Switch(
                    checked = preferences.kidsModeEnabled,
                    onCheckedChange = { viewModel.setKidsModeEnabled(it) },
                )
            }

            HorizontalDivider()

            SettingsSectionHeader("About")

            SettingItem(
                title = "Version",
                subtitle = "JellyPlay v1.0.0",
            )

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = {
                    viewModel.logout()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Sign Out")
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pinInput = ""
                pinConfirm = ""
                pinError = null
            },
            title = { Text("Set PIN") },
            text = {
                Column {
                    TextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pinInput = it
                                pinError = null
                            }
                        },
                        label = { Text("4-digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = pinError != null,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = pinConfirm,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pinConfirm = it
                                pinError = null
                            }
                        },
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = pinError != null,
                    )
                    if (pinError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            pinError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when {
                            pinInput.length != 4 -> pinError = "PIN must be 4 digits"
                            pinInput != pinConfirm -> pinError = "PINs do not match"
                            else -> {
                                viewModel.setPin(pinInput)
                                showPinDialog = false
                                pinInput = ""
                                pinConfirm = ""
                                pinError = null
                            }
                        }
                    },
                ) {
                    Text("Set")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPinDialog = false
                        pinInput = ""
                        pinConfirm = ""
                        pinError = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showPlayerPicker) {
        AlertDialog(
            onDismissRequest = { showPlayerPicker = false },
            title = { Text("Preferred Player") },
            text = {
                Column {
                    PlayerType.entries.forEach { player ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setPreferredPlayer(player)
                                    showPlayerPicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = preferences.preferredPlayer == player,
                                onClick = {
                                    viewModel.setPreferredPlayer(player)
                                    showPlayerPicker = false
                                },
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    player.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    player.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlayerPicker = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showAudioLanguagePicker) {
        LanguagePickerDialog(
            currentLanguage = preferences.preferredAudioLanguage,
            onDismiss = { showAudioLanguagePicker = false },
            onSelect = { language ->
                viewModel.setPreferredAudioLanguage(language)
                showAudioLanguagePicker = false
            },
        )
    }

    if (showSubtitleLanguagePicker) {
        LanguagePickerDialog(
            currentLanguage = preferences.preferredSubtitleLanguage,
            onDismiss = { showSubtitleLanguagePicker = false },
            onSelect = { language ->
                viewModel.setPreferredSubtitleLanguage(language)
                showSubtitleLanguagePicker = false
            },
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

private val languages = listOf(
    null to "Default",
    "aar" to "Afar",
    "abk" to "Abkhazian",
    "afr" to "Afrikaans",
    "aka" to "Akan",
    "alb" to "Albanian",
    "amh" to "Amharic",
    "ara" to "Arabic",
    "arg" to "Aragonese",
    "arm" to "Armenian",
    "asm" to "Assamese",
    "ava" to "Avaric",
    "ave" to "Avestan",
    "aym" to "Aymara",
    "aze" to "Azerbaijani",
    "bak" to "Bashkir",
    "bam" to "Bambara",
    "baq" to "Basque",
    "bel" to "Belarusian",
    "ben" to "Bengali",
    "bih" to "Bihari",
    "bis" to "Bislama",
    "bos" to "Bosnian",
    "bre" to "Breton",
    "bul" to "Bulgarian",
    "bur" to "Burmese",
    "cat" to "Catalan",
    "cha" to "Chamorro",
    "che" to "Chechen",
    "chi" to "Chinese",
    "chu" to "Church Slavic",
    "chv" to "Chuvash",
    "cor" to "Cornish",
    "cos" to "Corsican",
    "cre" to "Cree",
    "cze" to "Czech",
    "dan" to "Danish",
    "div" to "Divehi",
    "dut" to "Dutch",
    "dzo" to "Dzongkha",
    "eng" to "English",
    "epo" to "Esperanto",
    "est" to "Estonian",
    "ewe" to "Ewe",
    "fao" to "Faroese",
    "fij" to "Fijian",
    "fin" to "Finnish",
    "fre" to "French",
    "fry" to "Western Frisian",
    "ful" to "Fulah",
    "geo" to "Georgian",
    "ger" to "German",
    "gla" to "Scottish Gaelic",
    "gle" to "Irish",
    "glg" to "Galician",
    "glv" to "Manx",
    "grn" to "Guarani",
    "guj" to "Gujarati",
    "hat" to "Haitian",
    "hau" to "Hausa",
    "heb" to "Hebrew",
    "her" to "Herero",
    "hin" to "Hindi",
    "hmo" to "Hiri Motu",
    "hrv" to "Croatian",
    "hun" to "Hungarian",
    "ibo" to "Igbo",
    "ice" to "Icelandic",
    "ido" to "Ido",
    "iii" to "Sichuan Yi",
    "iku" to "Inuktitut",
    "ile" to "Interlingue",
    "ina" to "Interlingua",
    "ind" to "Indonesian",
    "ipk" to "Inupiaq",
    "ita" to "Italian",
    "jav" to "Javanese",
    "jpn" to "Japanese",
    "kal" to "Kalaallisut",
    "kan" to "Kannada",
    "kas" to "Kashmiri",
    "kau" to "Kanuri",
    "kaz" to "Kazakh",
    "khm" to "Central Khmer",
    "kik" to "Kikuyu",
    "kin" to "Kinyarwanda",
    "kir" to "Kirghiz",
    "kom" to "Komi",
    "kon" to "Kongo",
    "kor" to "Korean",
    "kua" to "Kuanyama",
    "kur" to "Kurdish",
    "lao" to "Lao",
    "lat" to "Latin",
    "lav" to "Latvian",
    "lim" to "Limburgan",
    "lin" to "Lingala",
    "lit" to "Lithuanian",
    "ltz" to "Luxembourgish",
    "lub" to "Luba-Katanga",
    "lug" to "Ganda",
    "mac" to "Macedonian",
    "mah" to "Marshallese",
    "mal" to "Malayalam",
    "mao" to "Maori",
    "mar" to "Marathi",
    "may" to "Malay",
    "mlg" to "Malagasy",
    "mlt" to "Maltese",
    "mon" to "Mongolian",
    "nau" to "Nauru",
    "nav" to "Navajo",
    "nbl" to "South Ndebele",
    "nde" to "North Ndebele",
    "ndo" to "Ndonga",
    "nep" to "Nepali",
    "nno" to "Norwegian Nynorsk",
    "nob" to "Norwegian Bokmål",
    "nor" to "Norwegian",
    "nya" to "Chichewa",
    "oci" to "Occitan",
    "oji" to "Ojibwa",
    "ori" to "Oriya",
    "orm" to "Oromo",
    "oss" to "Ossetian",
    "pan" to "Panjabi",
    "per" to "Persian",
    "pli" to "Pali",
    "pol" to "Polish",
    "por" to "Portuguese",
    "pus" to "Pushto",
    "que" to "Quechua",
    "roh" to "Romansh",
    "rum" to "Romanian",
    "run" to "Rundi",
    "rus" to "Russian",
    "sag" to "Sango",
    "san" to "Sanskrit",
    "sin" to "Sinhala",
    "slo" to "Slovak",
    "slv" to "Slovenian",
    "sme" to "Northern Sami",
    "smo" to "Samoan",
    "sna" to "Shona",
    "snd" to "Sindhi",
    "som" to "Somali",
    "sot" to "Southern Sotho",
    "spa" to "Spanish",
    "srd" to "Sardinian",
    "srp" to "Serbian",
    "ssw" to "Swati",
    "sun" to "Sundanese",
    "swa" to "Swahili",
    "swe" to "Swedish",
    "tah" to "Tahitian",
    "tam" to "Tamil",
    "tat" to "Tatar",
    "tel" to "Telugu",
    "tgk" to "Tajik",
    "tgl" to "Tagalog",
    "tha" to "Thai",
    "tib" to "Tibetan",
    "tir" to "Tigrinya",
    "ton" to "Tonga",
    "tsn" to "Tswana",
    "tso" to "Tsonga",
    "tuk" to "Turkmen",
    "tur" to "Turkish",
    "twi" to "Twi",
    "uig" to "Uighur",
    "ukr" to "Ukrainian",
    "urd" to "Urdu",
    "uzb" to "Uzbek",
    "ven" to "Venda",
    "vie" to "Vietnamese",
    "vol" to "Volapük",
    "wel" to "Welsh",
    "wln" to "Walloon",
    "wol" to "Wolof",
    "xho" to "Xhosa",
    "yid" to "Yiddish",
    "yor" to "Yoruba",
    "zha" to "Zhuang",
    "zul" to "Zulu",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerDialog(
    currentLanguage: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Language") },
        text = {
            LazyColumn {
                items(languages) { (code, name) ->
                    val isSelected = code == currentLanguage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(code)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(code) },
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    onClick: () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}
