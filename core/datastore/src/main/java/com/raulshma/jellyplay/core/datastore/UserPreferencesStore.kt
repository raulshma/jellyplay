package com.raulshma.jellyplay.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ACTIVE_SERVER_ID = stringPreferencesKey("active_server_id")
        val ACTIVE_USER_ID = stringPreferencesKey("active_user_id")
        val PREFERRED_PLAYER = stringPreferencesKey("preferred_player")
        val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")
        val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val DYNAMIC_THEMING = stringPreferencesKey("dynamic_theming")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            preferredPlayer = try {
                PlayerType.valueOf(prefs[Keys.PREFERRED_PLAYER] ?: PlayerType.INTERNAL.name)
            } catch (_: Exception) { PlayerType.INTERNAL },
            preferredSubtitleLanguage = prefs[Keys.PREFERRED_SUBTITLE_LANG],
            preferredAudioLanguage = prefs[Keys.PREFERRED_AUDIO_LANG],
            dynamicTheming = prefs[Keys.DYNAMIC_THEMING]?.toBoolean() ?: true,
        )
    }

    val activeServerId: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_SERVER_ID] }
    val activeUserId: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_USER_ID] }

    suspend fun setActiveServer(serverId: String) {
        context.dataStore.edit { it[Keys.ACTIVE_SERVER_ID] = serverId }
    }

    suspend fun setActiveUser(userId: String) {
        context.dataStore.edit { it[Keys.ACTIVE_USER_ID] = userId }
    }

    suspend fun setPreferredPlayer(playerType: PlayerType) {
        context.dataStore.edit { it[Keys.PREFERRED_PLAYER] = playerType.name }
    }

    suspend fun setPreferredSubtitleLanguage(language: String?) {
        context.dataStore.edit {
            if (language != null) it[Keys.PREFERRED_SUBTITLE_LANG] = language
            else it.remove(Keys.PREFERRED_SUBTITLE_LANG)
        }
    }

    suspend fun setPreferredAudioLanguage(language: String?) {
        context.dataStore.edit {
            if (language != null) it[Keys.PREFERRED_AUDIO_LANG] = language
            else it.remove(Keys.PREFERRED_AUDIO_LANG)
        }
    }

    suspend fun setDynamicTheming(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_THEMING] = enabled.toString() }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
