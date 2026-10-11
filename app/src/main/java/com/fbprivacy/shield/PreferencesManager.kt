package com.fbprivacy.shield

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fb_shield_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_AUTO_START_BOOT = booleanPreferencesKey("auto_start_boot")
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val KEY_CUSTOM_BLOCKLIST = stringSetPreferencesKey("custom_blocklist")
        val KEY_TOTAL_PACKETS = longPreferencesKey("total_packets_blocked")
        val KEY_TOTAL_BYTES = longPreferencesKey("total_bytes_blocked")
    }

    val autoStartOnBootFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_START_BOOT] ?: true
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_NOTIFICATIONS] ?: true
    }

    val customBlocklistFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_CUSTOM_BLOCKLIST] ?: emptySet()
    }

    val totalPacketsBlockedFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_TOTAL_PACKETS] ?: 0L
    }

    val totalBytesBlockedFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_TOTAL_BYTES] ?: 0L
    }

    suspend fun setAutoStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_START_BOOT] = enabled
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOTIFICATIONS] = enabled
        }
    }

    suspend fun addCustomBlockRule(rule: String) {
        val trimmed = rule.trim().lowercase()
        if (trimmed.isNotEmpty()) {
            context.dataStore.edit { preferences ->
                val current = preferences[KEY_CUSTOM_BLOCKLIST] ?: emptySet()
                preferences[KEY_CUSTOM_BLOCKLIST] = current + trimmed
            }
        }
    }

    suspend fun removeCustomBlockRule(rule: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_CUSTOM_BLOCKLIST] ?: emptySet()
            preferences[KEY_CUSTOM_BLOCKLIST] = current - rule
        }
    }

    suspend fun saveCumulativeStats(packets: Long, bytes: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TOTAL_PACKETS] = packets
            preferences[KEY_TOTAL_BYTES] = bytes
        }
    }

    suspend fun resetStats() {
        context.dataStore.edit { preferences ->
            preferences[KEY_TOTAL_PACKETS] = 0L
            preferences[KEY_TOTAL_BYTES] = 0L
        }
    }
}
