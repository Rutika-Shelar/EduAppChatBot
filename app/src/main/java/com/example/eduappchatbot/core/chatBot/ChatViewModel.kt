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
    apiBaseUrl: String,
    geminiApiKey: String = "",
    geminiUserClass: String = "6",
    geminiNodeNumber: String = "8",//change this
    geminiMaxWord: String = "250"
) : ViewModel() {

    private val llmClient = AgenticAIClient(apiBaseUrl)
    private val geminiClient = GeminiLLMClient(geminiApiKey, geminiUserClass, geminiNodeNumber, geminiMaxWord)

    private val conceptThreadMap = mutableMapOf<String, String>()
    private val conceptSessionMap = mutableMapOf<String, String>()

    private val conceptRepository = ConceptRepository(llmClient.service)

    private val _messages = MutableStateFlow<List<ChatMessageModel>>(emptyList())
    val messages: StateFlow<List<ChatMessageModel>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _typingText = MutableStateFlow("")
    val typingText: StateFlow<String> = _typingText

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    private val _conceptMapJSON = MutableStateFlow(
        """{"visualization_type":"None","main_concept":"Chat for a Concept Map","nodes":[],"edges":[]}"""
    )
    val conceptMapJSON: StateFlow<String> = _conceptMapJSON

    private val _shouldStartTTS = MutableStateFlow(false)
    val shouldStartTTS: StateFlow<Boolean> = _shouldStartTTS

    private val _fullTextForTTS = MutableStateFlow("")
    val fullTextForTTS: StateFlow<String> = _fullTextForTTS

    private val _lastUserMessage = MutableStateFlow("")
    val lastUserMessage: StateFlow<String> = _lastUserMessage

    private val _agentState = MutableStateFlow("")
    val agentState: StateFlow<String> = _agentState

    private val _agentMetadata = MutableStateFlow<SessionMetadata?>(null)
    val agentMetadata: StateFlow<SessionMetadata?> = _agentMetadata

    private val _translatedOutput = MutableStateFlow("")
    val translatedOutput: StateFlow<String> = _translatedOutput

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _availableConcepts = MutableStateFlow<List<String>>(emptyList())
    val availableConcepts: StateFlow<List<String>> = _availableConcepts

    private val _selectedConcept = MutableStateFlow<String?>(null)
    val selectedConcept: StateFlow<String?> = _selectedConcept

    private val _isSessionStarted = MutableStateFlow(false)
    val isSessionStarted: StateFlow<Boolean> = _isSessionStarted

    private val _pendingFirstUserMessage = MutableStateFlow<String?>(null)

    private val _currentLanguage = MutableStateFlow("en")
    val currentLanguage: StateFlow<String> = _currentLanguage

    private val _userId = MutableStateFlow("")
    val userId: StateFlow<String> = _userId

    private var typingJob: Job? = null
    private var conceptMapJob: Job? = null
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

            val language = _currentLanguage.value
            refreshAvailableConcepts(context, language)
            autoStartSavedConcept(context)

            DebugLogger.debugLog("ChatViewModel", "Initialization complete")
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

    // Select a concept to start or resume a session
    fun selectConcept(displayedConcept: String, context: Context) {
        viewModelScope.launch {
            val originalConcept = conceptRepository.getOriginalConcept(
                context,
                displayedConcept,
                _currentLanguage.value
            )

            _selectedConcept.value = displayedConcept

            DebugLogger.debugLog(
                "ChatViewModel",
                "Concept selected - Displayed: '$displayedConcept', Original: '$originalConcept'"
            )

            _isLoading.value = true
            cancelAnimations()

            val stored = loadThreadMapping(context, originalConcept)
            if (stored != null) {
                resumeExistingSession(context, stored.first, stored.second)
                return@launch
            }

            startNewSession(context, originalConcept)
        }
    }
// Resume an existing session using stored thread and session IDs
    private suspend fun resumeExistingSession(context: Context, threadId: String, sessionId: String?) {
        DebugLogger.debugLog("ChatViewModel", "Resuming session - thread=$threadId")

        llmClient.setCurrentThreadAndSession(threadId, sessionId)
        _isSessionStarted.value = true

        try {
            val histResult = llmClient.getSessionHistory(threadId)
            if (histResult.isSuccess) {
                val history = histResult.getOrNull()
                val messages = history?.messages ?: emptyList()
                val lastAssistant = messages.asSequence()
                    .lastOrNull { msg ->
                        (msg["role"] as? String)?.lowercase() in listOf("assistant", "ai")
                    }
                    ?.get("content") as? String

                if (!lastAssistant.isNullOrBlank()) {
                    _fullTextForTTS.value = lastAssistant
                    fetchConceptMapWithGemini(lastAssistant)
                    startTypingAnimation(lastAssistant, context)
                }
            }
        } catch (e: Exception) {
            DebugLogger.errorLog("ChatViewModel", "Error resuming session: ${e.message}")
        } finally {
            _isLoading.value = false
        }

        _pendingFirstUserMessage.value?.let { msg ->
            _pendingFirstUserMessage.value = null
            sendMessageAfterSessionReady(msg, context)
        }
    }

    // Start a new session for the selected concept
    private suspend fun startNewSession(context: Context, originalConcept: String) {
        try {
            val isKannada = _currentLanguage.value.equals("kn", ignoreCase = true)
            val userName = _userId.value.ifBlank { "android_user" }

            DebugLogger.debugLog(
                "ChatViewModel",
                "Starting NEW session - Concept: '$originalConcept', User: '$userName', isKannada: $isKannada"
            )

            val result = withTimeout(120_000L) {
                llmClient.startSession(
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
                        fetchConceptMapWithGemini(welcomeText)
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
                    llmClient.continueSession(userMessage)
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

                        _fullTextForTTS.value = text
                        fetchConceptMapWithGemini(text)
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

    // Start the typing animation for AI response
    private fun startTypingAnimation(fullText: String, context: Context) {
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            _isTyping.value = true
            _typingText.value = ""

            DebugLogger.debugLog("ChatViewModel", "AI RAW OUTPUT: ${fullText.take(100)}")

            val finalText = withContext(Dispatchers.IO) {
                try {
                    val targetLang = _currentLanguage.value
                    val translated = if (targetLang == "en") {
                        TranslationHelper.translateText(fullText, "kn", "en")
                    } else {
                        TranslationHelper.translateText(fullText, "en", "kn")
                    }

                    translated.ifBlank { fullText }
                        .replace(Regex("^Agent:\\s*"), "")
                } catch (e: Exception) {
                    DebugLogger.errorLog("ChatViewModel", "Translation error: ${e.message}")
                    fullText
                }
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

    private fun fetchConceptMapWithGemini(aiResponse: String) {
        viewModelScope.launch {
            try {
                val response = geminiClient.queryLLM(aiResponse, _currentLanguage.value)
                val conceptMapJson = geminiClient.extractConceptMapJSON(response)
                startProgressiveConceptMap(conceptMapJson)
            } catch (e: Exception) {
                DebugLogger.errorLog("ChatViewModel", "Gemini concept map error: ${e.message}")
            }
        }
    }

    private fun startProgressiveConceptMap(conceptMapJson: String) {
        conceptMapJob?.cancel()
        conceptMapJob = viewModelScope.launch {
            try {
                val jsonObj = JSONObject(conceptMapJson)
                val nodesArray = jsonObj.optJSONArray("nodes") ?: JSONArray()
                val edgesArray = jsonObj.optJSONArray("edges") ?: JSONArray()

                if (nodesArray.length() == 0 && edgesArray.length() == 0) {
                    _conceptMapJSON.value = conceptMapJson
                    return@launch
                }

                val progressiveNodes = JSONArray()
                val progressiveEdges = JSONArray()
                val audioSegments = jsonObj.optJSONArray("audioSegments") ?: JSONArray()

                for (i in 0 until nodesArray.length()) {
                    progressiveNodes.put(nodesArray.getJSONObject(i))
                    updateConceptMapState(jsonObj, progressiveNodes, JSONArray(), audioSegments)
                    delay(400L)
                }

                for (i in 0 until edgesArray.length()) {
                    progressiveEdges.put(edgesArray.getJSONObject(i))
                    updateConceptMapState(jsonObj, progressiveNodes, progressiveEdges, audioSegments)
                    delay(300L)
                }
            } catch (e: Exception) {
                DebugLogger.errorLog("ChatViewModel", "Concept map animation error: ${e.message}")
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

    fun setCurrentLanguage(languageShort: String) {
        _currentLanguage.value = languageShort
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

            conceptThreadMap.remove(originalConcept)
            conceptSessionMap.remove(originalConcept)

            withContext(Dispatchers.IO) {
                val prefs = context.getSharedPreferences("session_map", Context.MODE_PRIVATE)
                val raw = prefs.getString("concept_thread_map", "{}") ?: "{}"
                val json = JSONObject(raw)
                json.remove(originalConcept)
                prefs.edit { putString("concept_thread_map", json.toString()) }
            }

            _isSessionStarted.value = false
            llmClient.setCurrentThreadAndSession(null, null)
            _messages.value = emptyList()

            selectConcept(displayedConcept, context)
        }
    }

    fun updateTranslatedOutput(text: String) {
        _translatedOutput.value = text
    }

    override fun onCleared() {
        super.onCleared()
        cancelAnimations()
        stopNetworkObservation()
        slowNetworkJob?.cancel()
    }
}