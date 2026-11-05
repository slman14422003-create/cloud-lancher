package com.cloud.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.cloud.launcher.R
import com.cloud.launcher.ui.theme.CloudAccent
import com.cloud.launcher.ui.theme.CloudWhite

private val MenuOrigin = TransformOrigin(1f, 0f)

@Composable
fun HomeMenuButton(
    onOpenLibrary: () -> Unit,
    onOpenRecents: () -> Unit,
    onOpenProtected: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = open
    val density = LocalDensity.current
    val gapPx = with(density) { 46.dp.roundToPx() }

    Box(
        modifier = modifier
            .size(38.dp)
            .liquidGlass(CircleShape)
            .clickable { open = !open },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = CloudWhite)

        if (visibleState.currentState || visibleState.targetState) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, gapPx),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = scaleIn(
                        transformOrigin = MenuOrigin,
                        initialScale = 0.6f,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
                    ) + fadeIn(tween(140)),
                    exit = scaleOut(
                        transformOrigin = MenuOrigin,
                        targetScale = 0.8f,
                        animationSpec = tween(130)
                    ) + fadeOut(tween(110))
                ) {
                    MenuCard {
                        MenuRow(Icons.Filled.Apps, stringResource(R.string.home_library)) {
                            open = false
                            onOpenLibrary()
                        }
                        MenuDivider()
                        MenuRow(Icons.Filled.History, stringResource(R.string.home_recents)) {
                            open = false
                            onOpenRecents()
                        }
                        MenuDivider()
                        MenuRow(Icons.Filled.Security, stringResource(R.string.home_protected)) {
                            open = false
                            onOpenProtected()
                        }
                        MenuDivider()
                        MenuRow(Icons.Filled.Tune, stringResource(R.string.home_launcher_settings)) {
                            open = false
                            onOpenLauncherSettings()
                        }
                        MenuDivider()
                        MenuRow(Icons.Filled.Settings, stringResource(R.string.home_system_settings)) {
                            open = false
                            onOpenSystemSettings()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .width(220.dp)
            .shadow(10.dp, shape, clip = false)
            .clip(shape)
            .background(Color(0xF2262628))
            .border(0.5.dp, Color(0x33FFFFFF), shape)
    ) {
        content()
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = CloudWhite,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Icon(icon, contentDescription = null, tint = CloudAccent, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Color(0x1FFFFFFF))
    )
}
