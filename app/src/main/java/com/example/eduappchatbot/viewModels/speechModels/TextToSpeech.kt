package com.example.eduappchatbot.viewModels.speechModels

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.ViewModel
import com.example.eduappchatbot.utils.DebugLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeech : ViewModel(), TextToSpeech.OnInitListener {

    data class TTSState(
        val isInitialized: Boolean = false,
        val isSpeaking: Boolean = false,
        val selectedLanguage: String = "en-IN",
        val detectedLanguage: String = "",
        val selectedCharacter: String = "boy",
        val speechRate: Float = 0.75f,
        val pitch: Float = 1.0f,
        val statusMessage: String = "",
        val debugMode: Boolean = false,
        val availableVoices: List<Voice> = emptyList(),

        val selectedVoice: Voice? = null,
        val selectedVoiceDisplayName: String = "Default Voice",
        val currentViseme: String = "rest",
        val voicesFullyLoaded: Boolean = false,
        )

    private val _state = MutableStateFlow(TTSState())
    val state: StateFlow<TTSState> = _state.asStateFlow()

    private var textToSpeech: TextToSpeech? = null
    private var webView: WebView? = null
    private var availableVoices: List<Voice> = emptyList()

    private val languagePatterns = mapOf(
        "hi-IN" to Regex("[\u0900-\u097F]+"),
        "kn-IN" to Regex("[\u0C80-\u0CFF]+"),
        "ta-IN" to Regex("[\u0B80-\u0BFF]+"),
        "te-IN" to Regex("[\u0C00-\u0C7F]+")
    )

    private val languageNames = mapOf(
        "en-IN" to "English",
        "hi-IN" to "हिंदी (Hindi)",
        "kn-IN" to "ಕನ್ನಡ (Kannada)",
        "ta-IN" to "தமிழ் (Tamil)",
        "te-IN" to "తెలుగు (Telugu)"
    )

    private var speechStartTime: Long = 0
    private var totalEstimatedDuration: Long = 0

    private val _currentWordIndex = MutableStateFlow(-1)
    val currentWordIndex: StateFlow<Int> = _currentWordIndex.asStateFlow()
    private var currentSpeakingWords: List<String> = emptyList()
    private var currentWordRanges: List<IntRange> = emptyList()
    private var currentSpeakingText: String = ""

    // Setup utterance progress listener
    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _state.value = _state.value.copy(isSpeaking = true)
            speechStartTime = System.currentTimeMillis()
            updateStatus("Playing")
            DebugLogger.debugLog("TTS", "Utterance started: $utteranceId")
        }

        override fun onDone(utteranceId: String?) {
            // RESET
            _state.value = _state.value.copy(isSpeaking = false)
            _currentWordIndex.value = -1
            speechStartTime = 0
            totalEstimatedDuration = 0
            stopLipSync()
            updateStatus("Playback finished")
            DebugLogger.debugLog("TTS", "Utterance done: $utteranceId")
        }

        override fun onError(utteranceId: String?) {
            // RESET
            _state.value = _state.value.copy(isSpeaking = false)
            _currentWordIndex.value = -1
            updateStatus("Playback error")
            DebugLogger.errorLog("TTS", "Utterance error: $utteranceId")
        }

        // onRangeStart is available API 26+
        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // map character offset to word index
                val idx = currentWordRanges.indexOfFirst { range -> start in range }
                if (idx >= 0) {
                    _currentWordIndex.value = idx
                }
            }
        }
    }

    fun initialize(context: Context) {
        if (textToSpeech != null) return

        updateStatus("Initializing Text-to-Speech...")
        DebugLogger.debugLog("TextToSpeech", "Initializing Text-to-Speech...")
        textToSpeech = TextToSpeech(context, this)
    }

    fun setupWebView(webView: WebView) {
        this.webView = webView
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Initialize with current character
                    switchCharacter(_state.value.selectedCharacter)
                    injectCharacterImages(view?.context!!)
                    updateStatus("Lip sync ready")
                }
            }

            loadUrl("file:///android_asset/LipSync.html")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let { tts ->
                // register listener so we can update current word / speaking state
                tts.setOnUtteranceProgressListener(utteranceListener)

                val voices = tts.voices?.toList() ?: emptyList()

                // Filter to only Indian accent voices (both local and network)
                val indianVoices = voices.filter { voice ->
                    val locale = voice.locale.toString().lowercase()
                    val country = voice.locale.country.lowercase()
                    // Only keep voices with Indian locale (IN)
                    country == "in" || locale.contains("_in") || locale.contains("-in")
                }

                // Sort voices: prioritize by quality, then by name
                val sortedVoices = indianVoices.sortedWith(
                    compareByDescending<Voice> { it.quality }
                        .thenBy { it.locale.displayLanguage }
                        .thenBy { it.name }
                )



                _state.value = _state.value.copy(
                    isInitialized = true,
                    availableVoices = sortedVoices,
                    voicesFullyLoaded = true,
                    statusMessage = "TTS Ready • ${sortedVoices.size} voices (${indianVoices.count { it.isNetworkConnectionRequired }} network, ${indianVoices.count { !it.isNetworkConnectionRequired }} local)"
                )

                DebugLogger.debugLog(
                    "TTS",
                    "Initialized with ${sortedVoices.size} Indian voices. Network voices: ${sortedVoices.count { it.isNetworkConnectionRequired }}, Local voices: ${sortedVoices.count { !it.isNetworkConnectionRequired }}"
                )
            }
        }
    }
    fun formatVoiceName(voice: Voice): String {
        val gender = when {
            voice.features?.contains("male") == true -> "Male"
            voice.features?.contains("female") == true -> "Female"
            else -> ""
        }
        val network = if (voice.isNetworkConnectionRequired) " (Online)" else ""
        val quality = when (voice.quality) {
            Voice.QUALITY_VERY_HIGH -> "Best"
            Voice.QUALITY_HIGH -> "High"
            else -> ""
        }.takeIf { it.isNotEmpty() }?.let { " • $it" } ?: ""

        return "$gender ${voice.locale.displayLanguage} • ${voice.name}$quality$network".trim()
    }
    fun getFilteredVoiceOptions(languageShort: String, avatar: String): List<String> {
        val shortLang = languageShort.split('-', limit = 2)[0].lowercase()

        // Filter voices by language
        val filteredVoices = _state.value.availableVoices.filter { voice ->
            voice.locale.language.equals(shortLang, ignoreCase = true) &&
                    voice.locale.country.equals("IN", ignoreCase = true)
        }

        return filteredVoices.map { formatVoiceName(it) }
    }
    fun getDefaultVoiceName(languageShort: String, avatar: String): String {
        val shortLang = languageShort.split('-', limit = 2)[0].lowercase()

        val preferredNames = when (Pair(shortLang, avatar.lowercase())) {
            Pair("en", "boy") -> listOf("en-in-x-ene-local", "ene-local")
            Pair("en", "girl") -> listOf("en-in-x-ena-local", "ena-local")
            Pair("kn", "boy") -> listOf("kn-in-x-knd-network", "knd-network")
            Pair("kn", "girl") -> listOf("kn-in-x-knc-local", "knc-local")
            else -> emptyList()
        }

        val defaultVoice = preferredNames.firstNotNullOfOrNull { prefName ->
            _state.value.availableVoices.find { voice ->
                voice.name.contains(prefName, ignoreCase = true)
            }
        }

        return defaultVoice?.let { formatVoiceName(it) } ?: "Default Voice"
    }
    fun setVoice(voice: Voice) {
        textToSpeech?.let { tts ->
            // Stop any ongoing speech
            tts.stop()

            //  Set the new voice
            tts.voice = voice

            //  FORCE the engine to accept the new voice with a silent flush
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak("", TextToSpeech.QUEUE_FLUSH, null, "force_voice_${System.currentTimeMillis()}")
            } else {
                @Suppress("DEPRECATION")
                val params = HashMap<String, String>()
                params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "force_voice_${System.currentTimeMillis()}"
                tts.speak("", TextToSpeech.QUEUE_FLUSH, params)
            }
            _state.value = _state.value.copy(
                selectedVoice = voice,
                selectedVoiceDisplayName = formatVoiceName(voice)
            )

            DebugLogger.debugLog("TTS", "Voice FORCED to: ${formatVoiceName(voice)}")
        } ?: DebugLogger.errorLog("TTS", "Cannot set voice — engine is null")
    }

    /**
     * Speak the given text with lip sync animation
     */
    fun speak(text: String) {
        if (!_state.value.isInitialized || text.isBlank()) {
            updateStatus("Error: Cannot speak - TTS not ready or empty text")
            return
        }

        currentSpeakingText = text

        // build word list and ranges for mapping char offset -> word index
        val matches = Regex("\\S+").findAll(text).toList()
        currentSpeakingWords = matches.map { it.value }
        currentWordRanges = matches.map { it.range.first..it.range.last }

        textToSpeech?.let { tts ->
            _state.value.selectedVoice?.let { preferredVoice ->
                if (tts.voice != preferredVoice) {
                    tts.stop()
                    tts.voice = preferredVoice
        // Force the engine to accept the new voice with a silent flush
                    if (Build.VERSION.SDK_INT >= 21) {
                        tts.speak("", TextToSpeech.QUEUE_FLUSH, null, "pre_speak_flush")
                    }
                }
            }

            val detectedLang = detectLanguage(text)
            if (detectedLang != _state.value.selectedLanguage) {
                setLanguageInternal(detectedLang)
            }

            tts.setSpeechRate(_state.value.speechRate)
            tts.setPitch(_state.value.pitch)

            speechStartTime = System.currentTimeMillis()
            totalEstimatedDuration = estimateDuration(text)
            startLipSync(text)

            val utteranceId = "tts_${System.currentTimeMillis()}"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            } else {
                val params = HashMap<String, String>()
                params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
                @Suppress("DEPRECATION")
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params)
            }
        }
    }

    /**
     * Stop current speech and lip sync
     */
    fun stop() {
        textToSpeech?.stop()
        stopLipSync()

        // Reset timing
        speechStartTime = 0
        totalEstimatedDuration = 0
        _currentWordIndex.value = -1

        _state.value = _state.value.copy(
            isSpeaking = false,
            statusMessage = "Speech stopped"
        )
    }

    /**
     * Update text and detect language
     */
    fun updateText(text: String) {
        val detectedLangCode = detectLanguage(text)
        val langName = languageNames[detectedLangCode] ?: detectedLangCode

        _state.value = _state.value.copy(
            detectedLanguage = langName,
            statusMessage = "Detected language: $langName"
        )
    }

    /**
     * Set speech language
     */
    fun setLanguage(languageCode: String) {
        setLanguageInternal(languageCode)
        val langName = languageNames[languageCode] ?: languageCode
        _state.value = _state.value.copy(
            selectedLanguage = languageCode,
            statusMessage = "Language set to $langName"
        )
    }

    private fun setLanguageInternal(languageCode: String) {
        textToSpeech?.let { tts ->
            val locale = when (languageCode) {
                "en-IN" -> Locale("en", "IN")
                "hi-IN" -> Locale("hi", "IN")
                "kn-IN" -> Locale("kn", "IN")
                "ta-IN" -> Locale("ta", "IN")
                "te-IN" -> Locale("te", "IN")
                else -> Locale("en", "IN")
            }

            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                updateStatus("Warning: Language $languageCode not fully supported, using fallback")
                tts.language = Locale("en", "IN")
                _state.value = _state.value.copy(selectedLanguage = "hi-IN")
            } else {
                _state.value = _state.value.copy(selectedLanguage = languageCode)
            }
        }
    }

    /**
     * Get current playback position in milliseconds
     * Estimates position based on elapsed time since speech started
     */
    fun getCurrentPosition(): Float {
        return if (_state.value.isSpeaking && speechStartTime > 0) {
            val elapsed = System.currentTimeMillis() - speechStartTime
            elapsed.toFloat()
        } else {
            0f
        }
    }

    /**
     * Estimate total duration of text in milliseconds
     * Average speaking rate: ~150 words per minute = 2.5 words/second = 400ms/word
     */
    private fun estimateDuration(text: String): Long {
        val wordCount = text.split("\\s+".toRegex()).size
        val baseRate = 400L// milliseconds per word
        // Adjust for speech rate
        val adjustedRate = (baseRate / _state.value.speechRate).toLong()
        return wordCount * adjustedRate
    }

    fun getEstimatedDurationMs(text: String): Long {
        return estimateDuration(text)
    }
    /**
     * Set speech rate
     */
    fun setSpeechRate(rate: Float) {
        _state.value = _state.value.copy(speechRate = rate)
    }
    /**
     * Set speech pitch
     */
    fun setPitch(pitch: Float) {
        _state.value = _state.value.copy(pitch = pitch)
    }

    fun injectCharacterImages(context: Context) {
        val boyBase64 = context.assets.open("images/boy.png").use {
            android.util.Base64.encodeToString(it.readBytes(), android.util.Base64.NO_WRAP)
        }
        val girlBase64 = context.assets.open("images/girl.png").use {
            android.util.Base64.encodeToString(it.readBytes(), android.util.Base64.NO_WRAP)
        }

        webView?.evaluateJavascript(
            """
        window.lipSync.setImageData({
            boy: "data:image/png;base64,$boyBase64",
            girl: "data:image/png;base64,$girlBase64"
        });
        """.trimIndent(),
            null
        )
    }
    /**
     * Switch character in lip sync animation
     */
    fun switchCharacter(character: String) {
        _state.value = _state.value.copy(selectedCharacter = character)

        webView?.post {
            webView?.evaluateJavascript(
                "window.AndroidLipSyncAPI.switchCharacter('$character')"
            ) { result ->
                DebugLogger.debugLog("LipSync", "Character switch result: $result")
            }
        }
    }
    /**
     * Toggle debug mode in lip sync
     */
    fun toggleDebug() {
        val newDebugMode = !_state.value.debugMode
        _state.value = _state.value.copy(debugMode = newDebugMode)

        webView?.post {
            webView?.evaluateJavascript(
                "window.AndroidLipSyncAPI.toggleDebug($newDebugMode)"
            ) { result ->
                DebugLogger.debugLog("LipSync", "Debug toggle result: $result")
            }
        }
    }
    /**
     * Test lip sync animation
     */
    fun testAnimation() {
        webView?.post {
            webView?.evaluateJavascript(
                "window.AndroidLipSyncAPI.testAnimation()"
            ) { result ->
                DebugLogger.debugLog("LipSync", "Test animation result: $result")
            }
        }
    }
    /**
     * Start lip sync animation
     */
    private fun startLipSync(text: String) {
        val escapedText = text.replace("'", "\\'").replace("\n", "\\n")
        webView?.post {
            webView?.evaluateJavascript(
                "window.AndroidLipSyncAPI.startLipSync('$escapedText')"
            ) { result ->
                DebugLogger.debugLog("LipSync", "Start lip sync result: $result")
            }
        }
    }
    /**
     * Stop lip sync animation
     */
    private fun stopLipSync() {
        webView?.post {
            webView?.evaluateJavascript(
                "window.AndroidLipSyncAPI.stopLipSync()"
            ) { result ->
                DebugLogger.debugLog("LipSync", "Stop lip sync result: $result")
            }
        }
    }
    /**
     * Detect language from text
     */
    private fun detectLanguage(text: String): String {
        if (text.isBlank()) return "en-IN"
        // Check for Indic scripts
        for ((langCode, pattern) in languagePatterns) {
            if (pattern.find(text) != null) {
                return langCode
            }
        }
        // Default to English
        return "en-IN"
    }

    /**
     * Update status message
     */
    private fun updateStatus(message: String) {
        _state.value = _state.value.copy(statusMessage = message)
        DebugLogger.debugLog("TTS", message)
    }


        fun applyDefaultsForAvatarLanguage(avatar: String, languageCode: String) {
            // Normalize to short language code (accept "en" or "en-IN")
            val shortLang = languageCode.split('-', limit = 2)[0].lowercase()

            val preferredNames = when (Pair(shortLang, avatar.lowercase())) {
                Pair("en", "boy") -> listOf("en-in-x-ene-local", "ene-local", "en-in-x-ene-network", "ene-network")
                Pair("en", "girl") -> listOf("en-in-x-ena-local", "ena-local", "en-in-x-ena-network", "ena-network")
                Pair("kn", "boy") -> listOf("kn-in-x-knd-network", "knd-network", "knd-in-x-knd-local", "knd-local")
                Pair("kn", "girl") -> listOf("kn-in-x-knc-network", "knc-network", "kn-in-x-knc-local", "knc-local")
                else -> emptyList()
            }

            // Filter voices to only Indian accents (en-IN, kn-IN, hi-IN, etc.)
            val indianVoices = _state.value.availableVoices.filter { voice ->
                val localeStr = voice.locale.toString().lowercase()
                localeStr.contains("_in") || localeStr.contains("-in")
            }

            // Try to find voice by preferred name
            var chosen: Voice? = preferredNames
                .firstNotNullOfOrNull { prefName ->
                    indianVoices.find { voice ->
                        voice.name.contains(prefName, ignoreCase = true)
                    }
                }

            // Fallback 1: Find by language and gender match
            if (chosen == null) {
                chosen = indianVoices.firstOrNull { voice ->
                    val localeMatch = voice.locale.language.equals(shortLang, ignoreCase = true) &&
                            voice.locale.country.equals("IN", ignoreCase = true)

                    val genderMatch = when (avatar.lowercase()) {
                        "boy" -> {
                            val features = voice.features?.map { it.lowercase() } ?: emptyList()
                            features.any { it.contains("male") } && !features.any { it.contains("female") }
                        }
                        "girl" -> {
                            val features = voice.features?.map { it.lowercase() } ?: emptyList()
                            features.any { it.contains("female") }
                        }
                        else -> true
                    }
                    localeMatch && genderMatch
                }
            }

            // Fallback 2: Any voice in that language with Indian accent
            if (chosen == null) {
                chosen = indianVoices.firstOrNull { voice ->
                    voice.locale.language.equals(shortLang, ignoreCase = true) &&
                            voice.locale.country.equals("IN", ignoreCase = true)
                }
            }

            // Last fallback: First available Indian voice
            if (chosen == null) {
                chosen = indianVoices.firstOrNull()
            }

            // Apply the selected voice
            chosen?.let { voice ->
                setVoice(voice) // already contains the flush now

                // Extra safety — if something is currently speaking, restart it with the new voice
                if (_state.value.isSpeaking && currentSpeakingText.isNotBlank()) {
                    // small delay so the flush finishes
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        speak(currentSpeakingText)
                    }, 300)
                }
            }

            // Set language
            val langCode = when (shortLang) {
                "en" -> "en-IN"
                "kn" -> "kn-IN"
                "hi" -> "hi-IN"
                "ta" -> "ta-IN"
                "te" -> "te-IN"
                else -> "en-IN"
            }
            setLanguageInternal(langCode)

            setSpeechRate(0.75f)
            setPitch(1.0f)
            _state.value = _state.value.copy(
                speechRate = 0.75f,
                pitch = 1.0f,
                statusMessage = "Default voice applied: ${chosen?.let { formatVoiceName(it) } ?: "none"}"
            )

            updateStatus("Default voice applied: ${chosen?.let { formatVoiceName(it) } ?: "none"}")
            DebugLogger.debugLog("TTS", "Applying defaults with ${_state.value.availableVoices.size} filtered voices")
            DebugLogger.debugLog(
                "TTS",
                "Applied defaults: lang=$shortLang, avatar=$avatar, voice=${chosen?.name ?: "none"},speed=0.75x, pitch=1.0x"
            )
        }
    /**
     * Cleanup resources
     */
    fun cleanup() {
        textToSpeech?.let { tts ->
            tts.stop()
            tts.shutdown()
        }
        textToSpeech = null
        webView = null
        _state.value = _state.value.copy(
            isInitialized = false,
            isSpeaking = false,
            statusMessage = "Text-to-Speech resources released"
        )
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
