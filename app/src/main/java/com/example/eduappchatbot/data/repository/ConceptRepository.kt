package com.example.eduappchatbot.data.repository

import android.content.Context
import com.example.eduappchatbot.core.chatBot.AgenticAIService
import com.example.eduappchatbot.utils.DebugLogger
import com.example.eduappchatbot.utils.TranslationHelper
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.edit

/**
 * Repository for fetching and caching available concepts from the server
 * Also handles translation of concepts to user's preferred language
 * IMPORTANT: Always maintains mapping between translated concepts and original English concepts
 */
class ConceptRepository(
    private val apiService: AgenticAIService
) {
    companion object {
        private const val PREFS = "concepts_store"
        private const val KEY_CANONICAL = "concepts_en"
        private const val KEY_TRANSLATION_MAP = "concept_translation_map"
    }

    // Fetch concepts from server, cache canonical list, and translate if needed
    suspend fun getConcepts(context: Context, languageShort: String): List<String> {
        val serverList = try {
            val resp = apiService.getAvailableConcepts()
            if (resp.isSuccessful && resp.body()?.success == true) {
                resp.body()!!.concepts.sorted().also { saveCanonicalList(context, it) }
            } else {
                // fallback to cached canonical
                loadCanonicalList(context)
            }
        } catch (e: Exception) {
            DebugLogger.errorLog("ConceptRepository", "network fetch failed: ${e.message}")
            loadCanonicalList(context)
        } ?: emptyList()

        if (languageShort.equals("en", ignoreCase = true)) return serverList

        return try {
            // Translate for Kannada and save the mapping
            if (languageShort.equals("kn", ignoreCase = true)) {
                val translated = TranslationHelper.translateList(context, serverList, sourceShort = "en", targetShort = "kn")
                if (translated.isEmpty()) {
                    serverList
                } else {
                    // Save mapping: translatedConcept -> originalEnglishConcept
                    saveTranslationMapping(context, languageShort, serverList, translated)
                    translated.sorted()
                }
            } else {
                // fallback: return canonical for unsupported languages
                serverList
            }
        } catch (e: Exception) {
            DebugLogger.errorLog("ConceptRepository", "translation failed: ${e.message}")
            serverList
        }
    }

    /**
     * Get the original English concept name from a translated/displayed concept
     * This is critical for API calls which expect English concept names
     */
    fun getOriginalConcept(context: Context, displayedConcept: String, languageShort: String): String {
        // If language is English, the displayed concept IS the original
        if (languageShort.equals("en", ignoreCase = true)) {
            return displayedConcept
        }

        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val mapJson = prefs.getString("${KEY_TRANSLATION_MAP}_$languageShort", null) ?: return displayedConcept
            val jsonObj = JSONObject(mapJson)

            // Return the original English concept, or the displayed one if not found in map
            jsonObj.optString(displayedConcept, displayedConcept)
        } catch (e: Exception) {
            DebugLogger.errorLog("ConceptRepository", "getOriginalConcept failed: ${e.message}")
            displayedConcept
        }
    }

    /**
     * Get the translated concept name from an original English concept
     * Used when loading saved preferences
     */
    fun getTranslatedConcept(context: Context, originalConcept: String, languageShort: String): String {
        // If language is English, no translation needed
        if (languageShort.equals("en", ignoreCase = true)) {
            return originalConcept
        }

        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val mapJson = prefs.getString("${KEY_TRANSLATION_MAP}_$languageShort", null) ?: return originalConcept
            val jsonObj = JSONObject(mapJson)

            // Search through the map to find which translated value corresponds to this original
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val translatedKey = keys.next()
                val originalValue = jsonObj.getString(translatedKey)
                if (originalValue == originalConcept) {
                    return translatedKey
                }
            }
            originalConcept
        } catch (e: Exception) {
            DebugLogger.errorLog("ConceptRepository", "getTranslatedConcept failed: ${e.message}")
            originalConcept
        }
    }

    // Save canonical concept list to shared preferences
    private fun saveCanonicalList(context: Context, list: List<String>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit { putString(KEY_CANONICAL, arr.toString()) }
            DebugLogger.debugLog("ConceptRepository", "Saved ${list.size} canonical concepts")
        } catch (e: Exception) {
            DebugLogger.errorLog("ConceptRepository", "saveCanonicalList failed: ${e.message}")
        }
    }

    // Load canonical concept list from shared preferences
    private fun loadCanonicalList(context: Context): List<String>? {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_CANONICAL, null) ?: return null
            val arr = JSONArray(raw)
            List(arr.length()) { i -> arr.optString(i, "") }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            DebugLogger.errorLog("ConceptRepository", "loadCanonicalList failed: ${e.message}")
            null
        }
    }

    /**
     * Save mapping between translated concepts and original English concepts
     * Format: { "translatedConcept": "originalEnglishConcept" }
     */
    private fun saveTranslationMapping(
        context: Context,
        languageShort: String,
        originalList: List<String>,
        translatedList: List<String>
    ) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val mapJson = JSONObject()

            // Create mapping: translated -> original
            originalList.forEachIndexed { index, original ->
                if (index < translatedList.size) {
                    mapJson.put(translatedList[index], original)
                }
            }

            prefs.edit {
                putString("${KEY_TRANSLATION_MAP}_$languageShort", mapJson.toString())
            }
            DebugLogger.debugLog("ConceptRepository", "Saved translation mapping for $languageShort with ${mapJson.length()} entries")
        } catch (e: Exception) {
            DebugLogger.errorLog("ConceptRepository", "saveTranslationMapping failed: ${e.message}")
        }
    }
}