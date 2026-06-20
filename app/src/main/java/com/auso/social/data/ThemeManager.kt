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

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "auso_theme_prefs")

/**
 * Manages theme preference persistence using DataStore.
 * Values: "dark", "light", "system" (default)
 */
class ThemeManager(private val context: Context) {

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme_mode")

        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
        const val THEME_SYSTEM = "system"
    }

    val themeFlow: Flow<String> = context.themeDataStore.data.map { prefs ->
        prefs[THEME_KEY] ?: THEME_SYSTEM
    }

    suspend fun setTheme(theme: String) {
        context.themeDataStore.edit { prefs ->
            prefs[THEME_KEY] = theme
        }
    }

    suspend fun getThemeSync(): String {
        return context.themeDataStore.data.map { prefs ->
            prefs[THEME_KEY] ?: THEME_SYSTEM
        }.first()
    }
}
