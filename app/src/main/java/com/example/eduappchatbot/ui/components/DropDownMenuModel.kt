package com.example.eduappchatbot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.eduappchatbot.ui.theme.ColorHint
import com.example.eduappchatbot.ui.theme.LightGray
import com.example.eduappchatbot.ui.theme.TextPrimary
import com.example.eduappchatbot.ui.theme.TextSecondary
import com.example.eduappchatbot.ui.theme.textFieldBackgroundColor
import kotlin.collections.forEachIndexed
import kotlin.collections.lastIndex


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropDownMenuModel(
    label: String, // a string for label of the dropdown menu
    options: List<String>, // a list of strings as content of drop down menu
    selectedValue: String, // a string representing the currently selected to display it in Drop dropdown menu
    onValueSelected: (String) -> Unit, // a lambda function that takes a String parameter and returns nothing used for updating the selected value
    modifier: Modifier =Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(textFieldBackgroundColor)
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Column {
            Text(
                text = label,
                color = ColorHint
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedValue,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(textFieldBackgroundColor)
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option, color = TextPrimary) },
                    onClick = {
                        onValueSelected(option)
                        expanded = false
                    },
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )

                // adds a divider between each item except the last one
                if (index != options.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        thickness = 1.dp,
                        color = LightGray
                    )
                }
            }
        }
    }
}
