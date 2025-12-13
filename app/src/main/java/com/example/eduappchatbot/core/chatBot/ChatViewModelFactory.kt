package com.example.eduappchatbot.core.chatBot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ChatViewModelFactory(
    private val agenticAIBaseUrl: String,
    private val geminiApiKey: String = "",
    private val llmApiKey: String = "",
    private val llmUserClass: String = "6",
    private val llmNodeNumber: String = "8",
    private val llmMaxWord: String = "250"
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                agenticAIBaseUrl = agenticAIBaseUrl,
                geminiApiKey = geminiApiKey,
                llmApiKey = llmApiKey,
                llmUserClass = llmUserClass,
                llmNodeNumber = llmNodeNumber,
                llmMaxWord = llmMaxWord
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
