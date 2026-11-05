package com.cloud.launcher.ui.screens

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloud.launcher.R
import com.cloud.launcher.data.AppInfo
import com.cloud.launcher.root.RunningApp
import com.cloud.launcher.ui.LauncherViewModel
import com.cloud.launcher.ui.components.AppContextMenu
import com.cloud.launcher.ui.components.AppIcon
import com.cloud.launcher.ui.components.liquidGlass
import com.cloud.launcher.ui.theme.CloudAccent
import com.cloud.launcher.ui.theme.CloudBlack
import com.cloud.launcher.ui.theme.CloudDanger
import com.cloud.launcher.ui.theme.CloudGray
import com.cloud.launcher.ui.theme.CloudSuccess
import com.cloud.launcher.ui.theme.CloudWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MB = 1024L * 1024L
private val CloudAmber = Color(0xFFFFB74D)

private enum class RecentFilter { ALL, RUNNING, PROTECTED }

private enum class RecentSort { RECENT, RAM }

@Composable
private fun rememberMemory(): State<Pair<Int, Int>> {
    val context = LocalContext.current
    return produceState(initialValue = 0 to 0) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        while (true) {
            am.getMemoryInfo(info)
            value = (info.availMem / MB).toInt() to (info.totalMem / MB).toInt()
            delay(3_000L)
        }
    }
}

private fun ramColor(ratio: Float): Color = when {
    ratio < 0.5f -> CloudSuccess
    ratio < 0.8f -> CloudAmber
    else -> CloudDanger
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomRecentsScreen(vm: LauncherViewModel, onBack: () -> Unit) {
    val recentPkgs by vm.recentPkgs.collectAsStateWithLifecycle()
    val appMap by vm.appMap.collectAsStateWithLifecycle()
    val protectedPkgs by vm.protectedPkgs.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val root by vm.rootAvailable.collectAsStateWithLifecycle()
    val closing by vm.closing.collectAsStateWithLifecycle()
    val killingAll by vm.killingAll.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val memory by rememberMemory()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var filter by rememberSaveable { mutableStateOf(RecentFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(RecentSort.RECENT) }
    var hidden by remember { mutableStateOf(setOf<String>()) }

    val items = remember(recentPkgs, appMap) { recentPkgs.mapNotNull { appMap[it] } }
    val visible = remember(items, running, protectedPkgs, filter, sort) {
        val filtered = when (filter) {
            RecentFilter.ALL -> items
            RecentFilter.RUNNING -> items.filter { running.containsKey(it.packageName) }
            RecentFilter.PROTECTED -> items.filter { it.packageName in protectedPkgs }
        }
        if (sort == RecentSort.RAM) {
            filtered.sortedByDescending { running[it.packageName]?.rssKb ?: -1L }
        } else {
            filtered
        }
    }
    val runningCount = remember(items, running) { items.count { running.containsKey(it.packageName) } }
    val appsRamMb = remember(items, running) { items.sumOf { running[it.packageName]?.rssMb ?: 0L } }
    val maxRss = remember(running) { (running.values.maxOfOrNull { it.rssKb } ?: 1L).coerceAtLeast(1L) }
    val killable = remember(items, protectedPkgs) { items.any { it.packageName !in protectedPkgs } }

    LaunchedEffect(Unit) {
        vm.refreshPermissions()
        vm.syncRecents()
        while (true) {
            vm.refreshRunning()
            delay(4_000L)
        }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            launch {
                val result = snackbar.showSnackbar(
                    message = message.text,
                    actionLabel = message.actionLabel,
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) message.action?.invoke()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF06141D), CloudBlack, CloudBlack)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            RecentsHeader(
                count = items.size,
                runningCount = runningCount,
                canKillAll = root == true && killable,
                killingAll = killingAll,
                onBack = onBack,
                onKillAll = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.killAll()
                }
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "memory") {
                    MemoryCard(
                        free = memory.first,
                        total = memory.second,
                        appsMb = appsRamMb,
                        runningCount = runningCount
                    )
                }

                if (root == false) {
                    item(key = "banner_root") {
                        InfoBanner(
                            title = stringResource(R.string.root_required),
                            body = null,
                            actionLabel = stringResource(R.string.banner_root_action),
                            tint = CloudDanger,
                            onAction = vm::requestRoot
                        )
                    }
                } else if (root == true && (!setup.usageAccess || !setup.accessibility)) {
                    item(key = "banner_tracking") {
                        InfoBanner(
                            title = stringResource(R.string.banner_tracking_title),
                            body = stringResource(R.string.banner_tracking_body),
                            actionLabel = stringResource(R.string.banner_fix),
                            tint = CloudAmber,
                            onAction = vm::grantAllViaRoot
                        )
                    }
                }

                if (items.isNotEmpty()) {
                    item(key = "filters") {
                        FilterRow(
                            filter = filter,
                            sort = sort,
                            onFilter = { filter = it },
                            onToggleSort = {
                                sort = if (sort == RecentSort.RECENT) RecentSort.RAM else RecentSort.RECENT
                            }
                        )
                    }
                }

                if (visible.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            text = stringResource(
                                if (items.isEmpty()) R.string.recents_empty else R.string.filter_empty
                            )
                        )
                    }
                }

                items(visible, key = { it.packageName }) { app ->
                    val pkg = app.packageName
                    val isProtected = pkg in protectedPkgs
                    AnimatedVisibility(
                        visible = pkg !in hidden,
                        exit = shrinkVertically(tween(260)) + fadeOut(tween(200)),
                        modifier = Modifier.animateItemPlacement()
                    ) {
                        RecentRow(
                            app = app,
                            run = running[pkg],
                            rootAvailable = root,
                            maxRss = maxRss,
                            isProtected = isProtected,
                            isClosing = pkg in closing,
                            onOpen = { vm.launchApp(pkg) },
                            onClose = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    if (vm.closeApp(pkg)) {
                                        hidden = hidden + pkg
                                        delay(280L)
                                        vm.removeFromRecentsNow(pkg)
                                        hidden = hidden - pkg
                                    }
                                }
                            },
                            onPin = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                vm.toggleProtect(pkg)
                            },
                            onSwiped = {
                                if (!isProtected) vm.forceStop(pkg)
                                vm.removeFromRecents(pkg)
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuApp = app
                            }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) { data -> GlassSnackbar(data) }
    }

    menuApp?.let { app -> AppContextMenu(app = app, vm = vm, onDismiss = { menuApp = null }) }
}

