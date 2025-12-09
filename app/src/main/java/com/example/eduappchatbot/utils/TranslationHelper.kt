package com.example.eduappchatbot.utils

import android.content.Context
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.core.content.edit
import com.google.mlkit.nl.translate.Translator

object TranslationHelper {

    suspend fun translateList(
        context: Context,
        serverList: List<String>,
        sourceShort: String = "en",
        targetShort: String
    ): List<String> {
        if (targetShort.isBlank()) {
            return serverList
        }

        val cacheKey = cacheKeyFor(sourceShort, targetShort)
        getCachedList(context, cacheKey)?.let { cached ->
            if (cached.size == serverList.size) return cached
        }

        return try {
            val translated = translateListWithMlKit(serverList, sourceShort, targetShort)
            saveCacheList(context, cacheKey, translated)
            translated
        } catch (e: Exception) {
            DebugLogger.errorLog("TranslationHelper", "translateList failed: ${e.message}")
            serverList
        }
    }

    suspend fun translateText(
        text: String,
        sourceShort: String = "en",
        targetShort: String
    ): String {
        if (text.isBlank() || targetShort.isBlank()) {
            return text
        }

        return try {
            DebugLogger.debugLog(
                "TranslationHelper",
                "Translating: $sourceShort → $targetShort (${text.length} chars)"
            )

            val list = translateListWithMlKit(listOf(text), sourceShort, targetShort)
            val result = list.firstOrNull() ?: text

            // Fixed capitalization issues
            val fixedResult = fixCapitalization(result)

            DebugLogger.debugLog(
                "TranslationHelper",
                "Translation result: ${fixedResult.take(50)}..."
            )

            fixedResult
        } catch (e: Exception) {
            DebugLogger.errorLog("TranslationHelper", "Translation failed: ${e.message}")
            text  // Return original on failure
        }
    }

    private fun fixCapitalization(text: String): String {
        if (text.isBlank()) return text

        val result = StringBuilder()
        var shouldCapitalize = true

        for (char in text) {
            when {
                shouldCapitalize && char.isLetter() -> {
                    result.append(char.uppercaseChar())
                    shouldCapitalize = false
                }
                char == '.' || char == '!' || char == '?' -> {
                    result.append(char)
                    shouldCapitalize = true
                }
                char == ' ' && shouldCapitalize -> {
                    result.append(char)
                }
                else -> {
                    result.append(if (char.isLetter()) char.lowercaseChar() else char)
                }
            }
        }

        return result.toString()
    }

    fun clearCache(context: Context, sourceShort: String, targetShort: String) {
        val prefs = context.getSharedPreferences("translation_cache", Context.MODE_PRIVATE)
        prefs.edit { remove(cacheKeyFor(sourceShort, targetShort)) }
    }

    private fun cacheKeyFor(sourceShort: String, targetShort: String): String {
        return "concepts_${sourceShort.lowercase()}_${targetShort.lowercase()}"
    }

    private fun getCachedList(context: Context, cacheKey: String): List<String>? {
        val prefs = context.getSharedPreferences("translation_cache", Context.MODE_PRIVATE)
        val raw = prefs.getString(cacheKey, null) ?: return null
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> arr.optString(i, "") }
        } catch (e: Exception) {
            DebugLogger.errorLog("TranslationHelper", "getCachedList failed: ${e.message}")
            null
        }
    }

    private fun saveCacheList(context: Context, cacheKey: String, list: List<String>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            val prefs = context.getSharedPreferences("translation_cache", Context.MODE_PRIVATE)
            prefs.edit { putString(cacheKey, arr.toString()) }
        } catch (e: Exception) {
            DebugLogger.errorLog("TranslationHelper", "saveCacheList failed: ${e.message}")
        }
    }

    private suspend fun translateListWithMlKit(
        list: List<String>,
        sourceShort: String,
        targetShort: String
    ): List<String> {
        val src = mlKitLangCode(sourceShort)
        val tgt = mlKitLangCode(targetShort)

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(src)
            .setTargetLanguage(tgt)
            .build()

        val translator = Translation.getClient(options)
        return try {
            downloadModelIfNeeded(translator)
            val results = mutableListOf<String>()
            for (item in list) {
                val translated = translateTextSuspend(translator, item)
                results.add(fixCapitalization(translated))
            }
            results
        } finally {
            try {
                translator.close()
            } catch (e: Exception) {
                DebugLogger.errorLog("TranslationHelper", "Translator close failed: ${e.message}")
            }
        }
    }

    private fun mlKitLangCode(short: String): String {
        return when (short.lowercase()) {
            "en" -> TranslateLanguage.ENGLISH
            "kn" -> TranslateLanguage.KANNADA
            "hi" -> TranslateLanguage.HINDI
            "ta" -> TranslateLanguage.TAMIL
            "te" -> TranslateLanguage.TELUGU
            else -> TranslateLanguage.ENGLISH
        }
    }

    private suspend fun downloadModelIfNeeded(translator: Translator) =
        suspendCancellableCoroutine<Unit> { cont ->
            translator.downloadModelIfNeeded()
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { e ->
                    if (!cont.isCancelled) cont.resumeWithException(e)
                }
        }

    private suspend fun translateTextSuspend(translator: Translator, text: String) =
        suspendCancellableCoroutine<String> { cont ->
            translator.translate(text)
                .addOnSuccessListener { result ->
                    val fixed = fixCapitalization(result)
                    if (!cont.isCancelled) cont.resume(fixed)
                }
                .addOnFailureListener { e ->
                    if (!cont.isCancelled) cont.resumeWithException(e)
                }
        }
}