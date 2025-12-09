package com.example.eduappchatbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.eduappchatbot.core.chatBot.ChatViewModel
import com.example.eduappchatbot.core.chatBot.ChatViewModelFactory
import com.example.eduappchatbot.data.repository.UserSessionRepository
import com.example.eduappchatbot.ui.screens.home.ChatBotScreen
import com.example.eduappchatbot.ui.screens.home.UserInfoScreen
import com.example.eduappchatbot.ui.theme.EduAppChatBotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EduAppChatBotTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val userRepo = remember { UserSessionRepository(context.applicationContext) }

    val startDestination = remember {
        if (userRepo.isUserInfoComplete()) "chatBot" else "userInfo"
    }

    val apiBaseUrl = remember { BuildConfig.API_BASE_URL  }
    val geminiApiKey = remember {  BuildConfig.GEMINI_API_KEY }

    val geminiUserClass = remember { "6" }
    val geminiNodeNumber = remember { "8" }
    val geminiMaxWord = remember { "250" }

    val chatViewModelFactory = remember {
        ChatViewModelFactory(
            apiBaseUrl = apiBaseUrl,
            geminiApiKey = geminiApiKey,
            geminiUserClass = geminiUserClass,
            geminiNodeNumber = geminiNodeNumber,
            geminiMaxWord = geminiMaxWord
        )
    }
    NavHost(navController = navController, startDestination = startDestination) {
        composable("userInfo") {
            val chatViewModel: ChatViewModel = viewModel(factory = chatViewModelFactory)

            UserInfoScreen(
                chatViewModel = chatViewModel,
                onNavigateToChatBot = {
                    navController.navigate("chatBot") {
                        popUpTo("userInfo") { inclusive = true }
                    }
                }
            )
        }
        composable("chatBot") {
            val chatViewModel: ChatViewModel = viewModel(factory = chatViewModelFactory)
            ChatBotScreen(  chatViewModel = chatViewModel
            )
        }
    }
}