package com.example.eduappchatbot.core.chatBot

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eduappchatbot.data.dataClass.ChatMessageModel
import com.example.eduappchatbot.utils.DebugLogger
import com.example.eduappchatbot.utils.NetworkUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import com.example.eduappchatbot.data.repository.ConceptRepository
import com.example.eduappchatbot.data.repository.SessionRepository
import com.example.eduappchatbot.data.repository.UserSessionRepository
import com.example.eduappchatbot.utils.TranslationHelper
import androidx.core.content.edit

class ChatViewModel(
    agenticAIBaseUrl: String,
    geminiApiKey:String,
    llmApiKey: String = "",
    llmUserClass: String = "6",
    llmNodeNumber: String = "8",//change this
    llmMaxWord: String = "250"
) : ViewModel() {

    private val agenticAIClient = AgenticAIClient(agenticAIBaseUrl)
    private val model="meta-llama/llama-4-scout-17b-16e-instruct"

    private val geminiClient = GeminiLLMClient(geminiApiKey, llmUserClass, llmNodeNumber, llmMaxWord,model)
    private val llmClient = LLMClient(llmApiKey,llmUserClass, llmNodeNumber, llmMaxWord,model)

    private val validConceptMapStates= setOf("CI", "GE")
    // Maps to store thread and session IDs for concepts
    private val conceptThreadMap = mutableMapOf<String, String>()
    private val conceptSessionMap = mutableMapOf<String, String>()
    private val conceptRepository = ConceptRepository(agenticAIClient.service)

    // Chat messages
    private val _messages = MutableStateFlow<List<ChatMessageModel>>(emptyList())
    val messages: StateFlow<List<ChatMessageModel>> = _messages

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Typing animation state
    private val _typingText = MutableStateFlow("")
    val typingText: StateFlow<String> = _typingText

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    // Concept map with progressive drawing
    private val _conceptMapJSON = MutableStateFlow(
        """{"visualization_type":"None","main_concept":"Chat for a Concept Map","nodes":[],"edges":[]}"""
    )
    val conceptMapJSON: StateFlow<String> = _conceptMapJSON

    // TTS trigger
    private val _shouldStartTTS = MutableStateFlow(false)
    val shouldStartTTS: StateFlow<Boolean> = _shouldStartTTS

    private val _fullTextForTTS = MutableStateFlow("")
    val fullTextForTTS: StateFlow<String> = _fullTextForTTS

    // Last user message for when the send msg failed
    private val _lastUserMessage = MutableStateFlow("")
    val lastUserMessage: StateFlow<String> = _lastUserMessage

    // Agent state and metadata
    private val _agentState = MutableStateFlow("")
    val agentState: StateFlow<String> = _agentState

    private val _agentMetadata = MutableStateFlow<SessionMetadata?>(null)
    val agentMetadata: StateFlow<SessionMetadata?> = _agentMetadata

    // Translated output for display
    private val _translatedOutput = MutableStateFlow("")
    val translatedOutput: StateFlow<String> = _translatedOutput

    // Network connectivity state
    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected

    // Available concepts
    private val _availableConcepts = MutableStateFlow<List<String>>(emptyList())
    val availableConcepts: StateFlow<List<String>> = _availableConcepts

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels

    private val _selectedModel = MutableStateFlow("")
    val selectedModel: StateFlow<String> = _selectedModel

    private val groqModels = listOf("meta-llama/llama-4-scout-17b-16e-instruct")

    // Currently selected concept
    private val _selectedConcept = MutableStateFlow<String?>(null)
    val selectedConcept: StateFlow<String?> = _selectedConcept

    // Session started state
    private val _isSessionStarted = MutableStateFlow(false)
    val isSessionStarted: StateFlow<Boolean> = _isSessionStarted

    // Pending first user message if session not ready
    private val _pendingFirstUserMessage = MutableStateFlow<String?>(null)

    // Current language
    private val _currentLanguage = MutableStateFlow("en")
    val currentLanguage: StateFlow<String> = _currentLanguage

    private val _originalAIResponse = MutableStateFlow("") // Store original API response
    val originalAIResponse: StateFlow<String> = _originalAIResponse

    private val _translationCache = MutableStateFlow<Map<String, String>>(emptyMap())

    // User ID
    private val _userId = MutableStateFlow("")
    val userId: StateFlow<String> = _userId

    // Jobs for animation control
    private var typingJob: Job? = null
    private var conceptMapJob: Job? = null

    // Job for slow network detection
    private var slowNetworkJob: Job? = null
    private var connectivityObserverJob: Job? = null

    // Initialize user session data
    fun initializeUserSession(context: Context) {
        viewModelScope.launch {
            try {
                val userRepo = UserSessionRepository(context.applicationContext)

                val userLanguage = userRepo.getUserLanguage()
                _currentLanguage.value = userLanguage

                val userName = userRepo.getUserName()
                if (!userName.isNullOrBlank()) {
                    _userId.value = userName
                }

                DebugLogger.debugLog(
                    "ChatViewModel",
                    "User session initialized - ID=$userName, Language=$userLanguage"
                )
            } catch (e: Exception) {
                DebugLogger.errorLog("ChatViewModel", "initializeUserSession failed: ${e.message}")
            }
        }
    }

    fun initialize(context: Context) {
        viewModelScope.launch {
            DebugLogger.debugLog("ChatViewModel", "Starting full initialization")

            initializeUserSession(context)
            startNetworkObservation(context)

            refreshAvailableModels()
            refreshAvailableConcepts(context, _currentLanguage.value)
            autoStartSavedConcept(context)
            DebugLogger.debugLog("ChatViewModel", "Initialization complete")
        }
    }

    private suspend fun getOrTranslateText(text: String, context: Context): String {
        val targetLang = _currentLanguage.value
        val cacheKey = "${targetLang}_${text.hashCode()}"

        // Check cache first
        _translationCache.value[cacheKey]?.let {
            return it
        }

        return withContext(Dispatchers.IO) {
            try {
                val srcDetected = detectSourceLanguage(text)

                DebugLogger.debugLog(
                    "ChatViewModel",
                    "Translation DETECTION -> source: $srcDetected, target: $targetLang"
                )

                val result = when {
                    srcDetected == "en" && targetLang == "en" -> text
                    srcDetected == "kn" && targetLang == "kn" -> {
                        val translated = try {
                            TranslationHelper.translateText(text, "en", targetLang)
                        } catch (e: Exception) {
                            DebugLogger.errorLog("ChatViewModel", "Forced en->kn translation failed: ${e.message}")
                            null
                        }
                        translated?.takeIf { it.isNotBlank() } ?: text
                    }
                    else -> {
                        val translated = try {
                            TranslationHelper.translateText(text, srcDetected, targetLang)
                        } catch (e: Exception) {
                            DebugLogger.errorLog("TranslationHelper", "TranslationHelper failed: ${e.message}")
                            text
                        }
                        translated.ifBlank { text }
                    }
                }

                // Cache the result
                _translationCache.value = _translationCache.value + (cacheKey to result)

                DebugLogger.debugLog("ChatViewModel", "Post-translation (full): ${result.take(2000)}")
                result
            } catch (e: Exception) {
                DebugLogger.errorLog("ChatViewModel", "Translation error: ${e.message}")
                text
            }
        }
    }

// Save the thread and session mapping for a concept
    private fun saveThreadMapping(context: Context, concept: String, threadId: String?, sessionId: String?) {
        if (threadId.isNullOrBlank()) return

        conceptThreadMap[concept] = threadId
        sessionId?.let { conceptSessionMap[concept] = it }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                SessionRepository(context.applicationContext).saveMapping(concept, threadId, sessionId)
            } catch (e: Exception) {
                DebugLogger.errorLog("ChatViewModel", "saveThreadMapping failed: ${e.message}")
            }
        }
    }
