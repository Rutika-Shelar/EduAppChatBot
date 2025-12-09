
package com.example.eduappchatbot.utils

import org.json.JSONObject

object ConceptMapUtils {
    /**
     * Returns true when the provided JSON string contains a meaningful concept map:
     * - visualization_type is non-blank and not "None", OR
     * - nodes array has items, OR
     * - edges array has items.
     */

    // to determine if response looks like a definition
    fun responseLooksLikeDefinition(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.lowercase()
        val keywords = listOf("definition", "defined as", "is defined", "means", "meaning of", "definition of", "is a")
        return keywords.any { t.contains(it) }
    }

    fun hasConceptMapContent(json: String?): Boolean {
        if (json.isNullOrBlank()) return false
        return try {
            val obj = JSONObject(json)
            val nodes = if (obj.has("nodes")) obj.getJSONArray("nodes") else null
            val edges = if (obj.has("edges")) obj.getJSONArray("edges") else null
            val vis = obj.optString("visualization_type", "")
            val nodesCount = nodes?.length() ?: 0
            val edgesCount = edges?.length() ?: 0
            (vis.isNotBlank() && !vis.equals("None", ignoreCase = true)) || nodesCount > 0 || edgesCount > 0
        } catch (e: Exception) {
            false
        }
    }
}
