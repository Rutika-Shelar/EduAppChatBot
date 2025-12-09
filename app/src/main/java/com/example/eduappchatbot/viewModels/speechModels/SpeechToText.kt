package com.example.eduappchatbot.viewModels.speechModels

import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.example.eduappchatbot.utils.DebugLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpeechToText : ViewModel() {
    private val TAG = "SpeechToText"
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val RECORD_AUDIO_PERMISSION_REQUEST = 100
        private const val CONTINUOUS_RESTART_DELAY_MS = 100L
    }

    data class STTState(
        val isInitialized: Boolean = false,
        val isListening: Boolean = false,
        val selectedLanguage: String = "en-IN",
        val statusMessage: String = "",
        val resultText: String = "",
        val hasPermission: Boolean = false
    )

    private val _state = MutableStateFlow(STTState())
    val state: StateFlow<STTState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private val fullTranscript = StringBuilder()
    private var appContext: Context? = null

    // Initialize the SpeechToText system (UI should call this once e.g. on screen start)
    fun initialize(context: Context) {
        if (_state.value.isInitialized) return
        appContext = context.applicationContext
        DebugLogger.debugLog(TAG, "initialize() - Starting setup")

        val hasPermission = checkAudioPermission()
        _state.value = _state.value.copy(hasPermission = hasPermission)

        if (hasPermission) {
            initializeSpeechRecognizer()
        }
    }

    private fun checkAudioPermission(): Boolean {
        return appContext?.let { ctx ->
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } ?: false
    }

    private fun initializeSpeechRecognizer() {
        appContext?.let { ctx ->
            if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                    setRecognitionListener(speechRecognitionListener)
                }
                _state.value = _state.value.copy(
                    isInitialized = true,
                    statusMessage = "Speech recognizer initialized"
                )
                DebugLogger.debugLog(TAG, "Speech recognizer initialized successfully")
            } else {
                _state.value = _state.value.copy(
                    statusMessage = "Speech recognition not available on this device"
                )
                DebugLogger.errorLog(TAG, "Recognition not available")
            }
        }
    }

    /**
     * Public start API.
     * Pass `requestedLanguage` like "kn" or "kn-IN" to request Kannada recognition.
     * If `requestedLanguage` is null, current selected language is used.
     */
    fun startListening(requestedLanguage: String? = null) {
        requestedLanguage?.let { lang ->
            val normalized = normalizeLanguageTag(lang)
            setLanguage(normalized)
        }
        if (!_state.value.hasPermission) {
            _state.value = _state.value.copy(statusMessage = "Audio permission required")
            DebugLogger.errorLog(TAG, "startListening: missing audio permission")
            return
        }

        if (!_state.value.isInitialized) {
            _state.value = _state.value.copy(statusMessage = "Speech recognizer not initialized")
            DebugLogger.errorLog(TAG, "startListening: recognizer not initialized")
            return
        }

        if (_state.value.isListening) {
            DebugLogger.debugLog(TAG, "startListening: already listening")
            return
        }

        // Normalize and set language if provided
        requestedLanguage?.let { lang ->
            val normalized = normalizeLanguageTag(lang)
            setLanguage(normalized)
        }

        // Reset transcript and update state
        fullTranscript.clear()
        _state.value = _state.value.copy(
            resultText = "",
            statusMessage = "Listening...",
            isListening = true
        )

        DebugLogger.debugLog(TAG, "=== USER STARTED NEW SPEECH SESSION === language=${_state.value.selectedLanguage}")
        startRecognition()
    }

    // Stop the current speech recognition session
    fun stopListening() {
        if (!_state.value.isListening) return
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            DebugLogger.errorLog(TAG, "stopListening error: $e")
        }
        _state.value = _state.value.copy(
            isListening = false,
            statusMessage = "Stopped"
        )
        DebugLogger.debugLog(TAG, "Stopped Listening...")
    }

    // Internal method to start recognition using current state's language
    private fun startRecognition() {
        if (!_state.value.isListening) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, _state.value.selectedLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, _state.value.selectedLanguage)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        DebugLogger.debugLog(TAG, "startRecognition() - listening with language=${_state.value.selectedLanguage}")
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            DebugLogger.errorLog(TAG, "Error starting speech recognizer: $e")
            _state.value = _state.value.copy(statusMessage = "Error starting recognizer")
            // stop listening to avoid inconsistent state
            stopListening()
        }
    }

    fun setLanguage(language: String) {
        val normalized = normalizeLanguageTag(language)
        _state.value = _state.value.copy(selectedLanguage = normalized)
        DebugLogger.debugLog(TAG, "Language set to: $normalized")
    }

    // Accepts short or full tags: "kn" -> "kn-IN", "en" -> "en-IN", returns full tag
    private fun normalizeLanguageTag(tag: String): String {
        val code = tag.trim().lowercase()
        return when {
            code.startsWith("kn") -> "kn-IN"
            code.startsWith("hi") -> "hi-IN"
            code.startsWith("ta") -> "ta-IN"
            code.startsWith("te") -> "te-IN"
            code.startsWith("en") -> "en-IN"
            code.contains("-") -> tag // assume already full tag like en-IN
            else -> "en-IN"
        }
    }

    private val speechRecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            DebugLogger.debugLog(TAG, "onReadyForSpeech - Mic is live")
            _state.value = _state.value.copy(statusMessage = "Ready for speech...")
        }

        override fun onBeginningOfSpeech() {
            _state.value = _state.value.copy(statusMessage = "Speech detected...")
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _state.value = _state.value.copy(statusMessage = "Processing speech...")
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech (timeout)"
                else -> "Unknown error"
            }

            // Clear listening flag for recoverable errors
            _state.value = _state.value.copy(
                isListening = false,
                statusMessage = errorMessage
            )
            DebugLogger.errorLog(TAG, "onError: $errorMessage ($error)")

            // For timeouts or no match, do not spam — keep user in control to restart. UI may call startListening again.
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (matches.isNullOrEmpty() || matches[0].trim().isEmpty()) {
                DebugLogger.debugLog(TAG, "onResults() - Empty or blank result → stopping")
                // treat as no speech
                _state.value = _state.value.copy(statusMessage = "No speech detected")
                _state.value = _state.value.copy(isListening = false)
                return
            }

            val spokenText = matches[0].trim()
            DebugLogger.debugLog(TAG, "onResults() - Recognized: \"$spokenText\"")

            if (fullTranscript.isEmpty()) fullTranscript.append(spokenText) else fullTranscript.append(" ").append(spokenText)

            _state.value = _state.value.copy(
                resultText = fullTranscript.toString(),
                statusMessage = "Got it! Keep speaking..."
            )

            // If still in listening mode restart recognition to continue streaming input
            if (_state.value.isListening) {
                handler.postDelayed({ startRecognition() }, CONTINUOUS_RESTART_DELAY_MS)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!partial.isNullOrEmpty() && partial[0].trim().isNotEmpty()) {
                val current = if (fullTranscript.isEmpty()) partial[0] else fullTranscript.toString() + " " + partial[0]
                _state.value = _state.value.copy(resultText = current)
            }
        }

        override fun onRmsChanged(rmsdB: Float) {}
    }

    fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onPermissionGranted()
            } else {
                appContext?.let { ctx ->
                    Toast.makeText(ctx, "Audio permission required", Toast.LENGTH_LONG).show()
                }
                _state.value = _state.value.copy(
                    hasPermission = false,
                    statusMessage = "Audio permission denied"
                )
                DebugLogger.errorLog(TAG, "Permission denied")
            }
        }
    }

    private fun onPermissionGranted() {
        _state.value = _state.value.copy(hasPermission = true)
        if (!_state.value.isInitialized) {
            initializeSpeechRecognizer()
        }
    }

    override fun onCleared() {
        super.onCleared()
        destroy()
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            DebugLogger.errorLog(TAG, "destroy error: $e")
        } finally {
            speechRecognizer = null
            appContext = null
            _state.value = _state.value.copy(
                isInitialized = false,
                isListening = false
            )
        }
    }
}
