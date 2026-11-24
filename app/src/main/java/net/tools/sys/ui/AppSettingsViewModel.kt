package net.tools.sys.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.tools.sys.data.AppPreferencesRepository
import net.tools.sys.locale.AppLanguages
import net.tools.sys.locale.AppLocale

class AppSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferencesRepository(application)

    val useDarkTheme: StateFlow<Boolean> = preferences.useDarkTheme.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    val appLanguageTag: StateFlow<String> = preferences.appLanguageTag.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "",
    )

    fun setUseDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setUseDarkTheme(enabled)
        }
    }

    fun setAppLanguage(tag: String) {
        viewModelScope.launch {
            preferences.setAppLanguageTag(tag)
            AppLocale.apply(AppLanguages.normalizeTag(tag))
        }
    }
}