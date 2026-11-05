package com.cloud.launcher.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val favApps = remember(favorites, appMap) {
        favorites.mapNotNull { appMap[it] }.take(LauncherViewModel.MAX_FAVORITES)
    }
    val homeApps = remember(homePkgs, appMap) {
        homePkgs.mapNotNull { appMap[it] }.take(LauncherViewModel.MAX_HOME)
    }

    Box(modifier = Modifier.fillMaxSize().background(CloudBlack)) {
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
                        Text(
                            text = stringResource(R.string.home_hint),
                            color = CloudGray,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp)
                        )
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

            Spacer(Modifier.height(8.dp))

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
                    onOpenSetup = { vm.openSetup() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 20.dp)
                )
            }
        }
    }

    menuApp?.let { app -> AppContextMenu(app = app, vm = vm, onDismiss = { menuApp = null }) }
}