@Composable
private fun RecentsHeader(
    count: Int,
    runningCount: Int,
    canKillAll: Boolean,
    killingAll: Boolean,
    onBack: () -> Unit,
    onKillAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = CloudWhite)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.recents_title),
                color = CloudWhite,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.recents_subtitle, count, runningCount),
                color = CloudGray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        val shape = RoundedCornerShape(50)
        val canClick = canKillAll && !killingAll
        Row(
            modifier = Modifier
                .clip(shape)
                .background(
                    if (canKillAll) {
                        Brush.horizontalGradient(listOf(Color(0xFFFF8A80), CloudDanger))
                    } else {
                        Brush.horizontalGradient(listOf(CloudGray.copy(alpha = 0.22f), CloudGray.copy(alpha = 0.22f)))
                    }
                )
                .then(if (canClick) Modifier.clickable(onClick = onKillAll) else Modifier)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (killingAll) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Icon(Icons.Filled.ClearAll, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.kill_all),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun MemoryCard(free: Int, total: Int, appsMb: Long, runningCount: Int) {
    if (total <= 0) return
    val usedFraction = ((total - free).toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val animated by animateFloatAsState(usedFraction, tween(700), label = "memory")
    val color by animateColorAsState(ramColor(usedFraction), tween(500), label = "memoryColor")
    val track = CloudWhite.copy(alpha = 0.10f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(82.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Text(
                text = "${(animated * 100).toInt()}%",
                color = CloudWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.recents_ram, free, total),
                color = CloudWhite,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Memory, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.recents_apps_ram, appsMb),
                    color = CloudGray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(CloudSuccess))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.recents_running_label, runningCount),
                    color = CloudGray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun InfoBanner(
    title: String,
    body: String?,
    actionLabel: String,
    tint: Color,
    onAction: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.35f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = CloudWhite, style = MaterialTheme.typography.titleMedium)
            if (body != null) {
                Spacer(Modifier.height(2.dp))
                Text(body, color = CloudGray, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(tint)
                .clickable(onClick = onAction)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(actionLabel, color = CloudBlack, style = MaterialTheme.typography.titleMedium, fontSize = 14.sp)
        }
    }
}

@Composable
private fun FilterRow(
    filter: RecentFilter,
    sort: RecentSort,
    onFilter: (RecentFilter) -> Unit,
    onToggleSort: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChipItem(stringResource(R.string.filter_all), filter == RecentFilter.ALL) {
                onFilter(RecentFilter.ALL)
            }
        }
        item {
            FilterChipItem(stringResource(R.string.filter_running), filter == RecentFilter.RUNNING) {
                onFilter(RecentFilter.RUNNING)
            }
        }
        item {
            FilterChipItem(stringResource(R.string.filter_protected), filter == RecentFilter.PROTECTED) {
                onFilter(RecentFilter.PROTECTED)
            }
        }
        item {
            FilterChipItem(
                text = stringResource(if (sort == RecentSort.RAM) R.string.sort_ram else R.string.sort_recent),
                selected = sort == RecentSort.RAM,
                icon = if (sort == RecentSort.RAM) Icons.Filled.Sort else Icons.Filled.History,
                onClick = onToggleSort
            )
        }
    }
}

