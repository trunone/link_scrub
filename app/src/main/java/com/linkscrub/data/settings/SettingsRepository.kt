package com.linkscrub.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SERVER_BASE_URL = stringPreferencesKey("server_base_url")
        val ALLOW_CLEARTEXT = booleanPreferencesKey("allow_cleartext")
        val DEFAULT_TAGS = stringPreferencesKey("default_tags")
        val AUTO_SHORTEN = booleanPreferencesKey("auto_shorten")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val serverBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.SERVER_BASE_URL] ?: ""
    }

    val allowCleartext: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ALLOW_CLEARTEXT] ?: false
    }

    val defaultTags: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_TAGS] ?: ""
    }

    val autoShorten: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_SHORTEN] ?: false
    }

    val historyEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HISTORY_ENABLED] ?: true
    }

    fun getApiKey(): String {
        return encryptedPrefs.getString("shlink_api_key", "") ?: ""
    }

    fun saveApiKey(apiKey: String) {
        encryptedPrefs.edit().putString("shlink_api_key", apiKey).apply()
    }

    suspend fun updateServerBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_BASE_URL] = url
        }
    }

    suspend fun updateAllowCleartext(allow: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ALLOW_CLEARTEXT] = allow
        }
    }

    suspend fun updateDefaultTags(tags: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_TAGS] = tags
        }
    }

    suspend fun updateAutoShorten(autoShorten: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_SHORTEN] = autoShorten
        }
    }

    suspend fun updateHistoryEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HISTORY_ENABLED] = enabled
        }
    }
}
