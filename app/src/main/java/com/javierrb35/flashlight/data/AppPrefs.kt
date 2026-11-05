package com.javierrb35.flashlight

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "flashlight_prefs")

/** Small persisted settings; everything else keeps the original behaviour. */
data class Prefs(
    val keepScreenOn: Boolean = true,
    val strobeHz: Int = StrobeTiming.MIN_FLASHES_PER_SECOND,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
)

class AppPrefs(private val context: Context) {

    private object Keys {
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val STROBE_HZ = intPreferencesKey("strobe_hz")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }

    val prefs: Flow<Prefs> = context.dataStore.data.map { p ->
        Prefs(
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true,
            strobeHz = p[Keys.STROBE_HZ] ?: StrobeTiming.MIN_FLASHES_PER_SECOND,
            themeMode = p[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: false,
        )
    }

    suspend fun setKeepScreenOn(value: Boolean) =
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = value }

    suspend fun setStrobeHz(value: Int) =
        context.dataStore.edit { it[Keys.STROBE_HZ] = value }

    suspend fun setThemeMode(value: ThemeMode) =
        context.dataStore.edit { it[Keys.THEME_MODE] = value.name }

    suspend fun setDynamicColor(value: Boolean) =
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = value }
}
