package com.cloud.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.cloud.launcher.data.LauncherSettings

class BackgroundPreset(val top: Color, val bottom: Color)

val AccentPalette: List<Color> = listOf(
    Color(0xFF4FC3F7),
    Color(0xFF69F0AE),
    Color(0xFFB388FF),
    Color(0xFFFF80AB),
    Color(0xFFFFAB40),
    Color(0xFFFFE57F),
    Color(0xFFFF6E6E),
    Color(0xFFF5F5F5)
)

val BackgroundPalette: List<BackgroundPreset> = listOf(
    BackgroundPreset(Color(0xFF0A2236), Color(0xFF000000)),
    BackgroundPreset(Color(0xFF000000), Color(0xFF000000)),
    BackgroundPreset(Color(0xFF1E1038), Color(0xFF000000)),
    BackgroundPreset(Color(0xFF3A1420), Color(0xFF050205)),
    BackgroundPreset(Color(0xFF0B2A1C), Color(0xFF000000)),
    BackgroundPreset(Color(0xFF2A2C33), Color(0xFF0B0B0D)),
    BackgroundPreset(Color(0xFF3B1E0A), Color(0xFF000000))
)

val GlassTintPalette: List<Color?> = listOf(
    Color(0xFFFFFFFF),
    null,
    Color(0xFF4FC3F7),
    Color(0xFFB388FF),
    Color(0xFFFF80AB),
    Color(0xFF69F0AE),
    Color(0xFF000000)
)

object ThemeState {
    var accent by mutableStateOf(AccentPalette[0])
    var bgTop by mutableStateOf(BackgroundPalette[0].top)
    var bgBottom by mutableStateOf(BackgroundPalette[0].bottom)

    fun apply(settings: LauncherSettings) {
        accent = AccentPalette[settings.accentIndex.coerceIn(0, AccentPalette.lastIndex)]
        val bg = BackgroundPalette[settings.backgroundIndex.coerceIn(0, BackgroundPalette.lastIndex)]
        bgTop = bg.top
        bgBottom = bg.bottom
    }
}

@Composable
fun rememberThemeBrush(): Brush {
    val top = ThemeState.bgTop
    val bottom = ThemeState.bgBottom
    return remember(top, bottom) {
        Brush.verticalGradient(0f to top, 0.5f to bottom, 1f to bottom)
    }
}
