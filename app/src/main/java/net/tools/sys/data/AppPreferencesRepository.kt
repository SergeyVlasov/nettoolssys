package net.tools.sys.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferencesRepository(private val context: Context) {

    private val darkThemeKey = booleanPreferencesKey("dark_theme")

    val useDarkTheme: Flow<Boolean> = context.appPreferencesDataStore.data.map { prefs ->
        prefs[darkThemeKey] ?: false
    }

    suspend fun setUseDarkTheme(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[darkThemeKey] = enabled
        }
    }
}