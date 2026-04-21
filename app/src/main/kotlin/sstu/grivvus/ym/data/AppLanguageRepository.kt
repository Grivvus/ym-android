package sstu.grivvus.ym.data

import android.app.LocaleManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AppLanguageRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val localeManager = requireNotNull(context.getSystemService(LocaleManager::class.java))
    private val _selectedLanguage = MutableStateFlow(readSelectedLanguage())

    val selectedLanguage: StateFlow<AppLanguage> = _selectedLanguage.asStateFlow()

    fun currentSelectedLanguage(): AppLanguage = _selectedLanguage.value

    fun refreshSelectedLanguage() {
        _selectedLanguage.value = readSelectedLanguage()
    }

    fun applyLanguage(language: AppLanguage) {
        localeManager.applicationLocales = language.toLocaleList()
        _selectedLanguage.value = language
    }

    private fun readSelectedLanguage(): AppLanguage =
        AppLanguage.fromLocaleList(localeManager.applicationLocales)
}
