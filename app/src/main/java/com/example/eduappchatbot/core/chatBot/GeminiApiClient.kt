package com.example.eduappchatbot.core.chatBot

import com.example.eduappchatbot.utils.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLLMClient(
    private val apiKey: String,
    private val userClass: String,
    private val nodeNumber: String,
    private val maxWord: String
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Gemini API endpoint - using gemini-2.0-flash-lite
    private val model = "gemini-2.0-flash-lite"

    private fun getUrl(): String {
        return "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
    }

    /**
     * Main query method - now accepts AI response text instead of user query
     */
    suspend fun queryLLM(aiResponseText: String, languageName: String): String = withContext(Dispatchers.IO) {
        try {
            val languageName = when (languageName) {
                "en" -> "English"
                "kn" -> "Kannada"
                "hi" -> "Hindi"
                "ta" -> "Tamil"
                "te" -> "Telugu"
                else -> "English"
            }
            // Updated system prompt to work with AI responses
            val systemPrompt = """
                You are an AI that creates concept maps for Class $userClass students.
                Given an AI tutor's explanation/response, create a visual concept map.
                 
                IMPORTANT: All text in the concept map (node labels, edge labels, main_concept) must be in $languageName language
                no matter what language the AI response is in the response should be in $languageName.
                Respond in this exact format:
                [ANSWER]
                Explain in under $maxWord words. Be clear, age-appropriate, and conversational.
    
                [CONCEPT_MAP_JSON]
                Valid JSON only (no markdown/code blocks):
                {
                  "visualization_type": "Concept Map",
                  "main_concept": "main topic from the response",
                  "nodes": [
                    {"id": "A", "label": "Main", "category": "Main"},
                    {"id": "B", "label": "Sub 1", "category": "Secondary"},
                    {"id": "C", "label": "Sub 2", "category": "Secondary"},
                    {"id": "D", "label": "Detail", "category": "Leaf"}
                  ],
                  "edges": [
                    {"from": "A", "to": "B", "label": "relation", "id": "A->B"},
                    {"from": "A", "to": "C", "label": "relation", "id": "A->C"},
                    {"from": "B", "to": "D", "label": "relation", "id": "B->D"}
                  ],
                  "audioSegments": [
                    {
                      "segmentIndex": 0,
                      "spokenText": "text from response (in $languageName)",
                      "estimatedDuration": 3.5,
                      "highlightNodeIds": ["A"],
                      "showNodeIds": ["A"],
                      "highlightEdgeIds": [],
                      "action": "introduce"
                    }
                  ]
                }
            
                Rules:
                - Extract key concepts from the AI response
                - ALL text (labels, concepts, relations) MUST be in $languageName
                - 1 Main node, 2-3 Secondary nodes, rest Leaf nodes (~$nodeNumber total)
                - All edges need "id" as "FROM->TO"
                - audioSegments: split response into parts, match node reveals to explanation flow
                - estimatedDuration: 2-3 words/second
                - action types: "introduce", "expand", "connect"
                - Show nodes progressively (start with main only)
                - Make it age-appropriate for Class $userClass
            """.trimIndent()

            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "$systemPrompt\n\nAI Response to visualize:\n$aiResponseText")
                        })
                    })
                })
            }

            val jsonBody = JSONObject().apply {
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 8192)
                    put("topP", 0.95)
                })
            }

            val url = getUrl()
            DebugLogger.debugLog("GeminiLLMClient", "Sending request to model: $model")
            DebugLogger.debugLog("GeminiLLMClient", "Visualizing AI response of length: ${aiResponseText.length}")

            val requestBody = jsonBody.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            DebugLogger.debugLog("GeminiLLMClient", "Response code: ${response.code}")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                DebugLogger.errorLog("GeminiLLMClient", "Error: ${response.code} - ${response.message}")
                DebugLogger.errorLog("GeminiLLMClient", "Error body: $errorBody")
                response.close()
                return@withContext "Error: ${response.code} - ${response.message}"
            }

            val responseBody = response.body?.string() ?: ""
            response.close()

            DebugLogger.debugLog("GeminiLLMClient", "Response body received")
            return@withContext responseBody

        } catch (e: Exception) {
            DebugLogger.errorLog("GeminiLLMClient", "Exception during API call: ${e.message}")
            return@withContext "Error: ${e.message}"
        }
    }

    /**
     * Extracts the concept map JSON from the Gemini API response
     */
    fun extractConceptMapJSON(fullResponse: String): String {
        try {
            // Check if response is an error message
            if (fullResponse.startsWith("Error:")) {
                DebugLogger.errorLog("GeminiLLMClient", "Response is an error: $fullResponse")
                return getDefaultConceptMapJSON()
            }

            // Parse the Gemini API response
            val jsonResponse = JSONObject(fullResponse)
            val candidates = jsonResponse.getJSONArray("candidates")

            if (candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)

                // Check finish reason
                if (candidate.has("finishReason")) {
                    val finishReason = candidate.getString("finishReason")
                    if (finishReason == "MAX_TOKENS") {
                        DebugLogger.errorLog("GeminiLLMClient", "Response was cut off due to MAX_TOKENS limit")
                    }
                }

                // Check if content exists
                if (!candidate.has("content")) {
                    DebugLogger.errorLog("GeminiLLMClient", "No content in response")
                    return getDefaultConceptMapJSON()
                }

                val content = candidate.getJSONObject("content")

                // Check if parts exist
                if (!content.has("parts")) {
                    DebugLogger.errorLog("GeminiLLMClient", "No parts in content")
                    return getDefaultConceptMapJSON()
                }

                val parts = content.getJSONArray("parts")

                if (parts.length() > 0) {
                    val text = parts.getJSONObject(0).getString("text")

                    DebugLogger.debugLog("GeminiLLMClient", "Extracting concept map from content of length: ${text.length}")

                    // Look for any JSON object that contains "visualization_type": "Concept Map"
                    var currentPos = 0

                    while (currentPos < text.length) {
                        val startPos = text.indexOf("{", currentPos)
                        if (startPos == -1) break

                        // Find the matching closing brace
                        var braceCount = 0
                        var endIndex = startPos

                        for (i in startPos until text.length) {
                            when (text[i]) {
                                '{' -> braceCount++
                                '}' -> {
                                    braceCount--
                                    if (braceCount == 0) {
                                        endIndex = i + 1
                                        break
                                    }
                                }
                            }
                        }

                        if (endIndex > startPos) {
                            // Try to parse this JSON object
                            val candidateJson = text.substring(startPos, endIndex)

                            try {
                                val testObj = JSONObject(candidateJson)
                                // Check if it's a concept map JSON
                                if (testObj.has("visualization_type") &&
                                    testObj.has("main_concept") &&
                                    testObj.has("nodes") &&
                                    testObj.has("edges")) {

                                    // Post-process: Add edge IDs if missing
                                    val processedJson = addEdgeIdsIfMissing(candidateJson)
                                    return processedJson
                                }
                            } catch (e: Exception) {
                                // Not a valid JSON or not a concept map, continue searching
                                DebugLogger.errorLog("GeminiLLMClient", "Not a valid JSON or not a concept map: ${e.message}")
                            }
                        }

                        currentPos = endIndex
                    }
                }
            }

            DebugLogger.errorLog("GeminiLLMClient", "Could not extract concept map JSON from response")
            return getDefaultConceptMapJSON()

        } catch (e: Exception) {
            DebugLogger.errorLog("GeminiLLMClient", "Error extracting concept map JSON: ${e.message}")
            return getDefaultConceptMapJSON()
        }
    }

    /**
     * Adds "id" field to edges if missing
     * Format: "FROM_ID->TO_ID"
     */
    private fun addEdgeIdsIfMissing(jsonString: String): String {
        try {
            val jsonObject = JSONObject(jsonString)
            val edges = jsonObject.getJSONArray("edges")

            var modified = false
            for (i in 0 until edges.length()) {
                val edge = edges.getJSONObject(i)

                if (!edge.has("id") || edge.getString("id").isBlank()) {
                    val from = edge.getString("from")
                    val to = edge.getString("to")
                    edge.put("id", "$from->$to")
                    modified = true
                }
            }

            if (modified) {
                DebugLogger.debugLog("GeminiLLMClient", "Added missing edge IDs")
            }

            return jsonObject.toString()

        } catch (e: Exception) {
            DebugLogger.errorLog("GeminiLLMClient", "Error adding edge IDs: ${e.message}")
            return jsonString
        }
    }

    /**
     * Extracts the answer text from the Gemini API response
     */
    fun extractAnswer(fullResponse: String): String {
        try {
            // Check if response is an error message
            if (fullResponse.startsWith("Error:")) {
                return fullResponse // Return the error message as-is
            }

            val jsonResponse = JSONObject(fullResponse)
            val candidates = jsonResponse.getJSONArray("candidates")

            if (candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)

                // Check finish reason
                if (candidate.has("finishReason")) {
                    val finishReason = candidate.getString("finishReason")
                    if (finishReason == "MAX_TOKENS") {
                        DebugLogger.errorLog("GeminiLLMClient", "Response was cut off due to MAX_TOKENS limit")
                        return "The response was incomplete. Please try again with a simpler question."
                    }
                }

                // Check if content exists
                if (!candidate.has("content")) {
                    DebugLogger.errorLog("GeminiLLMClient", "No content in response")
                    return "I encountered an error processing the response."
                }

                val content = candidate.getJSONObject("content")

                // Check if parts exist
                if (!content.has("parts")) {
                    DebugLogger.errorLog("GeminiLLMClient", "No parts in content")
                    return "I encountered an error processing the response."
                }

                val parts = content.getJSONArray("parts")

                if (parts.length() > 0) {
                    val text = parts.getJSONObject(0).getString("text")

                    // Try to find [ANSWER] marker
                    val answerStart = text.indexOf("[ANSWER]")
                    if (answerStart != -1) {
                        val afterAnswer = text.substring(answerStart + "[ANSWER]".length).trim()

                        // Find where the JSON starts (to exclude it)
                        val jsonStart = afterAnswer.indexOf("{")

                        // Also find [CONCEPT_MAP_JSON] marker as alternative boundary
                        val conceptMapMarker = afterAnswer.indexOf("[CONCEPT_MAP_JSON]")

                        // Determine where to cut the answer
                        val cutPosition = when {
                            conceptMapMarker != -1 -> conceptMapMarker
                            jsonStart != -1 -> jsonStart
                            else -> afterAnswer.length
                        }

                        val rawAnswer = if (cutPosition != afterAnswer.length) {
                            afterAnswer.substring(0, cutPosition).trim()
                        } else {
                            afterAnswer
                        }

                        // Clean up the answer by removing any remaining bracket markers
                        var cleanedAnswer = rawAnswer

                        // Remove patterns like [COW], [Concept Map], [ANYTHING]
                        cleanedAnswer = cleanedAnswer.replace(Regex("\\[.*?]"), "").trim()

                        return cleanedAnswer.trim()
                    }

                    // No [ANSWER] marker, try to extract text before JSON or markers
                    var rawContent = text

                    // Find first JSON occurrence
                    val firstBrace = rawContent.indexOf("{")
                    if (firstBrace > 0) {
                        rawContent = rawContent.substring(0, firstBrace).trim()
                    }

                    // Find [CONCEPT_MAP_JSON] marker
                    val conceptMapMarker = rawContent.indexOf("[CONCEPT_MAP_JSON]")
                    if (conceptMapMarker != -1) {
                        rawContent = rawContent.substring(0, conceptMapMarker).trim()
                    }

                    // Clean up any bracket markers
                    rawContent = rawContent.replace(Regex("\\[.*?]"), "").trim()

                    return rawContent.ifBlank { text.trim() }
                }
            }

            return "I encountered an error processing the response."

        } catch (e: Exception) {
            DebugLogger.errorLog("GeminiLLMClient", "Error extracting answer: ${e.message}")
            return "I encountered an error processing the response."
        }
    }

    /**
     * Returns a default concept map JSON when extraction fails
     */
    private fun getDefaultConceptMapJSON(): String {
        return """
    {
      "visualization_type": "Concept Map",
      "main_concept": "Loading...",
      "nodes": [
        {"id": "A", "label": "Concept", "category": "Core"}
      ],
      "edges": [],
      "audioSegments": [
        {
          "segmentIndex": 0,
          "spokenText": "Loading concept map...",
          "estimatedDuration": 2.0,
          "highlightNodeIds": ["A"],
          "showNodeIds": ["A"],
          "highlightEdgeIds": [],
          "action": "introduce"
        }
      ]
    }
    """.trimIndent()
    }
}