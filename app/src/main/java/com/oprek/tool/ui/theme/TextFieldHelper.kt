package com.oprek.tool.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults

@OptIn(ExperimentalMaterial3Api::class)
fun darkTextFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = AccentGreen,
    focusedBorderColor = AccentGreen,
    unfocusedBorderColor = TextMuted,
    focusedLabelColor = AccentGreen,
    unfocusedLabelColor = TextSecondary,
    focusedPlaceholderColor = TextSecondary,
    unfocusedPlaceholderColor = TextMuted,
    focusedLeadingIconColor = AccentGreen,
    unfocusedLeadingIconColor = TextSecondary,
    focusedTrailingIconColor = AccentGreen,
    unfocusedTrailingIconColor = TextSecondary,
    selectionControlHandleColor = AccentGreen,
    selectionControlIndicatorColor = AccentGreen,
)
