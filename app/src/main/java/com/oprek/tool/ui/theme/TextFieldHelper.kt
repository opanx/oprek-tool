package com.oprek.tool.ui.theme

import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable

@Composable
fun darkTextFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = DarkSurface,
    unfocusedContainerColor = DarkSurface,
    focusedIndicatorColor = AccentGreen,
    unfocusedIndicatorColor = TextMuted,
    cursorColor = AccentGreen,
)
