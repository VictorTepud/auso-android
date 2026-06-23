package com.auso.social.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auso_prefs")

/**
 * Manages JWT token and user info persistence using DataStore
 */
class TokenManager(private val context: Context) {

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USERNAME_KEY = stringPreferencesKey("username")
    }

    val token: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[TOKEN_KEY]
    }

    val username: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[USERNAME_KEY]
    }

    val userId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[USER_ID_KEY]
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
        }
        com.auso.social.network.AusoApiClient.setToken(token)
    }

    suspend fun saveUserInfo(userId: String, username: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_ID_KEY] = userId
            prefs[USERNAME_KEY] = username
        }
    }

    suspend fun getTokenSync(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[TOKEN_KEY]
        }.first()
    }

    suspend fun getUsernameSync(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[USERNAME_KEY]
        }.first()
    }

    suspend fun getUserIdSync(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[USER_ID_KEY]
        }.first()
    }

    suspend fun isLoggedIn(): Boolean {
        return getTokenSync() != null
    }

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
        com.auso.social.network.AusoApiClient.setToken(null)
    }
}
