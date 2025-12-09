package com.example.eduappchatbot.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.eduappchatbot.ui.theme.ColorError

@Composable
fun ExpandableTextOutput(
    text: String,
    textColor: Color = Color.Black
) {
    var expanded by remember { mutableStateOf(false) }
    var showReadMoreButton by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        val scrollState = rememberScrollState()
        val textModifier = if (expanded) {
            Modifier
                .heightIn(max = 160.dp) // Set a max height for the expanded state
                .verticalScroll(scrollState) // Make it scrollable
        } else {
            Modifier // In collapsed state, no scrolling or height constraint is needed
        }

        Text(
            text = text,
            color = textColor,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layoutResult ->
                if (!expanded) {
                    showReadMoreButton = layoutResult.hasVisualOverflow
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            // Apply the conditional modifier here
            modifier = Modifier
                .padding(4.dp)
                .animateContentSize() // <-- FIX: This modifier fixes the gap
                .then(textModifier)
        )

        // Show the button only if the text overflows
        if (showReadMoreButton) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = if (expanded) "Show less" else "Read more",
                    color = ColorError
                )
            }
        }
    }
}