package com.example.ui.localization

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.LayoutDirection

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flag: String,
    val isRtl: Boolean = false
) {
    ENGLISH("en", "English", "English", "🇺🇸", false),
    FRENCH("fr", "French", "Français", "🇫🇷", false),
    PORTUGUESE("pt", "Portuguese", "Português", "🇵🇹", false),
    ARABIC("ar", "Arabic", "العربية", "🇸🇦", true);

    val layoutDirection: LayoutDirection
        get() = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

val LocalAppStrings = compositionLocalOf<AppStrings> {
    EnglishStrings
}

val LocalAppLanguage = compositionLocalOf<AppLanguage> {
    AppLanguage.ENGLISH
}
