package com.zipstats.app.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zipstats.app.ui.theme.ColorTheme
import com.zipstats.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val SETTINGS_NAME = "settings"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_NAME)

@Singleton
class SettingsRepository @Inject constructor(
    private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PURE_BLACK_OLED = booleanPreferencesKey("pure_black_oled")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.THEME_MODE] ?: "SYSTEM") {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val colorThemeFlow: Flow<ColorTheme> = context.dataStore.data.map { prefs ->
        ColorTheme.fromString(prefs[Keys.COLOR_THEME] ?: "RIDE_BLUE")
    }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC_COLOR] ?: true
    }

    val pureBlackOledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.PURE_BLACK_OLED] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = when (mode) {
                ThemeMode.LIGHT -> "LIGHT"
                ThemeMode.DARK -> "DARK"
                ThemeMode.SYSTEM -> "SYSTEM"
            }
        }
    }

    suspend fun setColorTheme(theme: ColorTheme) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COLOR_THEME] = theme.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setPureBlackOled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PURE_BLACK_OLED] = enabled
        }
    }
}


