package com.cloud.launcher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.cloud.launcher.data.LauncherSettings

val LocalLauncherSettings = staticCompositionLocalOf { LauncherSettings() }

private val CloudShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun CloudLauncherTheme(settings: LauncherSettings, content: @Composable () -> Unit) {
    val accent = ThemeState.accent
    val scheme = remember(accent) {
        darkColorScheme(
            primary = accent,
            onPrimary = CloudBlack,
            secondary = CloudGray,
            onSecondary = CloudBlack,
            background = CloudBlack,
            onBackground = CloudWhite,
            surface = CloudSurface,
            onSurface = CloudWhite,
            surfaceVariant = CloudSurfaceHigh,
            onSurfaceVariant = CloudGray,
            error = CloudDanger,
            outline = CloudOutline
        )
    }
    CompositionLocalProvider(LocalLauncherSettings provides settings) {
        MaterialTheme(
            colorScheme = scheme,
            typography = CloudTypography,
            shapes = CloudShapes,
            content = content
        )
    }
}
