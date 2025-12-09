package com.example.eduappchatbot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.eduappchatbot.data.dataClass.ChatMessageModel
import com.example.eduappchatbot.ui.theme.BrandPrimary
import com.example.eduappchatbot.ui.theme.TextPrimary
import com.example.eduappchatbot.ui.theme.TextSecondary
import com.example.eduappchatbot.ui.theme.White

private fun annotateBoldFromMarkers(input: String): AnnotatedString {
    if (!input.contains('*')) return AnnotatedString(input)

    val pattern = Regex("\\*\\*(.+?)\\*\\*|\\*(?!\\*)(.+?)\\*(?!\\*)")
    val cleaned = StringBuilder()
    val boldRanges = mutableListOf<IntRange>()
    var lastIndex = 0

    for (m in pattern.findAll(input)) {
        cleaned.append(input.substring(lastIndex, m.range.first))
        val inner = if (m.groupValues[1].isNotEmpty()) m.groupValues[1] else m.groupValues[2]
        val start = cleaned.length
        cleaned.append(inner)
        val end = cleaned.length
        boldRanges.add(start until end)
        lastIndex = m.range.last + 1
    }
    cleaned.append(input.substring(lastIndex))
    val cleanedStr = cleaned.toString()

    return buildAnnotatedString {
        append(cleanedStr)
        val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
        for (r in boldRanges) {
            val s = r.first.coerceIn(0, length)
            val e = (r.last + 1).coerceIn(0, length) // include the last char
            if (s < e) addStyle(boldStyle, s, e)
        }
    }
}

@Composable
fun ChatMessageBubbleModel(
    message: ChatMessageModel,
    modifier: Modifier = Modifier
) {
    val isFromAI = message.sender == "ai"

    Row(
        modifier = modifier,
        horizontalArrangement = if (isFromAI) Arrangement.Start else Arrangement.End
    ) {
        if (isFromAI) {
            // AI message - aligned left
            Row(
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SmartToy,
                    contentDescription = "AI",
                    tint = BrandPrimary,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp)
                )

                val annotated = remember(message.content) {
                    annotateBoldFromMarkers(message.content)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp, 12.dp, 12.dp, 4.dp))
                        .background(White)
                        .padding(12.dp)
                ) {
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
        } else {
            // User message - aligned right
            Row(
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp, 12.dp, 4.dp, 12.dp))
                        .background(BrandPrimary)
                        .padding(12.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = White
                    )
                }

                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User",
                    tint = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                )
            }
        }
    }
}
