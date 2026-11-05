package com.cloud.launcher.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.cloud.launcher.data.AppInfo
import com.cloud.launcher.ui.theme.CloudWhite
import kotlin.math.roundToInt

private class GridGeometry(
    val cellW: Float,
    val cellH: Float,
    val columns: Int,
    val rtl: Boolean,
    val centerPartialRow: Boolean
) {
    fun slot(index: Int, count: Int): Offset {
        return if (centerPartialRow && count <= columns) {
            val visual = if (rtl) count - 1 - index else index
            Offset(((columns - count) / 2f + visual) * cellW, 0f)
        } else {
            val col = index % columns
            val visual = if (rtl) columns - 1 - col else col
            Offset(visual * cellW, (index / columns) * cellH)
        }
    }

    fun indexAt(point: Offset, count: Int): Int {
        if (count <= 0) return 0
        return if (centerPartialRow && count <= columns) {
            val visual = ((point.x / cellW) - (columns - count) / 2f).toInt().coerceIn(0, count - 1)
            if (rtl) count - 1 - visual else visual
        } else {
            val visual = (point.x / cellW).toInt().coerceIn(0, columns - 1)
            val col = if (rtl) columns - 1 - visual else visual
            val row = (point.y / cellH).toInt().coerceAtLeast(0)
            (row * columns + col).coerceIn(0, count - 1)
        }
    }
}

@Composable
fun ReorderableAppGrid(
    apps: List<AppInfo>,
    columns: Int,
    cellHeight: Dp,
    iconSize: Dp,
    showLabels: Boolean,
    centerPartialRow: Boolean,
    onClick: (AppInfo) -> Unit,
    onMenu: (AppInfo) -> Unit,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptic = LocalHapticFeedback.current
    val keys = apps.map { it.packageName }
    val order = remember(keys) { keys.toMutableStateList() }
    val byPkg = apps.associateBy { it.packageName }
    var draggingPkg by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var lastDragEnd by remember { mutableLongStateOf(0L) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnMenu by rememberUpdatedState(onMenu)
    val currentOnReorder by rememberUpdatedState(onReorder)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cellW = maxWidth / columns
        val geometry = GridGeometry(
            cellW = with(density) { cellW.toPx() },
            cellH = with(density) { cellHeight.toPx() },
            columns = columns,
            rtl = rtl,
            centerPartialRow = centerPartialRow
        )
        val geometryState by rememberUpdatedState(geometry)
        val rows = maxOf(1, (order.size + columns - 1) / columns)

        Box(modifier = Modifier.fillMaxWidth().height(cellHeight * rows)) {
            order.forEachIndexed { index, pkg ->
                key(pkg) {
                    val app = byPkg[pkg]
                    if (app != null) {
                        val isDragging = draggingPkg == pkg
                        val slot = geometry.slot(index, order.size)
                        val target = IntOffset(slot.x.roundToInt(), slot.y.roundToInt())
                        val animated by animateIntOffsetAsState(
                            targetValue = target,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "slot"
                        )
                        val base = if (isDragging) target else animated
                        Box(
                            modifier = Modifier
                                .offset {
                                    if (isDragging) {
                                        IntOffset(
                                            base.x + dragOffset.x.roundToInt(),
                                            base.y + dragOffset.y.roundToInt()
                                        )
                                    } else {
                                        base
                                    }
                                }
                                .size(cellW, cellHeight)
                                .zIndex(if (isDragging) 10f else 0f)
                                .graphicsLayer {
                                    val scale = if (isDragging) 1.12f else 1f
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = if (isDragging) 0.92f else 1f
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (draggingPkg == null && SystemClock.uptimeMillis() - lastDragEnd > 350L) {
                                        currentOnClick(app)
                                    }
                                }
                                .pointerInput(pkg) {
                                    var moved = false
                                    var startOrder: List<String> = emptyList()
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            startOrder = order.toList()
                                            moved = false
                                            dragOffset = Offset.Zero
                                            draggingPkg = pkg
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount
                                            if (dragOffset.getDistance() > viewConfiguration.touchSlop) moved = true
                                            val g = geometryState
                                            val current = order.indexOf(pkg)
                                            if (current >= 0) {
                                                val origin = g.slot(current, order.size)
                                                val center = origin + dragOffset + Offset(g.cellW / 2f, g.cellH / 2f)
                                                val to = g.indexAt(center, order.size)
                                                if (to != current) {
                                                    order.add(to, order.removeAt(current))
                                                    val newOrigin = g.slot(to, order.size)
                                                    dragOffset += origin - newOrigin
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            val changed = order.toList() != startOrder
                                            val wasMoved = moved
                                            draggingPkg = null
                                            dragOffset = Offset.Zero
                                            lastDragEnd = SystemClock.uptimeMillis()
                                            if (changed) {
                                                currentOnReorder(order.toList())
                                            } else if (!wasMoved) {
                                                currentOnMenu(app)
                                            }
                                        },
                                        onDragCancel = {
                                            if (startOrder.isNotEmpty()) {
                                                order.clear()
                                                order.addAll(startOrder)
                                            }
                                            draggingPkg = null
                                            dragOffset = Offset.Zero
                                            lastDragEnd = SystemClock.uptimeMillis()
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AppIcon(packageName = pkg, size = iconSize)
                                if (showLabels) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = app.label,
                                        color = CloudWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
