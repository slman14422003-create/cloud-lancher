package com.cloud.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private val GlassFill: Brush = Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x1AFFFFFF)))

private val GlassBorder: Brush =
    Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x1FFFFFFF), Color(0x55FFFFFF)))

fun Modifier.liquidGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(GlassFill)
    .border(width = 1.dp, brush = GlassBorder, shape = shape)
