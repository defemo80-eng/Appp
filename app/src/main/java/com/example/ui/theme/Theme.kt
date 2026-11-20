package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private fun buildDeiPlayDarkScheme(oledBlack: Boolean) = darkColorScheme(
    primary = DeiPlayRed,
    onPrimary = DeiPlayWhite,
    primaryContainer = DeiPlayRedDeep,
    onPrimaryContainer = DeiPlayWhite,
    secondary = DeiPlayGold,
    onSecondary = DeiPlayBlack,
    secondaryContainer = DeiPlaySurfaceHigh,
    onSecondaryContainer = DeiPlayGoldSoft,
    tertiary = DeiPlayRedBright,
    onTertiary = DeiPlayWhite,
    background = if (oledBlack) DeiPlayOledBlack else DeiPlayBlack,
    onBackground = DeiPlayWhite,
    surface = if (oledBlack) DeiPlayOledBlack else DeiPlaySurface,
    onSurface = DeiPlayWhite,
    surfaceVariant = DeiPlaySurfaceVariant,
    onSurfaceVariant = DeiPlayTextSecondary,
    surfaceContainerLowest = DeiPlayOledBlack,
    surfaceContainerLow = DeiPlaySurface,
    surfaceContainer = DeiPlaySurfaceVariant,
    surfaceContainerHigh = DeiPlaySurfaceHigh,
    outline = DeiPlayOutline,
    outlineVariant = DeiPlayOutline,
    error = DeiPlayRedBright,
    onError = DeiPlayWhite
)

val DeiPlayShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DeiPlayTheme(
    oledBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = buildDeiPlayDarkScheme(oledBlack),
        typography = DeiPlayTypography,
        shapes = DeiPlayShapes,
        content = content
    )
}
