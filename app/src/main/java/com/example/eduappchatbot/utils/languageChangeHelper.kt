package com.example.eduappchatbot.utils

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguageChangeHelper {

    private fun toIetfTag(languageCode: String): String {
        return when (languageCode.lowercase()) {
            "en" -> "en-IN"
            "kn" -> "kn-IN"
            "hi" -> "hi-IN"
            "ta" -> "ta-IN"
            "te" -> "te-IN"
            else -> languageCode
        }
    }

    fun changeLanguage(context: Context, languageCode: String) {
        val tag = toIetfTag(languageCode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val localeList = LocaleList.forLanguageTags(tag)
            localeManager?.applicationLocales = localeList
        } else {
            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(tag)
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }

    fun getCurrentLanguageCode(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val currentList = localeManager?.applicationLocales
            val first = currentList?.get(0)
            first?.toLanguageTag() ?: defaultLanguageCode()
        } else {
            val currentList = AppCompatDelegate.getApplicationLocales()
            val firstLocale = if (currentList.size() > 0) currentList.get(0) else null
            firstLocale?.toLanguageTag() ?: defaultLanguageCode()
        }
    }

    private fun defaultLanguageCode(): String = "en-IN"
}
