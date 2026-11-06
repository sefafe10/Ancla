package com.ancla.safespace.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AnclaColors {
    val Canvas = Color(0xFFFFF9F1)
    val Surface = Color(0xFFFFFEFC)
    val Ink = Color(0xFF283B3A)
    val MutedInk = Color(0xFF667572)
    val Teal = Color(0xFF3D786B)
    val TealTint = Color(0xFFE1F0E9)
    val Peach = Color(0xFFF3C9AD)
    val PeachTint = Color(0xFFFFEDE1)
    val Gold = Color(0xFFE5B75F)
    val GoldTint = Color(0xFFFFF2D5)
    val Lilac = Color(0xFF8F83AE)
    val LilacTint = Color(0xFFEFEAF7)
    val Rose = Color(0xFFB95560)
    val RoseTint = Color(0xFFFCE9E8)
}

private val AnclaPalette = lightColorScheme(
    primary = AnclaColors.Teal,
    onPrimary = Color.White,
    secondary = AnclaColors.Gold,
    onSecondary = AnclaColors.Ink,
    tertiary = AnclaColors.Lilac,
    background = AnclaColors.Canvas,
    surface = AnclaColors.Surface,
    surfaceVariant = Color(0xFFF2EEE7),
    onBackground = AnclaColors.Ink,
    onSurface = AnclaColors.Ink,
    onSurfaceVariant = AnclaColors.MutedInk,
    error = AnclaColors.Rose,
    onError = Color.White,
)

@Composable
fun AnclaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AnclaPalette, content = content)
}
