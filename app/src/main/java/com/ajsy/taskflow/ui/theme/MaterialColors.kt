package com.ajsy.taskflow.ui.theme

import androidx.compose.ui.graphics.Color

data class MaterialColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color = Color.Transparent,
    val surface: Color = Color.Transparent,
    val surfaceVariant: Color = Color.Transparent,
    val onPrimary: Color = Color.White,
    val onSecondary: Color = Color.Black,
    val onTertiary: Color = Color.White,
    val onBackground: Color = Color.Black,
    val onSurface: Color = Color.Black,
    val error: Color = Color.Red,
    val onError: Color = Color.White,
)
