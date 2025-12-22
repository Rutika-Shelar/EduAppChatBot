package com.example.eduappchatbot.ui.screens.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.eduappchatbot.R
import com.example.eduappchatbot.core.chatBot.ChatViewModel
import com.example.eduappchatbot.data.repository.UserSessionRepository
import com.example.eduappchatbot.ui.components.*
import com.example.eduappchatbot.ui.theme.*
import com.example.eduappchatbot.utils.DebugLogger
import com.example.eduappchatbot.utils.LanguageChangeHelper
import com.example.eduappchatbot.viewModels.speechModels.SpeechToText
import com.example.eduappchatbot.viewModels.speechModels.TextToSpeech
import kotlinx.coroutines.delay
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatBotScreen(
    navController: NavController,
    chatViewModel: ChatViewModel,//chat ViewModel
    ttsController: TextToSpeech = viewModel(),// TextToSpeech core Util
    sttController: SpeechToText = viewModel(),// SpeechToText core Util
) {
    val context = LocalContext.current

    val ttsState by ttsController.state.collectAsState()// TTS states
    val sttState by sttController.state.collectAsState()// STT states

    // track current audio playback time
    var currentAudioTime by remember { mutableFloatStateOf(0f) }

    // Collects chat state from ChatViewModel
    val chatMessages by chatViewModel.messages.collectAsState()
    val isChatLoading by chatViewModel.isLoading.collectAsState()

    // Add after existing state collectors
    val typingText by chatViewModel.typingText.collectAsState()
    val isTyping by chatViewModel.isTyping.collectAsState()
    val shouldStartTTS by chatViewModel.shouldStartTTS.collectAsState()

    // Network connectivity state
    val isNetworkConnected by chatViewModel.isConnected.collectAsState()

    // Concept map JSON state
    val conceptMapJSON by chatViewModel.conceptMapJSON.collectAsState()


    // User session repository
    val userRepository = remember { UserSessionRepository(context.applicationContext) }
    val currentLanguage by chatViewModel.currentLanguage.collectAsState()

    // Agent metadata and state
    val agentMetadata by chatViewModel.agentMetadata.collectAsState()
    val imageUrl = agentMetadata?.imageUrl?.takeIf { it.isNotBlank() && it != "null" }
    val videoUrl = agentMetadata?.videoUrl?.takeIf { it.isNotBlank() && it != "null" }
    val agentState by chatViewModel.agentState.collectAsState()

    // Settings menu state
    var showSettingsMenu by remember { mutableStateOf(false) }
    val englishDisplayName = stringResource(R.string.option_english)
    val kannadaDisplayName = stringResource(R.string.option_kannada)
    val boyDisplayName = stringResource(R.string.boy)
    val girlDisplayName = stringResource(R.string.girl)
    val disableAvatar =stringResource(R.string.disable)
    val levelLowText = stringResource(R.string.level_low)
    val levelMediumText = stringResource(R.string.level_medium)
    val levelAdvancedText = stringResource(R.string.level_advanced)

    // Language selection state
    var selectedLanguage by remember { mutableStateOf(currentLanguage) }
    var selectedAvatar by remember { mutableStateOf("disable") }
    var selectedSpeed by remember { mutableStateOf("0.75x") }
    var selectedModel by remember { mutableStateOf("") }
    // scroll state for chat history messages and main screen
    val chatHistoryListState = rememberLazyListState()
    val mainScreenListState = rememberLazyListState()

    var messageInput by remember { mutableStateOf("") }

    val translatedOutput by chatViewModel.translatedOutput.collectAsState()

    // Starting message
    val startMessage = stringResource(R.string.start_msg)

    val aiMessageOutput = remember(isTyping, typingText, translatedOutput) {
        when {
            isTyping -> typingText
            translatedOutput.isNotBlank() -> translatedOutput
            else -> startMessage
        }
    }
    // Dialog states
    var showSessionResumeDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Concept selection pending state
    var pendingConceptSelection by remember { mutableStateOf<String?>(null) }

    // Autosuggestions state
    val autosuggestions by chatViewModel.autosuggestions.collectAsState()

    // Keyboard controller
    val keyboardController = LocalSoftwareKeyboardController.current
    // Audio playback tracking
    var hasPlayedOnce by remember { mutableStateOf(false) }

    // Collect available concepts and selected concept from ViewModel
    val availableConcepts by chatViewModel.availableConcepts.collectAsState()
    val selectedConcept by chatViewModel.selectedConcept.collectAsState()
    //selected student level state
    var selectedStudentLevel by remember { mutableStateOf("medium") }
    val studentLevel by chatViewModel.studentLevel.collectAsState()

    // Collect available models
    val availableModels by chatViewModel.availableModels.collectAsState()

    // Determine if any visual content is available to display
    val hasAnyVisualContent = remember(conceptMapJSON, imageUrl, videoUrl) {
        hasValidTabs(
            json = conceptMapJSON,
            imageUrl = imageUrl,
            videoUrl = videoUrl
        )
    }

    val voiceOptions = remember(ttsState.availableVoices, currentLanguage, selectedAvatar) {
        ttsController.getFilteredVoiceOptions(currentLanguage, selectedAvatar)
    }

    val displayedVoiceName = remember(ttsState.selectedVoice, currentLanguage, selectedAvatar) {
        if (ttsState.selectedVoice != null) {
            ttsController.formatVoiceName(ttsState.selectedVoice!!)
        } else {
            ttsController.getDefaultVoiceName(currentLanguage, selectedAvatar)
        }
    }
    LaunchedEffect(aiMessageOutput) {
        hasPlayedOnce = false
    }
    LaunchedEffect(availableModels) {
        if (availableModels.isNotEmpty() && selectedModel.isEmpty()) {
            selectedModel = availableModels.first()
        }
    }
    LaunchedEffect(studentLevel) {
        selectedStudentLevel = studentLevel
    }
    LaunchedEffect(ttsState.isSpeaking) {
        if (ttsState.isSpeaking) {
            hasPlayedOnce = true
            val startTime = System.currentTimeMillis()
            while (ttsState.isSpeaking) {
                currentAudioTime = (System.currentTimeMillis() - startTime) / 1000f
                delay(50)
            }
        } else {
            currentAudioTime = 0f
        }
    }

    LaunchedEffect(shouldStartTTS) {
        if (shouldStartTTS && ttsState.isInitialized) {
            val textToSpeak = when {
                translatedOutput.isNotBlank() -> translatedOutput
                else -> startMessage
            }

            if (textToSpeak.isNotBlank()) {
                if (sttState.isListening) {
                    sttController.stopListening()
                }
                ttsController.speak(textToSpeak)
            }
        }
    }
    // Initialize TTS settings when ready
    LaunchedEffect(ttsState.isInitialized, ttsState.voicesFullyLoaded) {
        if (ttsState.isInitialized && ttsState.voicesFullyLoaded && ttsState.availableVoices.isNotEmpty()) {
            val savedAvatar = ttsState.selectedCharacter.takeIf {
                it.isNotBlank() && it != "boy" && it != "girl"
            } ?: "disable"

            selectedAvatar = savedAvatar
            ttsController.switchCharacter(savedAvatar)

            if (savedAvatar != "disable") {
                ttsController.applyDefaultsForAvatarLanguage(savedAvatar, currentLanguage)
            }
            selectedSpeed = when (ttsState.speechRate) {
                in 0.0f..0.8f -> "0.75x"
                in 0.8f..1.1f -> "1.0x"
                in 1.1f..1.3f -> "1.25x"
                else -> "1.5x"
            }
        }
    }

    LaunchedEffect(sttState.resultText) {
        if (sttState.resultText.isNotBlank()) {
            messageInput = sttState.resultText
        }
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            delay(100)
            chatHistoryListState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        sttController.handlePermissionResult(
            SpeechToText.RECORD_AUDIO_PERMISSION_REQUEST,
            if (isGranted) intArrayOf(PackageManager.PERMISSION_GRANTED)
            else intArrayOf(PackageManager.PERMISSION_DENIED)
        )
    }
// Initialize components on first composition
    LaunchedEffect(Unit) {
        DebugLogger.debugLog("ChatBotScreen", "Starting initialization")

        chatViewModel.initialize(context)
        sttController.initialize(context)
        ttsController.initialize(context)

        val fullTag = when (currentLanguage) {
            "en" -> "en-IN"
            "kn" -> "kn-IN"
            "hi" -> "hi-IN"
            "ta" -> "ta-IN"
            "te" -> "te-IN"
            else -> "en-IN"
        }

        LanguageChangeHelper.changeLanguage(context, fullTag)
        sttController.setLanguage(fullTag)

        if (!sttState.hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        DebugLogger.debugLog("ChatBotScreen", "Initialization complete")
    }

    LaunchedEffect(currentLanguage) {
        selectedLanguage = currentLanguage

        val fullTag = when (currentLanguage) {
            "en" -> "en-IN"
            "kn" -> "kn-IN"
            "hi" -> "hi-IN"
            "ta" -> "ta-IN"
            "te" -> "te-IN"
            else -> "en-IN"
        }
        sttController.setLanguage(fullTag)
        LanguageChangeHelper.changeLanguage(context, fullTag)
    }

    DisposableEffect(Unit) {
        onDispose {
            sttController.destroy()
            ttsController.cleanup()
            chatViewModel.stopNetworkObservation()
        }
    }
    Scaffold(
        topBar = { TopAppBar(
            title = {
                Text(stringResource(R.string.ai_tutor_name))
            },
            navigationIcon = {
                Box{
                    IconButton(onClick = {showSettingsMenu=true}) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = IconPrimary
                        )
                    }

                    //Dropdown Menu
                    DropdownMenu(
                        expanded = showSettingsMenu,
                        onDismissRequest = { showSettingsMenu = false },
                        modifier = Modifier
                            .background(White)
                            .border(1.dp, BrandPrimary, RoundedCornerShape(0.dp))
                    )
                    {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .widthIn(min = 220.dp, max = 320.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.settings),
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.titleSmall,
                                )

                                IconButton(
                                    onClick = { showSettingsMenu = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Settings",
                                        tint = IconPrimary
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            DropDownMenuModel(
                                label = stringResource(R.string.select_language),
                                options = listOf(
                                    englishDisplayName,
                                    kannadaDisplayName
                                ),
                                selectedValue = when (selectedLanguage) {
                                    "en" -> englishDisplayName
                                    "kn" -> kannadaDisplayName
                                    else -> englishDisplayName
                                },
                                onValueSelected = { displayName ->
                                    val shortCode = when (displayName) {
                                        englishDisplayName -> "en"
                                        kannadaDisplayName -> "kn"
                                        else -> "en"
                                    }

                                    val currentConcept = selectedConcept
                                    if (currentConcept != null) {
                                        val originalConcept =
                                            chatViewModel.getOriginalConceptName(
                                                context,
                                                currentConcept,
                                                selectedLanguage
                                            )
                                        userRepository.savePreferredConcept(
                                            originalConcept
                                        )
                                    }

                                    chatViewModel.setCurrentLanguage(shortCode, context)
                                    chatViewModel.refreshAvailableConcepts(
                                        context,
                                        shortCode
                                    )

                                    val fullTag = when (shortCode) {
                                        "en" -> "en-IN"
                                        "kn" -> "kn-IN"
                                        else -> shortCode
                                    }
                                    LanguageChangeHelper.changeLanguage(
                                        context,
                                        fullTag
                                    )
                                    ttsController.applyDefaultsForAvatarLanguage(
                                        selectedAvatar,
                                        shortCode
                                    )
                                    ttsController.setLanguage(fullTag)
                                    sttController.setLanguage(fullTag)
                                    userRepository.updateLanguage(shortCode)
                                }
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.select_avatar),
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(8.dp))
                            DropDownMenuModel(
                                label = stringResource(R.string.avatar),
                                options = listOf(disableAvatar,boyDisplayName, girlDisplayName),
                                selectedValue = when (selectedAvatar.lowercase()) {
                                    "disable"-> disableAvatar
                                    "girl" -> girlDisplayName
                                    "boy" -> boyDisplayName
                                    else -> disableAvatar
                                },
                                onValueSelected = { displayName ->
                                    val avatarCode = when (displayName) {
                                        disableAvatar -> "disable"
                                        girlDisplayName -> "girl"
                                        boyDisplayName -> "boy"
                                        else -> disableAvatar
                                    }
                                    selectedAvatar = avatarCode
                                    ttsController.switchCharacter(avatarCode)

                                    if (avatarCode != "disable") {
                                        ttsController.applyDefaultsForAvatarLanguage(avatarCode, currentLanguage)
                                    } else {
                                        if (ttsState.isSpeaking) ttsController.stop()
                                    }
                                }
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.select_voice),
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(8.dp))
                            DropDownMenuModel(
                                label = stringResource(R.string.voice),
                                options = voiceOptions,
                                selectedValue = displayedVoiceName,
                                onValueSelected = { selectedDisplayName ->
                                    val selectedVoice =
                                        ttsState.availableVoices.find {
                                            ttsController.formatVoiceName(it) == selectedDisplayName
                                        }
                                    selectedVoice?.let {
                                        ttsController.setVoice(it)
                                        if (ttsState.isSpeaking) {
                                            ttsController.stop()
                                            ttsController.speak(aiMessageOutput)
                                        }
                                    }
                                }
                            )

                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.select_student_level),
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(8.dp))

                            DropDownMenuModel(
                                label = stringResource(R.string.student_level),
                                options = listOf(
                                    stringResource(R.string.level_low),
                                    stringResource(R.string.level_medium),
                                    stringResource(R.string.level_advanced)
                                ),
                                selectedValue = when (selectedStudentLevel) {
                                    "low" -> stringResource(R.string.level_low)
                                    "medium" -> stringResource(R.string.level_medium)
                                    "advanced" -> stringResource(R.string.level_advanced)
                                    else -> stringResource(R.string.level_medium)
                                },
                                onValueSelected = { displayName ->
                                    val levelCode = when (displayName) {
                                        levelLowText -> "low"
                                        levelMediumText -> "medium"
                                        levelAdvancedText -> "advanced"
                                        else -> "medium"
                                    }
                                    selectedStudentLevel = levelCode
                                    chatViewModel.setStudentLevel(levelCode)
                                    DebugLogger.debugLog("ChatBotScreen", "Student level changed to: $levelCode")
                                }
                            )

                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.select_speed),
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(8.dp))
                            DropDownMenuModel(
                                label = stringResource(R.string.speed),
                                options = listOf("0.75x", "1.0x", "1.25x", "1.5x"),
                                selectedValue = selectedSpeed,
                                onValueSelected = { label ->
                                    selectedSpeed = label
                                    val speed = when (label) {
                                        "0.75x" -> 0.75f
                                        "1.0x" -> 1.0f
                                        "1.25x" -> 1.25f
                                        "1.5x" -> 1.5f
                                        else -> 0.75f
                                    }
                                    ttsController.setSpeechRate(speed)
                                    if (ttsState.isSpeaking) {
                                        val currentText = aiMessageOutput
                                        ttsController.stop()
                                        ttsController.speak(currentText)
                                    }
                                }
                            )

                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Select Model",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(8.dp))

                            if (availableModels.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = BrandPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Text(
                                    text = "Loading models...",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            } else {
                                DropDownMenuModel(
                                    label = "AI Model",
                                    options = availableModels,
                                    selectedValue = selectedModel,
                                    onValueSelected = { displayName ->
                                        selectedModel = displayName
                                        chatViewModel.setSelectedModel(displayName)
                                        DebugLogger.debugLog("ChatBotScreen", "Model changed to: $displayName")
                                    }
                                )
                            }
                        }
                    }
                }
            },
            actions = {
                IconButton(onClick = {showLogoutDialog = true}) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout",
                        tint = IconPrimary
                    )
                }
            },
        )}
    ) { innerPadding ->
        LazyColumn(
            state = mainScreenListState,
            modifier = Modifier
                .fillMaxSize()
                .background(White)
                .padding(innerPadding)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Chat Card
            item {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(vertical = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.background(BackgroundPrimary)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            if(selectedAvatar !="disable") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(170.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Card(
                                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                                        modifier = Modifier
                                            .width(120.dp)
                                            .height(160.dp)
                                    ) {
                                        AndroidView(factory = {
                                            WebView(context).apply {
                                                ttsController.setupWebView(this)
                                            }
                                        })
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Outlined.SmartToy,
                                        contentDescription = "AI Icon",
                                        tint = AccentBlue
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.sarah_is_saying),
                                        color = AccentBlue
                                    )
                                    Spacer(Modifier.weight(1f))

                                    IconButton(
                                        onClick = {
                                            if (ttsState.isInitialized) {
                                                if (!ttsState.isSpeaking) {
                                                    if (sttState.isListening) {
                                                        sttController.stopListening()
                                                    }
                                                    ttsController.speak(aiMessageOutput)
                                                } else {
                                                    ttsController.stop()
                                                }
                                            } else {
                                                ttsController.initialize(context)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = BrandPrimary,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                ttsState.isSpeaking -> Icons.Default.Stop
                                                hasPlayedOnce -> Icons.Default.Replay
                                                else -> Icons.Default.PlayArrow
                                            },
                                            contentDescription = when {
                                                ttsState.isSpeaking -> "Stop Audio"
                                                hasPlayedOnce -> "Replay Audio"
                                                else -> "Play Audio"
                                            },
                                            modifier = Modifier.size(25.dp)
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                ) {
                                    Column(modifier = Modifier) {
                                        if (availableConcepts.isEmpty()) {
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(color = BrandPrimary)
                                            }
                                            Text(
                                                text = stringResource(R.string.loading_topics),
                                                color = TextSecondary,
                                                modifier = Modifier.align(Alignment.CenterHorizontally)
                                                    .padding(top = 8.dp)
                                            )
                                        } else {
                                            DropDownMenuModel(
                                                label = stringResource(R.string.select_concepts),
                                                options = availableConcepts,
                                                selectedValue = selectedConcept
                                                    ?: stringResource(R.string.tap_to_choose_topic),
                                                onValueSelected = { displayedConcept ->
                                                    if (chatViewModel.hasExistingSession(
                                                            displayedConcept,
                                                            context
                                                        )
                                                    ) {
                                                        pendingConceptSelection = displayedConcept
                                                        showSessionResumeDialog = true
                                                    } else {
                                                        chatViewModel.selectConcept(
                                                            displayedConcept,
                                                            context
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                    ScrollingSubtitle(
                                        text = aiMessageOutput,
                                        ttsController = ttsController,
                                        maxVisibleLines = 5,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (isTyping) {
                                        Text(
                                            text = "▋",
                                            color = AccentBlue,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                        )
                                    }

                                    if (agentState.isNotBlank()) {
                                        Text(
                                            text = stringResource(R.string.agent_state) + ": $agentState",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                                        )
                                    }
                                    // autosuggestions
                                    AutosuggestionChips(
                                        suggestions = autosuggestions,
                                        onSuggestionTapped = { suggestion ->
                                            chatViewModel.tapAutosuggestion(suggestion, context)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextField(
                                value = messageInput,
                                onValueChange = { messageInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(stringResource(R.string.ask_Sarah_a_question)) },
                                shape = RoundedCornerShape(20.dp),
                                singleLine = false,
                                maxLines = 4,
                                colors = TextFieldDefaults.colors(
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedContainerColor = BackgroundSecondary,
                                    focusedContainerColor = BackgroundSecondary,
                                    cursorColor = ColorHint,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    unfocusedPlaceholderColor = TextSecondary,
                                    focusedPlaceholderColor = TextSecondary
                                )
                            )

                            IconButton(
                                onClick = {
                                    if (messageInput.isNotBlank() && !isChatLoading && isNetworkConnected) {
                                        keyboardController?.hide()
                                        chatViewModel.sendMessage(messageInput, context)
                                        messageInput = ""
                                    }
                                },
                                enabled = messageInput.isNotBlank() && !isChatLoading && isNetworkConnected,
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (isNetworkConnected) SendButtonColor else ColorHint,
                                    contentColor = Color.White,
                                    disabledContainerColor = ColorHint,
                                    disabledContentColor = Color.White
                                )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Message"
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (sttState.isListening) {
                                        sttController.stopListening()
                                    } else {
                                        if (sttState.hasPermission && sttState.isInitialized) {
                                            sttController.startListening()
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                enabled = !ttsState.isSpeaking && sttState.hasPermission && !isChatLoading,
                                modifier = Modifier
                                    .size(48.dp)
                                    .border(1.dp, SendButtonColor, CircleShape)
                                    .alpha(if (ttsState.isSpeaking) 0.5f else 1f)
                            ) {
                                Icon(
                                    imageVector = if (sttState.isListening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                                    contentDescription = "Record Audio",
                                    tint = if (ttsState.isSpeaking || !sttState.hasPermission || isChatLoading)
                                        Color.Gray else SendButtonColor
                                )
                            }
                        }

                        Text(
                            text = when {
                                !isNetworkConnected -> stringResource(R.string.no_internet_connection)
                                isChatLoading -> stringResource(R.string.sending)
                                else -> stringResource(R.string.tap_to_send)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isNetworkConnected) ColorHint else Color.Red,
                            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
                        )
                    }
                }
            }

            // Visual Content Card
            if (hasAnyVisualContent) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(500.dp)
                            .background(BackgroundSecondary),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        SwitchableConceptView(
                            json = conceptMapJSON,
                            currentAudioTime = currentAudioTime,
                            isAudioPlaying = ttsState.isSpeaking,
                            imageUrl = imageUrl,
                            imageDescription = agentMetadata?.imageDescription,
                            videoUrl = videoUrl
                        )
                    }
                }
            }
            // Chat History Card
            item {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(White)
                    ) {
                        Row(
                            modifier = Modifier
                                .background(BackgroundSecondary)
                                .fillMaxWidth()
                                .padding(5.dp, 10.dp)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "Previous Conversation Icon",
                                tint = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.previous_conversation),
                                color = TextPrimary
                            )
                        }

                        LazyColumn(
                            state = chatHistoryListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (chatMessages.isEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.no_conversation_yet),
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else {
                                items(chatMessages, key = { it.hashCode() }) { message ->
                                    ChatMessageBubbleModel(
                                        message = message,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Session Resume Dialog
    AppDialog(
        show = showSessionResumeDialog && pendingConceptSelection != null,

        title = stringResource(R.string.existing_session_found),

        message = stringResource(R.string.resume_or_start_fresh),

        confirmText = stringResource(R.string.continue_session),
        dismissText = stringResource(R.string.start_new),

        onConfirm = {
            pendingConceptSelection?.let { concept ->
                chatViewModel.selectConcept(concept, context)
            }
            showSessionResumeDialog = false
            pendingConceptSelection = null
        },

        onDismiss = {
            pendingConceptSelection?.let { concept ->
                chatViewModel.startFreshSession(concept, context)
            }
            showSessionResumeDialog = false
            pendingConceptSelection = null
        }
    )
    // Logout Confirmation Dialog
    AppDialog(
        show = showLogoutDialog,
        title = stringResource(R.string.logout),
        message = stringResource(R.string.logout_confirmation),
        confirmText = stringResource(R.string.logout),
        onConfirm = {
            //stop ongoing processes
            sttController.stopListening()
            ttsController.stop()
            showLogoutDialog = false
            chatViewModel.stopNetworkObservation()
            //Clear all session data
            chatViewModel.clearAllSessions(context)
            userRepository.clearUserInfo()
            showLogoutDialog = false
            navController.navigate("userInfo") {
                popUpTo("chatBot") { inclusive = true }
            }
            DebugLogger.debugLog("ChatBotScreen", "Logout complete - all data cleared")
        },
        onDismiss = {
            showLogoutDialog = false
        }
    )


}