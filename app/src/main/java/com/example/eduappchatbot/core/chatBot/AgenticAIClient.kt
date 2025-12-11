package com.example.eduappchatbot.core.chatBot

import com.example.eduappchatbot.utils.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import retrofit2.Response
import java.io.IOException

class AgenticAIClient(
    agenticAIBaseUrl: String
) {
    val service: AgenticAIService

    private val _currentThreadId = MutableStateFlow<String?>(null)
    val currentThreadId: StateFlow<String?> = _currentThreadId

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId

    init {
        val retrofit = RetrofitProvider.buildRetrofit(agenticAIBaseUrl)
        service = retrofit.create(AgenticAIService::class.java)
    }

    fun getCurrentThreadId(): String? = _currentThreadId.value
    fun getCurrentSessionId(): String? = _currentSessionId.value

    fun setCurrentThreadAndSession(threadId: String?, sessionId: String?) {
        _currentThreadId.value = threadId
        _currentSessionId.value = sessionId
    }

    private suspend fun <T : Any> callWithRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 300L,
        factor: Double = 2.0,
        call: suspend () -> Response<T>
    ): Result<T> {
        var attempt = 0
        var lastEx: Exception? = null
        var delayMs = initialDelayMs

        while (attempt < maxAttempts) {
            attempt++
            try {
                val resp = call()

                when {
                    // HTTP success with valid body
                    resp.isSuccessful && resp.body() != null -> {
                        val body = resp.body()!!

                        // Check application-level success flag if it exists
                        val isAppSuccess = when (body) {
                            is StartSessionResponse -> body.success
                            is ContinueSessionResponse -> body.success
                            is SessionStatusResponse -> body.success
                            is SessionHistoryResponse -> body.success
                            is SessionSummaryResponse -> body.success
                            is ConceptsListResponse -> body.success
                            is PersonasListResponse -> body.success
                            is TestImageResponse -> body.success
                            is TestSimulationResponse -> body.success
                            is HealthResponse -> true // No success field
                            else -> true
                        }

                        if (isAppSuccess) {
                            return Result.success(body)
                        } else {
                            // Application returned success=false
                            val message = when (body) {
                                is StartSessionResponse -> body.message
                                is ContinueSessionResponse -> body.message
                                is SessionStatusResponse -> body.message
                                is SessionHistoryResponse -> body.message
                                is SessionSummaryResponse -> body.message
                                is ConceptsListResponse -> body.message
                                is PersonasListResponse -> body.message
                                is TestImageResponse -> body.message
                                is TestSimulationResponse -> body.message
                                else -> "Request failed"
                            }
                            lastEx = IOException("Server error: ${message ?: "Unknown error"}")
                        }
                    }

                    // HTTP success but null body
                    resp.isSuccessful -> {
                        lastEx = IOException("Empty response body (HTTP ${resp.code()})")
                    }

                    // HTTP error
                    else -> {
                        val errBody = try {
                            resp.errorBody()?.string()
                        } catch (_: Exception) {
                            null
                        }
                        lastEx = IOException("HTTP ${resp.code()}: ${errBody ?: resp.message()}")
                    }
                }
            } catch (e: Exception) {
                lastEx = e
                DebugLogger.errorLog(
                    "AgenticAIClient",
                    "Request attempt $attempt/$maxAttempts failed: ${e.message}"
                )
            }

            if (attempt < maxAttempts) {
                DebugLogger.debugLog(
                    "AgenticAIClient",
                    "Retrying in ${delayMs}ms... (attempt $attempt/$maxAttempts)"
                )
                delay(delayMs)
                delayMs = (delayMs * factor).toLong()
            }
        }

        // All attempts exhausted
        return Result.failure(lastEx ?: IOException("Unknown network error after $maxAttempts attempts"))
    }

    suspend fun startSession(
        conceptTitle: String,
        studentId: String,
        personaName: String? = null,
        sessionLabel: String? = null,
        isKannada: Boolean = false
    ): Result<StartSessionResponse> = withContext(Dispatchers.IO) {
        val req = StartSessionRequest(
            conceptTitle = conceptTitle,
            studentId = studentId,
            personaName = personaName,
            sessionLabel = sessionLabel,
            isKannada = isKannada
        )

        val res = callWithRetry { service.startSession(req) }

        // Update state only on success
        if (res.isSuccess) {
            val body = res.getOrNull()
            body?.threadId?.let { _currentThreadId.value = it }
            body?.sessionId?.let { _currentSessionId.value = it }
            DebugLogger.debugLog(
                "AgenticAIClient",
                "Session started: threadId=${body?.threadId}, sessionId=${body?.sessionId}"
            )
        }
        res
    }

    suspend fun continueSession(userMessage: String): Result<ContinueSessionResponse> =
        withContext(Dispatchers.IO) {
            val thread = _currentThreadId.value
                ?: return@withContext Result.failure(IOException("No active thread"))

            val req = ContinueSessionRequest(threadId = thread, userMessage = userMessage)

            val res = callWithRetry { service.continueSession(req) }

            // Update threadId if it changed (edge case)
            if (res.isSuccess) {
                val body = res.getOrNull()
                body?.threadId?.let {
                    if (it != _currentThreadId.value) {
                        DebugLogger.debugLog(
                            "AgenticAIClient",
                            "ThreadId updated: ${_currentThreadId.value} -> $it"
                        )
                        _currentThreadId.value = it
                    }
                }
            }

            res
        }

    suspend fun getSessionStatus(threadId: String): Result<SessionStatusResponse> =
        withContext(Dispatchers.IO) {
            callWithRetry { service.getSessionStatus(threadId) }
        }

    suspend fun getSessionHistory(threadId: String): Result<SessionHistoryResponse> =
        withContext(Dispatchers.IO) {
            callWithRetry { service.getSessionHistory(threadId) }
        }

    suspend fun getSessionSummary(threadId: String): Result<SessionSummaryResponse> =
        withContext(Dispatchers.IO) {
            callWithRetry { service.getSessionSummary(threadId) }
        }

    suspend fun getAvailableConcepts(): Result<ConceptsListResponse> =
        withContext(Dispatchers.IO) {
            callWithRetry { service.getAvailableConcepts() }
        }

    suspend fun getPersonas(): Result<PersonasListResponse> =
        withContext(Dispatchers.IO) {
            callWithRetry { service.getPersonas() }
        }

    suspend fun healthCheck(): Result<HealthResponse> =
        withContext(Dispatchers.IO) {
            callWithRetry(maxAttempts = 1) { service.healthCheck() }
        }
}