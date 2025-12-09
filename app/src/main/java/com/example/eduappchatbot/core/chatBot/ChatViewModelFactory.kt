package com.example.eduappchatbot.core.chatBot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ChatViewModelFactory(
    private val apiBaseUrl: String,
    private val geminiApiKey: String = "",
    private val geminiUserClass: String = "6",
    private val geminiNodeNumber: String = "8",
    private val geminiMaxWord: String = "250"
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                apiBaseUrl = apiBaseUrl,
                geminiApiKey = geminiApiKey,
                geminiUserClass = geminiUserClass,
                geminiNodeNumber = geminiNodeNumber,
                geminiMaxWord = geminiMaxWord
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
