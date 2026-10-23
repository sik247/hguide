package com.hguide.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Design tokens from images/home-redesign-v3 + screens-v1: midnight navy, hairline cobalt, amber action, turquoise accent. */
object Hg {
    val Bg = Color(0xFF061128)
    val BgTop = Color(0xFF0A1A3A)
    val Panel = Color(0xFF0F2350)
    val PanelDeep = Color(0xFF0A1A3D)
    val Line = Color(0xFF2B5299)
    val LineSoft = Color(0xFF1C3566)
    val Amber = Color(0xFFFFC94D)
    val AmberDeep = Color(0xFFF7A928)
    val OnAmber = Color(0xFF0A1530)
    val Cyan = Color(0xFF3EE3D3)
    val Blue = Color(0xFF3D7BFF)
    val Text = Color(0xFFF2F5FF)
    val Muted = Color(0xFF8DA4D4)
    val Dim = Color(0xFF586C99)
    val Rec = Color(0xFFFF5A5F)
}

// In-car legibility at arm's length: large, few sizes.
private val CarType = Typography(
    displayMedium = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold, lineHeight = 66.sp),
    headlineMedium = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 50.sp),
    titleLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp),
    titleMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    bodyLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontSize = 19.sp, lineHeight = 26.sp),
    labelLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
)

private val Scheme = darkColorScheme(
    primary = Hg.Amber, onPrimary = Hg.OnAmber, secondary = Hg.Cyan, background = Hg.Bg, surface = Hg.Panel,
    onBackground = Hg.Text, onSurface = Hg.Text, onSurfaceVariant = Hg.Muted, outline = Hg.Line, error = Hg.Rec,
)

/** Car UI is always dark: night driving and the reference designs are both navy. */
@Composable
fun HguideTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Scheme, typography = CarType, content = content)
