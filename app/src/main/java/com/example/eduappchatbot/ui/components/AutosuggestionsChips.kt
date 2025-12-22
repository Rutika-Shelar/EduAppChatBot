package com.example.eduappchatbot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LightbulbCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.eduappchatbot.ui.theme.BrandPrimary
import com.example.eduappchatbot.ui.theme.BackgroundSecondary
import com.example.eduappchatbot.ui.theme.TextPrimary
import com.example.eduappchatbot.ui.theme.TextSecondary

/**
 * Displays autosuggestions as clickable chips/nuggets
 * Each suggestion can be tapped to send as a message
 */
@Composable
fun AutosuggestionChips(
    suggestions: List<String>,
    onSuggestionTapped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = suggestions.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header with icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LightbulbCircle,
                    contentDescription = "Suggestions",
                    tint = BrandPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Suggestions",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // Render suggestions as individual items
            // ✅ Using Column instead of LazyColumn to avoid nested scroll issues
            suggestions.forEach { suggestion ->
                AutosuggestionChip(
                    text = suggestion,
                    onTap = { onSuggestionTapped(suggestion) }
                )
            }
        }
    }
}

/**
 * Individual autosuggestion chip
 */
@Composable
fun AutosuggestionChip(
    text: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BackgroundSecondary)
            .clickable(onClick = onTap)
            .padding(12.dp, 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2
        )
    }
}