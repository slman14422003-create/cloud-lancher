package com.cloud.launcher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.cloud.launcher.ui.theme.CloudAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import com.cloud.launcher.ui.theme.CloudWhite
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloud.launcher.R
import com.cloud.launcher.data.AppInfo
import com.cloud.launcher.ui.LauncherViewModel
import com.cloud.launcher.ui.components.AppContextMenu
import com.cloud.launcher.ui.components.ClockWidget
import com.cloud.launcher.ui.components.HomeMenuButton
import com.cloud.launcher.ui.components.ReorderableAppGrid
import com.cloud.launcher.ui.components.liquidGlass
import com.cloud.launcher.ui.theme.CloudBlack
import com.cloud.launcher.ui.theme.CloudGray

private const val GRID_COLUMNS = 4

private val HomeBackground: Brush = Brush.verticalGradient(
    0f to Color(0xFF0A2236),
    0.5f to Color(0xFF000000),
    1f to Color(0xFF000000)
)

@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    onOpenDrawer: () -> Unit,
    onOpenRecents: () -> Unit,
    onOpenProtected: () -> Unit
) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val homePkgs by vm.homeApps.collectAsStateWithLifecycle()
    val appMap by vm.appMap.collectAsStateWithLifecycle()
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    val currentOpenDrawer by rememberUpdatedState(onOpenDrawer)
    var hintVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val alreadyShown = vm.homeHintShown.filterNotNull().first()
        if (!alreadyShown) {
            hintVisible = true
            vm.markHomeHintShown()
            delay(15_000L)
            hintVisible = false
        }
    }
    val favApps = remember(favorites, appMap) {
        favorites.mapNotNull { appMap[it] }.take(LauncherViewModel.MAX_FAVORITES)
    }
    val homeApps = remember(homePkgs, appMap) {
        homePkgs.mapNotNull { appMap[it] }.take(LauncherViewModel.MAX_HOME)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBackground)
            .drawWithCache {
                val glow = Brush.radialGradient(
                    colors = listOf(CloudAccent.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.10f),
                    radius = size.width * 0.8f
                )
                onDrawBehind { drawRect(glow) }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -120f) currentOpenDrawer() },
                        onVerticalDrag = { _, dy -> total += dy }
                    )
                }
        ) {
            ClockWidget(modifier = Modifier.padding(top = 48.dp, bottom = 8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (homeApps.isEmpty()) {
                    if (favApps.isEmpty()) {
                        AnimatedVisibility(
                            visible = hintVisible,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            EmptyHomeCard(onOpenDrawer = currentOpenDrawer)
                        }
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val rows = (homeApps.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                        val cellHeight = minOf(96.dp, maxHeight / rows)
                        ReorderableAppGrid(
                            apps = homeApps,
                            columns = GRID_COLUMNS,
                            cellHeight = cellHeight,
                            iconSize = if (cellHeight < 80.dp) 44.dp else 54.dp,
                            showLabels = cellHeight >= 72.dp,
                            centerPartialRow = false,
                            onClick = { vm.launchApp(it.packageName) },
                            onMenu = { menuApp = it },
                            onReorder = vm::setHomeOrder
                        )
                    }
                }
            }

            SearchPill(
                onClick = currentOpenDrawer,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
                    .liquidGlass(RoundedCornerShape(36.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (favApps.isEmpty()) {
                    Box(modifier = Modifier.height(64.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.home_favorites_empty),
                            color = CloudGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    ReorderableAppGrid(
                        apps = favApps,
                        columns = LauncherViewModel.MAX_FAVORITES,
                        cellHeight = 64.dp,
                        iconSize = 52.dp,
                        showLabels = false,
                        centerPartialRow = true,
                        onClick = { vm.launchApp(it.packageName) },
                        onMenu = { menuApp = it },
                        onReorder = vm::setFavoritesOrder
                    )
                }
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                HomeMenuButton(
                    onOpenLibrary = onOpenDrawer,
                    onOpenRecents = onOpenRecents,
                    onOpenProtected = onOpenProtected,
                    onOpenSystemSettings = { vm.openSystemSettings() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 20.dp)
                )
            }
        }
    }

    menuApp?.let { app -> AppContextMenu(app = app, vm = vm, onDismiss = { menuApp = null }) }
}

@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = CloudAccent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.search_hint),
            color = CloudGray,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun EmptyHomeCard(onOpenDrawer: () -> Unit, modifier: Modifier = Modifier) {
    val bounce = rememberInfiniteTransition(label = "hint")
    val offset by bounce.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "hintOffset"
    )
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .liquidGlass(shape)
            .clickable(onClick = onOpenDrawer)
            .padding(horizontal = 22.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(CloudAccent.copy(alpha = 0.35f), Color.Transparent))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Apps, contentDescription = null, tint = CloudAccent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.home_hint),
            color = CloudWhite,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = CloudAccent,
            modifier = Modifier.size(26.dp).graphicsLayer { translationY = offset }
        )
        Text(
            text = stringResource(R.string.home_swipe_hint),
            color = CloudGray,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