// Load the thread and session mapping for a concept
    private suspend fun loadThreadMapping(context: Context, concept: String): Pair<String, String?>? {
        conceptThreadMap[concept]?.let { threadId ->
            val sessionId = conceptSessionMap[concept]
            return Pair(threadId, sessionId)
        }

        return withContext(Dispatchers.IO) {
            try {
                SessionRepository(context.applicationContext).loadMapping(concept)?.also { (thread, session) ->
                    conceptThreadMap[concept] = thread
                    if (!session.isNullOrBlank()) conceptSessionMap[concept] = session
                }
            } catch (e: Exception) {
                DebugLogger.errorLog("ChatViewModel", "loadThreadMapping failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Clear all session data - called on logout
     */
    fun clearAllSessions(context: Context) {
        viewModelScope.launch {
            try {
                // Clear in-memory maps
                conceptThreadMap.clear()
                conceptSessionMap.clear()
                _translationCache.value = emptyMap() // Clear translation cache

                // Clear SharedPreferences
                withContext(Dispatchers.IO) {
                    SessionRepository(context.applicationContext).clearAllMappings()
                }

                // Reset current session state
                _isSessionStarted.value = false
                agenticAIClient.setCurrentThreadAndSession(null, null)

                // Clear messages and states
                _messages.value = emptyList()
                _selectedConcept.value = null
                _pendingFirstUserMessage.value = null
                _originalAIResponse.value = ""
                _translatedOutput.value = ""
                _fullTextForTTS.value = ""

                DebugLogger.debugLog("ChatViewModel", "All sessions cleared successfully")
            } catch (e: Exception) {
                DebugLogger.errorLog("ChatViewModel", "clearAllSessions failed: ${e.message}")
            }
        }
    }

    fun autoStartSavedConcept(context: Context) {
        viewModelScope.launch {
            try {
                val userRepo = UserSessionRepository(context.applicationContext)
                val savedEnglishConcept = userRepo.getUserConcept()

                if (!savedEnglishConcept.isNullOrBlank()) {
                    DebugLogger.debugLog("ChatViewModel", "Auto-starting saved concept: '$savedEnglishConcept'")

                    val displayedConcept = if (_currentLanguage.value != "en") {
                        conceptRepository.getTranslatedConcept(context, savedEnglishConcept, _currentLanguage.value)
                    } else {
                        savedEnglishConcept
                    }

                    _selectedConcept.value = displayedConcept
                    selectConcept(displayedConcept, context)
                } else {
                    DebugLogger.debugLog("ChatViewModel", "No saved concept found to auto-start")
                }
            } catch (e: Exception) {
                DebugLogger.errorLog("ChatViewModel", "autoStartSavedConcept failed: ${e.message}")
            }
        }
    }

    /**
     * Select a concept to start or resume a session
     * - Check for existing session mapping
     * - Resume or start new session accordingly
     * - Manage loading and animation states
     *
      */
    fun selectConcept(displayedConcept: String, context: Context) {
        viewModelScope.launch {
            val originalConcept = conceptRepository.getOriginalConcept(
                context,
                displayedConcept,
                _currentLanguage.value
            )

            _selectedConcept.value = displayedConcept //update selected concept

            DebugLogger.debugLog(
                "ChatViewModel",
                "Concept selected - Displayed: '$displayedConcept', Original: '$originalConcept'"
            )

            _isLoading.value = true
            //reset states fix the issue of wrong concept map and tts text
            _agentState.value=""
            cancelAnimations()
            resetConceptMap()

            try {
                val stored = loadThreadMapping(context, originalConcept)
                if (stored != null) {
                    resumeExistingSession(context, stored.first, stored.second)
                } else {
                    startNewSession(context, originalConcept)
                }
            } catch (e: Exception) {
                DebugLogger.errorLog("ChatViewModel", "selectConcept error: ${e.message}")
                handleRequestFailure("Failed to load concept: ${e.message}")
            }
        }
    }

    /**
     * Resume an existing session given thread and session IDs
     * - Sets current thread and session
     * - Marks session as started
     * - Fetches session history and appends last assistant message with typing animation
     * - Sends any pending user message that was queued before session was ready
     */
    private suspend fun resumeExistingSession(context: Context, threadId: String, sessionId: String?) {
        DebugLogger.debugLog("ChatViewModel", "Resuming session - thread=$threadId")

    // Set current thread and session
    agenticAIClient.setCurrentThreadAndSession(threadId, sessionId)
    // Mark session as started
        _isSessionStarted.value = true

        try {
            // Fetch session history
            val histResult = agenticAIClient.getSessionHistory(threadId)

            // On success, extract last assistant message
            if (histResult.isSuccess) {
                val history = histResult.getOrNull()
                val messages = history?.messages ?: emptyList()
                // Append past messages to chat
                val lastAssistant = messages.asSequence()
                    .lastOrNull { msg ->
                        (msg["role"] as? String)?.lowercase() in listOf("assistant", "ai")
                    }
                    ?.get("content") as? String
                // Show last assistant message with typing animation
                if (!lastAssistant.isNullOrBlank()) {
                    _fullTextForTTS.value = lastAssistant

                    //  Launch status fetch in parallel -no wait
                    viewModelScope.launch {
                        try {
                            val statusResult = agenticAIClient.getSessionStatus(threadId)
                            if (statusResult.isSuccess) {
                                _agentState.value = statusResult.getOrNull()?.currentState.orEmpty()
                            }
                        } catch (e: Exception) {
                            DebugLogger.errorLog("ChatViewModel", "Status fetch error: ${e.message}")
                        }
                    }

                    startTypingAnimation(lastAssistant, context)
                }
            }
        } catch (e: Exception) {
            DebugLogger.errorLog("ChatViewModel", "Error resuming session: ${e.message}")
        } finally {
            _isLoading.value = false
        }

    // pending user message is that was sent before session was ready
    // send it now
        _pendingFirstUserMessage.value?.let { msg ->
            _pendingFirstUserMessage.value = null
            sendMessageAfterSessionReady(msg, context)
        }
    }

    /**
     * Start a new session for the selected concept
     * if result success then save thread and session mapping in SharedPreferences
     * generate concept map if agent state is valid for concept map
     * start typing animation with welcome text
     *
      */
    private suspend fun startNewSession(context: Context, originalConcept: String) {
        try {
            val isKannada = _currentLanguage.value.equals("kn", ignoreCase = true)
            val userName = _userId.value.ifBlank { "android_user" }

            DebugLogger.debugLog(
                "ChatViewModel",
                "Starting NEW session - Concept: '$originalConcept', User: '$userName', isKannada: $isKannada"
            )

            val result = withTimeout(120_000L) {
                agenticAIClient.startSession(
                    conceptTitle = originalConcept,
                    studentId = userName,//username as userid
                    isKannada = isKannada//boolean flag for kannada
                )
            }
            if (result.isSuccess) {
                val response = result.getOrNull()
                if (response != null && response.success) {
                    _isSessionStarted.value = true
                    saveThreadMapping(context, originalConcept, response.threadId, response.sessionId)

                    _agentState.value = response.currentState.orEmpty()

                    val welcomeText = response.agentResponse.orEmpty()
                    if (welcomeText.isNotBlank()) {
                        _fullTextForTTS.value = welcomeText
                        startTypingAnimation(welcomeText, context)
                    }
                } else {
                    handleRequestFailure("Session start failed: ${response?.message ?: "Unknown error"}")
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown error"
                handleRequestFailure("Failed to start session: $err")
            }
        } catch (e: Exception) {
            handleRequestFailure("Session start exception: ${e.message}")
        } finally {
            _isLoading.value = false
        }

        _pendingFirstUserMessage.value?.let { msg ->
            _pendingFirstUserMessage.value = null
            sendMessageAfterSessionReady(msg, context)
        }
    }

    // Send a user message to the AI
    fun sendMessage(userMessage: String, context: Context) {
        if (userMessage.isBlank()) return

        _lastUserMessage.value = userMessage

        if (!NetworkUtils.ensureConnectedOrShowToast(context)) {
            handleRequestFailure("No internet connection")
            return
        }

        if (!_isSessionStarted.value) {
            _pendingFirstUserMessage.value = userMessage
            _messages.update { it + ChatMessageModel(content = userMessage, sender = "user") }
            DebugLogger.debugLog("ChatViewModel", "Session not ready - queued message")
            return
        }

        sendMessageAfterSessionReady(userMessage, context)
    }

    // Internal function to send message after session is ready
    private fun sendMessageAfterSessionReady(userMessage: String, context: Context) {
        _messages.update { it + ChatMessageModel(content = userMessage, sender = "user") }
        _isLoading.value = true
        cancelAnimations()


        slowNetworkJob = viewModelScope.launch {
            delay(5000L)
            DebugLogger.debugLog("ChatViewModel", "Slow network detected")
        }

        viewModelScope.launch {
            try {
                val startNs = System.nanoTime()
                val result = withTimeout(120_000L) {
                    agenticAIClient.continueSession(userMessage)
                }
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L

                slowNetworkJob?.cancel()

                when {
                    result.isFailure -> {
                        val err = result.exceptionOrNull()?.message ?: "Request failed"
                        DebugLogger.errorLog("ChatViewModel", "continueSession FAILED: $err")
                        handleRequestFailure(err)
                    }
                    else -> {
                        val resp = result.getOrNull()!!

                        if (!resp.success) {
                            handleRequestFailure(resp.message ?: "Server returned error")
                            return@launch
                        }

                        DebugLogger.debugLog("ChatViewModel", "continueSession SUCCESS (${elapsedMs}ms)")

                        _agentState.value = resp.currentState.orEmpty()
                        resp.metadata?.let { _agentMetadata.value = it }

                        val text = resp.agentResponse.orEmpty()
                        if (text.isBlank()) {
                            handleRequestFailure("Empty response from server")
                            return@launch
                        }
                        startTypingAnimation(text, context)
                    }
                }
            } catch (e: Exception) {
                slowNetworkJob?.cancel()
                val errorMsg = when (e) {
                    is kotlinx.coroutines.TimeoutCancellationException -> "Request timed out"
                    else -> e.message ?: "Unknown error"
                }
                DebugLogger.errorLog("ChatViewModel", "continueSession exception: $errorMsg")
                handleRequestFailure(errorMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }
    /**
     * Helper function to check if concept map should be generated
     * Returns true only if agent state is CI or GE
     */
    private fun shouldGenerateConceptMap(agentState: String?): Boolean {
        if (agentState.isNullOrBlank()) return false
        val shouldGenerate = validConceptMapStates.contains(agentState)

        if (shouldGenerate) {
            DebugLogger.debugLog("ChatViewModel", "Agent state '$agentState' requires concept map - will generate")
        } else {
            DebugLogger.debugLog("ChatViewModel", "Agent state '$agentState' does not require concept map - skipping")
        }

        return shouldGenerate
    }

    fun setSelectedModel(modelId: String) {
        _selectedModel.value = modelId
        DebugLogger.debugLog("ChatViewModel", "Model updated to: $modelId")
        // Update both clients
        geminiClient.setModel(modelId)
        llmClient.setModel(modelId)
    }


    private fun containsKannada(text: String): Boolean {
        return text.any { ch ->
            val code = ch.code
            // Kannada block
            code in 0x0C80..0x0CFF ||
                    // Kannada extended / common related ranges (conservative)
                    code in 0x0C00..0x0CFF
        }
    }

    private fun containsLatinLetters(text: String): Boolean {
        return text.any { ch ->
            // Basic Latin and Latin-1 Supplement (covers common English chars)
            val code = ch.code
            (code in 0x0041..0x007A) || (code in 0x00C0..0x00FF) || ch.isWhitespace() || ch.isDigit()
        }
    }

    private fun detectSourceLanguage(text: String): String {
        // Prioritize Kannada detection if any Kannada script characters exist
        return when {
            containsKannada(text) -> "kn"
            containsLatinLetters(text) -> "en"
            else -> {
                // Safe default to English for downstream translation compatibility
                "en"
            }
        }
    }

    /**
     * Start typing animation for AI response
     * - Translates text if needed
     * - Updates translated output and TTS text
     * - Animates typing word by word
     * - Finally appends full message to chat
     * @param fullText The full AI response text
     */
    private fun startTypingAnimation(fullText: String, context: Context) {
        // Store the original API response
        _originalAIResponse.value = fullText
        // ════════════════════════════════════════════════════════════════
        // LOGGING: Track concept map generation start time
        // ════════════════════════════════════════════════════════════════
        val conceptMapStartTime = System.currentTimeMillis()
        DebugLogger.debugLog(
            "ChatViewModel",
            "═══════════════════════════════════════════════════════════"
        )
        DebugLogger.debugLog(
            "ChatViewModel",
            "┌─ CONCEPT MAP GENERATION STARTING"
        )
        DebugLogger.debugLog(
            "ChatViewModel",
            "├─ Timestamp: $conceptMapStartTime"
        )
        DebugLogger.debugLog(
            "ChatViewModel",
            "├─ Selected Model: ${_selectedModel.value}"
        )
        DebugLogger.debugLog(
            "ChatViewModel",
            "├─ Current Language: ${_currentLanguage.value}"
        )
        DebugLogger.debugLog(
            "ChatViewModel",
            "├─ AI Response Length: ${fullText.length} characters"
        )
        DebugLogger.debugLog(
            "ChatViewModel",
            "├─ Agent State: ${_agentState.value}"
        )
        DebugLogger.debugLog(
            "ChatViewModel",
            "└─ Will call fetchConceptMapWithLLM()"
        )

        viewModelScope.launch {
            fetchConceptMapWithLLM(fullText)
        }

        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            _isTyping.value = true
            _typingText.value = ""

            DebugLogger.debugLog("ChatViewModel", "AI RAW OUTPUT (preview): ${fullText.take(1000)}")

            val finalText = withContext(Dispatchers.IO) {
                getOrTranslateText(fullText, context)
            }

            _translatedOutput.value = finalText
            _fullTextForTTS.value = finalText

            _shouldStartTTS.value = true
            delay(100)
            _shouldStartTTS.value = false

            val words = finalText.split(" ")
            words.forEachIndexed { index, word ->
                _typingText.value += if (index == 0) word else " $word"
                delay(120L + (word.length * 8L).coerceAtMost(200L))
            }

            _messages.update { it + ChatMessageModel(content = finalText, sender = "ai") }
            _isTyping.value = false
            _typingText.value = ""
        }
    }

    /**
     * Reset concept map to default empty state
     * Called when concept map generation is skipped or fails
     */
    private fun resetConceptMap() {
        _conceptMapJSON.value = """{"visualization_type":"None","main_concept":"Chat for a Concept Map","nodes":[],"edges":[]}"""
        DebugLogger.debugLog("ChatViewModel", "Concept map reset to default")
    }

    // Check if the selected model is a Groq model
    private fun isGroqModel(modelId: String): Boolean {
        return groqModels.any { it.equals(modelId, ignoreCase = true) }
    }

    /**
     * Fetch concept map JSON using LLM based on AI response
     * - Checks if concept map generation is needed based on agent state
     * - Selects LLM model and calls appropriate client
     * - Extracts JSON from LLM response
     * - Starts progressive rendering of concept map
     * - Logs detailed timing breakdown for each phase
     */
    private fun fetchConceptMapWithLLM(
        aiResponse: String,
        startTimeMs: Long = System.currentTimeMillis()
    ) {

        conceptMapJob?.cancel()

        conceptMapJob = viewModelScope.launch {
            try {
                // ─────────────────────────────────────────────────────────────
                // STATE CHECK PHASE
                // ─────────────────────────────────────────────────────────────
                val stateCheckTime = System.currentTimeMillis()
                val stateCheckDuration = stateCheckTime - startTimeMs

                val stateSnapshot = _agentState.value
                if (!shouldGenerateConceptMap(stateSnapshot)) {
                    DebugLogger.debugLog(
                        "ChatViewModel",
                        "├─ STATE CHECK PASSED in ${stateCheckDuration}ms"
                    )
                    DebugLogger.debugLog(
                        "ChatViewModel",
                        "├─ Concept map skipped, state=$stateSnapshot"
                    )
                    DebugLogger.debugLog(
                        "ChatViewModel",
                        "└─ CONCEPT MAP GENERATION ABANDONED"
                    )
                    resetConceptMap()
                    return@launch
                }

                DebugLogger.debugLog(
                    "ChatViewModel",
                    "├─ STATE CHECK PASSED in ${stateCheckDuration}ms - State: $stateSnapshot"
                )

                // ─────────────────────────────────────────────────────────────
                // MODEL SELECTION PHASE
                // ─────────────────────────────────────────────────────────────
                val currentModel = _selectedModel.value
                val isGroq = isGroqModel(currentModel)
                val modelCheckTime = System.currentTimeMillis()
                val modelCheckDuration = modelCheckTime - stateCheckTime

                DebugLogger.debugLog(
                    "ChatViewModel",
                    "├─ MODEL SELECTION in ${modelCheckDuration}ms"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "├─ Selected Model: $currentModel"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "├─ Is Groq: $isGroq"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "├─ Calling ${if (isGroq) "LLMClient.queryLLM()" else "GeminiLLMClient.queryLLM()"}"
                )

                // ─────────────────────────────────────────────────────────────
                // LLM API CALL PHASE
                // ─────────────────────────────────────────────────────────────
                val llmCallStartTime = System.currentTimeMillis()
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "│"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "├─ [LLM API CALL] Starting at $llmCallStartTime"
                )

                val response = if (isGroq) {
                    llmClient.queryLLM(aiResponse, _currentLanguage.value)
                } else {
                    geminiClient.queryLLM(aiResponse, _currentLanguage.value)
                }

                val llmCallEndTime = System.currentTimeMillis()
                val llmCallDuration = llmCallEndTime - llmCallStartTime

                DebugLogger.debugLog(
                    "ChatViewModel",
                    "├─ [LLM API CALL] Completed in ${llmCallDuration}ms"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "├─ Response body size: ${response.length} characters"
                )

                // ─────────────────────────────────────────────────────────────
                // JSON EXTRACTION PHASE
                // ─────────────────────────────────────────────────────────────
                val jsonExtractStartTime = System.currentTimeMillis()
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "│"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "├─ [JSON EXTRACTION] Starting at $jsonExtractStartTime"
                )

                val json = if (isGroq) {
                    llmClient.extractConceptMapJSON(response)
                } else {
                    geminiClient.extractConceptMapJSON(response)
                }

                val jsonExtractEndTime = System.currentTimeMillis()
                val jsonExtractDuration = jsonExtractEndTime - jsonExtractStartTime

                DebugLogger.debugLog(
                    "ChatViewModel",
                    "├─ [JSON EXTRACTION] Completed in ${jsonExtractDuration}ms"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "├─ Extracted JSON size: ${json.length} characters"
                )

                // ─────────────────────────────────────────────────────────────
                // PROGRESSIVE RENDERING PHASE
                // ─────────────────────────────────────────────────────────────
                val progressiveRenderStartTime = System.currentTimeMillis()
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "│"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "├─ [PROGRESSIVE RENDER] Starting at $progressiveRenderStartTime"
                )

                startProgressiveConceptMap(json)

                // ─────────────────────────────────────────────────────────────
                // OVERALL SUMMARY
                // ─────────────────────────────────────────────────────────────
                val overallEndTime = System.currentTimeMillis()
                val overallDuration = overallEndTime - startTimeMs

                DebugLogger.debugLog(
                    "ChatViewModel",
                    "│"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "└─ CONCEPT MAP GENERATION COMPLETE"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "    BREAKDOWN:"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "    • State Check:        ${stateCheckDuration}ms"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "    • Model Selection:    ${modelCheckDuration}ms"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "    • LLM API Call:       ${llmCallDuration}ms "
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "    • JSON Extraction:    ${jsonExtractDuration}ms"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "    ─────────────────────────────────"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "    • TOTAL:              ${overallDuration}ms"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "═══════════════════════════════════════════════════════════"
                )

            } catch (e: Exception) {
                val errorTime = System.currentTimeMillis()
                val errorDuration = errorTime - startTimeMs

                DebugLogger.errorLog(
                    "ChatViewModel",
                    "└─  CONCEPT MAP GENERATION FAILED after ${errorDuration}ms"
                )
                DebugLogger.errorLog(
                    "ChatViewModel",
                    "    Error: ${e.message}"
                )
                DebugLogger.errorLog(
                    "ChatViewModel",
                    "═══════════════════════════════════════════════════════════"
                )
                resetConceptMap()
            }
        }
    }


    private fun startProgressiveConceptMap(conceptMapJson: String) {
        conceptMapJob?.cancel()

        val progressiveRenderStartTime = System.currentTimeMillis()
        DebugLogger.debugLog(
            "ChatViewModel",
            "├─ Progressive Rendering Started"
        )
        DebugLogger.debugLog(
            "ChatViewModel",
            "├─ Initial JSON size: ${conceptMapJson.length} chars"
        )

        conceptMapJob = viewModelScope.launch {
            try {
                val jsonObj = JSONObject(conceptMapJson)
                val nodesArray = jsonObj.optJSONArray("nodes") ?: JSONArray()
                val edgesArray = jsonObj.optJSONArray("edges") ?: JSONArray()

                DebugLogger.debugLog(
                    "ChatViewModel",
                    "├─ Nodes to render: ${nodesArray.length()}"
                )
                DebugLogger.debugLog(
                    "ChatViewModel",
                    "├─ Edges to render: ${edgesArray.length()}"
                )

                if (nodesArray.length() == 0 && edgesArray.length() == 0) {
                    _conceptMapJSON.value = conceptMapJson
                    DebugLogger.debugLog(
                        "ChatViewModel",
                        "├─ Empty concept map - showing as-is"
                    )
                    return@launch
                }

                val progressiveNodes = JSONArray()
                val progressiveEdges = JSONArray()
                val audioSegments = jsonObj.optJSONArray("audioSegments") ?: JSONArray()

                // Add nodes one by one with delay
                var nodeCount = 0
                for (i in 0 until nodesArray.length()) {
                    val nodeAddStartTime = System.currentTimeMillis()
                    progressiveNodes.put(nodesArray.getJSONObject(i))
                    updateConceptMapState(jsonObj, progressiveNodes, JSONArray(), audioSegments)
                    val nodeAddDuration = System.currentTimeMillis() - nodeAddStartTime

                    nodeCount++
                    if (nodeCount <= 2 || nodeCount == nodesArray.length()) {
                        DebugLogger.debugLog(
                            "ChatViewModel",
                            "├─ Node $nodeCount/${nodesArray.length()} added (${nodeAddDuration}ms)"
                        )
                    }

                    delay(400L)
                }

                // Add edges one by one
                var edgeCount = 0
                for (i in 0 until edgesArray.length()) {
                    val edgeAddStartTime = System.currentTimeMillis()
                    progressiveEdges.put(edgesArray.getJSONObject(i))
                    updateConceptMapState(jsonObj, progressiveNodes, progressiveEdges, audioSegments)
                    val edgeAddDuration = System.currentTimeMillis() - edgeAddStartTime

                    edgeCount++
                    if (edgeCount <= 2 || edgeCount == edgesArray.length()) {
                        DebugLogger.debugLog(
                            "ChatViewModel",
                            "├─ Edge $edgeCount/${edgesArray.length()} added (${edgeAddDuration}ms)"
                        )
                    }
                    //Delay between edge additions
                    delay(300L)
                }

                val progressiveRenderEndTime = System.currentTimeMillis()
                val progressiveRenderDuration = progressiveRenderEndTime - progressiveRenderStartTime

                DebugLogger.debugLog(
                    "ChatViewModel",
                    "├─ Progressive Rendering Completed in ${progressiveRenderDuration}ms"
                )

            } catch (e: Exception) {
                DebugLogger.errorLog(
                    "ChatViewModel",
                    "Concept map animation error: ${e.message}"
                )
                _conceptMapJSON.value = conceptMapJson
            }
        }
    }

    private fun updateConceptMapState(
        jsonObj: JSONObject,
        nodes: JSONArray,
        edges: JSONArray,
        audioSegments: JSONArray
    ) {
        val progressMap = JSONObject().apply {
            put("visualization_type", jsonObj.optString("visualization_type", "Concept Map"))
            put("main_concept", jsonObj.optString("main_concept", ""))
            put("nodes", nodes)
            put("edges", edges)
            put("audioSegments", audioSegments)
        }
        _conceptMapJSON.value = progressMap.toString()
    }

    fun refreshAvailableConcepts(context: Context, languageShort: String) {
        viewModelScope.launch {
            try {
                val concepts = conceptRepository.getConcepts(context, languageShort)
                _availableConcepts.value = concepts

                withContext(Dispatchers.IO) {
                    val prefs = context.getSharedPreferences("concepts_store", Context.MODE_PRIVATE)
                    val jsonArray = JSONArray().apply {
                        concepts.forEach { put(it) }
                    }
                    prefs.edit { putString("concepts_$languageShort", jsonArray.toString()) }
                }
            } catch (e: Exception) {
                DebugLogger.errorLog("ChatViewModel", "refreshAvailableConcepts failed: ${e.message}")
            }
        }
    }

    private fun refreshAvailableModels() {
        viewModelScope.launch {
            try {
                // Fetch AgenticAI models
                val agenticResult = agenticAIClient.getAvailableModels()
                val googleModels = if (agenticResult.isSuccess) {

                    val response = agenticResult.getOrNull()
                    DebugLogger.debugLog("ChatViewModel", "API Response: $response")
                    DebugLogger.debugLog("ChatViewModel", "Response success: ${response?.success}")
                    DebugLogger.debugLog("ChatViewModel", "Response total: ${response?.total}")
                    DebugLogger.debugLog("ChatViewModel", "Response models: ${response?.availableModels}")

                    val models = response?.availableModels ?: emptyList()
                    DebugLogger.debugLog("ChatViewModel", " Fetched ${models.size} models from API")
                    models.forEach { model ->
                        DebugLogger.debugLog("ChatViewModel", "  - $model")
                    }
                    models
                } else {
                    val error = agenticResult.exceptionOrNull()?.message ?: "Unknown error"
                    DebugLogger.errorLog("ChatViewModel", "Failed to fetch models: $error")
                    emptyList()                }
                // Merge
                val mergedModels = (googleModels + groqModels).distinct()

                _availableModels.value = mergedModels

                // Set default model if none selected
                if (_selectedModel.value.isEmpty() && mergedModels.isNotEmpty()) {
                    setSelectedModel(mergedModels.first())
                }

                DebugLogger.debugLog(
                    "ChatViewModel",
                    "Models loaded: ${mergedModels.size} total (${googleModels.size} Google + ${groqModels.size} Groq)"
                )
            } catch (e: Exception) {
                DebugLogger.errorLog("ChatViewModel", "refreshAvailableModels failed: ${e.message}")

                // Fallback to Groq only
                val fallbackModel = "meta-llama/llama-4-scout-17b-16e-instruct"
                _availableModels.value = listOf(fallbackModel)
                if (_selectedModel.value.isEmpty()) {
                    setSelectedModel(fallbackModel)
                }
            }
        }
    }

    fun startNetworkObservation(context: Context) {
        if (connectivityObserverJob != null) return

        connectivityObserverJob = viewModelScope.launch {
            NetworkUtils.connectivityFlow(context).collect { connected ->
                val wasConnected = _isConnected.value
                _isConnected.value = connected

                if (!connected && wasConnected) {
                    DebugLogger.debugLog("ChatViewModel", "Network lost")
                    if (_isLoading.value) {
                        _isLoading.value = false
                        handleRequestFailure("Network connection lost")
                    }
                }
            }
        }
    }

    fun stopNetworkObservation() {
        connectivityObserverJob?.cancel()
        connectivityObserverJob = null
    }

    private fun handleRequestFailure(errorMessage: String) {
        _messages.update {
            it + ChatMessageModel(
                content = "Error: $errorMessage",
                sender = "ai",
                isError = true,
                canRetry = true
            )
        }
        _isTyping.value = false
    }

    private fun cancelAnimations() {
        typingJob?.cancel()
        conceptMapJob?.cancel()
        _isTyping.value = false
        _typingText.value = ""
    }

    fun setCurrentLanguage(languageShort: String, context: Context) {
        val oldLang = _currentLanguage.value
        _currentLanguage.value = languageShort

        viewModelScope.launch {
            // Re-translate last AI response
            _originalAIResponse.value.takeIf { it.isNotBlank() }?.let { original ->
                val newTrans = withContext(Dispatchers.IO) { getOrTranslateText(original, context) }
                _translatedOutput.value = newTrans
                _fullTextForTTS.value = newTrans
            }

            // Re-translate all past AI messages
            if (oldLang != languageShort) {
                _messages.update { list ->
                    list.map { msg ->
                        if (msg.sender == "ai" && !msg.isError) {
                            msg
                        } else msg
                    }
                }
            }
        }
    }

    fun getOriginalConceptName(context: Context, displayedConcept: String, languageShort: String): String {
        return conceptRepository.getOriginalConcept(context, displayedConcept, languageShort)
    }

    fun hasExistingSession(displayedConcept: String, context: Context): Boolean {
        val originalConcept = conceptRepository.getOriginalConcept(
            context,
            displayedConcept,
            _currentLanguage.value
        )
        val stored = conceptThreadMap[originalConcept]
        return stored != null
    }

    fun startFreshSession(displayedConcept: String, context: Context) {
        viewModelScope.launch {
            val originalConcept = conceptRepository.getOriginalConcept(
                context,
                displayedConcept,
                _currentLanguage.value
            )
            try {
                conceptThreadMap.remove(originalConcept)
                conceptSessionMap.remove(originalConcept)

                withContext(Dispatchers.IO) {
                    SessionRepository(context.applicationContext).deleteMapping(originalConcept)
                }

                _isSessionStarted.value = false
                agenticAIClient.setCurrentThreadAndSession(null, null)
                _messages.value = emptyList()
                resetConceptMap()

                selectConcept(displayedConcept, context)
            }catch (e:Exception){
                DebugLogger.errorLog("ChatViewModel", "startFreshSession failed: ${e.message}")
                handleRequestFailure("Failed to start fresh session: ${e.message}")
                _isLoading.value =false
            }
        }
    }


    override fun onCleared() {
        super.onCleared()
        cancelAnimations()
        stopNetworkObservation()
        slowNetworkJob?.cancel()
    }
}