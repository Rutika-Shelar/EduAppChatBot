package com.example.eduappchatbot.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.eduappchatbot.ui.theme.BrandPrimary
import com.example.eduappchatbot.ui.theme.TextPrimary
import com.example.eduappchatbot.ui.theme.TextSecondary
import com.example.eduappchatbot.ui.theme.White

@Composable
fun AppDialog(
    show: Boolean,
    title: String? = null,
    message: String? = null,
    confirmText: String = "OK",
    dismissText: String? = null,
    confirmColor: Color = BrandPrimary,
    dismissColor: Color = BrandPrimary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: (@Composable () -> Unit)? = null
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = White,

        title = title?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
        },

        text = {
            Column {
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                content?.invoke()
            }
        },

        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = confirmColor
                )
            ) {
                Text(confirmText, color = Color.White)
            }
        },

        dismissButton = dismissText?.let {
            {
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = dismissColor
                    )
                ) {
                    Text(it)
                }
            }
        }
    )
}
