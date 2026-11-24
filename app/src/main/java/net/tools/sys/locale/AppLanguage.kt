package net.tools.sys.locale

import androidx.annotation.StringRes
import net.tools.sys.R

data class AppLanguageOption(
    val tag: String,
    @StringRes val labelRes: Int,
)

object AppLanguages {
    /** Empty [tag] means follow the system locale. */
    val options: List<AppLanguageOption> = listOf(
        AppLanguageOption("", R.string.language_system),
        AppLanguageOption("en", R.string.language_english),
        AppLanguageOption("es", R.string.language_spanish),
        AppLanguageOption("fr", R.string.language_french),
        AppLanguageOption("de", R.string.language_german),
        AppLanguageOption("ar", R.string.language_arabic),
        AppLanguageOption("zh-CN", R.string.language_chinese),
        AppLanguageOption("ko", R.string.language_korean),
        AppLanguageOption("ja", R.string.language_japanese),
        AppLanguageOption("ru", R.string.language_russian),
    )

    fun normalizeTag(tag: String): String =
        options.firstOrNull { it.tag == tag }?.tag ?: ""
}