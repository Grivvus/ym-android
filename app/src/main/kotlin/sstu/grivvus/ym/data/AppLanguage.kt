package sstu.grivvus.ym.data

import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(
    val languageTag: String?,
) {
    SYSTEM_DEFAULT(languageTag = null),
    ENGLISH(languageTag = Locale.ENGLISH.language),
    RUSSIAN(languageTag = "ru");

    fun toLocaleList(): LocaleList =
        languageTag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()

    companion object {
        fun fromLocaleList(localeList: LocaleList): AppLanguage {
            if (localeList.isEmpty) {
                return SYSTEM_DEFAULT
            }

            return when (localeList[0].language) {
                Locale.ENGLISH.language -> ENGLISH
                "ru" -> RUSSIAN
                else -> SYSTEM_DEFAULT
            }
        }
    }
}
