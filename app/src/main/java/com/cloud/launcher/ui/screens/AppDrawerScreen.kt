package com.cloud.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloud.launcher.R
import com.cloud.launcher.data.AppInfo
import com.cloud.launcher.data.DrawerBackground
import com.cloud.launcher.data.DrawerLayout
import com.cloud.launcher.ui.LauncherViewModel
import com.cloud.launcher.ui.components.AppContextMenu
import com.cloud.launcher.ui.components.AppIcon
import com.cloud.launcher.ui.components.liquidGlass
import com.cloud.launcher.ui.components.AppTile
import com.cloud.launcher.ui.components.CloudSearchBar
import com.cloud.launcher.ui.theme.CloudAccent
import com.cloud.launcher.ui.theme.CloudBlack
import com.cloud.launcher.ui.theme.CloudGray
import com.cloud.launcher.ui.theme.CloudWhite
import com.cloud.launcher.ui.theme.LocalLauncherSettings
import com.cloud.launcher.ui.theme.rememberThemeBrush

@Composable
fun AppDrawerScreen(vm: LauncherViewModel, onBack: () -> Unit) {
    val apps by vm.drawerApps.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sortByUsage by vm.sortByUsage.collectAsStateWithLifecycle()
    val protectedPkgs by vm.protectedPkgs.collectAsStateWithLifecycle()
    val settings = LocalLauncherSettings.current
    val themeBrush = rememberThemeBrush()
    val accent = CloudAccent
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }

    val backdrop: Brush = when (settings.drawerBackground) {
        DrawerBackground.THEME -> themeBrush
        DrawerBackground.BLACK -> Brush.verticalGradient(listOf(CloudBlack, CloudBlack))
        DrawerBackground.ACCENT -> Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), CloudBlack, CloudBlack))
    }

    BackHandler(enabled = query.isNotEmpty()) { vm.setQuery("") }
    DisposableEffect(Unit) {
        onDispose { vm.setQuery("") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CloudSearchBar(
                query = query,
                onQueryChange = vm::setQuery,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.setSortByUsage(!sortByUsage) }) {
                Icon(
                    imageVector = if (sortByUsage) Icons.Filled.TrendingUp else Icons.Filled.SortByAlpha,
                    contentDescription = stringResource(if (sortByUsage) R.string.sort_usage else R.string.sort_az),
                    tint = if (sortByUsage) CloudAccent else CloudGray
                )
            }
        }

        if (settings.drawerLayout == DrawerLayout.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.drawerColumns.coerceIn(3, 6)),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppTile(
                        app = app,
                        onClick = { vm.launchApp(app.packageName) },
                        onLongClick = { menuApp = app },
                        showLockBadge = app.packageName in protectedPkgs,
                        iconSize = settings.drawerIconSize.dp,
                        showLabel = settings.drawerShowLabels
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            ) {
                listItems(apps, key = { it.packageName }) { app ->
                    AppListRow(
                        app = app,
                        iconSize = settings.drawerIconSize.dp,
                        locked = app.packageName in protectedPkgs,
                        onClick = { vm.launchApp(app.packageName) },
                        onLongClick = { menuApp = app }
                    )
                }
            }
        }
    }

    menuApp?.let { app -> AppContextMenu(app = app, vm = vm, onDismiss = { menuApp = null }) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListRow(
    app: AppInfo,
    iconSize: androidx.compose.ui.unit.Dp,
    locked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .liquidGlass(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(packageName = app.packageName, size = iconSize.coerceAtMost(56.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = app.label,
            color = CloudWhite,
            maxLines = 1,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (locked) {
            Box(
                modifier = Modifier.size(22.dp).clip(CircleShape).background(CloudAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = CloudBlack, modifier = Modifier.size(13.dp))
            }
        }
    }
}
