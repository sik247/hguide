package com.hguide.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val Dark = darkColorScheme(primary = Color(0xFF7FB2FF), secondary = Color(0xFFFFC857), background = Color(0xFF0E1116), surface = Color(0xFF171B22))
private val Light = lightColorScheme(primary = Color(0xFF1E5FD1), secondary = Color(0xFFB36B00))

// In-car legibility: everything one step larger than the Material defaults.
private val CarType = Typography(
    bodyLarge = TextStyle(fontSize = 20.sp), bodyMedium = TextStyle(fontSize = 18.sp),
    titleLarge = TextStyle(fontSize = 28.sp), titleMedium = TextStyle(fontSize = 22.sp),
    labelLarge = TextStyle(fontSize = 18.sp),
)

@Composable
fun HguideTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = CarType, content = content)
