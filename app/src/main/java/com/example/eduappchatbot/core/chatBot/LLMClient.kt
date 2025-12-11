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

/*
* LLMClient handles communication with the LLM API
* current llm used = Groq
*/
class LLMClient(
    private val apiKey: String,
    private val userClass: String,
    private val nodeNumber: String,
    private val maxWord: String
) {

    val TAG= "LLMClient"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val url = "https://api.groq.com/openai/v1/chat/completions"

    /**
     * Main query method - accepts AI response text and language for concept map
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
            //System prompt to generate concept map JSON
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

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "AI Response to visualize:\n$aiResponseText")
                })
            }
            val jsonBody = JSONObject().apply {
                put("model", "meta-llama/llama-4-scout-17b-16e-instruct")
                put("messages", messages)
                put("temperature", 0.7)
                put("max_tokens", 8192)
            }

            DebugLogger.debugLog("TAG", "Sending request to Groq")
            DebugLogger.debugLog("TAG", "Visualizing AI response of length: ${aiResponseText.length}")

            val requestBody = jsonBody.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            // Execute the request
            val response = client.newCall(request).execute()

            DebugLogger.debugLog("TAG", "Response code: ${response.code}")

            // Handle unsuccessful response
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                DebugLogger.errorLog("TAG", "Error: ${response.code} - ${response.message}")
                DebugLogger.errorLog("TAG", "Error body: $errorBody")
                response.close()
                return@withContext "Error: ${response.code} - ${response.message}"
            }
            val responseBody = response.body?.string() ?: ""
            response.close()

            DebugLogger.debugLog("TAG", "Response body: $responseBody")
            return@withContext responseBody

        } catch (e: Exception) {
            DebugLogger.errorLog("TAG", "Exception during API call: ${e.message}")
            return@withContext "Error: ${e.message}"
        }
    }

    /**
     * Extracts the concept map JSON from the Groq API response
     */
    fun extractConceptMapJSON(fullResponse: String): String {
        try {
            // Check if response is an error message
            if (fullResponse.startsWith("Error:")) {
                DebugLogger.errorLog(TAG, "Response is an error: $fullResponse")
                return getDefaultConceptMapJSON()// Return default concept map JSON on error
            }

            val jsonResponse = JSONObject(fullResponse)
            val choices = jsonResponse.getJSONArray("choices")

            if (choices.length() > 0) {
                val message = choices.getJSONObject(0).getJSONObject("message")
                val content = message.getString("content")

                DebugLogger.debugLog(TAG, "Extracting concept map from content of length: ${content.length}")

                // Look for any JSON object that contains "visualization_type": "Concept Map"
                var currentPos = 0

                while (currentPos < content.length) {
                    val startPos = content.indexOf("{", currentPos)
                    if (startPos == -1) break

                    // Find the matching closing brace
                    var braceCount = 0
                    var endIndex = startPos

                    for (i in startPos until content.length) {
                        when (content[i]) {
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
                        val candidateJson = content.substring(startPos, endIndex)

                        try {
                            val testObj = JSONObject(candidateJson)
                            // Check if it's a concept map JSON
                            if (testObj.has("visualization_type") &&
                                testObj.has("main_concept") &&
                                testObj.has("nodes") &&
                                testObj.has("edges")) {

                                DebugLogger.debugLog(TAG, "Successfully extracted concept map JSON")

                                // Post-process: Add edge IDs if missing
                                val processedJson = addEdgeIdsIfMissing(candidateJson)

                                return processedJson
                            }
                        } catch (e: Exception) {
                            // Not a valid JSON or not a concept map, continue searching
                            DebugLogger.errorLog(TAG, "Not a valid JSON or not a concept map: ${e.message}")
                        }
                    }

                    currentPos = endIndex
                }
            }

            DebugLogger.errorLog(TAG, "Could not extract concept map JSON from response")
            return getDefaultConceptMapJSON()

        } catch (e: Exception) {
            DebugLogger.errorLog(TAG, "Error extracting concept map JSON: ${e.message}")
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

                // Add ID if missing
                if (!edge.has("id") || edge.getString("id").isBlank()) {
                    val from = edge.getString("from")
                    val to = edge.getString("to")
                    edge.put("id", "$from->$to")
                    modified = true
                }
            }

            if (modified) {
                DebugLogger.debugLog(TAG, "Added missing edge IDs")
            }

            return jsonObject.toString()

        } catch (e: Exception) {
            DebugLogger.errorLog(TAG, "Error adding edge IDs: ${e.message}")
            return jsonString // Return original if processing fails
        }
    }

    /**
     * Extracts the answer text from the Groq
     */
    fun extractAnswer(fullResponse: String): String {
        try {
            // Check if response is an error message
            if (fullResponse.startsWith("Error:")) {
                return fullResponse // Return the error message as-is
            }

            val jsonResponse = JSONObject(fullResponse)
            val choices = jsonResponse.getJSONArray("choices")

            if (choices.length() > 0) {
                val message = choices.getJSONObject(0).getJSONObject("message")
                val content = message.getString("content")

                // Try to find [ANSWER] marker
                val answerStart = content.indexOf("[ANSWER]")
                if (answerStart != -1) {
                    val afterAnswer = content.substring(answerStart + "[ANSWER]".length).trim()

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
                var rawContent = content

                // Find first JSON occurrence
                val firstBrace = rawContent.indexOf("{")
                if (firstBrace > 0) {
                    rawContent = rawContent.substring(0, firstBrace).trim()
                }

                val conceptMapMarker = rawContent.indexOf("[CONCEPT_MAP_JSON]")
                if (conceptMapMarker != -1) {
                    rawContent = rawContent.substring(0, conceptMapMarker).trim()
                }

                // Clean up any bracket markers
                rawContent = rawContent.replace(Regex("\\[.*?]"), "").trim()

                return rawContent.ifBlank { content.trim() }
            }

            return "I encountered an error processing the response."

        } catch (e: Exception) {
            DebugLogger.errorLog(TAG, "Error extracting answer: ${e.message}")
            return "I encountered an error processing the response."
        }
    }

    /**
     * Returns a default concept map JSON in case of errors
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