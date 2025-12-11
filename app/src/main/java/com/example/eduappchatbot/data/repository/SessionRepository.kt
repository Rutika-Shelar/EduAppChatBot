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
        conceptThreadMap[concept] = threadId
        conceptSessionMap[concept] = sessionId
        try {
            val prefs = context.getSharedPreferences("session_map", Context.MODE_PRIVATE)
            val raw = prefs.getString("concept_thread", "{}") ?: "{}"
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
            return Pair(threadId, sessionId)
        }
        // load from shared preferences
        return try {
            val prefs = context.getSharedPreferences("session_map", Context.MODE_PRIVATE)
            val raw = prefs.getString("concept_thread_map", null) ?: return null
            val json = JSONObject(raw)
            if (!json.has(concept)) return null
            val obj = json.getJSONObject(concept)
            val thread = obj.optString("thread", "") ?: return null
            val session = obj.optString("session", "")?: return null
            // cache in-memory
            conceptThreadMap[concept] = thread
            if (session.isNotBlank()) conceptSessionMap[concept] = session
            Pair(thread, session.takeIf { it.isNotBlank() })
        } catch (e: Exception) {
            DebugLogger.errorLog("SessionRepository", "loadMapping failed: ${e.message}")
            null
        }
    }
}
