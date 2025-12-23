package com.example.eduappchatbot.ui.components


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.eduappchatbot.R
import com.example.eduappchatbot.ui.theme.BrandPrimary
import com.example.eduappchatbot.ui.theme.ColorHint

/**
 * Compact chat status indicator for inline display
 * Smaller version for use in message input area
 */
@Composable
fun CompactStatusIndicator(
    isLoading: Boolean,
    message: String,
    modifier: Modifier = Modifier
) {
    val thinkingComposition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.ai_loading_lottie)
    )
    val normalComposition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.chatbot_lottie)
    )

    val composition = if (isLoading) thinkingComposition else normalComposition

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Small Lottie animation
        LottieAnimation(
            composition = composition,
            iterations = Int.MAX_VALUE,
            modifier = Modifier.size(70.dp)
        )

        // Status text
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            color = if (isLoading) BrandPrimary else ColorHint
        )
    }
}
