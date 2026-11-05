package com.cloud.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.cloud.launcher.data.GlassType
import com.cloud.launcher.ui.theme.GlassTintPalette
import com.cloud.launcher.ui.theme.LocalLauncherSettings
import com.cloud.launcher.ui.theme.ThemeState

fun Modifier.liquidGlass(shape: Shape): Modifier = composed {
    val settings = LocalLauncherSettings.current
    val accent = ThemeState.accent
    val base = GlassTintPalette[settings.glassTintIndex.coerceIn(0, GlassTintPalette.lastIndex)] ?: accent
    val dark = base.luminance() < 0.1f
    val k = (0.4f + settings.glassIntensity.coerceIn(0f, 1f) * 1.2f) * (if (dark) 2f else 1f)
    val edge = if (dark) Color.White else lerp(Color.White, base, 0.35f)

    fun a(value: Float): Float = (value * k).coerceIn(0f, 1f)

    when (settings.glassType) {
        GlassType.LIQUID -> this
            .clip(shape)
            .background(Brush.verticalGradient(listOf(base.copy(alpha = a(0.25f)), base.copy(alpha = a(0.10f)))))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(edge.copy(alpha = 0.5f), edge.copy(alpha = 0.12f), edge.copy(alpha = 0.33f))
                ),
                shape = shape
            )

        GlassType.FROST -> this
            .clip(shape)
            .background(base.copy(alpha = a(0.16f)))
            .border(0.8.dp, edge.copy(alpha = 0.18f), shape)

        GlassType.CLEAR -> this
            .clip(shape)
            .background(base.copy(alpha = a(0.04f)))
            .border(1.2.dp, edge.copy(alpha = 0.4f), shape)

        GlassType.SOLID -> this
            .clip(shape)
            .background(
                lerp(base, Color(0xFF14161A), 0.8f)
                    .copy(alpha = (0.55f + settings.glassIntensity.coerceIn(0f, 1f) * 0.4f).coerceIn(0f, 1f))
            )
            .border(0.8.dp, edge.copy(alpha = 0.14f), shape)
    }
}
