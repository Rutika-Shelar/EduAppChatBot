package com.example.eduappchatbot.core.chatBot

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AgenticAIService {
    //core session Endpoints
    @POST("/session/start")
    suspend fun startSession(@Body request: StartSessionRequest): Response<StartSessionResponse>

    @POST("/session/continue")
    suspend fun continueSession(@Body request: ContinueSessionRequest): Response<ContinueSessionResponse>

    @GET("/session/status/{thread_id}")
    suspend fun getSessionStatus(@Path("thread_id") threadId: String): Response<SessionStatusResponse>

    @GET("/session/history/{thread_id}")
    suspend fun getSessionHistory(@Path("thread_id") threadId: String): Response<SessionHistoryResponse>
    @GET("/session/summary/{thread_id}")
    suspend fun getSessionSummary(@Path("thread_id") threadId: String): Response<SessionSummaryResponse>

    @GET("/concepts")
    suspend fun getAvailableConcepts(): Response<ConceptsListResponse>

    @GET("/available-models")
    suspend fun getAvailableModels():Response<AvailableModelsResponse>
    //Utility Endpoints
    @GET("/personas")
    suspend fun getPersonas(): Response<PersonasListResponse>

    @GET("/health")
    suspend fun healthCheck(): Response<HealthResponse>

    //test endpoints
    @POST("/test/image")
    suspend fun getTestImage(@Body request: TestImageRequest): Response<TestImageResponse>

    @POST("/test/simulation")
    suspend fun getTestSimulation(@Body request: TestSimulationRequest): Response<TestSimulationResponse>

}

//All Data Classes
data class StartSessionRequest(
    @SerializedName("concept_title") val conceptTitle: String,
    @SerializedName("student_id") val studentId: String,
    @SerializedName("persona_name") val personaName: String? = null,
    @SerializedName("session_label") val sessionLabel: String? = null,
    @SerializedName("is_kannada") val isKannada: Boolean = false,
    @SerializedName("model") val model: String? = "gemma-3-27b-it",
    @SerializedName("student_level") val studentLevel: String = "medium"
)

data class ContinueSessionRequest(
    @SerializedName("thread_id") val threadId: String,
    @SerializedName("user_message") val userMessage: String,
    @SerializedName("model") val model: String? = "gemma-3-27b-it",
    @SerializedName("clicked_autosuggestion") val clickedAutosuggestion: Boolean? = false,
    @SerializedName("student_level") val studentLevel: String? = null
)


data class TestImageRequest(
    @SerializedName("concept_title") val conceptTitle: String,
    @SerializedName("definition_context") val definitionContext: String = ""
)

data class TestSimulationRequest(
    @SerializedName("concept_title") val conceptTitle: String,
    @SerializedName("simulation_type") val simulationType: String? = null
)

data class SessionMetadata(
    @SerializedName("show_simulation") val showSimulation: Boolean? = false,
    @SerializedName("simulation_config") val simulationConfig: Map<String,Any>? = emptyMap(),
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("image_description") val imageDescription : String? = null,
    @SerializedName("image_node") val imageNode: String? = null,
    @SerializedName("video_url") val videoUrl: String? = null,
    @SerializedName("video_node") val videoNode: String? = null,
    @SerializedName("quiz_score") val quizScore: Float? = null,
    @SerializedName("retrieval_score") val retrievalScore: Float? = null,
    @SerializedName("sim_concepts") val simConcepts: List<String>? = null,
    @SerializedName("sim_current_idx") val simCurrentIdx: Int? = null,
    @SerializedName("sim_total_concepts") val simTotalConcepts: Int? = null,
    @SerializedName("misconception_detected") val misconceptionDetected: Boolean? = false,
    @SerializedName("last_correction") val lastCorrection: String? = "",
    @SerializedName("node_transitions") val nodeTransitions: List<Map<String,Any>> = emptyList()
)

data class StartSessionResponse(
    val success: Boolean = false,
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("thread_id") val threadId: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("agent_response") val agentResponse: String? = null,
    @SerializedName("current_state") val currentState: String? = null,
    @SerializedName("concept_title") val conceptTitle: String? = null,
    val message: String? = "Session started successfully",
    val metadata: SessionMetadata = SessionMetadata(),
    val autosuggestions: List<String> = emptyList()
)

data class ContinueSessionResponse(
    val success: Boolean = false,
    @SerializedName("thread_id") val threadId: String? = null,
    @SerializedName("agent_response") val agentResponse: String? = null,
    @SerializedName("current_state") val currentState: String? = null,
    val metadata: SessionMetadata = SessionMetadata(),
    val message: String? = "Response generated successfully",
    val autosuggestions: List<String> = emptyList()
)

data class TestImageResponse(
    val success: Boolean = false,
    val concept: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("image_description") val imageDescription: String? = null,
    val message: String? = null
)

data class TestSimulationResponse(
    val success: Boolean = false,
    val concept: String? = null,
    @SerializedName("simulation_config") val simulationConfig: Map<String, Any>? = null,
    val message: String? = null
)

data class ConceptsListResponse(
    val success: Boolean = true,
    val concepts: List<String> = emptyList(),
    val total: Int = 0,
    val message: String? = "Available concepts retrieved successfully"
)

data class AvailableModelsResponse(
    val success: Boolean = true,
    @SerializedName("models") val availableModels: List<String> = emptyList(),
    val total : Int = 0,
    val default_model : String ?=null,
    val message: String? = null
)

data class PersonaInfo(
    val name: String,
    val description: String,
    @SerializedName("sample_phrases") val samplePhrases: List<String>
)

data class PersonasListResponse(
    val success: Boolean = true,
    val personas: List<PersonaInfo> = emptyList(),
    val total: Int = 0,
    val message: String? = "Available test personas retrieved successfully"
)

data class HealthResponse(
    val status: String,
    val version: String,
    val persistence: String,
    @SerializedName("agent_type") val agentType: String,
    @SerializedName("available_endpoints") val availableEndpoints: List<String>
)

data class SessionStatusResponse(
    val success: Boolean = false,
    @SerializedName("thread_id") val threadId: String? = null,
    val exists: Boolean = false,
    @SerializedName("current_state") val currentState: String? = null,
    val progress: Map<String, Any>? = null,
    @SerializedName("concept_title") val conceptTitle: String? = null,
    val message: String? = "Status retrieved successfully"
)

data class SessionHistoryResponse(
    val success: Boolean = false,
    @SerializedName("thread_id") val threadId: String? = null,
    val exists: Boolean = false,
    val messages: List<Map<String, Any>> = emptyList(),
    @SerializedName("node_transitions") val nodeTransitions: List<Map<String, Any>> = emptyList(),
    @SerializedName("concept_title") val conceptTitle: String? = null,
    val message: String? = "History retrieved successfully")

data class SessionSummaryResponse(
    val success: Boolean = false,
    @SerializedName("thread_id") val threadId: String? = null,
    val exists: Boolean = false,
    val summary: Map<String, Any>? = null,
    @SerializedName("quiz_score") val quizScore: Float? = null,
    @SerializedName("transfer_success") val transferSuccess: Boolean? = null,
    @SerializedName("misconception_detected") val misconceptionDetected: Boolean? = null,
    @SerializedName("definition_echoed") val definitionEchoed: Boolean? = null,
    val message: String? = "Summary retrieved successfully")