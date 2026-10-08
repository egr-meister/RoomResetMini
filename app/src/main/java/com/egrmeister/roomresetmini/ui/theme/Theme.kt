package com.egrmeister.roomresetmini.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Calm home-organizer palette. Text is charcoal on every surface (contrast ≥ 7:1 on cream/sand/sage/blue). */
object RoomColors {
    val Cream = Color(0xFFFBF4E8)
    val CreamSurface = Color(0xFFFFFCF6)
    val Charcoal = Color(0xFF2B2B2B)
    val CharcoalMuted = Color(0xFF55524C)

    val Sand = Color(0xFFE9DCC4)
    val SandLight = Color(0xFFF2EADB)
    val SandBorder = Color(0xFFB9A27A)
    val Wall = Color(0xFFCDB994)

    val Sage = Color(0xFFCFE3C8)
    val SageBorder = Color(0xFF7FA676)
    val SageDeep = Color(0xFF3F5E45)

    val PaleBlue = Color(0xFFD6E8F5)
    val BlueBorder = Color(0xFF7FA7C6)

    val Error = Color(0xFF9C3524)
}

private val Scheme = lightColorScheme(
    primary = RoomColors.SageDeep,
    onPrimary = Color.White,
    primaryContainer = RoomColors.Sage,
    onPrimaryContainer = RoomColors.Charcoal,
    secondary = Color(0xFF6B5A3A),
    onSecondary = Color.White,
    secondaryContainer = RoomColors.Sand,
    onSecondaryContainer = RoomColors.Charcoal,
    tertiary = Color(0xFF34607F),
    onTertiary = Color.White,
    tertiaryContainer = RoomColors.PaleBlue,
    onTertiaryContainer = RoomColors.Charcoal,
    background = RoomColors.Cream,
    onBackground = RoomColors.Charcoal,
    surface = RoomColors.CreamSurface,
    onSurface = RoomColors.Charcoal,
    surfaceVariant = RoomColors.SandLight,
    onSurfaceVariant = RoomColors.CharcoalMuted,
    surfaceContainer = RoomColors.CreamSurface,
    surfaceContainerLow = RoomColors.CreamSurface,
    surfaceContainerHigh = RoomColors.SandLight,
    surfaceContainerHighest = RoomColors.Sand,
    outline = RoomColors.SandBorder,
    outlineVariant = RoomColors.Wall,
    error = RoomColors.Error,
    onError = Color.White,
)

private val RoomShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun RoomResetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, shapes = RoomShapes, content = content)
}
