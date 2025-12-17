package com.example.eduappchatbot.data.repository

import android.content.Context
import com.example.eduappchatbot.utils.DebugLogger
import org.json.JSONObject
import androidx.core.content.edit

class SessionRepository(private val context: Context) {
    private val conceptThreadMap = mutableMapOf<String, String>()
    private val conceptSessionMap = mutableMapOf<String, String?>()

    /*
    save mapping of concept to threadId and sessionId
    saved in shared preferences as JSON object
    */
    fun saveMapping(concept: String, threadId: String, sessionId: String?) {
        if (concept.isBlank() || threadId.isBlank()) {
            DebugLogger.errorLog("SessionRepository", "Cannot save mapping with blank concept or threadId")
            return
        }
        conceptThreadMap[concept] = threadId
        conceptSessionMap[concept] = sessionId
        try {
            val prefs = context.getSharedPreferences("session_map", Context.MODE_PRIVATE)
            val raw = prefs.getString("concept_thread_map", "{}") ?: "{}"
            val json = JSONObject(raw)
            val item = JSONObject().apply {
                put("thread", threadId)
                put("session", sessionId ?: JSONObject.NULL)
            }
            json.put(concept, item)
            prefs.edit { putString("concept_thread_map", json.toString()) }
        } catch (e: Exception) {
            DebugLogger.errorLog("SessionRepository", "saveMapping failed: ${e.message}")
        }
    }

    /*
    * load mapping of concept to threadId and sessionId
    * returns Pair(threadId, sessionId) or null if not found
    * reads from in-memory cache first, then from shared preferences
    * */
    fun loadMapping(concept: String): Pair<String, String?>? {
        // check in-memory cache first
        conceptThreadMap[concept]?.let { threadId ->
            val sessionId = conceptSessionMap[concept]
            DebugLogger.debugLog("SessionRepository", "Loaded from cache for '$concept': threadId=$threadId, sessionId=$sessionId")
            return Pair(threadId, sessionId)
        }
        // load from shared preferences
        return try {
            val prefs = context.getSharedPreferences("session_map", Context.MODE_PRIVATE)
            val raw = prefs.getString("concept_thread_map", null) ?: return null
            val json = JSONObject(raw)
            if (!json.has(concept)){
                DebugLogger.debugLog("SessionRepository", "No mapping found for concept: '$concept'")
                return null
            }
            val obj = json.getJSONObject(concept)
            val thread = obj.optString("thread", "")
            if(thread.isBlank()){
                DebugLogger.errorLog("SessionRepository", "Thread ID is blank for concept: '$concept'")
                return null
            }

            val sessionValue = obj.opt("session")
            val session = when {
                sessionValue == null || sessionValue == JSONObject.NULL -> null
                sessionValue is String && sessionValue.isNotBlank() -> sessionValue
                else -> null
            }

            // Cache in-memory
            conceptThreadMap[concept] = thread
            conceptSessionMap[concept] = session

            DebugLogger.debugLog("SessionRepository", "Loaded from SharedPrefs for '$concept': threadId=$thread, sessionId=$session")
            Pair(thread, session)
        } catch (e: Exception) {
            DebugLogger.errorLog("SessionRepository", "loadMapping failed: ${e.message}")
            null
        }
    }

    /**
     * Delete mapping for a specific concept
     * Used when starting a fresh session
     */
    fun deleteMapping(concept: String) {
        if (concept.isBlank()) return

        try {
            // Remove from in-memory cache
            conceptThreadMap.remove(concept)
            conceptSessionMap.remove(concept)

            // Remove from SharedPreferences
            val prefs = context.getSharedPreferences("session_map", Context.MODE_PRIVATE)
            val raw = prefs.getString("concept_thread_map", "{}") ?: "{}"
            val json = JSONObject(raw)

            if (json.has(concept)) {
                json.remove(concept)
                prefs.edit { putString("concept_thread_map", json.toString()) }
                DebugLogger.debugLog("SessionRepository", "Deleted mapping for concept: '$concept'")
            }
        } catch (e: Exception) {
            DebugLogger.errorLog("SessionRepository", "deleteMapping failed: ${e.message}")
        }
    }

    /**
     * Clear all session mappings
     * Called on logout
     */
    fun clearAllMappings() {
        try {
            conceptThreadMap.clear()
            conceptSessionMap.clear()

            val prefs = context.getSharedPreferences("session_map", Context.MODE_PRIVATE)
            prefs.edit {
                remove("concept_thread_map")
            }
            DebugLogger.debugLog("SessionRepository", "Cleared all session mappings")
        } catch (e: Exception) {
            DebugLogger.errorLog("SessionRepository", "clearAllMappings failed: ${e.message}")
        }
    }

    /**
     * Check if a mapping exists for a concept
     */
    fun hasMapping(concept: String): Boolean {
        if (concept.isBlank()) return false

        // Check in-memory first
        if (conceptThreadMap.containsKey(concept)) return true

        // Check SharedPreferences
        return try {
            val prefs = context.getSharedPreferences("session_map", Context.MODE_PRIVATE)
            val raw = prefs.getString("concept_thread_map", null) ?: return false
            val json = JSONObject(raw)
            json.has(concept)
        } catch (e: Exception) {
            DebugLogger.errorLog("SessionRepository", "hasMapping check failed: ${e.message}")
            false
        }
    }
}
