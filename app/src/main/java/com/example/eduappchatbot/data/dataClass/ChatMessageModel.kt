package com.example.eduappchatbot.data.dataClass

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessageModel(
    val sender: String, // "user" or "ai"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val canRetry: Boolean = false
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}