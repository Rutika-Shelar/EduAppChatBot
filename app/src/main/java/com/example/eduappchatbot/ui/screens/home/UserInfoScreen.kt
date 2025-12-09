package com.example.eduappchatbot.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.eduappchatbot.core.chatBot.ChatViewModel
import com.example.eduappchatbot.ui.components.DropDownMenuModel
import com.example.eduappchatbot.ui.theme.*
import com.example.eduappchatbot.data.repository.UserSessionRepository
import com.example.eduappchatbot.utils.DebugLogger

@Composable
fun UserInfoScreen(
    onNavigateToChatBot: () -> Unit,
    chatViewModel: ChatViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var userName by remember { mutableStateOf("") }
    var userPhone by remember { mutableStateOf("") }

    // initialize selected language from stored short code
    val userRepo = remember { UserSessionRepository(context.applicationContext) }
    fun shortToDisplay(short: String) = when {
        short.startsWith("kn", ignoreCase = true) -> "Kannada"
        else -> "English"
    }
    fun displayToShort(display: String) = when (display) {
        "Kannada" -> "kn"
        else -> "en"
    }

    var selectedLanguage by remember { mutableStateOf(shortToDisplay(userRepo.getUserLanguage())) }
    // Observe concepts provided by the viewModel
    val availableConcepts by chatViewModel.availableConcepts.collectAsState()

    // local UI state
    var selectedConcept by remember { mutableStateOf("") }
    var userNameError by remember { mutableStateOf<String?>(null) }
    var userPhoneError by remember { mutableStateOf<String?>(null) }
    var languageError by remember { mutableStateOf<String?>(null) }
    var conceptError by remember { mutableStateOf<String?>(null) }

    fun validateAndSave(): Boolean {
        var valid = true
        userNameError = null
        userPhoneError = null
        languageError = null
        conceptError = null

        if (userName.isBlank()) {
            userNameError = "Please enter your name"
            valid = false
        }

        val digitsOnly = userPhone.filter { it.isDigit() }
        if (digitsOnly.length != 10) {
            userPhoneError = "Enter a valid 10-digit phone number"
            valid = false
        }

        if (selectedLanguage.isBlank()) {
            languageError = "Please select a language"
            valid = false
        }

        if (selectedConcept.isBlank() || selectedConcept == "Tap to choose topic") {
            conceptError = "Please select a topic to start learning"
            valid = false
        }

        return valid
    }

    LaunchedEffect(selectedLanguage) {
        try {
            val langShort = displayToShort(selectedLanguage)
            chatViewModel.refreshAvailableConcepts(context, langShort)
        } catch (e: Exception) {
            DebugLogger.errorLog("UserInfoScreen", "Failed to refresh concepts: ${e.message}")
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(White)
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome to AI Tutor",
                style = MaterialTheme.typography.headlineLarge,
                color = BrandPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Let's get to know you",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundPrimary)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Your Learning",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = userName,
                        onValueChange = {
                            userName = it
                            userNameError = null // Clear error on input
                        },
                        label = { Text("Your Name") },
                        placeholder = { Text("Enter your name") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Name Icon",
                                tint = BrandPrimary
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandPrimary,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = BrandPrimary,
                            unfocusedLabelColor = TextSecondary,
                            cursorColor = BrandPrimary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        isError = userNameError != null
                    )

                    if (userNameError != null) {
                        Text(
                            text = userNameError!!,
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    OutlinedTextField(
                        value = userPhone,
                        onValueChange = {
                            userPhone = it
                            userPhoneError = null // Clear error on input
                        },
                        label = { Text("Phone Number") },
                        placeholder = { Text("Enter 10-digit number") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = "Phone Icon",
                                tint = BrandPrimary
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandPrimary,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = BrandPrimary,
                            unfocusedLabelColor = TextSecondary,
                            cursorColor = BrandPrimary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        isError = userPhoneError != null
                    )

                    if (userPhoneError != null) {
                        Text(
                            text = userPhoneError!!,
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        DropDownMenuModel(
                            label = "Preferred Language",
                            options = listOf("English", "Kannada"),
                            selectedValue = selectedLanguage,
                            onValueSelected = {
                                selectedLanguage = it
                                languageError = null // Clear error on selection
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (languageError != null) {
                            Text(
                                text = languageError!!,
                                color = Color.Red,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                            )
                        }
                    }

                    // Concept selector (from API via ViewModel)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (availableConcepts.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = BrandPrimary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Loading topics...", color = TextSecondary)
                            }
                        } else {
                            DropDownMenuModel(
                                label = "Preferred Topic",
                                options = availableConcepts,
                                selectedValue = selectedConcept.ifBlank { "Tap to choose topic" },
                                onValueSelected = {
                                    selectedConcept = it
                                    conceptError = null // Clear error on selection
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (conceptError != null) {
                            Text(
                                text = conceptError!!,
                                color = Color.Red,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (validateAndSave()) {
                                val langShort = displayToShort(selectedLanguage)

                                // CRITICAL: Convert displayed (possibly translated) concept back to original English
                                val displayedConcept = selectedConcept.takeIf {
                                    it.isNotBlank() && it != "Tap to choose topic"
                                }

                                val originalEnglishConcept = if (displayedConcept != null) {
                                    // Get the original English concept name for storage
                                    chatViewModel.getOriginalConceptName(context, displayedConcept, langShort)
                                } else {
                                    null
                                }

                                try {
                                    // ALWAYS save the original English concept name
                                    userRepo.saveUserInfo(userName, userPhone, langShort, originalEnglishConcept)
                                    DebugLogger.debugLog(
                                        "UserInfoScreen",
                                        "Saved user info - Language: $langShort, Original concept: ${originalEnglishConcept ?: "none"}, Displayed: ${displayedConcept ?: "none"}"
                                    )
                                } catch (e: Exception) {
                                    DebugLogger.errorLog("UserInfoScreen", "Failed to persist user info: ${e.message}")
                                }
                                onNavigateToChatBot()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandPrimary,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Text(
                            text = "Continue to Learning",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}