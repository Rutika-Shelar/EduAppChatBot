package com.example.eduappchatbot.data.repository

import android.content.Context
import androidx.core.content.edit
import com.example.eduappchatbot.utils.DebugLogger

/**
 * Repository for managing user session information
 * Stores and retrieves user details like name, phone, language preference and selected concept
 */
class UserSessionRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("user_session_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_LANGUAGE = "user_language"
        private const val KEY_USER_CONCEPT = "user_concept"
        private const val KEY_IS_USER_INFO_COMPLETE = "is_user_info_complete"
    }

    /**
     * Save user information to persistent storage
     * concept is optional and will be stored if provided
     */
    fun saveUserInfo(userName: String, userPhone: String, language: String, concept: String? = null) {
        try {
            prefs.edit {
                putString(KEY_USER_NAME, userName)
                putString(KEY_USER_PHONE, userPhone)
                putString(KEY_USER_LANGUAGE, language)
                if (concept != null) putString(KEY_USER_CONCEPT, concept) else remove(KEY_USER_CONCEPT)
                putBoolean(KEY_IS_USER_INFO_COMPLETE, true)
            }
            DebugLogger.debugLog("UserSessionRepository", "User info saved: $userName, Language: $language, Concept: ${concept ?: "none"}")
        } catch (e: Exception) {
            DebugLogger.errorLog("UserSessionRepository", "Failed to save user info: ${e.message}")
        }
    }

    /**
     * Save only the preferred concept
     */
    fun savePreferredConcept(concept: String) {
        try {
            prefs.edit { putString(KEY_USER_CONCEPT, concept) }
            DebugLogger.debugLog("UserSessionRepository", "Preferred concept saved: $concept")
        } catch (e: Exception) {
            DebugLogger.errorLog("UserSessionRepository", "Failed to save preferred concept: ${e.message}")
        }
    }

    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    fun getUserPhone(): String? = prefs.getString(KEY_USER_PHONE, null)

    fun getUserLanguage(): String = prefs.getString(KEY_USER_LANGUAGE, "en") ?: "en"

    fun getUserConcept(): String? = prefs.getString(KEY_USER_CONCEPT, null)

    fun isUserInfoComplete(): Boolean = prefs.getBoolean(KEY_IS_USER_INFO_COMPLETE, false)

    fun clearUserInfo() {
        try {
            prefs.edit {
                remove(KEY_USER_NAME)
                remove(KEY_USER_PHONE)
                remove(KEY_USER_LANGUAGE)
                remove(KEY_USER_CONCEPT)
                putBoolean(KEY_IS_USER_INFO_COMPLETE, false)
            }
            DebugLogger.debugLog("UserSessionRepository", "User info cleared")
        } catch (e: Exception) {
            DebugLogger.errorLog("UserSessionRepository", "Failed to clear user info: ${e.message}")
        }
    }

    fun updateLanguage(language: String) {
        try {
            prefs.edit { putString(KEY_USER_LANGUAGE, language) }
            DebugLogger.debugLog("UserSessionRepository", "Language updated to: $language")
        } catch (e: Exception) {
            DebugLogger.errorLog("UserSessionRepository", "Failed to update language: ${e.message}")
        }
    }

    /**
     * Get complete user info as a data object
     */
    fun getUserInfo(): UserInfo? {
        val name = getUserName()
        val phone = getUserPhone()
        val language = getUserLanguage()
        val concept = getUserConcept()
        return if (name != null && phone != null) {
            UserInfo(name, phone, language, concept)
        } else {
            null
        }
    }
}

/**
 * Data class representing user information
 */
data class UserInfo(
    val name: String,
    val phone: String,
    val language: String,
    val concept: String?
)