@Composable
private fun FilterChipItem(
    text: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (selected) CloudAccent.copy(alpha = 0.22f) else CloudWhite.copy(alpha = 0.07f),
        tween(180),
        label = "chipBg"
    )
    val fg = if (selected) CloudAccent else CloudGray
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) CloudAccent.copy(alpha = 0.55f) else CloudWhite.copy(alpha = 0.10f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyState(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(CloudAccent.copy(alpha = 0.25f), Color.Transparent))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.History, contentDescription = null, tint = CloudAccent, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = text,
            color = CloudGray,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun GlassSnackbar(data: SnackbarData) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xF01E1E20))
            .border(1.dp, Color(0x33FFFFFF), shape)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = data.visuals.message,
            color = CloudWhite,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp)
        )
        val label = data.visuals.actionLabel
        if (label != null) {
            TextButton(onClick = { data.performAction() }) {
                Text(label, color = CloudAccent, style = MaterialTheme.typography.titleMedium, fontSize = 14.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    app: AppInfo,
    run: RunningApp?,
    rootAvailable: Boolean?,
    maxRss: Long,
    isProtected: Boolean,
    isClosing: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onSwiped: () -> Unit,
    onLongPress: () -> Unit
) {
    val currentOnSwiped by rememberUpdatedState(onSwiped)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.Settled) {
                false
            } else {
                currentOnSwiped()
                true
            }
        }
    )
    val ratio = if (run != null) (run.rssKb.toFloat() / maxRss.toFloat()).coerceIn(0f, 1f) else 0f
    val barFraction by animateFloatAsState(ratio, tween(500), label = "rssBar")
    val barColor = ramColor(ratio)

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CloudDanger.copy(alpha = 0.35f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = CloudWhite)
            }
        },
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(RoundedCornerShape(22.dp))
                    .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                    AppIcon(packageName = app.packageName, size = 48.dp)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(CloudBlack)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(if (run != null) CloudSuccess else CloudGray.copy(alpha = 0.5f))
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        color = CloudWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(3.dp))
                    if (isClosing) {
                        Text(
                            text = stringResource(R.string.closing_app),
                            color = CloudDanger,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else if (run != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(text = "${run.rssMb} MB", color = barColor)
                            Spacer(Modifier.width(6.dp))
                            Pill(text = "PID ${run.pid}", color = CloudGray)
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(CloudWhite.copy(alpha = 0.10f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(barFraction.coerceAtLeast(0.04f))
                                    .clip(CircleShape)
                                    .background(barColor)
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(
                                if (rootAvailable == true) R.string.status_not_running else R.string.status_unknown
                            ),
                            color = CloudGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = onPin,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isProtected) CloudAccent.copy(alpha = 0.22f) else CloudWhite.copy(alpha = 0.07f))
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.pin_app),
                        tint = if (isProtected) CloudAccent else CloudGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onClose,
                    enabled = !isClosing,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isProtected) CloudWhite.copy(alpha = 0.05f) else CloudDanger.copy(alpha = 0.20f)
                        )
                ) {
                    if (isClosing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = CloudDanger
                        )
                    } else {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close_app),
                            tint = if (isProtected) CloudGray.copy(alpha = 0.45f) else CloudDanger,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun Pill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = color, style = MaterialTheme.typography.labelSmall, fontSize = 12.sp)
    }
}
