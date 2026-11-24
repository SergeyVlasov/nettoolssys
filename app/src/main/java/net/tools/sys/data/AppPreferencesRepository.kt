package net.tools.sys.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.tools.sys.locale.AppLanguages

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferencesRepository(private val context: Context) {

    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val languageTagKey = stringPreferencesKey("app_language_tag")

    val useDarkTheme: Flow<Boolean> = context.appPreferencesDataStore.data.map { prefs ->
        prefs[darkThemeKey] ?: false
    }

    val appLanguageTag: Flow<String> = context.appPreferencesDataStore.data.map { prefs ->
        AppLanguages.normalizeTag(prefs[languageTagKey] ?: "")
    }

    suspend fun setUseDarkTheme(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[darkThemeKey] = enabled
        }
    }

    suspend fun setAppLanguageTag(tag: String) {
        val normalized = AppLanguages.normalizeTag(tag)
        context.appPreferencesDataStore.edit { prefs ->
            prefs[languageTagKey] = normalized
        }
    }

    suspend fun currentAppLanguageTag(): String = appLanguageTag.first()
}