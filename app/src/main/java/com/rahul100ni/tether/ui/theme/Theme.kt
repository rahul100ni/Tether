package com.rahul100ni.tether.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// ── Force dark-only — no light theme variant ──────────────────────────────
private val TetherColorScheme = darkColorScheme(
    primary              = TetherNeon,
    onPrimary            = TetherOnNeon,
    primaryContainer     = TetherNeonContainer,
    onPrimaryContainer   = TetherNeon,
    secondary            = TetherNeonDim,
    onSecondary          = TetherOnNeon,
    secondaryContainer   = TetherNeonContainer,
    onSecondaryContainer = TetherBrightText,
    background           = TetherBlack,
    onBackground         = TetherBrightText,
    surface              = TetherSurface,
    onSurface            = TetherOnSurface,
    surfaceVariant       = TetherSurfaceVar,
    onSurfaceVariant     = TetherMutedText,
    outline              = TetherOutline,
    outlineVariant       = TetherOutlineVar,
    error                = TetherError,
    onError              = TetherOnError,
    errorContainer       = TetherErrorContainer,
    onErrorContainer     = TetherOnErrorContainer,
)

// ── Shapes — sharp cards, large-radius buttons ────────────────────────────
val TetherShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TetherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TetherColorScheme,
        typography  = Typography,
        shapes      = TetherShapes,
        content     = content
    )
}
