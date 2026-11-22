package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LavBoxDarkColorScheme = darkColorScheme(
    primary = RoyalPurple,
    onPrimary = Color.White,
    primaryContainer = DeepAmethyst,
    onPrimaryContainer = SoftLilac,
    secondary = ElectricViolet,
    onSecondary = Color.White,
    secondaryContainer = ElevatedVioletSurface,
    onSecondaryContainer = LuminousLavender,
    tertiary = NeonOrchid,
    onTertiary = Color.White,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = MidnightVoid,
    onSurface = TextPrimary,
    surfaceVariant = VelvetSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = MidnightVoid,
    surfaceContainer = VelvetSurface,
    surfaceContainerHigh = GlassSurface,
    surfaceContainerHighest = ElevatedVioletSurface,
    outline = GlassBorder,
    outlineVariant = SubtleDivider,
    error = CrimsonAlert,
    onError = Color.White
)

val LavBoxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun LavBoxTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = LavBoxDarkColorScheme,
        typography = LavBoxTypography,
        shapes = LavBoxShapes,
        content = content
    )
}
